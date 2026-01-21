package ai.koog.agents.features.mcp.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.tool.McpServerMeta
import ai.koog.agents.core.feature.handler.tool.McpTransportType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.mcp.DefaultMcpToolDescriptorParser
import ai.koog.agents.features.mcp.McpToolImpl
import ai.koog.agents.features.mcp.McpToolDescriptorParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Configuration for the MCP (Model Context Protocol) feature.
 *
 * This configuration allows adding MCP server connections that will be used to
 * register MCP tools into the agent's tool registry.
 */
public class McpFeatureConfig : FeatureConfig() {

    private val _clients = mutableListOf<McpClient>()

    /**
     * Provides a read-only list of MCP server transport configurations.
     */
    internal val clients: List<McpClient>
        get() = _clients.toList()

    private val lock = Mutex()
    private var initialized = false
    private var protocolVersion = LATEST_PROTOCOL_VERSION
    private var sessionId: String? = null

    @OptIn(InternalAgentsApi::class)
    override suspend fun initialize(context: AIAgentContext) {
        lock.withLock {
            if (!initialized) {
                clients.forEach { client ->
                    client.client.connect(client.transport)
                }
                context.llm.toolRegistry.addAll(*toolRegistry().tools.toTypedArray())
                initialized = true
            }
        }
    }

    /**
     * Builds a tool registry by aggregating tools from all configured MCP server connections.
     *
     * This method iterates through all configured MCP clients, retrieves their available tools,
     * and registers them in a centralized [ToolRegistry]. Each tool is wrapped as an [McpToolImpl]
     * with its associated client and parsed descriptor, making it available for agent execution.
     *
     * @return A fully initialized [ToolRegistry] containing all tools from configured MCP servers.
     * @throws IllegalStateException if any server connection fails or tool listing fails.
     */
    @OptIn(InternalAgentsApi::class)
    internal suspend fun toolRegistry(): ToolRegistry {
        return ToolRegistry.builder()
            .apply {
                clients.forEach { client ->
                    val server = McpServerMeta(
                        serverName = client.serverInfo.serverName,
                        serverUrl = client.serverInfo.serverUrl,
                        serverPort = client.serverInfo.serverPort,
                        siteUrl = client.client.serverVersion?.websiteUrl,
                        instructions = client.client.serverInstructions,
                        mcpProtocolVersion = protocolVersion,
                        mcpTransportType = client.transportType,
                        sessionId = sessionId,
                    )
                    client.client.listTools().tools.forEach {
                        tool(McpToolImpl(client.client, client.parser.parse(it), server))
                    }
                }
            }
            .build()
    }

    /**
     * Adds an MCP server connection via the specified transport configuration.
     *
     * This method registers a new MCP server client that will be used to discover and execute
     * tools from the remote server. The client is initialized with the provided transport layer,
     * metadata, and tool descriptor parser.
     *
     * @param transport Mcp transport configuration.
     * @param serverInfo The MCP server url, port and other metadata.
     * @param clientInfo The MCP client implementation metadata identifying this client to the server.
     * @param options Additional client configuration options for customizing client behavior.
     * @param parser The parser used to convert MCP SDK tool definitions into agent-compatible
     *               [ai.koog.agents.core.tools.ToolDescriptor] format.
     */
    public fun addMcpServerFromTransport(
        transport: Transport,
        serverInfo: McpServerInfo,
        clientInfo: Implementation = Implementation("koog-mcp-client", "1.0.0"),
        options: ClientOptions = ClientOptions(),
        parser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
    ) {
        _clients.add(
            McpClient(
                client = Client(clientInfo, options),
                serverInfo = serverInfo,
                transport = transport,
                transportType = when (transport) {
                    is SseClientTransport -> McpTransportType.Tcp
                    else -> McpTransportType.Pipe
                },
                parser = parser,
            )
        )
    }
}

/**
 * Configuration describing an MCP server transport connection.
 *
 * This data class encapsulates all information needed to establish a connection
 * to an MCP server, including server identification, network location, and the
 * underlying transport mechanism.
 *
 * @property serverName The human-readable name identifying the MCP server.
 * @property serverUrl The optional URL where the server is accessible (e.g., "http://localhost").
 * @property serverPort The optional port number on which the server listens.
 */
public data class McpServerInfo(
    val serverName: String,
    val serverUrl: String? = null,
    val serverPort: Int? = null,
)

/**
 * Converts an [McpServerMeta] instance into a [McpServerInfo].
 */
@OptIn(InternalAgentsApi::class)
public fun McpServerMeta.toServerInfo(): McpServerInfo = McpServerInfo(
    serverName = serverName,
    serverUrl = serverUrl,
    serverPort = serverPort,
)

internal data class McpClient(
    val transport: Transport,
    val transportType: McpTransportType,
    val client: Client,
    val serverInfo: McpServerInfo,
    val parser: McpToolDescriptorParser
)

/**
 * Creates a new sse client transport for the specified port.
 */
public fun sseClientTransport(port: Int, url: String = "http://localhost:$port"): Transport {
    return SseClientTransport(
        client = HttpClient { install(SSE) },
        urlString = url
    )
}

