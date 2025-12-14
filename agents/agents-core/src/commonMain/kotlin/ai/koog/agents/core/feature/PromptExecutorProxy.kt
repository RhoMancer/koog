package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.agent.context.withParent
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap.
 * @property pipeline The [ai.koog.agents.core.feature.pipeline.AIAgentPipeline] associated with the executor.
 */
public class PromptExecutorProxy(
    private val executor: PromptExecutor,
    private val pipeline: AIAgentPipeline,
    private val runId: String,
    private val context: AIAgentContext,
) : PromptExecutor {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = promptWithParent(context, prompt, ExecutionPathPartPrefix.LLM) { executionInfo ->
        logger.debug { "Executing LLM call (prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        pipeline.onLLMCallStarting(executionInfo, runId, prompt, model, tools, context)

        val responses = executor.execute(prompt, model, tools)

        logger.trace { "Finished LLM call with responses: [${responses.joinToString { "${it.role}: ${it.content}" }}]" }
        pipeline.onLLMCallCompleted(executionInfo, runId, prompt, model, tools, responses, null, context)

        responses
    }

    /**
     * Executes a streaming call to the language model with tool support.
     *
     * This method wraps the underlying executor's streaming functionality with pipeline hooks
     * to enable monitoring and processing of stream events. It triggers before-stream handlers
     * before starting, stream-frame handlers for each frame received, and after-stream handlers
     * upon completion.
     *
     * @param prompt The prompt to send to the language model
     * @param model The language model to use for streaming
     * @param tools The list of available tool descriptors for the streaming call
     * @return A Flow of StreamFrame objects representing the streaming response
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        logger.debug { "Executing LLM streaming call (prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        return executor.executeStreaming(prompt, model, tools)
            .onStart {
                promptWithParent(context, prompt, ExecutionPathPartPrefix.STREAMING) { executionInfo ->
                    logger.debug { "Starting LLM streaming call" }
                    pipeline.onLLMStreamingStarting(executionInfo, runId, prompt, model, tools, context)
                }
            }
            .onEach { frame ->
                promptWithParent(context, prompt, ExecutionPathPartPrefix.STREAMING) { executionInfo ->
                    logger.debug { "Received frame from LLM streaming call: $frame" }
                    pipeline.onLLMStreamingFrameReceived(executionInfo, runId, prompt, model, frame, context)
                }
            }
            .catch { exception ->
                promptWithParent(context, prompt, ExecutionPathPartPrefix.STREAMING) { executionInfo ->
                    logger.debug(exception) { "Error in LLM streaming call" }
                    pipeline.onLLMStreamingFailed(executionInfo, runId, prompt, model, exception, context)
                    throw exception
                }
            }
            .onCompletion { error ->
                promptWithParent(context, prompt, ExecutionPathPartPrefix.STREAMING) { executionInfo ->
                    logger.debug(error) { "Finished LLM streaming call" }
                    pipeline.onLLMStreamingCompleted(executionInfo, runId, prompt, model, tools, context)
                }
            }
    }

    // TODO: Add Pipeline interceptors for this method
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing LLM call prompt: $prompt with tools: [${tools.joinToString { it.name }}]" }

        val responses = executor.executeMultipleChoices(prompt, model, tools)

        logger.debug {
            val messageBuilder = StringBuilder()
                .appendLine("Finished LLM call with LLM Choice response:")

            responses.forEachIndexed { index, response ->
                messageBuilder.appendLine("- Response #$index")
                response.forEach { message ->
                    messageBuilder.appendLine("  -- [${message.role}] ${message.content}")
                }
            }

            "Finished LLM call with responses: $messageBuilder"
        }

        return responses
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult =
        promptWithParent(context, prompt, prefix = ExecutionPathPartPrefix.MODERATION) { executionInfo ->
            logger.debug { "Executing moderation LLM request (prompt: $prompt)" }
            pipeline.onLLMCallStarting(executionInfo, runId, prompt, model, tools = emptyList(), context)

            val result = executor.moderate(prompt, model)

            logger.trace { "Finished moderation LLM request with response: $result" }
            pipeline.onLLMCallCompleted(
                executionInfo,
                runId,
                prompt,
                model,
                tools = emptyList(),
                responses = emptyList(),
                moderationResponse = result,
                context = context
            )

            result
        }

    override suspend fun models(): List<String> {
        return executor.models()
    }

    override fun close() {
        executor.close()
    }

    //region Private Methods

    private enum class ExecutionPathPartPrefix(val id: String) {
        LLM("llm"),
        STREAMING("stream"),
        MODERATION("moderation"),
        TOOL("tool")
    }

    private suspend fun <T> promptWithParent(
        context: AIAgentContext,
        prompt: Prompt,
        prefix: ExecutionPathPartPrefix,
        block: suspend (executionInfo: AgentExecutionInfo) -> T
    ): T = withParent(context, "${prefix.id}:${prompt.messages.lastOrNull()?.content ?: prompt.id}") { executionInfo ->
        block(executionInfo)
    }

    //endregion Private Methods
}
