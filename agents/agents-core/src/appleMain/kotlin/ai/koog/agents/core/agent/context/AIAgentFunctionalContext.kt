@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@OptIn(InternalAgentsApi::class)
@Suppress("UNCHECKED_CAST", "MissingKDocForPublicAPI")
public actual open class AIAgentFunctionalContext internal actual constructor(
    environment: AIAgentEnvironment,
    agentId: String,
    runId: String,
    agentInput: Any?,
    config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    strategyName: String,
    pipeline: AIAgentFunctionalPipeline,
    executionInfo: AgentExecutionInfo,
    parentContext: AIAgentContext?
) : AIAgentContext {
    @PublishedApi
    internal val delegate: AIAgentFunctionalContextImpl = AIAgentFunctionalContextImpl(
        environment = environment,
        agentId = agentId,
        pipeline = pipeline,
        runId = runId,
        agentInput = agentInput,
        config = config,
        llm = llm,
        stateManager = stateManager,
        storage = storage,
        strategyName = strategyName,
        parentContext = parentContext,
        executionInfo = executionInfo,
    )

    actual override val environment: AIAgentEnvironment = delegate.environment
    actual override val agentId: String = delegate.agentId
    actual override val pipeline: AIAgentFunctionalPipeline = delegate.pipeline
    actual override val runId: String = delegate.runId
    actual override val agentInput: Any? = delegate.agentInput
    actual override val config: AIAgentConfig = delegate.config
    actual override val llm: AIAgentLLMContext = delegate.llm
    actual override val stateManager: AIAgentStateManager = delegate.stateManager
    actual override val storage: AIAgentStorage = delegate.storage
    actual override val strategyName: String = delegate.strategyName
    actual override val executionInfo: AgentExecutionInfo = delegate.executionInfo

    @InternalAgentsApi
    actual override val parentContext: AIAgentContext? = delegate.parentContext

    actual open override fun store(key: AIAgentStorageKey<*>, value: Any): Unit = delegate.store(key, value)

    actual open override fun <T> get(key: AIAgentStorageKey<*>): T? = delegate.get(key)

    actual open override fun remove(key: AIAgentStorageKey<*>): Boolean = delegate.remove(key)

    actual open override suspend fun getHistory(): List<Message> = delegate.getHistory()

    public actual open fun copy(
        environment: AIAgentEnvironment,
        agentId: String,
        runId: String,
        agentInput: Any?,
        config: AIAgentConfig,
        llm: AIAgentLLMContext,
        stateManager: AIAgentStateManager,
        storage: AIAgentStorage,
        strategyName: String,
        pipeline: AIAgentFunctionalPipeline,
        parentRootContext: AIAgentContext?
    ): AIAgentFunctionalContext = delegate.copy(
        environment = environment,
        agentId = agentId,
        runId = runId,
        agentInput = agentInput,
        config = config,
        llm = llm,
        stateManager = stateManager,
        storage = storage,
        strategyName = strategyName,
        pipeline = pipeline,
        parentRootContext = parentRootContext,
    )

    public actual open suspend fun requestLLM(
        message: String,
        allowToolCalls: Boolean
    ): Message.Response = delegate.requestLLM(message, allowToolCalls)

    public actual open fun onAssistantMessage(
        response: Message.Response,
        action: (Message.Assistant) -> Unit
    ): Unit = delegate.onAssistantMessage(response, action)

    public actual open fun Message.Response.asAssistantMessageOrNull(): Message.Assistant? = with(delegate) {
        this@asAssistantMessageOrNull.asAssistantMessageOrNull()
    }

    public actual open fun Message.Response.asAssistantMessage(): Message.Assistant = with(delegate) {
        this@asAssistantMessage.asAssistantMessage()
    }

    public actual open fun onMultipleToolCalls(
        response: List<Message.Response>,
        action: (List<Message.Tool.Call>) -> Unit
    ): Unit = delegate.onMultipleToolCalls(response, action)

    public actual open fun extractToolCalls(
        response: List<Message.Response>
    ): List<Message.Tool.Call> = delegate.extractToolCalls(response)

    public actual open fun onMultipleAssistantMessages(
        response: List<Message.Response>,
        action: (List<Message.Assistant>) -> Unit
    ): Unit = delegate.onMultipleAssistantMessages(response, action)

    public actual open suspend fun latestTokenUsage(): Int = delegate.latestTokenUsage()

    public actual suspend inline fun <reified T> requestLLMStructured(
        message: String,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>> = delegate.requestLLMStructuredImpl(message, examples, fixingParser)

    public actual open suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition?
    ): Flow<StreamFrame> = delegate.requestLLMStreaming(message, structureDefinition)

    public actual open suspend fun requestLLMMultiple(message: String): List<Message.Response> =
        delegate.requestLLMMultiple(message)

    public actual open suspend fun requestLLMOnlyCallingTools(message: String): Message.Response =
        delegate.requestLLMOnlyCallingTools(message)

    public actual open suspend fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor
    ): Message.Response = delegate.requestLLMForceOneTool(message, tool)

    public actual open suspend fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>
    ): Message.Response = delegate.requestLLMForceOneTool(message, tool)

    public actual open suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
        delegate.executeTool(toolCall)

    public actual open suspend fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean
    ): List<ReceivedToolResult> = delegate.executeMultipleTools(toolCalls, parallelTools)

    public actual open suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Response =
        delegate.sendToolResult(toolResult)

    public actual open suspend fun sendMultipleToolResults(
        results: List<ReceivedToolResult>
    ): List<Message.Response> = delegate.sendMultipleToolResults(results)

    public actual open suspend fun <ToolArg, TResult> executeSingleTool(
        tool: Tool<ToolArg, TResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean
    ): SafeTool.Result<TResult> = delegate.executeSingleTool(tool, toolArgs, doUpdatePrompt)

    public actual open suspend fun compressHistory(
        strategy: HistoryCompressionStrategy,
        preserveMemory: Boolean
    ): Unit = delegate.compressHistory(strategy, preserveMemory)

    public actual open suspend fun <Input> subtaskWithVerification(
        input: Input,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        defineTask: suspend AIAgentFunctionalContext.(Input) -> String
    ): CriticResult<Input> = delegate.subtaskWithVerification(
        input,
        tools,
        llmModel,
        llmParams,
        runMode,
        assistantResponseRepeatMax,
        defineTask
    )

    public actual suspend inline fun <Input, reified Output> subtask(
        input: Input,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        noinline defineTask: suspend AIAgentFunctionalContext.(Input) -> String
    ): Output = delegate.subtaskImpl(input, tools, llmModel, llmParams, runMode, assistantResponseRepeatMax, defineTask)

    public actual open suspend fun <Input, Output : Any> subtask(
        input: Input,
        outputClass: KClass<Output>,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        defineTask: suspend AIAgentFunctionalContext.(Input) -> String
    ): Output = delegate.subtask(
        input,
        outputClass,
        tools,
        llmModel,
        llmParams,
        runMode,
        assistantResponseRepeatMax,
        defineTask
    )

    public actual open suspend fun <Input, OutputTransformed> subtask(
        input: Input,
        tools: List<Tool<*, *>>?,
        finishTool: Tool<*, OutputTransformed>,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        defineTask: suspend AIAgentFunctionalContext.(Input) -> String
    ): OutputTransformed = delegate.subtask(
        input,
        tools,
        finishTool,
        llmModel,
        llmParams,
        runMode,
        assistantResponseRepeatMax,
        defineTask
    )
}

