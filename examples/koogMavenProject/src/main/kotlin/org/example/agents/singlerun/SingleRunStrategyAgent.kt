package org.example.agents.singlerun

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.features.debugger.feature.Debugger
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

object SingleRunStrategyAgent {

    fun createAgent(openAiKey: String, writer: TraceFeatureMessageRemoteWriter): AIAgent<String, String> {
        return AIAgent(
            strategy = singleRunStrategy(),
            promptExecutor = simpleOpenAIExecutor(openAiKey),
            llmModel = OpenAIModels.Reasoning.O4Mini,
            systemPrompt = "You are a code assistant. Provide concise code examples.",
            installFeatures = {
                install(Debugger)
            }
        )
    }
}