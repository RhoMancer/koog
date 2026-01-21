package ai.koog.agents.features.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.features.mcp.feature.Mcp
import ai.koog.agents.features.mcp.feature.toServerInfo
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.kotest.assertions.json.shouldEqualJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.SAME_THREAD)
class McpToolTest {
    companion object {
        private const val TEST_PORT = 3001
        private val testServer = TestMcpServer(TEST_PORT)

        @BeforeAll
        @JvmStatic
        fun setup() {
            testServer.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            testServer.stop()
        }
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun testMcpTools(action: suspend (toolRegistry: ToolRegistry) -> Unit) {
        val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                val agent = AIAgent(
                    systemPrompt = "You are a test agent.",
                    llmModel = OpenAIModels.Chat.GPT4oMini,
                    promptExecutor = getMockExecutor { },
                    installFeatures = {
                        install(Mcp) {
                            addMcpServerFromTransport(
                                serverInfo = testServer.serverMeta.toServerInfo(),
                                transport = SseClientTransport(HttpClient {
                                    install(SSE)
                                }, testServer.serverMeta.serverUrl),
                            )
                        }
                    }
                )
                agent.run("Start the test")
                (agent as GraphAIAgent).toolRegistry
            }
        }

        action(toolRegistry)
    }

    @Test
    fun `test McpToolRegistry provides all tools`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
            val expectedToolDescriptors = listOf(
                ToolDescriptor(
                    name = "greeting",
                    description = "A simple greeting tool",
                    requiredParameters = listOf(
                        ToolParameterDescriptor(
                            name = "name",
                            type = ToolParameterType.String,
                            description = "A name to greet",
                        )
                    ),
                    optionalParameters = listOf(
                        ToolParameterDescriptor(
                            name = "title",
                            type = ToolParameterType.String,
                            description = "Title to use in the greeting",
                        )
                    )
                ),
                ToolDescriptor(
                    name = "empty",
                    description = "An empty tool",
                )
            )

            // Actual list of tools provided
            val actualToolDescriptor = toolRegistry.tools.map { it.descriptor }
            assertEquals(expectedToolDescriptors, actualToolDescriptor)
        }
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun `test greeting tool`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
            val greetingTool = toolRegistry.getTool("greeting") as McpToolImpl
            val args = buildJsonObject { put("name", "Test") }

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    greetingTool.execute(args)
                }
            }

            val content = result.content.single() as TextContent
            assertEquals("Hello, Test!", content.text)

            val argsWithTitle = buildJsonObject {
                put("name", "Test")
                put("title", "Mr.")
            }
            val resultWithTitle = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    greetingTool.execute(argsWithTitle)
                }
            }

            val contentWithTitle = resultWithTitle.content.single() as TextContent
            assertEquals("Hello, Mr. Test!", contentWithTitle.text)

            val encodedResult = greetingTool.encodeResultToString(result)
            encodedResult shouldEqualJson """{"content":[{"text":"Hello, Test!","type":"text"}]}"""
        }
    }

    @Test
    fun `test empty tool`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
            val emptyTool = toolRegistry.getTool("empty") as McpToolImpl
            val args = EmptyJsonObject

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    emptyTool.execute(args)
                }
            }
            assertEquals(emptyList(), result.content.orEmpty())

            val encodedResult = emptyTool.encodeResultToString(result)
            encodedResult shouldEqualJson """{"content":[]}"""
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `test encode result`() {
        val result = CallToolResult(listOf(TextContent("Hello world")))
        val toolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )
        val mcpTool = McpToolImpl(
            mcpClient = Client(clientInfo = Implementation(name = "Test", version = "1.0")),
            descriptor = toolDescriptor,
            serverDescription = testServer.serverMeta
        )
        val encodedResult = mcpTool.encodeResultToString(result)

        encodedResult shouldEqualJson """{"content":[{"text":"Hello world","type":"text"}]}"""
    }

    @Test
    @OptIn(InternalAgentsApi::class)
    fun `test encode null result`() {
        val result: CallToolResult? = null
        val toolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )
        val mcpTool = McpToolImpl(
            mcpClient = Client(clientInfo = Implementation(name = "Test", version = "1.0")),
            descriptor = toolDescriptor,
            serverDescription = testServer.serverMeta
        )
        val encodedResult = mcpTool.encodeResultToString(result)

        assertEquals(
            expected = "null",
            actual = encodedResult
        )
    }
}
