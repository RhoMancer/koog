# Module agents-features-mcp

Provides integration with [Model Context Protocol (MCP)](https://modelcontextprotocol.io) servers as an agent feature.

## Overview

The MCP feature enables Koog AI agents to connect to MCP servers and use their tools seamlessly. This feature handles:
- Connecting to MCP servers through various transport mechanisms (stdio, SSE)
- Registering MCP tools into the agent's tool registry
- Managing multiple MCP server connections

### What is MCP?

The Model Context Protocol (MCP) is a standardized protocol that enables AI agents to interact with external tools and services through a consistent interface. MCP servers expose tools as API endpoints that can be called by AI agents, with each tool having a defined name and input schema in JSON Schema format.

To learn more about MCP, visit [https://modelcontextprotocol.io](https://modelcontextprotocol.io)

### Where to find MCP servers?

You can find ready-to-use MCP servers in:
- [MCP Marketplace](https://mcp.so/)
- [MCP DockerHub](https://hub.docker.com/u/mcp)

MCP servers support stdio transport and optionally SSE transport protocols to communicate with agents.

## Using in your project

To use the MCP servers in your agent, you need to:

**Install the MCP feature** (during agent construction)

### Basic Example

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    toolRegistry = toolRegistry, // Use combined registry
    // ... other parameters
) {
    // 4. Install MCP feature with the same config
    install(Mcp) {
        // Must match the configuration used for tool registry
        addMcpServerFromTransport(
            transport = stdioClientTransport(process),
            serverInfo = McpServerInfo("google-maps-server")
        )
    }
}
```

### Multiple MCP Servers

You can connect to multiple MCP servers simultaneously:

```kotlin
// Start multiple MCP servers
val googleMapsProcess = ProcessBuilder(
    "docker", "run", "-i",
    "-e", "GOOGLE_MAPS_API_KEY=$apiKey",
    "mcp/google-maps"
).start()

val playwrightProcess = ProcessBuilder(
    "npx", "@playwright/mcp@latest", "--port", "8931"
).start()

// Create agent with all MCP tools available
val agent = AIAgent(
    // ...
) {
    install(Mcp) {
        // Google Maps via stdio
        addServerTransport(
            transport = stdioClientTransport(googleMapsProcess),
            serverInfo = McpServerInfo("google-maps-server"),
            id = "google-maps"
        )

        // Playwright via SSE
        addServerTransport(
            transport = sseClientTransport("8931"),
            serverInfo = McpServerInfo("playwright-server", "http://localhost:8931", 8931),
        )
    }
}
```

## Transport Types

MCP supports different transport mechanisms for communication:

### Standard Input/Output (stdio)

Use stdio transport when the MCP server is running as a separate process:

```kotlin
val process = ProcessBuilder("path/to/mcp/server").start()
val transport = stdioClientTransport(process)
```

### Server-Sent Events (SSE)

Use SSE transport when the MCP server is running as a web service:

```kotlin
val transport = sseClientTransport(port = 8931)
```

## Architecture Details

### Tool Registration Flow

1. **Configuration Phase**: MCP servers are registered with the feature configuration
2. **Agent Construction**: Agent is created
3. **Tools Installation**: Mcp tools are registered into the agent's tool registry ONLY after agent started

### Key Components

- **McpFeatureConfig**: Configuration for MCP server connections
- **Mcp**: The feature implementation that manages MCP clients
- **McpTool**: Base class for MCP tools located in core module to provide common functionality without mcp feature dependency
- **McpToolImpl**: Implementation of McpTool on top of mcp client sdk

## Advanced Configuration

### Custom Tool Parser

You can provide a custom tool descriptor parser:

```kotlin
object CustomMcpParser : McpToolDescriptorParser {
    override fun parse(sdkTool: SDKTool): ToolDescriptor {
        // Custom parsing logic
    }
}

val toolRegistry = mcpToolRegistry(
    mcpConfig = mcpConfig,
    mcpToolParser = CustomMcpParser
)
```

## Migration from agents-mcp

If you're migrating from the deprecated `agents-mcp` module, here's what you need to change:

### 1. Update Usage Pattern

**Before (Old Pattern):**
```kotlin
// Old single-step approach
val toolRegistry = McpToolRegistryProvider.fromTransport(
    transport = McpToolRegistryProvider.defaultStdioTransport(process)
)

val agent = AIAgent(
    promptExecutor = executor,
    toolRegistry = toolRegistry,
    // ...
)
```

**After (New Feature Pattern):**
```kotlin
// New way approach:
val agent = AIAgent(
    promptExecutor = executor,
    toolRegistry = toolRegistry,
    // ...
) {
    // Install MCP feature with the same configuration
    install(Mcp) {
        addServerTransport(
            transport = stdioClientTransport(process),
            serverInfo = McpServerInfo("server description"),
        )
    }
}
```

### 3. Update Dependencies

If you explicitly depend on `agents-mcp`, change it to `agents-features-mcp`:

**build.gradle.kts:**
```kotlin
// Before
implementation("ai.koog:agents-mcp")

// After
implementation("ai.koog:agents-features-mcp")
```

**Note:** If you're using the `koog-agents` meta-package, it already includes `agents-features-mcp`, so no changes needed.

### Key Differences

1. **Feature installation**: MCP is now a proper agent feature that can be installed
2. **Named servers**: Each server connection now requires a name and optional port
3. **Consistent pattern**: Follows the same pattern as other agents-features modules (tracing, event-handler, etc.)

## See Also

- [MCP Official Documentation](https://modelcontextprotocol.io)
- [MCP Marketplace](https://mcp.so/)
- [Koog Agents Core Documentation](../../../agents-core/Module.md)
- [Koog Tools Documentation](../../../agents-tools/Module.md)
