package ai.koog.agents.example.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.mcp.feature.Mcp
import ai.koog.agents.features.mcp.feature.McpServerInfo
import ai.koog.agents.features.mcp.feature.sseClientTransport
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Example of using the MCP (Model Context Protocol) feature with Playwright.
 *
 * This example demonstrates how to:
 * 1. Start a Playwright MCP server on port 8931
 * 2. Configure the MCP feature with SSE transport
 * 3. Create a tool registry with MCP tools before agent construction
 * 4. Install the MCP feature in an AI agent
 * 5. Use the tools to automate browser interactions
 *
 * The example specifically shows how to open a browser and navigate to jetbrains.com
 * using the Playwright tools provided by the MCP server.
 */
fun main() {
    // Get the API key from environment variables
    val openAIApiToken = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")

    // Start the Playwright MCP server
    val process = ProcessBuilder(
        "npx",
        "@playwright/mcp@latest",
        "--port",
        "8931"
    ).start()

    // Wait for the server to start
    Thread.sleep(2000)

    try {
        runBlocking {
            try {
                println("Connecting to Playwright MCP server...")
                // Create the agent with MCP feature
                val agent = AIAgent(
                    promptExecutor = simpleOpenAIExecutor(openAIApiToken),
                    llmModel = OpenAIModels.Chat.GPT4o,
                ) {
                    // Install MCP feature with the same configuration
                    install(Mcp) {
                        addMcpServerFromTransport(
                            transport = sseClientTransport(8931, "http://localhost:8931/sse"),
                            serverInfo = McpServerInfo("playwright", "http://localhost:8931/sse", 8931),
                        )
                    }
                }

                val request = "Open a browser, navigate to jetbrains.com, accept all cookies, click AI in toolbar"
                println("Sending request: $request")
                agent.run(
                    request +
                        "You can only call tools. Use the Playwright tools to complete this task."
                )
            } catch (e: Exception) {
                println("Error connecting to Playwright MCP server: ${e.message}")
                e.printStackTrace()
            }
        }
    } finally {
        // Shutdown the curl process
        println("Closing connection to Playwright MCP server")
        process.destroy()
    }
}
