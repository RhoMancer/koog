package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedHandler
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedHandler
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingHandler
import ai.koog.agents.core.feature.handler.agent.AgentContextHandler
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingHandler
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedHandler
import ai.koog.agents.core.feature.handler.agent.AgentEventHandler
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedHandler
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingHandler
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingHandler
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingHandler
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.llm.AfterLLMCallContext
import ai.koog.agents.core.feature.handler.llm.BeforeLLMCallContext
import ai.koog.agents.core.feature.handler.llm.LLMCallEventHandler
import ai.koog.agents.core.feature.handler.node.NodeAfterExecuteContext
import ai.koog.agents.core.feature.handler.node.NodeBeforeExecuteContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionEventHandler
import ai.koog.agents.core.feature.handler.tool.ToolExecutionEventHandler
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyEventHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallResultContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallValidationFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedHandler
import ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingHandler
import ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedHandler
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedHandler
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KType

/**
 * Pipeline for AI agent features that provides interception points for various agent lifecycle events.
 *
 * The pipeline allows features to:
 * - Be installed into agent contexts
 * - Intercept agent creation and environment transformation
 * - Intercept strategy execution before and after it happens
 * - Intercept node execution before and after it happens
 * - Intercept LLM (Language Learning Model) calls before and after they happen
 * - Intercept tool calls before and after they happen
 *
 * This pipeline serves as the central mechanism for extending and customizing agent behavior
 * through a flexible interception system. Features can be installed with custom configurations
 * and can hook into different stages of the agent's execution lifecycle.
 */
public class AIAgentPipeline {

    /**
     * Companion object for the AIAgentPipeline class.
     */
    private companion object {
        /**
         * Logger instance for the AIAgentPipeline class.
         */
        private val logger = KotlinLogging.logger { }
    }

    private val featurePrepareDispatcher = Dispatchers.Default.limitedParallelism(5)

    /**
     * Map of registered features and their configurations.
     * Keys are feature storage keys, values are feature configurations.
     */
    private val registeredFeatures: MutableMap<AIAgentStorageKey<*>, FeatureConfig> = mutableMapOf()

    /**
     * Map of agent handlers registered for different features.
     * Keys are feature storage keys, values are agent handlers.
     */
    private val agentEventHandlers: MutableMap<AIAgentStorageKey<*>, AgentEventHandler<*>> = mutableMapOf()

    /**
     * Map of strategy handlers registered for different features.
     * Keys are feature storage keys, values are strategy handlers.
     */
    private val strategyEventHandlers: MutableMap<AIAgentStorageKey<*>, StrategyEventHandler<*>> = mutableMapOf()

    /**
     * Map of agent context handlers registered for different features.
     * Keys are feature storage keys, values are agent context handlers.
     */
    private val agentContextHandler: MutableMap<AIAgentStorageKey<*>, AgentContextHandler<*>> = mutableMapOf()

    /**
     * Map of node execution handlers registered for different features.
     * Keys are feature storage keys, values are node execution handlers.
     */
    private val nodeExecutionEventHandlers: MutableMap<AIAgentStorageKey<*>, NodeExecutionEventHandler> = mutableMapOf()

    /**
     * Map of tool execution handlers registered for different features.
     * Keys are feature storage keys, values are tool execution handlers.
     */
    private val toolExecutionEventHandlers: MutableMap<AIAgentStorageKey<*>, ToolExecutionEventHandler> = mutableMapOf()

    /**
     * Map of LLM execution handlers registered for different features.
     * Keys are feature storage keys, values are LLM execution handlers.
     */
    private val llmCallEventHandlers: MutableMap<AIAgentStorageKey<*>, LLMCallEventHandler> = mutableMapOf()

    /**
     * Installs a feature into the pipeline with the provided configuration.
     *
     * This method initializes the feature with a custom configuration and registers it in the pipeline.
     * The feature's message processors are initialized during installation.
     *
     * @param Config The type of the feature configuration
     * @param Feature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        val config = feature.createInitialConfig().apply { configure() }
        feature.install(
            config = config,
            pipeline = this,
        )

        registeredFeatures[feature.key] = config
    }

    internal suspend fun prepareFeatures() {
        withContext(featurePrepareDispatcher) {
            registeredFeatures.values.forEach { featureConfig ->
                featureConfig.messageProcessors.map { processor ->
                    launch {
                        logger.debug { "Start preparing processor: ${processor::class.simpleName}" }
                        processor.initialize()
                        logger.debug { "Finished preparing processor: ${processor::class.simpleName}" }
                    }
                }
            }
        }
    }

    /**
     * Closes all feature stream providers.
     *
     * This internal method properly shuts down all message processors of registered features,
     * ensuring resources are released appropriately.
     */
    internal suspend fun closeFeaturesStreamProviders() {
        registeredFeatures.values.forEach { config -> config.messageProcessors.forEach { provider -> provider.close() } }
    }

    //region Trigger Agent Handlers

    /**
     * Notifies all registered handlers that an agent has started execution.
     *
     * @param runId The unique identifier for the agent run
     * @param agent The agent instance for which the execution has started
     * @param strategy The strategy being executed by the agent
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onAgentStarting(
        runId: String,
        agent: AIAgent<*, *>,
        strategy: AIAgentStrategy<*, *>,
        context: AIAgentContextBase
    ) {
        agentEventHandlers.values.forEach { handler ->
            val eventContext =
                AgentStartingContext(
                    agent = agent,
                    runId = runId,
                    strategy = strategy,
                    feature = handler.feature,
                    context = context
                )
            handler.handleAgentStartingUnsafe(eventContext)
        }
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param agentId The unique identifier of the agent that finished execution
     * @param runId The unique identifier of the agent run
     * @param result The result produced by the agent, or null if no result was produced
     */
    public suspend fun onAgentCompleted(
        agentId: String,
        runId: String,
        result: Any?,
        resultType: KType,
    ) {
        val eventContext =
            AgentCompletedContext(agentId = agentId, runId = runId, result = result, resultType = resultType)
        agentEventHandlers.values.forEach { handler -> handler.agentCompletedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param agentId The unique identifier of the agent that encountered the error
     * @param runId The unique identifier of the agent run
     * @param throwable The exception that was thrown during agent execution
     */
    public suspend fun onAgentExecutionFailed(
        agentId: String,
        runId: String,
        throwable: Throwable
    ) {
        val eventContext = AgentExecutionFailedContext(agentId = agentId, runId = runId, throwable = throwable)
        agentEventHandlers.values.forEach { handler -> handler.agentExecutionFailedHandler.handle(eventContext) }
    }

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param agentId The unique identifier of the agent that will be closed.
     */
    public suspend fun onAgentClosing(
        agentId: String
    ) {
        val eventContext = AgentClosingContext(agentId = agentId)
        agentEventHandlers.values.forEach { handler -> handler.agentClosingHandler.handle(eventContext) }
    }

    /**
     * Transforms the agent environment by applying all registered environment transformers.
     *
     * This method allows features to modify or enhance the agent's environment before it starts execution.
     * Each registered handler can apply its own transformations to the environment in sequence.
     *
     * @param strategy The strategy associated with the agent
     * @param agent The agent instance for which the environment is being transformed
     * @param baseEnvironment The initial environment to be transformed
     * @return The transformed environment after all handlers have been applied
     */
    public fun onAgentEnvironmentTransforming(
        strategy: AIAgentStrategy<*, *>,
        agent: AIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        return agentEventHandlers.values.fold(baseEnvironment) { environment, handler ->
            val eventContext =
                AgentEnvironmentTransformingContext(strategy = strategy, agent = agent, feature = handler.feature)
            handler.transformEnvironmentUnsafe(eventContext, environment)
        }
    }

    /**
     * Retrieves all features associated with the given agent context.
     *
     * This method collects features from all registered agent context handlers
     * that are applicable to the provided context.
     *
     * @param context The agent context for which to retrieve features
     * @return A map of feature keys to their corresponding feature instances
     */
    public fun getAgentFeatures(context: AIAgentContextBase): Map<AIAgentStorageKey<*>, Any> {
        return agentContextHandler.mapValues { (_, featureProvider) ->
            featureProvider.handle(context)
        }
    }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param strategy The strategy that has started execution
     * @param context The context of the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyStarting(strategy: AIAgentStrategy<*, *>, context: AIAgentContextBase) {
        strategyEventHandlers.values.forEach { handler ->
            val eventContext =
                StrategyStartingContext(runId = context.runId, strategy = strategy, feature = handler.feature)
            handler.handleStrategyStartingUnsafe(eventContext)
        }
    }

    /**
     * Notifies all registered strategy handlers that a strategy has finished execution.
     *
     * @param strategy The strategy that has finished execution
     * @param context The context of the strategy execution
     * @param result The result produced by the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyCompleted(
        strategy: AIAgentStrategy<*, *>,
        context: AIAgentContextBase,
        result: Any?,
        resultType: KType,
    ) {
        strategyEventHandlers.values.forEach { handler ->
            val eventContext = StrategyCompletedContext(
                runId = context.runId,
                strategy = strategy,
                feature = handler.feature,
                result = result,
                resultType = resultType
            )
            handler.handleStrategyCompletedUnsafe(eventContext)
        }
    }

    //endregion Trigger Strategy Handlers

    //region Trigger Node Handlers

    /**
     * Notifies all registered node handlers before a node is executed.
     *
     * @param node The node that is about to be executed
     * @param context The agent context in which the node is being executed
     * @param input The input data for the node execution
     */
    public suspend fun onNodeExecutionStarting(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?,
        inputType: KType
    ) {
        val eventContext = NodeExecutionStartingContext(node, context, input, inputType)
        nodeExecutionEventHandlers.values.forEach { handler -> handler.nodeExecutionStartingHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered node handlers after a node has been executed.
     *
     * @param node The node that was executed
     * @param context The agent context in which the node was executed
     * @param input The input data that was provided to the node
     * @param output The output data produced by the node execution
     */
    public suspend fun onNodeExecutionCompleted(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?,
        output: Any?,
        inputType: KType,
        outputType: KType,
    ) {
        val eventContext = NodeExecutionCompletedContext(node, context, input, output, inputType, outputType)
        nodeExecutionEventHandlers.values.forEach { handler -> handler.nodeExecutionCompletedHandler.handle(eventContext) }
    }

    /**
     * Handles errors occurring during the execution of a node by invoking all registered node execution error handlers.
     *
     * @param node The instance of the node where the error occurred.
     * @param context The context associated with the AI agent executing the node.
     * @param throwable The exception or error that occurred during node execution.
     */
    public suspend fun onNodeExecutionFailed(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        throwable: Throwable
    ) {
        val eventContext = NodeExecutionFailedContext(node, context, throwable)
        nodeExecutionEventHandlers.values.forEach { handler -> handler.nodeExecutionFailedHandler.handle(eventContext) }
    }

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param prompt The prompt that will be sent to the language model
     * @param tools The list of tool descriptors available for the LLM call
     * @param model The language model instance that will process the request
     */
    public suspend fun onLLMCallStarting(runId: String, prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>) {
        val eventContext = LLMCallStartingContext(runId, prompt, model, tools)
        llmCallEventHandlers.values.forEach { handler -> handler.llmCallStartingHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered LLM handlers after a language model call has completed.
     *
     * @param runId Identifier for the current run.
     * @param prompt The prompt that was sent to the language model
     * @param tools The list of tool descriptors that were available for the LLM call
     * @param model The language model instance that processed the request
     * @param responses The response messages received from the language model
     */
    public suspend fun onLLMCallCompleted(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult? = null,
    ) {
        val eventContext = LLMCallCompletedContext(runId, prompt, model, tools, responses, moderationResponse)
        llmCallEventHandlers.values.forEach { handler -> handler.llmCallCompletedHandler.handle(eventContext) }
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    /**
     * Notifies all registered tool handlers when a tool is called.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that is being called
     * @param toolArgs The arguments provided to the tool
     */
    public suspend fun onToolExecutionStarting(runId: String, toolCallId: String?, tool: Tool<*, *>, toolArgs: ToolArgs) {
        val eventContext = ToolExecutionStartingContext(runId, toolCallId, tool, toolArgs)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolExecutionStartingHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool for which validation failed
     * @param toolArgs The arguments that failed validation
     * @param error The validation error message
     */
    public suspend fun onToolValidationFailed(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        error: String
    ) {
        val eventContext = ToolValidationFailedContext(runId, toolCallId, tool, toolArgs, error)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolValidationFailedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that failed
     * @param toolArgs The arguments provided to the tool
     * @param throwable The exception that caused the failure
     */
    public suspend fun onToolExecutionFailed(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        throwable: Throwable
    ) {
        val eventContext = ToolExecutionFailedContext(runId, toolCallId, tool, toolArgs, throwable)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolExecutionFailedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that was called
     * @param toolArgs The arguments that were provided to the tool
     * @param result The result produced by the tool, or null if no result was produced
     */
    public suspend fun onToolExecutionCompleted(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        result: ToolResult?
    ) {
        val eventContext = ToolExecutionCompletedContext(runId, toolCallId, tool, toolArgs, result)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolExecutionCompletedHandler.handle(eventContext) }
    }

    //endregion Trigger Tool Call Handlers

    //region Interceptors

    /**
     * Sets a feature handler for agent context events.
     *
     * @param feature The feature for which to register the handler
     * @param handler The handler responsible for processing the feature within the agent context
     *
     * Example:
     * ```
     * pipeline.interceptContextAgentFeature(MyFeature) { agentContext ->
     *   // Inspect agent context
     * }
     * ```
     */
    public fun <TFeature : Any> interceptContextAgentFeature(
        feature: AIAgentFeature<*, TFeature>,
        handler: AgentContextHandler<TFeature>,
    ) {
        agentContextHandler[feature.key] = handler
    }

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This method registers a transformer function that will be called when an agent environment
     * is being created, allowing the feature to customize the environment based on the agent context.
     *
     * @param interceptContext The context of the feature being intercepted, providing access to the feature key and implementation
     * @param transform A function that transforms the environment, with access to the agent creation context
     *
     * Example:
     * ```
     * pipeline.interceptEnvironmentCreated(InterceptContext) { environment ->
     *     // Modify the environment based on agent context
     *     environment.copy(
     *         variables = environment.variables + mapOf("customVar" to "value")
     *     )
     * }
     * ```
     */
    public fun <TFeature : Any> interceptEnvironmentCreated(
        interceptContext: InterceptContext<TFeature>,
        transform: AgentEnvironmentTransformingContext<TFeature>.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentEventHandler<TFeature> =
            agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) } as? AgentEventHandler<TFeature>
                ?: return

        existingHandler.agentEnvironmentTransformingHandler = AgentEnvironmentTransformingHandler handler@{ eventContext, env ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler env
            }
            eventContext.transform(env)
        }
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * @param handle The handler that processes agent creation events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeAgentStarted(InterceptContext) {
     *     readStages { stages ->
     *         // Inspect agent stages
     *     }
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (AgentStartingContext<TFeature>) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentEventHandler<TFeature> =
            agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) } as? AgentEventHandler<TFeature>
                ?: return

        existingHandler.agentStartingHandler = AgentStartingHandler handler@{ eventContext: AgentStartingContext<TFeature> ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            handle(eventContext)
        }
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * @param handle A suspend function providing custom logic to execute when the agent completes,
     *
     * Example:
     * ```
     * pipeline.interceptAgentFinished(InterceptContext { eventContext ->
     *     // Handle the completion result here, using the strategy name and the result.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentCompletedContext) -> Unit
    ) {
        val existingHandler = agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) }

        existingHandler.agentCompletedHandler = AgentCompletedHandler handler@{ eventContext: AgentCompletedContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * @param handle A suspend function providing custom logic to execute when an error occurs,
     *
     * Example:
     * ```
     * pipeline.interceptAgentRunError(InterceptContext) { eventContext ->
     *     // Handle the error here, using the strategy name and the exception that occurred.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentExecutionFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        val existingHandler = agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) }

        existingHandler.agentExecutionFailedHandler = AgentExecutionFailedHandler handler@{ eventContext: AgentExecutionFailedContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * @param TFeature The type of feature this handler is associated with.
     * @param interceptContext The context containing details about the feature and its implementation.
     * @param handle A suspendable function that is executed during the agent's pre-close phase.
     *                The function receives the feature instance and the event context as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptAgentBeforeClosed(InterceptContext) { eventContext ->
     *     // Handle agent run before close event.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentClosing(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentClosingContext) -> Unit
    ) {
        val existingHandler = agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) }

        existingHandler.agentClosingHandler = AgentClosingHandler handler@{ eventContext: AgentClosingContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param handle A suspend function that processes the start of a strategy, accepting the strategy context
     *
     * Example:
     * ```
     * pipeline.interceptStrategyStarted(InterceptContext) {
     *     val strategyName = strategy.name
     *     logger.info("Strategy $strategyName has started execution")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (StrategyStartingContext<TFeature>) -> Unit
    ) {
        val existingHandler = strategyEventHandlers
            .getOrPut(interceptContext.feature.key) { StrategyEventHandler(interceptContext.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (existingHandler as? StrategyEventHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${interceptContext.featureImpl::class}>, but get a handler of type <${interceptContext.feature.key}> instead. " +
                    "Skipping adding strategy started interceptor for feature."
            }
            return
        }

        existingHandler.strategyStartingHandler = StrategyStartingHandler handler@{ eventContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            handle(eventContext)
        }
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * @param handle A suspend function that processes the completion of a strategy, accepting the strategy name
     *               and its result as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptStrategyFinished(InterceptContext) { result ->
     *     // Handle the completion of the strategy here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (StrategyCompletedContext<TFeature>) -> Unit
    ) {
        val existingHandler = strategyEventHandlers.getOrPut(interceptContext.feature.key) { StrategyEventHandler(interceptContext.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (existingHandler as? StrategyEventHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${interceptContext.featureImpl::class}>, " +
                    "but get a handler of type <${interceptContext.feature.key}> instead. " +
                    "Skipping adding strategy finished interceptor for feature."
            }
            return
        }

        existingHandler.strategyCompletedHandler = StrategyCompletedHandler handler@{ eventContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            handle(eventContext)
        }
    }

    /**
     * Intercepts node execution before it starts.
     *
     * @param handle The handler that processes before-node events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeNode(InterceptContext) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} is about to execute with input: ${eventContext.input}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptNodeExecutionStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: NodeExecutionStartingContext) -> Unit
    ) {
        val existingHandler = nodeExecutionEventHandlers.getOrPut(interceptContext.feature.key) { NodeExecutionEventHandler() }

        existingHandler.nodeExecutionStartingHandler = NodeExecutionStartingHandler handler@{ eventContext: NodeExecutionStartingContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts node execution after it completes.
     *
     * @param handle The handler that processes after-node events
     *
     * Example:
     * ```
     * pipeline.interceptAfterNode(InterceptContext) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} executed with input: ${eventContext.input} and produced output: ${eventContext.output}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptNodeExecutionCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: NodeExecutionCompletedContext) -> Unit
    ) {
        val existingHandler = nodeExecutionEventHandlers.getOrPut(interceptContext.feature.key) { NodeExecutionEventHandler() }

        existingHandler.nodeExecutionCompletedHandler = NodeExecutionCompletedHandler handler@{ eventContext: NodeExecutionCompletedContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and handles node execution errors for a given feature.
     *
     * @param interceptContext The context containing the feature and its implementation required for interception.
     * @param handle A suspend function that processes the node execution error within the scope of the provided feature.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionError(InterceptContext) { eventContext ->
     *     logger.error("Node ${eventContext.node.name} execution failed with error: ${eventContext.throwable}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptNodeExecutionFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: NodeExecutionFailedContext) -> Unit
    ) {
        val existingHandler = nodeExecutionEventHandlers.getOrPut(interceptContext.feature.key) { NodeExecutionEventHandler() }

        existingHandler.nodeExecutionFailedHandler =
            NodeExecutionFailedHandler handler@{ eventContext: NodeExecutionFailedContext ->
                if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                    return@handler
                }
                with(interceptContext.featureImpl) { handle(eventContext) }
            }
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * @param handle The handler that processes before-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeLLMCall(InterceptContext) { eventContext ->
     *     logger.info("About to make LLM call with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptLLMCallStaring(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMCallStartingContext) -> Unit
    ) {
        val existingHandler = llmCallEventHandlers.getOrPut(interceptContext.feature.key) { LLMCallEventHandler() }

        existingHandler.llmCallStartingHandler = LLMCallStartingHandler handler@{ eventContext: LLMCallStartingContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * @param handle The handler that processes after-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptAfterLLMCall(InterceptContext) { eventContext ->
     *     // Process or analyze the response
     * }
     * ```
     */
    public fun <TFeature : Any> interceptLLMCallCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMCallCompletedContext) -> Unit
    ) {
        val existingHandler = llmCallEventHandlers.getOrPut(interceptContext.feature.key) { LLMCallEventHandler() }

        existingHandler.llmCallCompletedHandler = LLMCallCompletedHandler handler@{ eventContext: LLMCallCompletedContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and handles tool calls for the specified feature and its implementation.
     * Updates the tool call handler for the given feature key with a custom handler.
     *
     * @param handle A suspend lambda function that processes tool calls, taking the tool, and its arguments as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptToolCall(InterceptContext) { eventContext ->
     *    // Process or log the tool call
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolExecutionStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolExecutionStartingContext) -> Unit
    ) {
        val existingHandler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }

        existingHandler.toolExecutionStartingHandler = ToolExecutionStartingHandler handler@{ eventContext: ToolExecutionStartingContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * @param handle A suspendable lambda function that will be invoked when a tool validation error occurs.
     *        The lambda provides the tool's stage, tool instance, tool arguments, and the value that caused the validation error.
     *
     * Example:
     * ```
     * pipeline.interceptToolValidationError(InterceptContext) { eventContext ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptTooValidationFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolValidationFailedContext) -> Unit
    ) {
        val existingHandler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }

        existingHandler.toolValidationFailedHandler = ToolValidationFailedHandler handler@{ eventContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * @param handle A suspend function that is invoked when a tool call fails. It provides the stage,
     *               the tool, the tool arguments, and the throwable that caused the failure.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallFailure(InterceptContext) { eventContext ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolExecutionFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolExecutionFailedContext) -> Unit
    ) {
        val existingHandler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }

        existingHandler.toolExecutionFailedHandler = ToolExecutionFailedHandler handler@{ eventContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     *
     * @param handle A suspending function that defines the behavior to execute when a tool call result is intercepted.
     * The function takes as parameters the stage of the tool call, the tool being called, its arguments,
     * and the result of the tool call if available.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallResult(InterceptContext) { eventContext ->
     *     // Handle the tool call result here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolExecutionCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolExecutionCompletedContext) -> Unit
    ) {
        val existingHandler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }

        existingHandler.toolExecutionCompletedHandler = ToolExecutionCompletedHandler handler@{ eventContext ->
            if (!registeredFeatures[interceptContext.feature.key].isAccepted(eventContext)) {
                return@handler
            }
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    //endregion Interceptors

    //region Interceptors (Deprecated)

    /**
     * Intercepts the execution after a node's processing.
     *
     * This method is deprecated and scheduled for removal.
     * Use interceptNodeExecutionStarting with updated parameters instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptNodeExecutionStarting(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptNodeExecutionStarting(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptBeforeNode(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: NodeBeforeExecuteContext) -> Unit
    ) {
        interceptNodeExecutionStarting(interceptContext, handle)
    }

    /**
     * Intercepts node execution after it completes.
     *
     * This method has been deprecated and replaced by `interceptNodeExecutionCompleted(InterceptContext, handle)`
     * for improved contextual handling and extensibility.
     * Use `interceptNodeExecutionCompleted` instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptNodeExecutionCompleted(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptNodeExecutionCompleted(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptAfterNode(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: NodeAfterExecuteContext) -> Unit
    ) {
        interceptNodeExecutionCompleted(interceptContext, handle)
    }

    /**
     * Intercepts the result of a tool call and allows handling it within a specified context.
     *
     * This method is deprecated and planned for removal.
     * Use `interceptToolExecutionCompleted` instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptToolExecutionCompleted(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptToolExecutionCompleted(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptToolCallResult(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallResultContext) -> Unit
    ) {
        interceptToolExecutionCompleted(interceptContext, handle)
    }

    /**
     * Intercepts LLM calls before they are made.
     *
     * Deprecated: use interceptLLMCallStaring instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptLLMCallStaring(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptLLMCallStaring(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptBeforeLLMCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: BeforeLLMCallContext) -> Unit
    ) {
        interceptLLMCallStaring(interceptContext, handle)
    }

    /**
     * Intercepts after an LLM call is completed.
     *
     * Deprecated: use interceptLLMCallCompleted instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptLLMCallCompleted(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptLLMCallCompleted(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptAfterLLMCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AfterLLMCallContext) -> Unit
    ) {
        interceptLLMCallCompleted(interceptContext, handle)
    }

    /**
     * Intercepts tool call starting.
     *
     * Deprecated: use interceptToolExecutionStarting instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptToolExecutionStarting(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptToolExecutionStarting(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptToolCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallStartingContext) -> Unit
    ) {
        interceptToolExecutionStarting(interceptContext, handle)
    }

    /**
     * Intercepts tool call validation failed events.
     *
     * Deprecated: use interceptTooValidationFailed instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptTooValidationFailed(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptTooValidationFailed(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptToolCallValidationFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallValidationFailedContext) -> Unit
    ) {
        interceptTooValidationFailed(interceptContext, handle)
    }

    /**
     * Intercepts tool call failures.
     *
     * Deprecated: use interceptToolExecutionFailed instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptToolExecutionFailed(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptToolExecutionFailed(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptToolCallFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallFailedContext) -> Unit
    ) {
        interceptToolExecutionFailed(interceptContext, handle)
    }

    /**
     * Intercepts before agent started event.
     *
     * Deprecated: use interceptAgentStarting instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptAgentStarting(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptAgentStarting(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptBeforeAgentStarted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (AgentStartingContext<TFeature>) -> Unit
    ) {
        interceptAgentStarting(interceptContext, handle)
    }

    /**
     * Intercepts strategy started event.
     *
     * Deprecated: use interceptStrategyStarting instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptStrategyStarting(InterceptContext, handle) instead",
        replaceWith = ReplaceWith(
            expression = "interceptStrategyStarting(InterceptContext(feature, featureImpl), handle)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptStrategyStarted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (StrategyStartingContext<TFeature>) -> Unit
    ) {
        interceptStrategyStarting(interceptContext, handle)
    }

    /**
     * Deprecated: use interceptContextAgentFeature instead.
     */
    @Deprecated(
        message = "Scheduled for removal. Use interceptContextAgentFeature(feature, handler) instead",
        replaceWith = ReplaceWith(
            expression = "interceptContextAgentFeature(feature, handler)",
            imports = ["ai.koog.agents.core.feature.InterceptContext"]
        ),
    )
    public fun <TFeature : Any> interceptContextStageFeature(
        feature: AIAgentFeature<*, TFeature>,
        handler: AgentContextHandler<TFeature>
    ) {
        interceptContextAgentFeature(feature, handler)
    }

    //endregion Interceptors (Deprecated)

    //region Private Methods

    private fun FeatureConfig?.isAccepted(eventContext: AgentLifecycleEventContext): Boolean {
        return this?.eventFilter?.invoke(eventContext) == true
    }

    //endregion Private Methods
}
