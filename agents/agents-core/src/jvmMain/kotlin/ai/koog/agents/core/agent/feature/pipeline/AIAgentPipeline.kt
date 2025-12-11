@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.agent.*
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
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolException
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.future.await
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Pipeline for AI agent features that provides interception points for various agent lifecycle events.
 *
 * The pipeline allows features to:
 * - Be installed into agent contexts
 * - Intercept agent creation and environment transformation
 * - Intercept strategy execution before and after it happens
 * - Intercept node execution before and after it happens
 * - Intercept LLM  calls before and after they happen
 * - Intercept tool calls before and after they happen
 *
 * This pipeline serves as the central mechanism for extending and customizing agent behavior
 * through a flexible interception system. Features can be installed with custom configurations
 * and can hook into different stages of the agent's execution lifecycle.
 *
 * @param clock Clock instance for time-related operations
 */
public actual abstract class AIAgentPipeline @JvmOverloads actual constructor(clock: Clock) {

    @PublishedApi
    internal val pipelineDelegate: AIAgentPipelineImpl = AIAgentPipelineImpl(clock)

    @Suppress("MissingKDocForPublicAPI")
    public actual open val clock: Clock = pipelineDelegate.clock

    // JVM Unique Interceptors

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This overload is JVM-friendly and accepts an async transformer.
     *
     * @param feature The feature associated with this transformer.
     * @param transform An async transformer that takes the transforming context and the current environment,
     *                  and returns a possibly modified environment.
     *
     * Example (Java):
     * pipeline.interceptEnvironmentCreated(feature, (ctx, environment) -> {
     *     // Modify the environment and return a CompletionStage
     *     return java.util.concurrent.CompletableFuture.completedFuture(environment);
     * });
     */
    @JavaAPI
    public fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        transform: TransformInterceptor<AgentEnvironmentTransformingContext, AIAgentEnvironment>
    ) {
        interceptEnvironmentCreated(feature) { environment ->
            transform.transform(this, environment)
        }
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentStarting(feature, eventContext -> {
     *     // Inspect agent stages
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<AgentStartingContext>
    ) {
        interceptAgentStarting(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentCompleted(feature, eventContext -> {
     *     // Handle completion
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<AgentCompletedContext>
    ) {
        interceptAgentCompleted(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentExecutionFailed(feature, eventContext -> {
     *     // Handle the error
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<AgentExecutionFailedContext>
    ) {
        interceptAgentExecutionFailed(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentClosing(feature, eventContext -> {
     *     // Pre-close actions
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<AgentClosingContext>
    ) {
        interceptAgentClosing(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptStrategyStarting(feature, event -> {
     *     // Strategy started
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<StrategyStartingContext>
    ) {
        interceptStrategyStarting(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptStrategyCompleted(feature, event -> {
     *     // Strategy completed
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<StrategyCompletedContext>
    ) {
        interceptStrategyCompleted(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMCallStarting(feature, eventContext -> {
     *     // About to call LLM
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<LLMCallStartingContext>
    ) {
        interceptLLMCallStarting(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMCallCompleted(feature, eventContext -> {
     *     // Process response
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<LLMCallCompletedContext>
    ) {
        interceptLLMCallCompleted(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts streaming operations before they begin to modify or log the streaming request.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingStarting(feature, eventContext -> {
     *     // About to start streaming
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<LLMStreamingStartingContext>
    ) {
        interceptLLMStreamingStarting(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts stream frames as they are received during the streaming process.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingFrameReceived(feature, eventContext -> {
     *     // Handle stream frame
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<LLMStreamingFrameReceivedContext>
    ) {
        interceptLLMStreamingFrameReceived(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts errors during the streaming process.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingFailed(feature, eventContext -> {
     *     // Handle streaming error
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<LLMStreamingFailedContext>
    ) {
        interceptLLMStreamingFailed(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts streaming operations after they complete to perform post-processing or cleanup.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingCompleted(feature, eventContext -> {
     *     // Streaming completed
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<LLMStreamingCompletedContext>
    ) {
        interceptLLMStreamingCompleted(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts and handles tool calls for the specified feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolCallStarting(feature, eventContext -> {
     *     // Process tool call
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<ToolCallStartingContext>
    ) {
        interceptToolCallStarting(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolValidationFailed(feature, eventContext -> {
     *     // Handle validation failure
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<ToolValidationFailedContext>
    ) {
        interceptToolValidationFailed(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolCallFailed(feature, eventContext -> {
     *     // Handle tool call failure
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<ToolCallFailedContext>
    ) {
        interceptToolCallFailed(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolCallCompleted(feature, eventContext -> {
     *     // Handle tool call result
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    public fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: AsyncInterceptor<ToolCallCompletedContext>
    ) {
        interceptToolCallCompleted(feature) { ctx ->
            handle.intercept(ctx).await()
        }
    }


    // Default Multiplatform Interceptors

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
        result: Any?
    ) {
        pipelineDelegate.onAgentCompleted(executionInfo, agentId, runId, result)
    }

    public actual open suspend fun onAgentExecutionFailed(
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        exception: Throwable?
    ) {
        pipelineDelegate.onAgentExecutionFailed(executionInfo, agentId, runId, exception)
    }

    public actual open suspend fun onAgentClosing(
        executionInfo: AgentExecutionInfo,
        agentId: String
    ) {
        pipelineDelegate.onAgentClosing(executionInfo, agentId)
    }

    public actual open suspend fun onAgentEnvironmentTransforming(
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, AIAgentGraphContextBase>,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
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
        tools: List<ToolDescriptor>
    ) {
        pipelineDelegate.onLLMCallStarting(executionInfo, runId, prompt, model, tools)
    }

    public actual open suspend fun onLLMCallCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult?
    ) {
        pipelineDelegate.onLLMCallCompleted(executionInfo, runId, prompt, model, tools, responses, moderationResponse)
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    public actual open suspend fun onToolCallStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
    ) {
        pipelineDelegate.onToolCallStarting(executionInfo, runId, toolCallId, toolName, toolArgs)
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
    ) {
        pipelineDelegate.onToolValidationFailed(
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolArgs,
            toolDescription,
            message,
            error
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
        exception: Throwable?
    ) {
        pipelineDelegate.onToolCallFailed(
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolArgs,
            toolDescription,
            message,
            exception
        )
    }

    public actual open suspend fun onToolCallCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        toolResult: JsonElement?
    ) {
        pipelineDelegate.onToolCallCompleted(
            executionInfo,
            runId,
            toolCallId,
            toolName,
            toolArgs,
            toolDescription,
            toolResult
        )
    }

    //endregion Trigger Tool Call Handlers

    //region Trigger LLM Streaming

    public actual open suspend fun onLLMStreamingStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        pipelineDelegate.onLLMStreamingStarting(executionInfo, runId, prompt, model, tools)
    }

    public actual open suspend fun onLLMStreamingFrameReceived(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        streamFrame: StreamFrame
    ) {
        pipelineDelegate.onLLMStreamingFrameReceived(executionInfo, runId, prompt, model, streamFrame)
    }

    public actual open suspend fun onLLMStreamingFailed(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        exception: Throwable
    ) {
        pipelineDelegate.onLLMStreamingFailed(executionInfo, runId, prompt, model, exception)
    }

    public actual open suspend fun onLLMStreamingCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        pipelineDelegate.onLLMStreamingCompleted(executionInfo, runId, prompt, model, tools)
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
