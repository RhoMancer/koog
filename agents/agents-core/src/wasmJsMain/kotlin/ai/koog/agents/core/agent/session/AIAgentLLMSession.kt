@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

@OptIn(ExperimentalStdlibApi::class)
public actual sealed class AIAgentLLMSession actual constructor(
    executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    prompt: Prompt,
    model: LLModel,
    config: AIAgentConfig,
) : AutoCloseable {
    private val delegate = AIAgentLLMSessionImpl(executor, tools, prompt, model, config)

    protected actual open val config: AIAgentConfig = delegate.config

    public actual open val prompt: Prompt = delegate.prompt

    public actual open val tools: List<ToolDescriptor> = delegate.tools

    public actual open val model: LLModel = delegate.model

    protected actual open var isActive: Boolean = delegate.isActive

    protected actual open fun validateSession(): Unit = delegate.validateSession()

    protected actual open fun preparePrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt =
        delegate.preparePrompt(prompt, tools)

    protected actual open fun executeStreaming(prompt: Prompt, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        delegate.executeStreaming(prompt, tools)

    protected actual open suspend fun executeMultiple(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = delegate.executeMultiple(prompt, tools)

    protected actual open suspend fun executeSingle(prompt: Prompt, tools: List<ToolDescriptor>): Message.Response =
        delegate.executeSingle(prompt, tools)

    public actual open suspend fun requestLLMMultipleWithoutTools(): List<Message.Response> =
        delegate.requestLLMMultipleWithoutTools()

    public actual open suspend fun requestLLMWithoutTools(): Message.Response = delegate.requestLLMWithoutTools()

    public actual open suspend fun requestLLMOnlyCallingTools(): Message.Response =
        delegate.requestLLMOnlyCallingTools()

    public actual open suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response =
        delegate.requestLLMForceOneTool(tool)

    public actual open suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response =
        delegate.requestLLMForceOneTool(tool)

    public actual open suspend fun requestLLM(): Message.Response = delegate.requestLLM()

    public actual open suspend fun requestLLMStreaming(): Flow<StreamFrame> = delegate.requestLLMStreaming()

    public actual open suspend fun requestModeration(moderatingModel: LLModel?): ModerationResult =
        delegate.requestModeration()

    public actual open suspend fun requestLLMMultiple(): List<Message.Response> = delegate.requestLLMMultiple()

    public actual open suspend fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
    ): Result<StructuredResponse<T>> = delegate.requestLLMStructured(config)

    public actual open suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>> = delegate.requestLLMStructured(serializer, examples, fixingParser)

    public actual open suspend fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>
    ): StructuredResponse<T> = delegate.parseResponseToStructuredResponse(response, config)

    public actual open suspend fun requestLLMMultipleChoices(): List<LLMChoice> = delegate.requestLLMMultipleChoices()

    actual override fun close(): Unit = delegate.close()
}
