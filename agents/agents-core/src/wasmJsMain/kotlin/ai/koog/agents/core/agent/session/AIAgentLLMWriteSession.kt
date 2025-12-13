@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlin.reflect.KClass

public actual open class AIAgentLLMWriteSession internal actual constructor(
    environment: AIAgentEnvironment,
    executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    config: AIAgentConfig,
    clock: Clock
) : AIAgentLLMSession(executor, tools, prompt, model, config) {
    @PublishedApi
    internal val delegate: AIAgentLLMWriteSessionImpl = AIAgentLLMWriteSessionImpl(
        environment,
        executor,
        tools,
        toolRegistry,
        prompt,
        model,
        config,
        clock
    )

    actual override var prompt: Prompt = delegate.prompt

    actual override var tools: List<ToolDescriptor> = delegate.tools

    actual override var model: LLModel = delegate.model

    @PublishedApi
    internal actual val environment: AIAgentEnvironment = delegate.environment

    @PublishedApi
    internal actual val toolRegistry: ToolRegistry = delegate.toolRegistry

    public actual val clock: Clock = delegate.clock

    public actual open fun <TArgs, TResult> findTool(tool: Tool<TArgs, TResult>): SafeTool<TArgs, TResult> =
        delegate.findTool(tool)

    @Suppress(names = ["UNCHECKED_CAST"])
    public actual open fun <TArgs, TResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult> =
        delegate.findTool(toolClass) as SafeTool<TArgs, TResult>

    public actual open fun appendPrompt(body: PromptBuilder.() -> Unit): Unit = delegate.appendPrompt(body)

    @Deprecated(
        message = "Use `appendPrompt` instead",
        replaceWith = ReplaceWith(expression = "appendPrompt(body)")
    )
    public actual open fun updatePrompt(body: PromptBuilder.() -> Unit): Unit = delegate.appendPrompt(body)

    public actual open fun rewritePrompt(body: (Prompt) -> Prompt): Unit = delegate.rewritePrompt(body)

    public actual open fun changeModel(newModel: LLModel): Unit = delegate.changeModel(newModel)

    public actual open fun changeLLMParams(newParams: LLMParams): Unit = delegate.changeLLMParams(newParams)

    public actual open suspend fun requestLLMStreaming(definition: StructureDefinition?): Flow<StreamFrame> =
        delegate.requestLLMStreaming(definition)

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int
    ): Flow<SafeTool.Result<TResult>> = with(delegate) { toParallelToolCallsImpl(safeTool, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        tool: Tool<TArgs, TResult>,
        concurrency: Int
    ): Flow<SafeTool.Result<TResult>> = with(delegate) { toParallelToolCallsImpl(tool, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int
    ): Flow<SafeTool.Result<TResult>> = with(delegate) { toParallelToolCallsImpl(toolClass, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int
    ): Flow<String> = with(delegate) { toParallelToolCallsRawImpl(safeTool, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int
    ): Flow<String> = with(delegate) { toParallelToolCallsRawImpl(toolClass, concurrency) }
}
