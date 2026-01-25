package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.config.FeatureSystemVariables
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.feature.handler.AgentLifecycleHandlersCollector
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingHandler
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingHandler
import ai.koog.agents.core.feature.handler.agent.AgentEventHandler
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingHandler
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallEventHandler
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyEventHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingEventHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallEventHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailureHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallResultHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationErrorHandler
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.agents.core.system.getVMOptionOrNull
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.safeCast

/**
 * Default implementation of [AIAgentPipelineAPI]
 */
public class AIAgentPipelineImpl(
    override val config: AIAgentConfig,
    public override val clock: Clock
) : AIAgentPipelineAPI {

    // Notes on suppressed warnings used in this class:
    // - Some members are annotated with @Suppress to satisfy explicit API requirements
    //   (e.g., explicit public visibility) or to keep implementation details concise.
    //   These suppressions are intentional and safe.

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Represents a registered feature in the system.
     *
     * This class encapsulates the implementation of a feature and its associated configuration.
     * It is used to maintain feature details after registration.
     *
     * @property featureImpl The implementation instance of the feature.
     * @property featureConfig The configuration settings associated with the feature.
     */
    @Suppress("RedundantVisibilityModifier") // have to put public here, explicitApi requires it
    private class RegisteredFeature(
        public val featureImpl: Any,
        public val featureConfig: FeatureConfig
    )

    private val registeredFeatures: MutableMap<AIAgentStorageKey<*>, RegisteredFeature> = mutableMapOf()

    @OptIn(ExperimentalAgentsApi::class)
    private val systemFeatures: Set<AIAgentStorageKey<*>> = setOf(
        Debugger.key
    )

    // Map:
    //   feature -> agent event type -> handler
    private val agentLifecycleHandlersCollector = AgentLifecycleHandlersCollector()

    private val agentEventHandlers: MutableMap<AIAgentStorageKey<*>, AgentEventHandler> = mutableMapOf()

    private val strategyEventHandlers: MutableMap<AIAgentStorageKey<*>, StrategyEventHandler> = mutableMapOf()

    private val toolCallEventHandlers: MutableMap<AIAgentStorageKey<*>, ToolCallEventHandler> = mutableMapOf()

    private val llmCallEventHandlers: MutableMap<AIAgentStorageKey<*>, LLMCallEventHandler> = mutableMapOf()

    private val llmStreamingEventHandlers: MutableMap<AIAgentStorageKey<*>, LLMStreamingEventHandler> = mutableMapOf()

    public override fun <TFeature : Any> feature(
        featureClass: KClass<TFeature>,
        feature: AIAgentFeature<*, TFeature>
    ): TFeature? {
        val featureImpl = registeredFeatures[feature.key]?.featureImpl ?: return null

        return featureClass.safeCast(featureImpl)
            ?: throw IllegalArgumentException(
                "Feature ${feature.key} is found, but it is not of the expected type.\n" +
                    "Expected type: ${featureClass.simpleName}\n" +
                    "Actual type: ${featureImpl::class.simpleName}"
            )
    }

    public override fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        featureKey: AIAgentStorageKey<TFeatureImpl>,
        featureConfig: TConfig,
        featureImpl: TFeatureImpl,
    ) {
        registeredFeatures[featureKey] = RegisteredFeature(featureImpl, featureConfig)
    }

    public override suspend fun uninstall(
        featureKey: AIAgentStorageKey<*>
    ) {
        registeredFeatures
            .filter { (key, _) -> key == featureKey }
            .forEach { (key, registeredFeature) ->
                registeredFeature.featureConfig.messageProcessors.forEach { provider -> provider.close() }
                registeredFeatures.remove(key)
            }
    }

    //region Internal Handlers

    internal suspend fun prepareFeature(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { processor ->
            logger.debug { "Start preparing processor: ${processor::class.simpleName}" }
            processor.initialize()
            logger.debug { "Finished preparing processor: ${processor::class.simpleName}" }
        }
    }

    @InternalAgentsApi
    override suspend fun prepareFeatures() {
        // Install system features (if exist)
        installFeaturesFromSystemConfig()

        // Prepare features
        registeredFeatures.values.forEach { featureConfig ->
            prepareFeature(featureConfig.featureConfig)
        }
    }

    internal suspend fun closeFeatureMessageProcessors(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { provider ->
            logger.trace { "Start closing feature processor: ${featureConfig::class.simpleName}" }
            provider.close()
            logger.trace { "Finished closing feature processor: ${featureConfig::class.simpleName}" }
        }
    }

    @InternalAgentsApi
    override suspend fun closeAllFeaturesMessageProcessors() {
        registeredFeatures.values.forEach { registerFeature ->
            closeFeatureMessageProcessors(registerFeature.featureConfig)
        }
    }

    //endregion Internal Handlers

    //region Invoke Agent Handlers

    // TODO: SD -- rename all to invokeOnAgentStarting
    @OptIn(InternalAgentsApi::class)
    internal override suspend fun <TInput, TOutput> invokeOnAgentStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    ) {
        val eventContext = AgentStartingContext(eventId, executionInfo, agent, runId, context)

        val registeredHandlers = agentLifecycleHandlersCollector.getHandlersForEvent<AgentStartingContext>(
            eventType = AgentLifecycleEventType.AgentStarting,
        )

        registeredHandlers.forEach { (featureKey, handlers) ->
            logger.trace { "Execute registered 'OnAgentStarting' handlers for feature: '${featureKey.name}'" }
            handlers.forEach { handler -> handler.handle(eventContext) }
        }
    }

    public override suspend fun onAgentCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        result: Any?,
        context: AIAgentContext
    ) {
        val eventContext = AgentCompletedContext(eventId, executionInfo, agentId, runId, result, context)
        agentEventHandlers.values.forEach { handler -> handler.agentCompletedHandler.handle(eventContext) }
    }

    public override suspend fun onAgentExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        throwable: Throwable,
        context: AIAgentContext
    ) {
        val eventContext = AgentExecutionFailedContext(eventId, executionInfo, agentId, runId, throwable, context)
        agentEventHandlers.values.forEach { handler -> handler.agentExecutionFailedHandler.handle(eventContext) }
    }

    public override suspend fun onAgentClosing(eventId: String, executionInfo: AgentExecutionInfo, agentId: String) {
        val eventContext = AgentClosingContext(eventId, executionInfo, agentId, config)
        agentEventHandlers.values.forEach { handler -> handler.agentClosingHandler.handle(eventContext) }
    }

    public override suspend fun onAgentEnvironmentTransforming(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        val eventContext = AgentEnvironmentTransformingContext(eventId, executionInfo, agent, config)
        return agentEventHandlers.values.fold(baseEnvironment) { environment, handler ->
            handler.transformEnvironment(eventContext, environment)
        }
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onStrategyStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext
    ) {
        val eventContext = StrategyStartingContext(eventId, executionInfo, strategy, context)
        strategyEventHandlers.values.forEach { handler -> handler.handleStrategyStarting(eventContext) }
    }

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onStrategyCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType
    ) {
        val eventContext =
            StrategyCompletedContext(eventId, executionInfo, strategy, context, result, resultType)
        strategyEventHandlers.values.forEach { handler -> handler.handleStrategyCompleted(eventContext) }
    }

    //endregion Invoke Strategy Handlers

    //region Invoke LLM Call Handlers

    public override suspend fun onLLMCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        val eventContext = LLMCallStartingContext(eventId, executionInfo, runId, prompt, model, tools, context)
        llmCallEventHandlers.values.forEach { handler -> handler.invokeOnLLMCallStartingHandlers(eventContext) }
    }

    public override suspend fun onLLMCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult?,
        context: AIAgentContext
    ) {
        val eventContext =
            LLMCallCompletedContext(
                eventId,
                executionInfo,
                runId,
                prompt,
                model,
                tools,
                responses,
                moderationResponse,
                context
            )
        llmCallEventHandlers.values.forEach { handler -> handler.invokeOnLLMCallCompletedHandlers(eventContext) }
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    public override suspend fun onToolCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        context: AIAgentContext
    ) {
        val eventContext = ToolCallStartingContext(
            eventId,
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolDescription,
            toolArgs,
            context
        )
        toolCallEventHandlers.values.forEach { handler -> handler.toolCallHandler.handle(eventContext) }
    }

    public override suspend fun onToolValidationFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        message: String,
        error: AIAgentError,
        context: AIAgentContext
    ) {
        val eventContext = ToolValidationFailedContext(
            eventId,
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolDescription,
            toolArgs,
            message,
            error,
            context
        )
        toolCallEventHandlers.values.forEach { handler -> handler.toolValidationErrorHandler.handle(eventContext) }
    }

    public override suspend fun onToolCallFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        message: String,
        error: AIAgentError?,
        context: AIAgentContext
    ) {
        val eventContext = ToolCallFailedContext(
            eventId,
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolDescription,
            toolArgs,
            message,
            error,
            context
        )
        toolCallEventHandlers.values.forEach { handler -> handler.toolCallFailureHandler.handle(eventContext) }
    }

    public override suspend fun onToolCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        toolResult: JsonElement?,
        context: AIAgentContext
    ) {
        val eventContext = ToolCallCompletedContext(
            eventId,
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolDescription,
            toolArgs,
            toolResult,
            context
        )
        toolCallEventHandlers.values.forEach { handler -> handler.toolCallResultHandler.handle(eventContext) }
    }

    //endregion Invoke Tool Call Handlers

    //region Invoke LLM Streaming

    public override suspend fun onLLMStreamingStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        val eventContext =
            LLMStreamingStartingContext(eventId, executionInfo, runId, prompt, model, tools, context)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingStartingHandler.handle(eventContext) }
    }

    public override suspend fun onLLMStreamingFrameReceived(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        streamFrame: StreamFrame,
        context: AIAgentContext
    ) {
        val eventContext =
            LLMStreamingFrameReceivedContext(eventId, executionInfo, runId, prompt, model, streamFrame, context)
        llmStreamingEventHandlers.values.forEach { handler ->
            handler.llmStreamingFrameReceivedHandler.handle(
                eventContext
            )
        }
    }

    public override suspend fun onLLMStreamingFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        throwable: Throwable,
        context: AIAgentContext
    ) {
        val eventContext =
            LLMStreamingFailedContext(eventId, executionInfo, runId, prompt, model, throwable, context)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingFailedHandler.handle(eventContext) }
    }

    public override suspend fun onLLMStreamingCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        val eventContext =
            LLMStreamingCompletedContext(eventId, executionInfo, runId, prompt, model, tools, context)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingCompletedHandler.handle(eventContext) }
    }

    //endregion Invoke LLM Streaming

    //region Interceptors

    @OptIn(InternalAgentsApi::class)
    public override fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        transform: suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        val handler: AgentEventHandler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }

        handler.agentEnvironmentTransformingHandler = AgentEnvironmentTransformingHandler(
            function = createConditionalHandler(feature, transform)
        )
    }

    // TODO: SD -- rename all to
    //  onAgentStarting(...)
    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        agentLifecycleHandlersCollector.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        agentLifecycleHandlersCollector.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        agentLifecycleHandlersCollector.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentExecutionFailed,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }

        handler.agentClosingHandler = AgentClosingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(feature.key) { StrategyEventHandler() }

        handler.strategyStartingHandler = StrategyStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(feature.key) { StrategyEventHandler() }

        handler.strategyCompletedHandler = StrategyCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(feature.key) { LLMCallEventHandler() }
        handler.addLLMCallStartingHandler(handler = createConditionalHandler(feature, handle))
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(feature.key) { LLMCallEventHandler() }
        handler.addLLMCallCompletedHandler(handler = createConditionalHandler(feature, handle))
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingStartingHandler = LLMStreamingStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingFrameReceivedHandler = LLMStreamingFrameReceivedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingFailedHandler = LLMStreamingFailedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingCompletedHandler = LLMStreamingCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolCallHandler = ToolCallHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolValidationErrorHandler = ToolValidationErrorHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolCallFailureHandler = ToolCallFailureHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolCallResultHandler = ToolCallResultHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    //endregion Interceptors

    //region Deprecated Interceptors

    @Deprecated(
        message = "Please use interceptAgentStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentStarting(feature, handle)",
            imports = arrayOf("ai.koog.agents.core.feature.handler.agent.AgentStartingContext")
        )
    )
    public override fun interceptBeforeAgentStarted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        interceptAgentStarting(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptAgentCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.agent.AgentCompletedContext"
            )
        )
    )
    public override fun interceptAgentFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        interceptAgentCompleted(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptAgentExecutionFailed instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentExecutionFailed(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext"
            )
        )
    )
    public override fun interceptAgentRunError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentExecutionFailedContext) -> Unit
    ) {
        interceptAgentExecutionFailed(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptAgentClosing instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentClosing(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.agent.AgentClosingContext"
            )
        )
    )
    public override fun interceptAgentBeforeClose(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentClosingContext) -> Unit
    ) {
        interceptAgentClosing(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptStrategyStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptStrategyStarting(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext"
            )
        )
    )
    public override fun interceptStrategyStart(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        interceptStrategyStarting(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptStrategyCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptStrategyCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext"
            )
        )
    )
    public override fun interceptStrategyFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        interceptStrategyCompleted(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptLLMCallStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptLLMCallStarting(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext"
            )
        )
    )
    public override fun interceptBeforeLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        interceptLLMCallStarting(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptLLMCallCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptLLMCallCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext"
            )
        )
    )
    public override fun interceptAfterLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        interceptLLMCallCompleted(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptToolCallStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolCallStarting(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext"
            )
        )
    )
    public override fun interceptToolCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        interceptToolCallStarting(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptToolCallCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolCallCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext"
            )
        )
    )
    public override fun interceptToolCallResult(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        interceptToolCallCompleted(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptToolCallFailed instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolCallFailed(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext"
            )
        )
    )
    public override fun interceptToolCallFailure(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        interceptToolCallFailed(feature, handle)
    }

    @Deprecated(
        message = "Please use interceptToolValidationFailed instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolValidationFailed(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext"
            )
        )
    )
    public override fun interceptToolValidationError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        interceptToolValidationFailed(feature, handle)
    }

    //endregion Deprecated Interceptors

    //region Private Methods

    private fun installFeaturesFromSystemConfig() {
        val featuresFromSystemConfig = readFeatureKeysFromSystemVariables()
        val filteredSystemFeaturesToInstall = filterSystemFeaturesToInstall(featuresFromSystemConfig)

        filteredSystemFeaturesToInstall.forEach { systemFeatureKey ->
            installSystemFeature(systemFeatureKey)
        }
    }

    private fun readFeatureKeysFromSystemVariables(): List<String> {
        val collectedFeaturesKeys = mutableListOf<String>()

        @OptIn(ExperimentalAgentsApi::class)
        getEnvironmentVariableOrNull(FeatureSystemVariables.KOOG_FEATURES_ENV_VAR_NAME)
            ?.let { featuresString ->
                featuresString.split(",").forEach { featureString ->
                    collectedFeaturesKeys.add(featureString.trim())
                }
            }

        @OptIn(ExperimentalAgentsApi::class)
        getVMOptionOrNull(FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME)
            ?.let { featuresString ->
                featuresString.split(",").forEach { featureString ->
                    collectedFeaturesKeys.add(featureString.trim())
                }
            }

        return collectedFeaturesKeys.toList()
    }

    private fun filterSystemFeaturesToInstall(featureKeys: List<String>): List<AIAgentStorageKey<*>> {
        val filteredSystemFeaturesToInstall = mutableListOf<AIAgentStorageKey<*>>()

        // Check config features exist in the system features list
        featureKeys.forEach { configFeatureKey ->
            val systemFeatureKey = systemFeatures.find { systemFeature -> systemFeature.name == configFeatureKey }

            // Check requested feature is in the known system features list
            if (systemFeatureKey == null) {
                logger.warn {
                    "Feature with key '$configFeatureKey' does not exist in the known system features list:\n" +
                        systemFeatures.joinToString("\n") { " - ${it.name}" }
                }
                return@forEach
            }

            // Ignore system features if already installed by a user
            if (registeredFeatures.keys.any { registerFeatureKey -> registerFeatureKey.name == configFeatureKey }) {
                logger.debug {
                    "Feature with key '$configFeatureKey' has already been registered. " +
                        "Skipping system feature from config registration."
                }
                return@forEach
            }

            filteredSystemFeaturesToInstall.add(systemFeatureKey)
        }

        return filteredSystemFeaturesToInstall.toList()
    }

    @OptIn(ExperimentalAgentsApi::class)
    private fun installSystemFeature(featureKey: AIAgentStorageKey<*>) {
        logger.debug { "Installing system feature: ${featureKey.name}" }
        when (featureKey) {
            Debugger.key -> {
                when (this) {
                    is AIAgentGraphPipeline -> {
                        this.install(Debugger) {
                            // Use default configuration
                        }
                    }

                    is AIAgentFunctionalPipeline -> {
                        this.install(Debugger) {
                            // Use default configuration
                        }
                    }
                }
            }

            else -> {
                error(
                    "Unsupported system feature key: ${featureKey.name}. " +
                        "Please make sure all system features are registered in the systemFeatures list.\n" +
                        "Current system features list:\n${systemFeatures.joinToString("\n") { " - ${it.name}" }}"
                )
            }
        }
    }

    @InternalAgentsApi
    public override fun <TContext : AgentLifecycleEventContext> createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend (TContext) -> Unit
    ): suspend (TContext) -> Unit = handler@{ eventContext ->
        val featureConfig = registeredFeatures[feature.key]?.featureConfig

        if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
            return@handler
        }

        handle(eventContext)
    }

    @InternalAgentsApi
    public override fun createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment
    ): suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment =
        handler@{ eventContext, env ->
            val featureConfig = registeredFeatures[feature.key]?.featureConfig

            if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
                return@handler env
            }

            handle(eventContext, env)
        }

    public override fun FeatureConfig.isAccepted(eventContext: AgentLifecycleEventContext): Boolean {
        return this.eventFilter.invoke(eventContext)
    }

    //endregion Private Methods
}
