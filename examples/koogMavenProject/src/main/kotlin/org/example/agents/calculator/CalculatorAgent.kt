package org.example.agents.calculator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.debugger.feature.Debugger
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import org.example.agents.calculator.strategy.CalculatorStrategy
import org.example.agents.calculator.tools.CalculatorTools

object CalculatorAgent {

    fun createAgent(openAiKey: String): AIAgent<String, String> {

        val toolRegistry = ToolRegistry {
            tool(AskUser)
            tool(SayToUser)
            tools(CalculatorTools().asTools())
        }

        return AIAgent(
            strategy = CalculatorStrategy.strategy,
            promptExecutor = simpleOpenAIExecutor(openAiKey),
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = toolRegistry,
            systemPrompt = "You are a calculator. Always use tools to calculate!!!",
            installFeatures = {
                install(Debugger)
            }
        )
    }

}
