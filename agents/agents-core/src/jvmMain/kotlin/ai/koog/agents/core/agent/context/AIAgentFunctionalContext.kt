@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.context

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.NonSuspendAIAgentFunctionalStrategy
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
import ai.koog.agents.core.utils.asCoroutineContext
import ai.koog.agents.core.utils.runOnLLMDispatcher
import ai.koog.agents.core.utils.runOnMainDispatcher
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Flow.Publisher
import java.util.concurrent.Flow.Subscription
import java.util.function.BiFunction
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

    @JavaAPI
    @JvmOverloads
    public fun getHistory(executorService: ExecutorService? = null): List<Message> =
        config.runOnMainDispatcher(executorService) { getHistory() }

    @JvmOverloads
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

    @JvmOverloads
    public actual open suspend fun requestLLM(
        message: String,
        allowToolCalls: Boolean
    ): Message.Response = delegate.requestLLM(message, allowToolCalls)

    /**
     * Sends a request to the Large Language Model (LLM) and retrieves its response.
     *
     * @param message The input message to be sent to the LLM.
     * @param allowToolCalls Determines whether the LLM is allowed to use tools during its response generation.
     *                       Defaults to true.
     * @param executorService An optional `ExecutorService` instance that enables custom thread management for the request.
     *                        Defaults to null.
     * @return A [Message.Response] object containing the*/
    @JavaAPI
    @JvmOverloads
    public fun requestLLM(
        message: String,
        allowToolCalls: Boolean = true,
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnLLMDispatcher(executorService) {
        requestLLM(message, allowToolCalls)
    }

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

    /**
     * Retrieves the most recent token usage count synchronously.
     *
     * This method executes the `latestTokenUsage` function on the main dispatcher. It can leverage
     * an optional `ExecutorService` to provide a custom thread management mechanism.
     *
     * @param executorService An optional `ExecutorService` instance for managing thread execution. Defaults to `null`.
     * @return The latest token usage count as an integer.
     */
    @JavaAPI
    @JvmOverloads
    public fun latestTokenUsage(
        executorService: ExecutorService? = null
    ): Int = config.runOnMainDispatcher(executorService) {
        latestTokenUsage()
    }

    @JvmOverloads
    public actual suspend inline fun <reified T> requestLLMStructured(
        message: String,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>> = delegate.requestLLMStructuredImpl(message, examples, fixingParser)

    /**
     * Sends a structured request to the Large Language Model (LLM) and processes the response.
     *
     * @param T The type of the structured response required.
     * @param message The input message to be sent to the LLM.
     * @param clazz The Kotlin class type of the expected structured response.
     * @param examples An optional list of example objects used to guide the model's structured response generation.
     * @param fixingParser An optional parser to correct or validate the structured response.
     * @return A [Result] containing a [StructuredResponse] of the requested type [T], or an error if the request fails.
     */
    @JavaAPI
    @JvmOverloads
    @OptIn(InternalSerializationApi::class)
    public suspend fun <T : Any> requestLLMStructured(
        message: String,
        clazz: KClass<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> =
        delegate.requestLLMStructuredImpl(message, clazz.serializer(), examples, fixingParser)

    @JvmOverloads
    public actual open suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition?
    ): Flow<StreamFrame> = delegate.requestLLMStreaming(message, structureDefinition)

    /**
     * Sends a request to the Language Learning Model (LLM) for streaming data.
     *
     * @param message The message or query to be sent to the LLM for processing.
     * @param structureDefinition An optional parameter specifying the structured data definition for parsing or validating the response.
     * @param executorService An optional executor service to be used for managing coroutine execution. Defaults to null, which will use the default executor service.
     * @return A `Publisher` that emits `StreamFrame` objects representing the streamed response from the LLM.
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition?,
        executorService: ExecutorService? = null
    ): Publisher<StreamFrame> = config.runOnLLMDispatcher(executorService) {
        // TODO: Use JavaRX instead of Publisher!
        val context = executorService.asCoroutineContext(
            defaultExecutorService = config.llmRequestExecutorService,
            fallbackDispatcher = Dispatchers.IO
        )

        Publisher { subscriber ->
            val scope = CoroutineScope(context)
            val job = scope.launch {
                try {
                    requestLLMStreaming(message, structureDefinition).collect { frame ->
                        subscriber.onNext(frame)
                    }
                    subscriber.onComplete()
                } catch (e: Throwable) {
                    subscriber.onError(e)
                }
            }
            subscriber.onSubscribe(object : Subscription {
                override fun request(n: Long) {
                    // Basic implementation without backpressure handling for simplicity.
                    // For production, consider adding flow control (e.g., using a shared flow or buffer).
                }

                override fun cancel() {
                    job.cancel()
                }
            })
        }
    }

    public actual open suspend fun requestLLMMultiple(message: String): List<Message.Response> =
        delegate.requestLLMMultiple(message)

    /**
     * Sends a request to the Large Language Model (LLM) and retrieves multiple responses.
     *
     * @param message The input message to be sent to the LLM.
     * @param executorService An optional `ExecutorService` instance for managing thread execution. Defaults to `null`.
     * @return A list of [Message.Response] objects containing the LLM responses to the provided message.
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMMultiple(
        message: String,
        executorService: ExecutorService? = null
    ): List<Message.Response> = config.runOnLLMDispatcher(executorService) {
        requestLLMMultiple(message)
    }

    public actual open suspend fun requestLLMOnlyCallingTools(message: String): Message.Response =
        delegate.requestLLMOnlyCallingTools(message)

    /**
     * Executes a request to the LLM, restricting the process to only calling external tools as needed.
     *
     * @param message The input message or query to be processed by the LLM.
     * @param executorService Optional executor service to specify custom threading behavior; if null, a default executor is used.
     * @return The response generated by the LLM, encapsulated in a Message.Response object.
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMOnlyCallingTools(
        message: String,
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnLLMDispatcher(executorService) {
        requestLLMOnlyCallingTools(message)
    }

    public actual open suspend fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor
    ): Message.Response = delegate.requestLLMForceOneTool(message, tool)

    /**
     * Sends a request to the LLM (Large Language Model) system using a specified tool, ensuring the
     * use of exactly one tool in the response generation process.
     *
     * @param message The input message or prompt to be processed by the LLM.
     * @param tool The specific tool descriptor that defines the tool to be used.
     * @param executorService An optional custom executor service for handling the request.
     *                        Defaults to null, in which case the default dispatcher is used.
     * @return The response generated by the LLM system as a `Message.Response` object.
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor,
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnLLMDispatcher(executorService) {
        requestLLMForceOneTool(message, tool)
    }

    public actual open suspend fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>
    ): Message.Response = delegate.requestLLMForceOneTool(message, tool)

    /**
     * Sends a request to the LLM (Large Language Model) forcing the use of a specified tool and returns the response.
     *
     * @param message The message to be sent to the LLM.
     * @param tool The tool that the LLM is forced to use in processing the message.
     * @param executorService Optional parameter for an ExecutorService to control the dispatching of the request.
     * If not provided, a default dispatcher will be used.
     * @return A Message.Response object containing the result from the LLM after processing the request.
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>,
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnLLMDispatcher(executorService) {
        requestLLMForceOneTool(message, tool)
    }

    public actual open suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
        delegate.executeTool(toolCall)

    /**
     * Executes the specified tool call using an optional executor service.
     *
     * @param toolCall the tool call to be executed
     * @param executorService the executor service to run the tool call on, defaults to null
     * @return the result of the executed tool call
     */
    @JavaAPI
    @JvmOverloads
    public fun executeTool(
        toolCall: Message.Tool.Call,
        executorService: ExecutorService? = null
    ): ReceivedToolResult = config.runOnMainDispatcher(executorService) {
        executeTool(toolCall)
    }

    @JvmOverloads
    public actual open suspend fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean
    ): List<ReceivedToolResult> = delegate.executeMultipleTools(toolCalls, parallelTools)

    /**
     * Executes multiple tool calls either sequentially or in parallel based on the provided configurations.
     *
     * @param toolCalls a list of tool call objects to be executed
     * @param parallelTools a boolean flag indicating whether the tool calls should be executed in parallel
     * @param executorService an optional executor service to manage parallel execution; if not provided, a default executor is used
     * @return a list of results obtained from executing the provided tool calls
     */
    @JavaAPI
    @JvmOverloads
    public fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean,
        executorService: ExecutorService? = null
    ): List<ReceivedToolResult> = config.runOnMainDispatcher(executorService) {
        executeMultipleTools(toolCalls, parallelTools)
    }

    public actual open suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Response =
        delegate.sendToolResult(toolResult)

    /**
     * Sends the provided tool result for processing.
     *
     * @param toolResult The result from a tool that is to be sent for further processing.
     * @param executorService An optional executor service to manage thread execution. If null, a default dispatcher will be used.
     * @return A response message object after processing the tool result.
     */
    @JavaAPI
    @JvmOverloads
    public fun sendToolResult(
        toolResult: ReceivedToolResult,
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnLLMDispatcher(executorService) {
        sendToolResult(toolResult)
    }

    public actual open suspend fun sendMultipleToolResults(
        results: List<ReceivedToolResult>
    ): List<Message.Response> = delegate.sendMultipleToolResults(results)

    /**
     * Sends multiple tool results for processing and returns the corresponding responses.
     *
     * @param results A list of ReceivedToolResult representing the tool results to be processed.
     * @param executorService An optional ExecutorService to run the dispatch operation. If null, a default executor is used.
     * @return A list of Message.Response objects representing the responses after processing the tool results.
     */
    @JavaAPI
    @JvmOverloads
    public fun sendMultipleToolResults(
        results: List<ReceivedToolResult>,
        executorService: ExecutorService? = null
    ): List<Message.Response> = config.runOnLLMDispatcher(executorService) {
        sendMultipleToolResults(results)
    }

    @JvmOverloads
    public actual open suspend fun <ToolArg, TResult> executeSingleTool(
        tool: Tool<ToolArg, TResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean
    ): SafeTool.Result<TResult> = delegate.executeSingleTool(tool, toolArgs, doUpdatePrompt)

    /**
     * Executes a single tool with the specified arguments.
     *
     * @param tool The tool to be executed.
     * @param toolArgs The arguments to be passed to the tool.
     * @param doUpdatePrompt A flag indicating whether to update the prompt during execution.
     * @param executorService The executor service to be used for execution. If null, a default dispatcher will be used.
     * @return The result of the executed tool wrapped in a SafeTool.Result.
     */
    @JavaAPI
    @JvmOverloads
    public fun <ToolArg, ToolResult> executeSingleTool(
        tool: Tool<ToolArg, ToolResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean,
        executorService: ExecutorService? = null
    ): SafeTool.Result<ToolResult> = config.runOnMainDispatcher(executorService) {
        executeSingleTool(tool, toolArgs, doUpdatePrompt)
    }

    @JvmOverloads
    public actual open suspend fun compressHistory(
        strategy: HistoryCompressionStrategy,
        preserveMemory: Boolean
    ): Unit = delegate.compressHistory(strategy, preserveMemory)

    /**
     * Compresses the historical data of a tool's operations using the specified compression strategy.
     * This method is designed for optimizing memory usage by reducing the size of stored historical data.
     *
     * @param strategy the strategy to use for compressing the history, defaults to the whole history compression strategy
     * @param preserveMemory a flag indicating whether to prioritize memory preservation during compression, defaults to true
     * @param executorService an optional executor service to perform the operation asynchronously, defaults to null
     * @return Unit
     */
    @JavaAPI
    @JvmOverloads
    public fun <ToolArg, ToolResult> compressHistory(
        strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
        preserveMemory: Boolean = true,
        executorService: ExecutorService? = null
    ): Unit = config.runOnLLMDispatcher(executorService) {
        compressHistory(strategy, preserveMemory)
    }

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

    @JavaAPI
    @JvmOverloads
    public fun <Input> subtaskWithVerification(
        input: Input,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        executorService: ExecutorService? = null,
        defineTask: BiFunction<AIAgentFunctionalContext, Input, String>
    ): CriticResult<Input> = config.runOnMainDispatcher(executorService) {
        subtaskWithVerification(
            input,
            tools,
            llmModel,
            llmParams,
            runMode,
            assistantResponseRepeatMax
        ) {
            config.runOnMainDispatcher(executorService) { defineTask.apply(this, it) }
        }
    }

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

    @JavaAPI
    @JvmOverloads
    public fun <Input, Output : Any> subtask(
        input: Input,
        outputClass: Class<Output>,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        executorService: ExecutorService? = null,
        defineTask: BiFunction<AIAgentFunctionalContext, Input, String>
    ): Output = config.runOnMainDispatcher(executorService) {
        subtask(
            input,
            outputClass.kotlin,
            tools,
            llmModel,
            llmParams,
            runMode,
            assistantResponseRepeatMax
        ) {
            config.runOnMainDispatcher(executorService) { defineTask.apply(this, it) }
        }
    }

    public actual open suspend fun <Input, Output, OutputTransformed> subtask(
        input: Input,
        tools: List<Tool<*, *>>?,
        finishTool: Tool<Output, OutputTransformed>,
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

    @JavaAPI
    @JvmOverloads
    public fun <Input, Output, OutputTransformed> subtask(
        input: Input,
        finishTool: Tool<Output, OutputTransformed>,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        executorService: ExecutorService? = null,
        defineTask: BiFunction<AIAgentFunctionalContext, Input, String>
    ): OutputTransformed = config.runOnMainDispatcher(executorService) {
        subtask(
            input,
            tools,
            finishTool,
            llmModel,
            llmParams,
            runMode,
            assistantResponseRepeatMax
        ) {
            config.runOnMainDispatcher(executorService) { defineTask.apply(this, it) }
        }
    }
}
