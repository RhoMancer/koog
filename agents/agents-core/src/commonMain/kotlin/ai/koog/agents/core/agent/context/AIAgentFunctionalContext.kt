@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

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

/**
 * Represents the execution context for an AI agent operating in a loop.
 * It provides access to critical parts such as the environment, configuration, large language model (LLM) context,
 * state management, and storage. Additionally, it enables the agent to store, retrieve, and manage context-specific data
 * during its execution lifecycle.
 *
 * @property environment The environment interface allowing the agent to interact with the external world,
 * including executing tools and reporting problems.
 * @property agentId A unique identifier for the agent, differentiating it from other agents in the system.
 * @property runId A unique identifier for the current run or instance of the agent's operation.
 * @property agentInput The input data passed to the agent, which can be of any type, depending on the agent's context.
 * @property config The configuration settings for the agent, including its prompt and model details,
 * as well as operational constraints like iteration limits.
 * @property llm The context for interacting with the large language model used by the agent, enabling message history
 * retrieval and processing.
 * @property stateManager The state management component responsible for tracking and updating the agent's state during its execution.
 * @property storage A storage interface providing persistent storage capabilities for the agent's data.
 * @property strategyName The name of the agent's strategic approach or operational method, determining its behavior
 * during execution.
 */
@OptIn(InternalAgentsApi::class)
@Suppress("UNCHECKED_CAST", "MissingKDocForPublicAPI")
public expect open class AIAgentFunctionalContext internal constructor(
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
    parentContext: AIAgentContext? = null
) : AIAgentContext {

    override val environment: AIAgentEnvironment
    override val agentId: String
    override val pipeline: AIAgentFunctionalPipeline
    override val executionInfo: AgentExecutionInfo
    override val runId: String
    override val agentInput: Any?
    override val config: AIAgentConfig
    override val llm: AIAgentLLMContext
    override val stateManager: AIAgentStateManager
    override val storage: AIAgentStorage
    override val strategyName: String

    @InternalAgentsApi
    override val parentContext: AIAgentContext?
    open override fun store(key: AIAgentStorageKey<*>, value: Any)

    open override fun <T> get(key: AIAgentStorageKey<*>): T?

    open override fun remove(key: AIAgentStorageKey<*>): Boolean

    open override suspend fun getHistory(): List<Message>

    /**
     * Creates a copy of the current [AIAgentFunctionalContext], allowing for selective overriding of its properties.
     * This method is particularly useful for creating modified contexts during agent execution without mutating
     * the original context - perfect for when you need to experiment with different configurations or
     * pass tweaked contexts down the execution pipeline while keeping the original pristine!
     *
     * @param environment The [AIAgentEnvironment] to be used in the new context, or retain the current playground if not specified.
     * @param agentId The unique agent identifier, or keep the same identity if you're feeling attached.
     * @param runId The run identifier for this execution adventure, or stick with the current journey.
     * @param agentInput The input data for the agent - fresh data or the same trusty input, your choice!
     * @param config The [AIAgentConfig] for the new context, or keep the current rulebook.
     * @param llm The [AIAgentLLMContext] to be used, or maintain the current AI conversation partner.
     * @param stateManager The [AIAgentStateManager] to be used, or preserve the current state keeper.
     * @param storage The [AIAgentStorage] to be used, or stick with the current memory bank.
     * @param strategyName The strategy name, or maintain the current game plan.
     * @param pipeline The [AIAgentFunctionalPipeline] to be used, or keep the current execution superhighway.
     * @param parentRootContext The parent root context, or maintain the current family tree.
     * @return A shiny new [AIAgentFunctionalContext] with your desired modifications applied!
     */
    public open fun copy(
        environment: AIAgentEnvironment = this.environment,
        agentId: String = this.agentId,
        runId: String = this.runId,
        agentInput: Any? = this.agentInput,
        config: AIAgentConfig = this.config,
        llm: AIAgentLLMContext = this.llm,
        stateManager: AIAgentStateManager = this.stateManager,
        storage: AIAgentStorage = this.storage,
        strategyName: String = this.strategyName,
        pipeline: AIAgentFunctionalPipeline = this.pipeline,
        parentRootContext: AIAgentContext? = this.parentContext,
    ): AIAgentFunctionalContext

    /**
     * Sends a message to a Large Language Model (LLM) and optionally allows the use of tools during the LLM interaction.
     * The message becomes part of the current prompt, and the LLM's response is processed accordingly,
     * either with or without tool integrations based on the provided parameters.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param allowToolCalls Specifies whether tool calls are allowed during the LLM interaction. Defaults to `true`.
     */
    public open suspend fun requestLLM(
        message: String,
        allowToolCalls: Boolean = true
    ): Message.Response

    /**
     * Executes the provided action if the given response is of type [Message.Assistant].
     *
     * @param response The response message to evaluate, which may or may not be of type [Message.Assistant].
     * @param action A lambda function to execute if the response is an instance of [Message.Assistant].
     */
    public open fun onAssistantMessage(
        response: Message.Response,
        action: (Message.Assistant) -> Unit
    )

    /**
     * Attempts to cast a `Message.Response` instance to a `Message.Assistant` type.
     *
     * This method checks if the first element in the response is of type `Message.Assistant`
     * and, if so, returns it; otherwise, it returns `null`.
     *
     * @return The `Message.Assistant` instance if the cast is successful, or `null` if the cast fails.
     */
    public open fun Message.Response.asAssistantMessageOrNull(): Message.Assistant?

    /**
     * Casts the current instance of a [Message.Response] to a [Message.Assistant].
     * This function should only be used when it is guaranteed that the instance
     * is of type [Message.Assistant], as it will throw an exception if the type
     * does not match.
     *
     * @return The current instance cast to [Message.Assistant].
     */
    public open fun Message.Response.asAssistantMessage(): Message.Assistant

    /**
     * Invokes the provided action when multiple tool call messages are found within a given list of response messages.
     * Filters the list of responses to include only instances of `Message.Tool.Call` and executes the action on the filtered list if it is not empty.
     *
     * @param response A list of response messages to be checked for tool call messages.
     * @param action A lambda function to be executed with the list of filtered tool call messages, if any exist.
     */
    public open fun onMultipleToolCalls(
        response: List<Message.Response>,
        action: (List<Message.Tool.Call>) -> Unit
    )

    /**
     * Extracts a list of tool call messages from a given list of response messages.
     *
     * @param response A list of response messages to filter, potentially containing various types of responses.
     * @return A list of messages specifically representing tool calls, which are instances of [Message.Tool.Call].
     */
    public open fun extractToolCalls(
        response: List<Message.Response>
    ): List<Message.Tool.Call>

    /**
     * Filters the provided list of response messages to include only assistant messages and,
     * if the filtered list is not empty, performs the specified action with the filtered list.
     *
     * @param response A list of response messages to be processed. Only those of type `Message.Assistant` will be considered.
     * @param action A lambda function to execute on the list of assistant messages if the filtered list is not empty.
     */
    public open fun onMultipleAssistantMessages(
        response: List<Message.Response>,
        action: (List<Message.Assistant>) -> Unit
    )

    /**
     * Retrieves the latest token usage from the prompt within the LLM session.
     *
     * @return The latest token usage information as an integer.
     */
    public open suspend fun latestTokenUsage(): Int

    /**
     * Sends a message to a Large Language Model (LLM) and requests structured data from the LLM with error correction capabilities.
     * The message becomes part of the current prompt, and the LLM's response is processed to extract structured data.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param structure Definition of expected output format and parsing logic.
     * @param retries Number of retry attempts for failed generations.
     * @param fixingModel LLM used for error correction.
     * @return Result containing the structured response if successful, or an error if parsing failed.
     */
    public suspend inline fun <reified T> requestLLMStructured(
        message: String,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>>

    /**
     * Sends a message to a Large Language Model (LLM) and streams the LLM response.
     * The message becomes part of the current prompt, and the LLM's response is streamed as it's generated.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param structureDefinition Optional structure to guide the LLM response.
     * @return A flow of [StreamFrame] objects from the LLM response.
     */
    public open suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition? = null
    ): Flow<StreamFrame>

    /**
     * Sends a message to a Large Language Model (LLM) and gets multiple LLM responses with tool calls enabled.
     * The message becomes part of the current prompt, and multiple responses from the LLM are collected.
     *
     * @param message The content of the message to be sent to the LLM.
     * @return A list of LLM responses.
     */
    public open suspend fun requestLLMMultiple(message: String): List<Message.Response>

    /**
     * Sends a message to a Large Language Model (LLM) that will only call tools without generating text responses.
     * The message becomes part of the current prompt, and the LLM is instructed to only use tools.
     *
     * @param message The content of the message to be sent to the LLM.
     * @return The LLM response containing tool calls.
     */
    public open suspend fun requestLLMOnlyCallingTools(message: String): Message.Response

    /**
     * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
     * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param tool The tool descriptor that the LLM must use.
     * @return The LLM response containing the tool call.
     */
    public open suspend fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor
    ): Message.Response

    /**
     * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
     * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param tool The tool that the LLM must use.
     * @return The LLM response containing the tool call.
     */
    public open suspend fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>
    ): Message.Response

    /**
     * Executes a tool call and returns the result.
     *
     * @param toolCall The tool call to execute.
     * @return The result of the tool execution.
     */
    public open suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult

    /**
     * Executes multiple tool calls and returns their results.
     * These calls can optionally be executed in parallel.
     *
     * @param toolCalls The list of tool calls to execute.
     * @param parallelTools Specifies whether tools should be executed in parallel, defaults to false.
     * @return A list of results from the executed tool calls.
     */
    public open suspend fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean = false
    ): List<ReceivedToolResult>

    /**
     * Adds a tool result to the prompt and requests an LLM response.
     *
     * @param toolResult The tool result to add to the prompt.
     * @return The LLM response.
     */
    public open suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Response

    /**
     * Adds multiple tool results to the prompt and gets multiple LLM responses.
     *
     * @param results The list of tool results to add to the prompt.
     * @return A list of LLM responses.
     */
    public open suspend fun sendMultipleToolResults(
        results: List<ReceivedToolResult>
    ): List<Message.Response>

    /**
     * Calls a specific tool directly using the provided arguments.
     *
     * @param tool The tool to execute.
     * @param toolArgs The arguments to pass to the tool.
     * @param doUpdatePrompt Specifies whether to add tool call details to the prompt.
     * @return The result of the tool execution.
     */
    public open suspend fun <ToolArg, TResult> executeSingleTool(
        tool: Tool<ToolArg, TResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean = true
    ): SafeTool.Result<TResult>

    /**
     * Compresses the current LLM prompt (message history) into a summary, replacing messages with a TLDR.
     *
     * @param input The input value that will be returned unchanged after compression.
     * @param strategy Determines which messages to include in compression.
     * @param preserveMemory Specifies whether to retain message memory after compression.
     * @return The input value, unchanged.
     */
    public open suspend fun compressHistory(
        strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
        preserveMemory: Boolean = true
    )

    /**
     * Executes a subtask with validation and verification of the results.
     * The method defines a subtask for the AI agent using the provided input
     * and additional parameters and ensures that the output is evaluated
     * based on its correctness and feedback.
     *
     * @param Input The type of the input provided to the subtask.
     * @param input The input data for the subtask, which will be used to
     * create and execute the task.
     * @param tools An optional list of tools that can be used during
     * the execution of the subtask.
     * @param llmModel An optional parameter specifying the LLM model to be used for the subtask.
     * @param llmParams Optional configuration parameters for the LLM, such as temperature
     * and token limits.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax An optional parameter specifying the maximum number of
     * retries for getting valid responses from the assistant.
     * @param defineTask A suspend function that defines the subtask as a string
     * based on the provided input.
     * @return A [CriticResult] object containing the verification status, feedback,
     * and the original input for the subtask.
     */
    @OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)
    public open suspend fun <Input> subtaskWithVerification(
        input: Input,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        defineTask: suspend AIAgentFunctionalContext.(input: Input) -> String
    ): CriticResult<Input>

    /**
     * Executes a subtask within the larger context of an AI agent's functional operation. This method allows you to define a specific
     * task to be performed, using the given input, tools, and optional configuration parameters.
     *
     * @param Input The type of input provided to the subtask.
     * @param Output The type of the output expected from the subtask.
     * @param input The input data required for the subtask execution.
     * @param tools A list of tools available for use within the subtask.
     * @param llmModel The optional large language model to be used during the subtask, if different from the default one.
     * @param llmParams The configuration parameters for the large language model, such as temperature, etc.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of times the assistant response can repeat. Useful to control redundant outputs.
     * @param defineTask A suspendable lambda defining the actual task logic, which takes the provided input and produces a task description.
     * @return The result of the subtask execution, as an instance of type Output.
     */
    @OptIn(InternalAgentToolsApi::class)
    public suspend inline fun <Input, reified Output> subtask(
        input: Input,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        noinline defineTask: suspend AIAgentFunctionalContext.(input: Input) -> String
    ): Output

    /**
     * Executes a subtask within the larger context of an AI agent's functional operation. This method allows you to define a specific
     * task to be performed, using the given input, tools, and optional configuration parameters.
     *
     * @param Input The type of input provided to the subtask.
     * @param Output The type of the output expected from the subtask.
     * @param input The input data required for the subtask execution.
     * @param tools A list of tools available for use within the subtask.
     * @param llmModel The optional large language model to be used during the subtask, if different from the default one.
     * @param llmParams The configuration parameters for the large language model, such as temperature, etc.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of times the assistant response can repeat. Useful to control redundant outputs.
     * @param defineTask A suspendable lambda defining the actual task logic, which takes the provided input and produces a task description.
     * @return The result of the subtask execution, as an instance of type Output.
     */
    @OptIn(InternalAgentToolsApi::class)
    public open suspend fun <Input, Output : Any> subtask(
        input: Input,
        outputClass: KClass<Output>,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        defineTask: suspend AIAgentFunctionalContext.(input: Input) -> String
    ): Output

    /**
     * Executes a subtask within the AI agent's functional context. This method enables the use of tools to
     * achieve a specific task based on the input provided. It defines the task using an inline function,
     * employs tools iteratively, and attempts to complete the subtask with a designated finishing tool.
     *
     * @param input The input data required to define and execute the subtask.
     * @param tools An optional list of tools that can be used to achieve the task, excluding the finishing tool.
     * @param finishTool A mandatory tool that determines the final result of the subtask by producing and transforming output.
     * @param llmModel An optional specific language learning model (LLM) to use for executing the subtask.
     * @param llmParams Optional parameters for configuring the behavior of the LLM during subtask execution.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of feedback attempts allowed from the language model if the subtask is not completed.
     * @param defineTask A suspendable function used to define the task based on the provided input.
     * @return The transformed final result of executing the finishing tool to complete the subtask.
     */
    @OptIn(InternalAgentToolsApi::class, DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
    public open suspend fun <Input, Output, OutputTransformed> subtask(
        input: Input,
        tools: List<Tool<*, *>>? = null,
        finishTool: Tool<Output, OutputTransformed>,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        defineTask: suspend AIAgentFunctionalContext.(input: Input) -> String
    ): OutputTransformed
}
