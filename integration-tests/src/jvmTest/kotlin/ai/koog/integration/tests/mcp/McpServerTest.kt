package ai.koog.integration.tests.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.handler.tool.McpTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.mcp.feature.Mcp
import ai.koog.agents.features.mcp.feature.McpServerInfo
import ai.koog.agents.mcp.server.startSseMcpServer
import ai.koog.agents.testing.network.NetUtil.isPortAvailable
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.netty.Netty
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class McpServerTest {

    companion object {
        @JvmStatic
        fun getModels() = listOf(
            OpenAIModels.Chat.GPT4o,
            // Enable when fixed: KG-588 singleRunStrategy outputs empty response when using an MCP server
            // GoogleModels.Gemini2_5FlashLite
        )
    }

    @OptIn(InternalAgentsApi::class)
    @ParameterizedTest
    @MethodSource("getModels")
    fun integration_testMcpServerWithSSETransport(model: LLModel) = runTest(timeout = 1.minutes) {
        val randomNumberTool = RandomNumberTool()
        randomNumberTool.shouldNotBeTypeOf<McpTool<*, *>>()

        val (server, connectors) = startSseMcpServer(
            factory = Netty,
            tools = ToolRegistry.Companion {
                tool(randomNumberTool)
            },
        )

        val port = connectors.firstOrNull()?.port ?: 0
        port shouldNotBe 0

        try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(40.seconds) {
                    val aiAgent = AIAgent(
                        promptExecutor = SingleLLMPromptExecutor(getLLMClientForProvider(model.provider)),
                        strategy = singleRunStrategy(),
                        llmModel = model,
                    ) {
                        install(Mcp) {
                            addMcpServerFromTransport(
                                transport = SseClientTransport(HttpClient {
                                    install(SSE)
                                }, "http://localhost:$port"),
                                serverInfo = McpServerInfo("http://localhost:$port", "http://localhost:$port", port)
                            )
                        }
                    }
                    val result = aiAgent.run(
                        agentInput = "Provide random number using ${randomNumberTool.name}, YOU MUST USE TOOLS!"
                    )
                    val toolRegistry = (aiAgent as GraphAIAgent).toolRegistry

                    toolRegistry.tools.map { it.descriptor }.shouldContainExactly(randomNumberTool.descriptor)
                    toolRegistry.tools.forEach { it.shouldBeTypeOf<McpTool<*, *>>() }
                    result.replace("[\\s,_]+".toRegex(), "").shouldContain(randomNumberTool.last.toString())
                }
            }
        } finally {
            server.close()

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                RetryUtils.withRetry {
                    isPortAvailable(port).shouldBeTrue()
                }
            }
        }
    }
}
