package ai.koog.agents.features.mcp.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Feature that integrates Model Context Protocol (MCP) servers with Koog agents.
 *
 * The MCP feature allows agents to dynamically connect to MCP servers and use their tools.
 * It handles:
 * - Connecting to MCP servers via various transports (stdio, SSE)
 * - Retrieving and parsing MCP tool definitions
 * - Registering MCP tools into the agent's tool registry
 * - Managing multiple MCP server connections
 *
 * **Important**: MCP feature modifies the agent's tool registry during construction.
 * MCP tools are registered when the feature is installed and become available throughout the agent's lifecycle.
 *
 * Example of installing MCP feature:
 * ```kotlin
 * val process = ProcessBuilder("docker", "run", "-i", "mcp/google-maps").start()
 *
 * val agent = AIAgent(
 *     promptExecutor = executor,
 *     strategy = strategy,
 *     // other parameters...
 * ) {
 *     install(Mcp) {
 *         // Add an MCP server via stdio transport
 *         addServerTransport(
 *             transport = stdioClientTransport(process),
 *             serverInfo = McpServerInfo("google-maps-server"),
 *         )
 *
 *         // Add an MCP server via sse transport
 *         addMcpServerFromTransport(
 *             transport = sseClientTransport(port),
 *             serverInfo = McpServerInfo("Local Computer Use", "http://localhost:$port", port)
 *         )
 *     }
 * }
 * ```
 *
 * After installation, all MCP tools from configured servers are available to the agent
 * and can be called by the LLM like any other tool.
 */
public class Mcp internal constructor(
    /**
     * Map of server ID to MCP client connections.
     */
    public val config: McpFeatureConfig
) {

    /**
     * Companion object implementing agent feature, handling [Mcp] creation and installation.
     */
    public companion object Feature : AIAgentGraphFeature<McpFeatureConfig, Mcp> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<Mcp> = AIAgentStorageKey("agents-features-mcp")

        override fun createInitialConfig(): McpFeatureConfig = McpFeatureConfig()

        override fun install(
            config: McpFeatureConfig,
            pipeline: AIAgentGraphPipeline,
        ): Mcp {
            logger.info { "Start installing feature: ${Mcp::class.simpleName}" }
            if (config.clients.isEmpty()) {
                logger.warn { "No MCP server transports are defined. No MCP tools will be available." }
            }
            return Mcp(config)
        }
    }
}


