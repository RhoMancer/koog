package ai.koog.agents.example.android

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking

fun main() {
    val executor = simpleOllamaAIExecutor()

    val model = LLModel(
        provider = LLMProvider.Ollama,
        id = "gemma3:1b",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Schema.JSON.Full,
            LLMCapability.Tools
        )
    )

    val message = runBlocking {
        executor.execute(
            prompt("test") {
                system("You are a helpful assistant.")
                user("What is the meaning of life?")
            }, model = model, tools = emptyList()
        )
    }
    println(message)
}