package ai.koog.agents.core.agent.concierge

import ai.koog.agents.core.CalculatorChatExecutor
import ai.koog.agents.core.CalculatorTools
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.testing.tools.MockEnvironment
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ConciergeTest {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Test
    fun testCustomStrategy() = runTest {

        val model = OpenAIModels.Chat.GPT4o
        val openAIAPIKey = System.getenv("OPENAI_API_KEY")

        //region Concierge config

        val conciergeConfig = AgentConfiguration(
            name = "Calculator",
            llm = AgentConfiguration.Llm(
                model.id,
                "https://api.app.stgn.grazie.aws.intellij.net",
                openAIAPIKey,
                null,
            ),
            strategy = AgentConfiguration.AgentStrategy.CONCIERGE_CUSTOM,
            systemPrompt = "You are a mathematician with access to a tool named add.",
            tools = emptyArray<AgentConfiguration.ToolDefinition>(),
            // [new AgentConfiguration.ToolDefinition(
            //     AgentConfiguration.ToolType.MCP,
            //     'server-everything',
            //     new AgentConfiguration.ToolOptions(
            //         'http://localhost:3001/mcp',
            //         AgentConfiguration.TransportType.STREAMABLE_HTTP,
            //         ["Authorization"],
            //         ["Bearer token"]
            //     )
            // )],
            reasoningInterval = 1,
            maxNodesIterations = 100,
            maxReasoningIterations = 50,
            tracingEnabled = true,
            persistenceStorageProvider = null,
            contextReductionConfig = null,
            outputSchema = """{
  "type": "object",
  "properties": {
    "myOutput": {
      "type": "string"
    }
  },
  "required": ["myOutput"],
  "additionalProperties": false
}"""
        )

        //endregion Concierge config

        //region Agent config

        val toolRegistry = ToolRegistry {
            tool(CalculatorTools.PlusTool)
        }

        val promptExecutor = simpleOpenAIExecutor(openAIAPIKey)

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                user("Hello")
                assistant("Hi there")
            },
            model = model,
            maxAgentIterations = 100
        )
        val environment = MockEnvironment(toolRegistry, promptExecutor)

        val strategy = ConciergeCustomReActStrategy(logger).createStrategy(conciergeConfig)

        //endregion Agent config

        val userPrompt = "add 2 and 3"

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onNodeExecutionStarting { event ->
                    println("SD -- NODE: ${event.node.id}")
                }

                onLLMCallStarting { event ->
                    println("SD -- LLM CALL:\n${event.prompt.messages.lastOrNull()?.content}")
                    println("-------------------")
                }

                onToolCallCompleted { event ->
                    println("SD -- TOOL CALL: ${event.tool.name}\n${event.result}")
                    println("-------------------")
                }
            }
        }

        println("SD -- agent START: $userPrompt")
        val result = agent.run(userPrompt)
        println("SD -- agent DONE: $result")
    }
}
