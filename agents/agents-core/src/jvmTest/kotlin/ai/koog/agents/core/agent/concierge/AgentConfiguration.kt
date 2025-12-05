package ai.koog.agents.core.agent.concierge

import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
internal data class AgentConfiguration(
    val name: String,
    val llm: Llm,
    val strategy: AgentStrategy = AgentStrategy.SINGLE_RUN,
    val systemPrompt: String = "",
    val tools: Array<ToolDefinition> = emptyArray(),
    val reasoningInterval: Int = DEFAULT_REASONING_INTERVAL,
    val maxNodesIterations: Int = DEFAULT_MAX_NODES_ITERATIONS,
    val maxReasoningIterations: Int = DEFAULT_MAX_REASONING_ITERATIONS,
    val tracingEnabled: Boolean = false,
    val persistenceStorageProvider: KoogelisPersistenceStorageProvider? = null,
    val contextReductionConfig: ContextReductionConfig? = null,
    val outputSchema: String? = null
) {

    companion object {
        const val DEFAULT_REASONING_INTERVAL: Int = 1
        const val DEFAULT_MAX_NODES_ITERATIONS: Int = 100
        const val DEFAULT_MAX_REASONING_ITERATIONS: Int = 50
    }

    data class Llm(
        val id: String,
        val url: String,
        val authToken: String,
        val llmParams: LlmParams? = null
    )

    data class LlmParams(
        val temperature: Double? = null,
        val maxTokens: Int? = null,
        val numberOfChoices: Int? = null,
        val speculation: String? = null,
        val schema: String? = null,
        val toolChoice: ToolChoice? = null,
        val user: String? = null,
        val additionalPropertiesKeys: Array<String>? = null,
        val additionalPropertiesValues: Array<String>? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            other as LlmParams

            if (temperature != other.temperature) return false
            if (maxTokens != other.maxTokens) return false
            if (numberOfChoices != other.numberOfChoices) return false
            if (speculation != other.speculation) return false
            if (schema != other.schema) return false
            if (toolChoice != other.toolChoice) return false
            if (user != other.user) return false
            if (!additionalPropertiesKeys.contentEquals(other.additionalPropertiesKeys)) return false
            if (!additionalPropertiesValues.contentEquals(other.additionalPropertiesValues)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = temperature?.hashCode() ?: 0
            result = 31 * result + (maxTokens ?: 0)
            result = 31 * result + (numberOfChoices ?: 0)
            result = 31 * result + (speculation?.hashCode() ?: 0)
            result = 31 * result + (schema?.hashCode() ?: 0)
            result = 31 * result + (toolChoice?.hashCode() ?: 0)
            result = 31 * result + (user?.hashCode() ?: 0)
            result = 31 * result + (additionalPropertiesKeys?.contentHashCode() ?: 0)
            result = 31 * result + (additionalPropertiesValues?.contentHashCode() ?: 0)
            return result
        }

    }

    data class ToolDefinition(
        val type: ToolType,
        val id: String,
        val options: ToolOptions?
    )

    data class ToolOptions(
        val serverUrl: String,
        val transportType: TransportType = TransportType.STREAMABLE_HTTP,
        val headersKeys: Array<String> = emptyArray(),
        val headersValues: Array<String> = emptyArray()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            other as ToolOptions

            if (serverUrl != other.serverUrl) return false
            if (transportType != other.transportType) return false
            if (!headersKeys.contentEquals(other.headersKeys)) return false
            if (!headersValues.contentEquals(other.headersValues)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = serverUrl.hashCode()
            result = 31 * result + transportType.hashCode()
            result = 31 * result + headersKeys.contentHashCode()
            result = 31 * result + headersValues.contentHashCode()
            return result
        }
    }

    enum class AgentStrategy {
        SINGLE_RUN, RE_ACT, CONCIERGE_CUSTOM
    }

    enum class ToolType {
        SIMPLE, MCP
    }

    enum class TransportType {
        SSE, STREAMABLE_HTTP
    }

    data class ContextReductionConfig(

        /**
         * should keep context within the maxMessages threshold
         */
        val maxMessages: Int? = null,

        /**
         * should keep context within the maxTokens threshold
         */
        val maxTokens: Int? = null,

        /**
         * should preserve system messages in context reduction
         */
        val preserveSystemMessages: Boolean? = null,

        /**
         * should not summarize when below a threshold
         */
        val keepRecentMessagesCount: Int? = null,

        /**
         * prefix for summary messages
         */
        val summaryPrefix: String? = null
    )

    sealed class ToolChoice {
        /**
         *  LLM will call the tool [name] as a response
         */

        data class Named(val name: String) : ToolChoice() {
            init {
                require(name.isNotBlank()) { "Tool choice name must not be empty or blank" }
            }
        }

        /**
         * LLM will not call tools at all, and only generate text
         */

        object None : ToolChoice()

        /**
         * LLM will automatically decide whether to call tools or to generate text
         */

        object Auto : ToolChoice()

        /**
         * LLM will only call tools
         */

        object Required : ToolChoice()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        other as AgentConfiguration

        if (maxNodesIterations != other.maxNodesIterations) return false
        if (tracingEnabled != other.tracingEnabled) return false
        if (name != other.name) return false
        if (llm != other.llm) return false
        if (strategy != other.strategy) return false
        if (systemPrompt != other.systemPrompt) return false
        if (!tools.contentEquals(other.tools)) return false
        if (persistenceStorageProvider != other.persistenceStorageProvider) return false

        return true
    }

    override fun hashCode(): Int {
        var result = maxNodesIterations
        result = 31 * result + tracingEnabled.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + llm.hashCode()
        result = 31 * result + strategy.hashCode()
        result = 31 * result + systemPrompt.hashCode()
        result = 31 * result + tools.contentHashCode()
        result = 31 * result + (persistenceStorageProvider?.hashCode() ?: 0)
        return result
    }

}
