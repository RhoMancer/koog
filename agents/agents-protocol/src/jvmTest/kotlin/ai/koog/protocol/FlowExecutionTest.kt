package ai.koog.protocol

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.flow.KoogFlow
import ai.koog.protocol.mock.TestMcpServer
import ai.koog.protocol.parser.FlowJsonConfigParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FlowExecutionTest : FlowTestBase() {

    companion object {

        private val logger = KotlinLogging.logger { }

        /**
         * A mock tool that matches the MCP greeting tool's signature.
         * This is used to tell the mock LLM to call the greeting tool with specific arguments.
         * The actual tool execution will be done by the real MCP tool from the registry.
         */
        private val greetingToolMock = object : Tool<JsonObject, String>(
            argsSerializer = JsonObject.serializer(),
            resultSerializer = String.serializer(),
            descriptor = ToolDescriptor(
                name = "greeting",
                description = "A simple greeting tool",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "name",
                        type = ToolParameterType.String,
                        description = "A name to greet"
                    )
                )
            )
        ) {
            override suspend fun execute(args: JsonObject): String {
                throw UnsupportedOperationException("Mock tool should not be executed directly")
            }
        }
    }

    @Test
    fun testFlowRun_randomNumbersFlowJson() = runTest {
        val jsonContent = readFlow("random_koog_agent_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val generateNumbersAgentTask = "Generate two random numbers between 1 and 100. Output them with a space between them."
        val calculatorAgentTask = "Your task is to sum all individual numbers in the input string. Numbers are separated by spaces."

        // Finalized tool for mocking with FlowAgentInput type
        val finalizeTool = SubgraphWithTaskUtils.finishTool<FlowAgentInput>()

        // Mock executor: the first agent returns "42 58", the second returns "100"
        val testExecutor = getMockExecutor {
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("42 58")) onCondition { request ->
                request.contains(generateNumbersAgentTask)
            }
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("100")) onCondition { request ->
                request.contains(calculatorAgentTask)
            }
        }

        val flow = KoogFlow(
            id = flowConfig.id ?: "test-flow",
            agents = flowConfig.agents,
            tools = emptyList(),
            transitions = flowConfig.transitions,
            defaultModel = flowConfig.defaultModel,
            promptExecutor = testExecutor
        )

        val result = flow.run()

        // Verify the result is the sum from the calculator agent
        assertIs<FlowAgentInput.InputString>(result)
        assertEquals("100", result.data)
    }

    @Test
    fun testFlowRun_withMcpToolExecution() = runTest(timeout = 30.seconds) {
        val jsonContent = readFlow("greeting_flow_with_mcp_tool.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val taskInput = "Use the greeting tool to greet the user named 'TestUser'"

        assertEquals(1, flowConfig.tools.size, "Check tools were parsed from JSON")

        // Finalized tool for mocking with FlowAgentInput type
        val finalizeTool = SubgraphWithTaskUtils.finishTool<FlowAgentInput>()

        val testExecutor = getMockExecutor {
            // When asked to greet, call the greeting tool
            mockLLMToolCall(
                greetingToolMock,
                buildJsonObject { put("name", "TestUser") }
            ) onCondition { request ->
                request.contains(taskInput)
            }

            // After getting a tool result, finalize with the greeting
            mockLLMToolCall(
                finalizeTool,
                FlowAgentInput.InputString("Hello, TestUser!")
            ) onCondition { request ->
                request.contains("Hello, TestUser!")
            }
        }

        val flow = KoogFlow(
            id = flowConfig.id ?: "test-flow",
            agents = flowConfig.agents,
            tools = flowConfig.tools,
            transitions = flowConfig.transitions,
            defaultModel = flowConfig.defaultModel,
            promptExecutor = testExecutor
        )

        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withMcpServer(port = 3002) { _ ->
                flow.run()
            }
        }

        assertIs<FlowAgentInput.InputString>(result)
        assertTrue(result.data.contains("Hello, TestUser!"), "Result should contain greeting: ${result.data}")
    }

    //region Private Methods

    private suspend fun withMcpServer(port: Int, block: suspend (mcpServer: TestMcpServer) -> FlowAgentInput): FlowAgentInput {
        val mcpServer = TestMcpServer(port)
        try {
            logger.info { "Starting MCP server on port $port" }
            mcpServer.start()
            delay(1.seconds)
            return block(mcpServer)
        } finally {
            logger.info { "Stopping MCP server" }
            mcpServer.stop()
        }
    }

    //endregion Private Methods
}
