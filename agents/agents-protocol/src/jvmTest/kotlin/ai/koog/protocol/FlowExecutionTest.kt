package ai.koog.protocol

import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.flow.KoogFlow
import ai.koog.protocol.parser.FlowJsonConfigParser
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.SAME_THREAD)
class FlowExecutionTest : FlowTestBase() {

    companion object {
        private const val TEST_PORT = 3002
        private val testServer = TestMcpServer(TEST_PORT)

        @BeforeAll
        @JvmStatic
        fun setup() {
            testServer.start()
            // Wait for server to start
            Thread.sleep(1000)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            testServer.stop()
        }
    }

    @Test
    fun testFlowRun_randomNumbersFlowJson() = runTest {
        val jsonContent = readFlow("random_koog_agent_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Create finalize tool for mocking with FlowAgentInput type
        val finalizeTool = SubgraphWithTaskUtils.finishTool<FlowAgentInput>()

        // Mock executor: first agent returns "42 58", second returns "100"
        val testExecutor = getMockExecutor {
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("42 58")) onCondition { request ->
                request.contains("Generate two random numbers")
            }
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("100")) onCondition { request ->
                // Only match the second agent's requests (not the first one)
                !request.contains("Generate two random numbers")
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
    fun testFlowRun_withMcpToolExecution() = runTest(timeout = 60.seconds) {
        val jsonContent = readFlow("greeting_flow_with_mcp_tool.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify tools were parsed correctly
        assertEquals(1, flowConfig.tools.size)

        // Create the flow with MCP tools from config
        val flow = KoogFlow(
            id = flowConfig.id ?: "test-flow",
            agents = flowConfig.agents,
            tools = flowConfig.tools,
            transitions = flowConfig.transitions,
            defaultModel = flowConfig.defaultModel,
            promptExecutor = null // Will use default executor
        )

        // Build tool registry and verify MCP tools are available
        val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                flow.buildToolRegistryForTest()
            }
        }

        // Verify that MCP tools from the server are available
        assertTrue(toolRegistry.tools.isNotEmpty(), "Tool registry should contain MCP tools")

        // Find the greeting tool
        val greetingTool = toolRegistry.getToolOrNull("greeting")
        assertTrue(greetingTool != null, "Greeting tool should be available")
        assertIs<McpTool>(greetingTool)

        // Execute the greeting tool directly
        val args = buildJsonObject { put("name", "TestUser") }
        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                greetingTool.execute(args)
            }
        }

        // Verify the greeting tool result
        val content = result.content.single() as TextContent
        assertEquals("Hello, TestUser!", content.text)
    }
}
