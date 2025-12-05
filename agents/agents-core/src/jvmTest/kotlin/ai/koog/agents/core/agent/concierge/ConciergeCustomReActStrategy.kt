package ai.koog.agents.core.agent.concierge

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onIsInstance
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A custom ReAct (Reasoning and Acting) strategy implementation for the Concierge agent.
 */
internal class ConciergeCustomReActStrategy(private val logger: KLogger? = null) {

    companion object {
        private const val DEFAULT_REASONING_PROMPT =
            "Please give your thoughts about the task and plan the next steps."

        private val json = Json {
            prettyPrint = true
            allowStructuredMapKeys = true
        }
    }

    fun createStrategy(
        config: AgentConfiguration
    ): AIAgentGraphStrategy<String, String> = strategy<String, String>(config.name) {

        validateInputParameters(config)

        // Strategy parameters
        val outputJsonObject = getOutputJsonObjectOrNull(config.outputSchema)
        val compressionStrategy = config.contextReductionConfig?.let { contextReductionConfig ->
            KoogelisHistoryCompressionStrategy(contextReductionConfig)
        }
        var reasoningIteration = 1

        // Nodes
        val reasoningStepKey = createStorageKey<Int>("reasoning_step")

        val nodeSetup by node<String, String> {
            storage.set(reasoningStepKey, 0)
            it
        }

        val nodeCheckLoopIterationsLimit by node<Unit, Unit>("check agent iterations limit") {
            if (reasoningIteration++ > config.maxReasoningIterations) {
                error("Exceeded max reasoning iterations: $reasoningIteration/${config.maxReasoningIterations}")
            }
        }

        val nodeCallLLMWithReasonInput by node<String, Unit> { stageInput ->
            llm.writeSession {
                appendPrompt {
                    user(stageInput)
                    user(DEFAULT_REASONING_PROMPT)
                }

                requestLLMWithoutTools()
            }
        }

        val nodeCallLLM by node<Unit, Message.Response> {
            reasoningIteration++

            llm.writeSession {
                outputJsonObject?.let {
                    prompt = llm.prompt.withUpdatedParams {
                        schema = LLMParams.Schema.JSON.Standard(
                            name = "custom-agent-structured-output",
                            schema = outputJsonObject
                        )
                    }
                }

                println("- CHECK START ------------------------------------------------------------------------")
                val res = requestLLM()
                println("- CHECK FINISH ------------------------------------------------------------------------")
                res
            }
        }

        val nodeExecuteTool by nodeExecuteTool()

        val nodeCallLLMReason by node<ReceivedToolResult, Unit> { result ->
            val reasoningStep = storage.getValue(reasoningStepKey)
            llm.writeSession {
                appendPrompt {
                    tool {
                        result(result)
                    }
                }

                if (reasoningStep % config.reasoningInterval == 0) {
                    appendPrompt {
                        user(DEFAULT_REASONING_PROMPT)
                    }
                    requestLLMWithoutTools()
                }
            }
            storage.set(reasoningStepKey, reasoningStep + 1)
        }

        val nodeCustomHistoryCompression by nodeCustomLLMCompressHistory<ReceivedToolResult>(
            name = "compress-concierge-history",
            compressionStrategy = compressionStrategy
        )

        // Edges
        nodeStart then nodeSetup then nodeCallLLMWithReasonInput then nodeCheckLoopIterationsLimit

        edge(nodeCheckLoopIterationsLimit forwardTo nodeCallLLM)

        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })

        edge(nodeExecuteTool forwardTo nodeCustomHistoryCompression onCondition {
            isCompressionNeeded(compressionStrategy)
        })
        edge(nodeCustomHistoryCompression forwardTo nodeCallLLMReason)

        edge(nodeExecuteTool forwardTo nodeCallLLMReason)
        edge(nodeCallLLMReason forwardTo nodeCheckLoopIterationsLimit)
    }

    //region Private Methods

    private fun validateInputParameters(
        config: AgentConfiguration,
    ) {
        require(config.reasoningInterval > 0) {
            "Reasoning interval must be greater than 0. Current value: ${config.reasoningInterval}"
        }

        require(config.maxReasoningIterations > 0) {
            "Max reasoning iterations must be greater than 0. Current value: ${config.maxReasoningIterations}"
        }
    }

    private fun getOutputJsonObjectOrNull(outputSchema: String?): JsonObject? {
        if (outputSchema.isNullOrBlank()) {
            return null
        }

        return try {
            json.parseToJsonElement(outputSchema) as JsonObject
        } catch (e: IllegalArgumentException) {
            logger?.error(e) { "Failed to parse output schema: $outputSchema" }
            return null
        }
    }

    @AIAgentBuilderDslMarker
    private inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeCustomLLMCompressHistory(
        name: String,
        compressionStrategy: KoogelisHistoryCompressionStrategy?,
    ): AIAgentNodeDelegate<T, T> {
        if (compressionStrategy == null) {
            return node<T, T> { it }
        }

        return nodeLLMCompressHistory(name = name, strategy = compressionStrategy)
    }

    private suspend fun AIAgentGraphContextBase.isCompressionNeeded(
        compressionStrategy: KoogelisHistoryCompressionStrategy?
    ): Boolean = llm.readSession {
        compressionStrategy != null && compressionStrategy.isCompressionNeeded(
            originalMessages = prompt.messages.toList(),
            memoryMessages = emptyList()
        )
    }

    //endregion Private Methods
}
