@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolException
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KType

public actual abstract class AIAgentPipeline actual constructor(agentConfig: AIAgentConfig, clock: Clock) {

    @PublishedApi
    internal val pipelineDelegate: AIAgentPipelineImpl = AIAgentPipelineImpl(agentConfig, clock)

    public actual open val clock: Clock = pipelineDelegate.clock

    public actual open fun <TFeature : Any> feature(
        featureClass: KClass<TFeature>,
        feature: AIAgentFeature<*, TFeature>
    ): TFeature? = pipelineDelegate.feature(featureClass, feature)

    //region Trigger Agent Handlers

    @OptIn(InternalAgentsApi::class)
    public actual open suspend fun <TInput, TOutput> onAgentStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    ) {
        pipelineDelegate.onAgentStarting<TInput, TOutput>(executionInfo, runId, agent, context)
    }

    public actual open suspend fun onAgentCompleted(
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        result: Any?,
        context: AIAgentContext
    ) {
        pipelineDelegate.onAgentCompleted(executionInfo, agentId, runId, result, context)
    }

    public actual open suspend fun onAgentExecutionFailed(
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        exception: Throwable?,
        context: AIAgentContext
    ) {
        pipelineDelegate.onAgentExecutionFailed(executionInfo, agentId, runId, exception, context)
    }

    public actual open suspend fun onAgentClosing(
        executionInfo: AgentExecutionInfo,
        agentId: String,
    ) {
        pipelineDelegate.onAgentClosing(executionInfo, agentId)
    }

    public actual open suspend fun onAgentEnvironmentTransforming(
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, AIAgentGraphContextBase>,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment,
    ): AIAgentEnvironment =
        pipelineDelegate.onAgentEnvironmentTransforming(executionInfo, strategy, agent, baseEnvironment)

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    @OptIn(InternalAgentsApi::class)
    public actual open suspend fun onStrategyStarting(
        executionInfo: AgentExecutionInfo, strategy: AIAgentStrategy<*, *, *>, context: AIAgentContext
    ) {
        pipelineDelegate.onStrategyStarting(executionInfo, strategy, context)
    }

    @OptIn(InternalAgentsApi::class)
    public actual open suspend fun onStrategyCompleted(
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType,
    ) {
        pipelineDelegate.onStrategyCompleted(executionInfo, strategy, context, result, resultType)
    }

    //endregion Trigger Strategy Handlers

    //region Trigger LLM Call Handlers

    public actual open suspend fun onLLMCallStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        pipelineDelegate.onLLMCallStarting(executionInfo, runId, prompt, model, tools, context)
    }

    public actual open suspend fun onLLMCallCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult?,
        context: AIAgentContext
    ) {
        pipelineDelegate.onLLMCallCompleted(
            executionInfo,
            runId,
            prompt,
            model,
            tools,
            responses,
            moderationResponse,
            context
        )
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    public actual open suspend fun onToolCallStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        context: AIAgentContext
    ) {
        pipelineDelegate.onToolCallStarting(executionInfo, runId, toolCallId, toolName, toolArgs, context)
    }

    public actual open suspend fun onToolValidationFailed(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        message: String,
        error: ToolException,
        context: AIAgentContext
    ) {
        pipelineDelegate.onToolValidationFailed(
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolArgs,
            toolDescription,
            message,
            error,
            context
        )
    }

    public actual open suspend fun onToolCallFailed(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        message: String,
        exception: Throwable?,
        context: AIAgentContext
    ) {
        pipelineDelegate.onToolCallFailed(
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolArgs,
            toolDescription,
            message,
            exception,
            context
        )
    }

    public actual open suspend fun onToolCallCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        toolResult: JsonElement?,
        context: AIAgentContext
    ) {
        pipelineDelegate.onToolCallCompleted(
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolArgs,
            toolDescription,
            toolResult,
            context
        )
    }

    //endregion Trigger Tool Call Handlers

    //region Trigger LLM Streaming

    public actual open suspend fun onLLMStreamingStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        pipelineDelegate.onLLMStreamingStarting(executionInfo, runId, prompt, model, tools, context)
    }

    public actual open suspend fun onLLMStreamingFrameReceived(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        streamFrame: StreamFrame,
        context: AIAgentContext
    ) {
        pipelineDelegate.onLLMStreamingFrameReceived(executionInfo, runId, prompt, model, streamFrame, context)
    }

    public actual open suspend fun onLLMStreamingFailed(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        exception: Throwable,
        context: AIAgentContext
    ) {
        pipelineDelegate.onLLMStreamingFailed(executionInfo, runId, prompt, model, exception, context)
    }

    public actual open suspend fun onLLMStreamingCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        pipelineDelegate.onLLMStreamingCompleted(executionInfo, runId, prompt, model, tools, context)
    }

    //endregion Trigger LLM Streaming

    //region Interceptors

    public actual open fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        transform: suspend AgentEnvironmentTransformingContext.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        pipelineDelegate.interceptEnvironmentCreated(feature, transform)
    }

    public actual open fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentStarting(feature, handle)
    }

    public actual open fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentCompleted(feature, handle)
    }

    public actual open fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentExecutionFailed(feature, handle)
    }

    public actual open fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentClosing(feature, handle)
    }

    public actual open fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptStrategyStarting(feature, handle)
    }

    public actual open fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptStrategyCompleted(feature, handle)
    }

    public actual open fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMCallStarting(feature, handle)
    }

    public actual open fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMCallCompleted(feature, handle)
    }

    public actual open fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingStarting(feature, handle)
    }

    public actual open fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingFrameReceived(feature, handle)
    }

    public actual open fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingFailed(feature, handle)
    }

    public actual open fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingCompleted(feature, handle)
    }

    public actual open fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallStarting(feature, handle)
    }

    public actual open fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolValidationFailed(feature, handle)
    }

    public actual open fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallFailed(feature, handle)
    }

    public actual open fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallCompleted(feature, handle)
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
    public actual open fun interceptBeforeAgentStarted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptBeforeAgentStarted(feature, handle)
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
    public actual open fun interceptAgentFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentFinished(feature, handle)
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
    public actual open fun interceptAgentRunError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentExecutionFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentRunError(feature, handle)
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
    public actual open fun interceptAgentBeforeClose(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentClosingContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentBeforeClose(feature, handle)
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
    public actual open fun interceptStrategyStart(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptStrategyStart(feature, handle)
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
    public actual open fun interceptStrategyFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptStrategyFinished(feature, handle)
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
    public actual open fun interceptBeforeLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptBeforeLLMCall(feature, handle)
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
    public actual open fun interceptAfterLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptAfterLLMCall(feature, handle)
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
    public actual open fun interceptToolCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCall(feature, handle)
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
    public actual open fun interceptToolCallResult(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallResult(feature, handle)
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
    public actual open fun interceptToolCallFailure(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallFailure(feature, handle)
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
    public actual open fun interceptToolValidationError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolValidationError(feature, handle)
    }

    //endregion Deprecated Interceptors

    //region Private Methods

    protected actual open fun <TContext : AgentLifecycleEventContext> createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend (TContext) -> Unit
    ): suspend (TContext) -> Unit = pipelineDelegate.createConditionalHandler(feature, handle)

    protected actual open fun createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend AgentEnvironmentTransformingContext.(AIAgentEnvironment) -> AIAgentEnvironment
    ): suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment =
        pipelineDelegate.createConditionalHandler(feature, handle)

    //endregion Private Methods
    internal actual open suspend fun prepareFeatures() {
        pipelineDelegate.prepareFeatures()
    }

    internal actual open suspend fun closeAllFeaturesMessageProcessors() {
        pipelineDelegate.closeAllFeaturesMessageProcessors()
    }

    protected actual open fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        featureKey: AIAgentStorageKey<TFeatureImpl>,
        featureConfig: TConfig,
        featureImpl: TFeatureImpl
    ) {
    }

    protected actual open suspend fun uninstall(featureKey: AIAgentStorageKey<*>) {
        pipelineDelegate.uninstall(featureKey)
    }

    protected actual open fun FeatureConfig.isAccepted(eventContext: AgentLifecycleEventContext): Boolean =
        with(pipelineDelegate) {
            isAccepted(eventContext)
        }
}
