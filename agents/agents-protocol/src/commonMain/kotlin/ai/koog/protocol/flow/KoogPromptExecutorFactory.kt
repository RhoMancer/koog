package ai.koog.protocol.flow

import ai.koog.ktor.utils.getModelFromIdentifier
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Factory for creating PromptExecutor instances based on model configuration.
 */
public object KoogPromptExecutorFactory {

    private val logger = KotlinLogging.logger { }

    /**
     * Resolves model string to LLModel instance.
     *
     * Supports various model string formats:
     * - "provider/model-id" (e.g., "openai/gpt-4o")
     * - Just "model-id" (defaults to OpenAI)
     *
     * @param modelString Model string in format "provider/model-id" (e.g., "openai/gpt-4o")
     * @return LLModel instance
     */
    public fun resolveModel(modelString: String?, defaultModel: String?): LLModel {
        if (modelString == null) {
            return OpenAIModels.Chat.GPT4o
        }

        val model = getModelFromIdentifier(modelString, delimiter = "/")
            ?: getModelFromIdentifier(modelString)
            ?: error("Invalid model string: $modelString")

        logger.debug { "Resolved input model config (model string: $modelString, default model: $defaultModel) to LLModel: $model" }
        return model
    }
}
