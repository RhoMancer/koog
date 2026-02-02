package ai.koog.protocol.flow

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.system.systemSecretsReader

/**
 * Factory for creating PromptExecutor instances based on model configuration.
 */
public object KoogPromptExecutorFactory {

    private const val OPENAI_API_KEY_ENV = "OPENAI_API_KEY"
    private const val ANTHROPIC_API_KEY_ENV = "ANTHROPIC_API_KEY"
    private const val GOOGLE_API_KEY_ENV = "GOOGLE_API_KEY"
    private const val OPENROUTER_API_KEY_ENV = "OPENROUTER_API_KEY"
    private const val DEEPSEEK_API_KEY_ENV = "DEEPSEEK_API_KEY"
    private const val MISTRAL_API_KEY_ENV = "MISTRAL_API_KEY"
    private const val OLLAMA_BASE_URL_ENV = "OLLAMA_BASE_URL"

    /**
     * Creates a PromptExecutor based on the model string (e.g., "openai/gpt-4o").
     * Currently, supports OpenAI models via environment variable.
     *
     * @param modelString Model string in format "provider/model-id" (e.g., "openai/gpt-4o")
     * @return PromptExecutor instance for the specified provider
     */
    public fun createExecutor(modelString: String?): PromptExecutor {
        val provider = modelString?.split("/", limit = 2)?.getOrNull(0)?.lowercase() ?: "openai"

        return when (provider) {
            "openai" -> {
                val apiKey = systemSecretsReader().getSecret(OPENAI_API_KEY_ENV)
                    ?: error("$OPENAI_API_KEY_ENV environment variable is not set")

                simpleOpenAIExecutor(apiKey)
            }
            else -> error("Unsupported provider: $provider. Currently only 'openai' is supported.")
        }
    }

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
    public fun resolveModel(modelString: String?): LLModel {
        if (modelString == null) {
            return OpenAIModels.Chat.GPT4o
        }

        val parts = modelString.split("/", limit = 2)
        val providerName = if (parts.size > 1) parts[0].lowercase() else "openai"
        val modelId = if (parts.size > 1) parts[1] else modelString

        return when (providerName) {
            "openai" -> resolveOpenAIModel(modelId)
            else -> LLModel(
                provider = resolveProvider(providerName),
                id = modelId,
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.ToolChoice,
                    LLMCapability.Completion
                ),
                contextLength = 128_000,
                maxOutputTokens = 16_384
            )
        }
    }

    private fun resolveOpenAIModel(modelId: String): LLModel {
        return when (modelId.lowercase()) {
            "gpt-4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
            "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
            "gpt-4.1-mini" -> OpenAIModels.Chat.GPT4_1Mini
            "o1" -> OpenAIModels.Chat.O1
            "o3" -> OpenAIModels.Chat.O3
            "o3-mini" -> OpenAIModels.Chat.O3Mini
            else -> OpenAIModels.Chat.GPT4o
        }
    }

    internal fun resolveProvider(provider: String): LLMProvider {
        return when (provider.lowercase()) {
            "openai" -> LLMProvider.OpenAI
            "anthropic" -> LLMProvider.Anthropic
            "google" -> LLMProvider.Google
            "meta" -> LLMProvider.Meta
            "alibaba" -> LLMProvider.Alibaba
            "openrouter" -> LLMProvider.OpenRouter
            "ollama" -> LLMProvider.Ollama
            "bedrock" -> LLMProvider.Bedrock
            "deepseek" -> LLMProvider.DeepSeek
            "mistralai" -> LLMProvider.MistralAI
            else -> LLMProvider.OpenAI
        }
    }

    /**
     * Creates an LLM client for the specified provider.
     * Reads API keys from environment variables.
     *
     * @param provider Provider name (e.g., "openai", "anthropic", "google")
     * @return LLMClient instance or null if the provider is not supported or API key is missing
     */
    public fun createClientForProvider(provider: String): LLMClient? {
        val secrets = systemSecretsReader()
        return when (provider.lowercase()) {
            "openai" -> {
                val apiKey = secrets.getSecret(OPENAI_API_KEY_ENV) ?: return null
                OpenAILLMClient(apiKey)
            }
            "anthropic" -> {
                val apiKey = secrets.getSecret(ANTHROPIC_API_KEY_ENV) ?: return null
                AnthropicLLMClient(apiKey)
            }
            "google" -> {
                val apiKey = secrets.getSecret(GOOGLE_API_KEY_ENV) ?: return null
                GoogleLLMClient(apiKey)
            }
            "openrouter" -> {
                val apiKey = secrets.getSecret(OPENROUTER_API_KEY_ENV) ?: return null
                OpenRouterLLMClient(apiKey)
            }
            "deepseek" -> {
                val apiKey = secrets.getSecret(DEEPSEEK_API_KEY_ENV) ?: return null
                DeepSeekLLMClient(apiKey)
            }
            "mistralai" -> {
                val apiKey = secrets.getSecret(MISTRAL_API_KEY_ENV) ?: return null
                MistralAILLMClient(apiKey)
            }
            "ollama" -> {
                val baseUrl = secrets.getSecret(OLLAMA_BASE_URL_ENV) ?: "http://localhost:11434"
                OllamaClient(baseUrl)
            }
            else -> null
        }
    }
}
