package ai.koog.integration.tests.features

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.mcp.feature.Mcp
import ai.koog.agents.features.mcp.feature.McpFeatureConfig
import ai.koog.agents.features.mcp.feature.McpServerInfo
import ai.koog.agents.features.mcp.feature.sseClientTransport
import ai.koog.agents.mcp.server.addTool
import ai.koog.agents.testing.network.NetUtil.isPortAvailable
import ai.koog.agents.testing.tools.MockLLMBuilder
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.kotest.matchers.booleans.shouldBeTrue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

const val McpServerPort = 3001
const val McpServerName = "TestServer"
const val McpServerVersion = "dev"
const val McpServerInstructions = "instructions"

suspend fun startMcpServer(
    tools: ToolRegistry
) = startMcpServer(McpServerPort, tools)

suspend fun startMcpServer(
    port: Int,
    tools: ToolRegistry,
): Server {
    val server = Server(
        serverInfo = Implementation(McpServerName, McpServerVersion),
        options = ServerOptions(ServerCapabilities(ServerCapabilities.Tools(listChanged = true))),
        instructions = McpServerInstructions
    )

    tools.tools.forEach { tool ->
        server.addTool(tool)
    }

    val embeddedServer = embeddedServer(factory = Netty, host = "localhost", port = port) {
        mcp { server }
    }
    server.onClose { embeddedServer.stop(1000, 1000) }
    embeddedServer.startSuspend(wait = false)
    delay(1.seconds)
    embeddedServer
    return server
}

/**
 * Closes the MCP server and waits for the port to become available.
 */
suspend fun closeMcpServer(server: Server, port: Int) {
    server.close()

    withContext(Dispatchers.Default.limitedParallelism(1)) {
        RetryUtils.withRetry {
            isPortAvailable(port).shouldBeTrue()
        }
    }
}

fun createMcpAgent(
    toolRegistry: ToolRegistry = ToolRegistry.empty(),
    mcpBuilder: McpFeatureConfig.() -> Unit = {},
    builder: MockLLMBuilder.() -> Unit = {},
) = createMcpAgent(McpServerPort, toolRegistry, mcpBuilder, builder)

/**
 * Creates an agent with MCP feature installed (without OpenTelemetry).
 */
fun createMcpAgent(
    port: Int,
    toolRegistry: ToolRegistry = ToolRegistry.empty(),
    mcpBuilder: McpFeatureConfig.() -> Unit = {},
    builder: MockLLMBuilder.() -> Unit = {},
): AIAgent<String, String> {
    return AIAgent(
        promptExecutor = getMockExecutor {
            builder(this)
        },
        toolRegistry = toolRegistry,
        strategy = singleRunStrategy(),
        llmModel = OpenAIModels.Chat.GPT4o,
    ) {
        install(Mcp) {
            addMcpServerFromTransport(
                transport = sseClientTransport(port),
                serverInfo = McpServerInfo(McpServerName, "http://localhost:$port", port)
            )
            mcpBuilder()
        }
    }
}

fun sseClientTransport(port: Int): SseClientTransport {
    return SseClientTransport(
        HttpClient { install(SSE) },
        "http://localhost:$port"
    )
}

fun runTestWithTimeout(body: suspend () -> Unit) = runTest {
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        withTimeout(60.seconds) {
            body()
        }
    }
}
