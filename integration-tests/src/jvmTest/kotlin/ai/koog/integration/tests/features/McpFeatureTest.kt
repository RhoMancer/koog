package ai.koog.integration.tests.features

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.handler.tool.McpTool
import ai.koog.agents.core.feature.handler.tool.McpTransportType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.mcp.feature.McpServerInfo
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.integration.tests.utils.tools.CalculatorTool
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Integration tests for MCP (Model Context Protocol) feature.
 */
@Execution(ExecutionMode.SAME_THREAD)
class McpFeatureTest {

    private lateinit var server: Server
    private lateinit var serverTool: RandomNumberTool

    @BeforeEach
    fun cleanup() {
        server = runBlocking {
            serverTool = RandomNumberTool()
            startMcpServer(ToolRegistry { tool(serverTool) })
        }
    }

    @AfterEach
    fun teardown() {
        runBlocking {
            closeMcpServer(server, McpServerPort)
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should register and execute MCP tools via SSE transport`() = runTestWithTimeout {
        val aiAgent = createMcpAgent(McpServerPort) {
            mockLLMToolCall(RandomNumberTool(), RandomNumberTool.Args()) onRequestEquals "get random"
        }

        try {
            aiAgent.run("get random")

            // Verify tool was registered correctly
            val toolRegistry = (aiAgent as GraphAIAgent).toolRegistry
            toolRegistry.tools.map { it.descriptor }.shouldContainExactly(serverTool.descriptor)
            toolRegistry.tools.forEach { it.shouldBeInstanceOf<McpTool<*, *>>() }

            // Verify tool was actually called
            serverTool.results.size shouldBe 1
        } finally {
            aiAgent.close()
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should handle multiple MCP tool invocations`() = runTestWithTimeout {
        val aiAgent = createMcpAgent {
            mockLLMToolCall(
                listOf(
                    RandomNumberTool() to RandomNumberTool.Args(1),
                    RandomNumberTool() to RandomNumberTool.Args(2),
                    RandomNumberTool() to RandomNumberTool.Args(3),
                )
            ) onRequestEquals "Generate three random numbers"
        }
        aiAgent.run("Generate three random numbers")
        serverTool.results.size shouldBe 3
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should preserve MCP tool metadata`() = runTestWithTimeout {
        val aiAgent = createMcpAgent {
            mockLLMToolCall(serverTool, RandomNumberTool.Args()) onRequestEquals "get random"
        }

        aiAgent.run("get random")
        val toolRegistry = (aiAgent as GraphAIAgent).toolRegistry
        val mcpTool = toolRegistry.tools.first() as McpTool<*, *>

        // Verify MCP server metadata is preserved
        mcpTool.serverDescription.serverName shouldBe McpServerName
        mcpTool.serverDescription.serverUrl shouldBe "http://localhost:$McpServerPort"
        mcpTool.serverDescription.serverPort shouldBe McpServerPort
        mcpTool.serverDescription.instructions shouldBe McpServerInstructions
        mcpTool.serverDescription.mcpProtocolVersion shouldBe LATEST_PROTOCOL_VERSION
        mcpTool.serverDescription.mcpTransportType shouldBe McpTransportType.Tcp

        // Verify tool descriptor is preserved
        mcpTool.descriptor.name shouldBe serverTool.name
        mcpTool.descriptor.description shouldBe serverTool.descriptor.description
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should support multiple MCP servers simultaneously`() = runTestWithTimeout {
        val port2 = McpServerPort + 1
        val server2 = startMcpServer(
            port = port2,
            tools = ToolRegistry { tool(CalculatorTool) },
        )

        val aiAgent = createMcpAgent(ToolRegistry.empty(), {
            addMcpServerFromTransport(
                transport = sseClientTransport(port2),
                serverInfo = McpServerInfo("${McpServerName}-calc", "http://localhost:$port2", port2)
            )
        })

        try {
            aiAgent.run("test")
            val toolRegistry = (aiAgent as GraphAIAgent).toolRegistry
            val toolNames = toolRegistry.tools.map { it.descriptor.name }
            toolNames.shouldContain(serverTool.name)
            toolNames.shouldContain(CalculatorTool.name)
        } finally {
            closeMcpServer(server2, port2)
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should have agent tools AND mcp tools`() = runTestWithTimeout {
        val aiAgent = createMcpAgent(ToolRegistry {
            tool(CalculatorTool)
        })

        aiAgent.run("test")
        val toolRegistry = (aiAgent as GraphAIAgent).toolRegistry
        val toolNames = toolRegistry.tools.map { it.descriptor.name }
        toolNames.shouldContain(serverTool.name)
        toolNames.shouldContain(CalculatorTool.name)
    }
}
