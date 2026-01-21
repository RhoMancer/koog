package ai.koog.agents.example.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.mcp.feature.Mcp
import ai.koog.agents.features.mcp.feature.McpServerInfo
import ai.koog.agents.features.mcp.stdioClientTransport
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

/**
 * Example of using the MCP (Model Context Protocol) feature with Google Maps.
 *
 * This example demonstrates how to:
 * 1. Start a Docker container with the Google Maps MCP server
 * 2. Configure the MCP feature with stdio transport
 * 3. Create a tool registry with MCP tools before agent construction
 * 4. Install the MCP feature in an AI agent
 * 5. Use the tools to answer a question about geographic data
 *
 * The example specifically shows how to get the elevation of the JetBrains office in Munich
 * by using the maps_geocode and maps_elevation tools provided by the MCP server.
 */
suspend fun main() {
    // Get the API key from environment variables
    val googleMapsApiKey =
        System.getenv("GOOGLE_MAPS_API_KEY") ?: error("GOOGLE_MAPS_API_KEY environment variable not set")
    val openAIApiToken = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")

    // Start the Docker container with the Google Maps MCP server
    val process = ProcessBuilder(
        "docker",
        "run",
        "-i",
        "-e",
        "GOOGLE_MAPS_API_KEY=$googleMapsApiKey",
        "mcp/google-maps"
    ).start()

    // Wait for the server to start
    Thread.sleep(2000)

    try {
        simpleOpenAIExecutor(openAIApiToken).use { executor ->
            // Create the agent with MCP feature
            val agent = AIAgent(
                promptExecutor = executor,
                llmModel = OpenAIModels.Chat.GPT4o,
            ) {
                // Install MCP feature with the same configuration
                install(Mcp) {
                    addMcpServerFromTransport(
                        transport = stdioClientTransport(process),
                        serverInfo = McpServerInfo("google-maps"),
                    )
                }
            }

            val request = "Get elevation of the Jetbrains Office in Munich, Germany?"
            println(request)
            agent.run(
                request +
                    "You can only call tools. Get it by calling maps_geocode and maps_elevation tools."
            )
        }
    } finally {
        // Shutdown the Docker container
        process.destroy()
    }
}
