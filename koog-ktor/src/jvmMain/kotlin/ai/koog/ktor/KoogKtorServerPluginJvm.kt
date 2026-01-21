package ai.koog.ktor

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.mcp.DefaultMcpToolDescriptorParser
import ai.koog.agents.features.mcp.McpToolDescriptorParser
import ai.koog.agents.features.mcp.feature.sseClientTransport
import ai.koog.agents.features.mcp.stdioClientTransport
import io.ktor.http.Url
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Configuration class for MCPTools that manages the integration of various tool registries
 * into the system. Provides methods to process tools using different transport mechanisms.
 *
 * @param agentConfig Configuration for the Koog agent server, which includes tool registry details.
 */
@OptIn(InternalAgentsApi::class)
public class McpToolsConfig(private val agentConfig: KoogAgentsConfig.AgentConfig) {
    private val mutex = Mutex()

    /**
     * Processes a given `Process` instance to register tools from an MCP server.
     *
     * The method leverages a transport protocol for communication with the MCP server, parses the tool
     * definitions using the specified or default `McpToolDescriptorParser`, and registers the tools
     * in the agent's tool registry under a given client name and version.
     *
     * @param process The `Process` instance representing the MCP server communication process.
     *               Its input and output streams will be used for the transport.
     * @param mcpToolParser The parser that converts MCP tool definitions to standardized descriptors.
     *                      Defaults to `DefaultMcpToolDescriptorParser`.
     * @param name The name of the MCP client for identifying the source of the tools.
     *             Defaults to `DEFAULT_MCP_CLIENT_NAME`.
     * @param version The version of the MCP client for identifying the source of the tools.
     *                Defaults to `DEFAULT_MCP_CLIENT_VERSION`.
     */
    public fun process(
        process: Process,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
        name: String = "mcp-client-cli",
        version: String = "1.0.0",
    ) {
        agentConfig.scope.launch {
            val client = Client(Implementation(name, version)).apply {
                connect(stdioClientTransport(process))
            }
            fromClient(client, mcpToolParser)
        }
    }

    /**
     * Registers tools from an MCP server using server-sent events (SSE) transport.
     *
     * This method establishes an SSE connection to an MCP server at the given URL to retrieve and register
     * tools in the tool registry.
     *
     * @param url The URL to establish the SSE connection with the MCP server.
     * @param mcpToolParser A parser for converting the MCP SDK tool definitions into a standardized format.
     * Defaults to `DefaultMcpToolDescriptorParser`.
     * @param name The name of the MCP client. Defaults to `DEFAULT_MCP_CLIENT_NAME`.
     * @param version The version of the MCP client. Defaults to `DEFAULT_MCP_CLIENT_VERSION`.
     */
    public fun sse(
        url: String,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
        name: String = "mcp-client-cli",
        version: String = "1.0.0",
    ) {
        agentConfig.scope.launch {
            val client = Client(Implementation(name, version)).apply {
                connect(sseClientTransport(Url(url).port, url))
            }
            fromClient(client, mcpToolParser)
        }
    }

    /**
     * Registers tools from an existing MCP client into the tool registry.
     *
     * This method retrieves tools from the given MCP client, parses their definitions using
     * the provided or default MCP tool descriptor parser, and adds them to the tool registry.
     *
     * @param mcpClient The MCP client connected to an MCP server, providing access to tools.
     * @param mcpToolParser The parser used to convert raw tool information into standardized tool descriptors.
     * Defaults to the standard parser implementation.
     */
    public fun client(
        mcpClient: Client,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser
    ) {
        agentConfig.scope.launch {
            fromClient(mcpClient, mcpToolParser)
        }
    }

    private suspend fun fromClient(client: Client, mcpToolParser: McpToolDescriptorParser) {
        val sdkTools = client.listTools().tools
        val mcpTools = ToolRegistry {
            tools(sdkTools.map { McpTool(client, mcpToolParser.parse(it)) })
        }
        mutex.withLock { agentConfig.toolRegistry += mcpTools }
    }

    private class McpTool(
        private val mcpClient: Client,
        descriptor: ToolDescriptor,
    ) : Tool<JsonObject, CallToolResult?>(
        argsSerializer = JsonObject.serializer(),
        resultSerializer = CallToolResult.serializer().nullable,
        descriptor = descriptor,
    ) {
        override suspend fun execute(args: JsonObject): CallToolResult {
            return mcpClient.callTool(
                name = descriptor.name,
                arguments = args
            )
        }

        override fun encodeResultToString(result: CallToolResult?): String {
            val preparedResultJson: JsonElement = result
                ?.let {
                    JsonObject(
                        json.encodeToJsonElement(resultSerializer, it).jsonObject
                            // LLM doesn't need "meta" fields, leave only actual data
                            .filter { (key, _) -> key !in listOf("type", "_meta") }
                    )
                }
                ?: JsonNull

            return json.encodeToString(preparedResultJson)
        }
    }
}

/**
 * Configures the MCP (Modular Configuration Protocol) tools for the agent with the provided configuration block.
 *
 * @param configure A suspend lambda used to configure the MCPToolsConfig instance.
 */
public fun KoogAgentsConfig.AgentConfig.mcp(configure: McpToolsConfig.() -> Unit) {
    McpToolsConfig(this).configure()
}
