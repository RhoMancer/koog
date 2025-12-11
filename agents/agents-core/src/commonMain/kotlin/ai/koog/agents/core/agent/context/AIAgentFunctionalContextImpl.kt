package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

@OptIn(InternalAgentsApi::class)
@Suppress("UNCHECKED_CAST")
@PublishedApi
internal class AIAgentFunctionalContextImpl(
    override val environment: AIAgentEnvironment,
    override val agentId: String,
    override val runId: String,
    override val agentInput: Any?,
    override val config: AIAgentConfig,
    override val llm: AIAgentLLMContext,
    override val stateManager: AIAgentStateManager,
    override val storage: AIAgentStorage,
    override val strategyName: String,
    override val pipeline: AIAgentFunctionalPipeline,
    override val executionInfo: AgentExecutionInfo,
    override val parentContext: AIAgentContext? = null
) : AIAgentFunctionalContext(
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
    parentContext = parentContext,
    executionInfo = executionInfo
) {

    private val storeMap: MutableMap<AIAgentStorageKey<*>, Any> = mutableMapOf()

    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        storeMap[key] = value
    }

    override fun <T> get(key: AIAgentStorageKey<*>): T? = storeMap[key] as T?

    override fun remove(key: AIAgentStorageKey<*>): Boolean = storeMap.remove(key) != null

    override suspend fun getHistory(): List<Message> {
        return llm.readSession { prompt.messages }
    }

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
    public override fun copy(
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
        parentRootContext: AIAgentContext?,
    ): AIAgentFunctionalContext {
        val freshContext = AIAgentFunctionalContextImpl(
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
            executionInfo = executionInfo,
            parentContext = parentRootContext,
        )

        // Copy over the internal store map to preserve any stored values
        freshContext.storeMap.putAll(this.storeMap)

        return freshContext
    }

    /**
     * Sends a message to a Large Language Model (LLM) and optionally allows the use of tools during the LLM interaction.
     * The message becomes part of the current prompt, and the LLM's response is processed accordingly,
     * either with or without tool integrations based on the provided parameters.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param allowToolCalls Specifies whether tool calls are allowed during the LLM interaction. Defaults to `true`.
     */
    override suspend fun requestLLM(
        message: String,
        allowToolCalls: Boolean
    ): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            if (allowToolCalls) {
                requestLLM()
            } else {
                requestLLMWithoutTools()
            }
        }
    }

    /**
     * Executes the provided action if the given response is of type [Message.Assistant].
     *
     * @param response The response message to evaluate, which may or may not be of type [Message.Assistant].
     * @param action A lambda function to execute if the response is an instance of [Message.Assistant].
     */
    override fun onAssistantMessage(
        response: Message.Response,
        action: (Message.Assistant) -> Unit
    ) {
        if (response is Message.Assistant) {
            action(response)
        }
    }

    /**
     * Attempts to cast a `Message.Response` instance to a `Message.Assistant` type.
     *
     * This method checks if the first element in the response is of type `Message.Assistant`
     * and, if so, returns it; otherwise, it returns `null`.
     *
     * @return The `Message.Assistant` instance if the cast is successful, or `null` if the cast fails.
     */
    override fun Message.Response.asAssistantMessageOrNull(): Message.Assistant? = this as? Message.Assistant

    /**
     * Casts the current instance of a [Message.Response] to a [Message.Assistant].
     * This function should only be used when it is guaranteed that the instance
     * is of type [Message.Assistant], as it will throw an exception if the type
     * does not match.
     *
     * @return The current instance cast to [Message.Assistant].
     */
    override fun Message.Response.asAssistantMessage(): Message.Assistant = this as Message.Assistant

    /**
     * Invokes the provided action when multiple tool call messages are found within a given list of response messages.
     * Filters the list of responses to include only instances of `Message.Tool.Call` and executes the action on the filtered list if it is not empty.
     *
     * @param response A list of response messages to be checked for tool call messages.
     * @param action A lambda function to be executed with the list of filtered tool call messages, if any exist.
     */
    override fun onMultipleToolCalls(
        response: List<Message.Response>,
        action: (List<Message.Tool.Call>) -> Unit
    ) {
        response.filterIsInstance<Message.Tool.Call>().takeIf { it.isNotEmpty() }?.let {
            action(it)
        }
    }

    /**
     * Extracts a list of tool call messages from a given list of response messages.
     *
     * @param response A list of response messages to filter, potentially containing various types of responses.
     * @return A list of messages specifically representing tool calls, which are instances of [Message.Tool.Call].
     */
    override fun extractToolCalls(
        response: List<Message.Response>
    ): List<Message.Tool.Call> = response.filterIsInstance<Message.Tool.Call>()

    /**
     * Filters the provided list of response messages to include only assistant messages and,
     * if the filtered list is not empty, performs the specified action with the filtered list.
     *
     * @param response A list of response messages to be processed. Only those of type `Message.Assistant` will be considered.
     * @param action A lambda function to execute on the list of assistant messages if the filtered list is not empty.
     */
    override fun onMultipleAssistantMessages(
        response: List<Message.Response>,
        action: (List<Message.Assistant>) -> Unit
    ) {
        response.filterIsInstance<Message.Assistant>().takeIf { it.isNotEmpty() }?.let {
            action(it)
        }
    }

    /**
     * Retrieves the latest token usage from the prompt within the LLM session.
     *
     * @return The latest token usage information as an integer.
     */
    override suspend fun latestTokenUsage(): Int {
        return llm.readSession { prompt.latestTokenUsage }
    }

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
    @PublishedApi
    internal suspend inline fun <reified T> requestLLMStructuredImpl(
        message: String,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = requestLLMStructuredImpl(message, serializer<T>(), examples, fixingParser)

    /**
     * Sends a structured request to the language model (LLM) and processes the response.
     *
     * @param message The message or prompt to be sent to the LLM.
     * @param serializer The serializer used to encode and decode the structured response.
     * @param examples Optional examples provided to guide the LLM in generating the structured response. Default is an empty list.
     * @param fixingParser An optional parser to fix or adjust the structure of the response. Default is null.
     * @return A [Result] containing a [StructuredResponse] with the structured result or an error if the operation fails.
     */
    @PublishedApi
    internal suspend fun <T> requestLLMStructuredImpl(
        message: String,
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMStructured(
                serializer,
                examples,
                fixingParser
            )
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) and streams the LLM response.
     * The message becomes part of the current prompt, and the LLM's response is streamed as it's generated.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param structureDefinition Optional structure to guide the LLM response.
     * @return A flow of [StreamFrame] objects from the LLM response.
     */
    override suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition?
    ): Flow<StreamFrame> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMStreaming(structureDefinition)
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) and gets multiple LLM responses with tool calls enabled.
     * The message becomes part of the current prompt, and multiple responses from the LLM are collected.
     *
     * @param message The content of the message to be sent to the LLM.
     * @return A list of LLM responses.
     */
    override suspend fun requestLLMMultiple(message: String): List<Message.Response> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMMultiple()
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) that will only call tools without generating text responses.
     * The message becomes part of the current prompt, and the LLM is instructed to only use tools.
     *
     * @param message The content of the message to be sent to the LLM.
     * @return The LLM response containing tool calls.
     */
    override suspend fun requestLLMOnlyCallingTools(message: String): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMOnlyCallingTools()
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
     * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param tool The tool descriptor that the LLM must use.
     * @return The LLM response containing the tool call.
     */
    override suspend fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor
    ): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMForceOneTool(tool)
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
     * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param tool The tool that the LLM must use.
     * @return The LLM response containing the tool call.
     */
    override suspend fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>
    ): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMForceOneTool(tool)
        }
    }

    /**
     * Executes a tool call and returns the result.
     *
     * @param toolCall The tool call to execute.
     * @return The result of the tool execution.
     */
    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        return environment.executeTool(toolCall)
    }

    /**
     * Executes multiple tool calls and returns their results.
     * These calls can optionally be executed in parallel.
     *
     * @param toolCalls The list of tool calls to execute.
     * @param parallelTools Specifies whether tools should be executed in parallel, defaults to false.
     * @return A list of results from the executed tool calls.
     */
    override suspend fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean
    ): List<ReceivedToolResult> {
        return if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }
    }

    /**
     * Adds a tool result to the prompt and requests an LLM response.
     *
     * @param toolResult The tool result to add to the prompt.
     * @return The LLM response.
     */
    override suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Response {
        return llm.writeSession {
            updatePrompt {
                tool {
                    result(toolResult)
                }
            }

            requestLLM()
        }
    }

    /**
     * Adds multiple tool results to the prompt and gets multiple LLM responses.
     *
     * @param results The list of tool results to add to the prompt.
     * @return A list of LLM responses.
     */
    override suspend fun sendMultipleToolResults(
        results: List<ReceivedToolResult>
    ): List<Message.Response> {
        return llm.writeSession {
            updatePrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMMultiple()
        }
    }

    /**
     * Calls a specific tool directly using the provided arguments.
     *
     * @param tool The tool to execute.
     * @param toolArgs The arguments to pass to the tool.
     * @param doUpdatePrompt Specifies whether to add tool call details to the prompt.
     * @return The result of the tool execution.
     */
    public override suspend fun <ToolArg, TResult> executeSingleTool(
        tool: Tool<ToolArg, TResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean
    ): SafeTool.Result<TResult> {
        return llm.writeSession {
            if (doUpdatePrompt) {
                updatePrompt {
                    user(
                        "Tool call: ${tool.name} was explicitly called with args: ${
                            tool.encodeArgs(toolArgs)
                        }"
                    )
                }
            }

            val toolResult = findTool(tool).execute(toolArgs)

            if (doUpdatePrompt) {
                updatePrompt {
                    user(
                        "Tool call: ${tool.name} was explicitly called and returned result: ${
                            toolResult.content
                        }"
                    )
                }
            }
            toolResult
        }
    }

    /**
     * Compresses the current LLM prompt (message history) into a summary, replacing messages with a TLDR.
     *
     * @param input The input value that will be returned unchanged after compression.
     * @param strategy Determines which messages to include in compression.
     * @param preserveMemory Specifies whether to retain message memory after compression.
     * @return The input value, unchanged.
     */
    override suspend fun compressHistory(
        strategy: HistoryCompressionStrategy,
        preserveMemory: Boolean
    ) {
        llm.writeSession {
            replaceHistoryWithTLDR(strategy, preserveMemory)
        }
    }

}
