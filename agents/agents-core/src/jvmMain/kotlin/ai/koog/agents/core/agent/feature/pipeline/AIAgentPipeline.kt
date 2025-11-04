package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
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
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.future.await
import kotlinx.datetime.Clock
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

    /**
     * Retrieves a feature implementation from the current pipeline using the specified [feature], if it is registered.
     *
     * @param TFeature A feature implementation type.
     * @param feature A feature to fetch.
     * @param featureClass The [KClass] of the feature to be retrieved.
     * @return The feature associated with the provided key, or null if no matching feature is found.
     * @throws IllegalArgumentException if the specified [featureClass] does not correspond to a registered feature.
     */
    public actual open fun <TFeature : Any> feature(
        featureClass: KClass<TFeature>,
        feature: AIAgentFeature<*, TFeature>
    ): TFeature? = pipelineDelegate.feature(featureClass, feature)

    //region Trigger Agent Handlers

    /**
     * Notifies all registered handlers that an agent has started execution.
     *
     * @param runId The unique identifier for the agent run
     * @param agent The agent instance for which the execution has started
     * @param context The context of the agent execution, providing access to the agent environment and context features
     */
    @OptIn(InternalAgentsApi::class)
    public actual open suspend fun <TInput, TOutput> onAgentStarting(
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    ) {
        pipelineDelegate.onAgentStarting<TInput, TOutput>(runId, agent, context)
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param agentId The unique identifier of the agent that finished execution
     * @param runId The unique identifier of the agent run
     * @param result The result produced by the agent, or null if no result was produced
     */
    public actual open suspend fun onAgentCompleted(
        agentId: String,
        runId: String,
        result: Any?
    ) {
        pipelineDelegate.onAgentCompleted(agentId, runId, result)
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param agentId The unique identifier of the agent that encountered the error
     * @param runId The unique identifier of the agent run
     * @param throwable The exception that was thrown during agent execution
     */
    public actual open suspend fun onAgentExecutionFailed(
        agentId: String,
        runId: String,
        throwable: Throwable
    ) {
        pipelineDelegate.onAgentExecutionFailed(agentId, runId, throwable)
    }

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param agentId The unique identifier of the agent that will be closed.
     */
    public actual open suspend fun onAgentClosing(
        agentId: String
    ) {
        pipelineDelegate.onAgentClosing(agentId)
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
    public actual open suspend fun onAgentEnvironmentTransforming(
        strategy: AIAgentStrategy<*, *, AIAgentGraphContextBase>,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment = pipelineDelegate.onAgentEnvironmentTransforming(strategy, agent, baseEnvironment)

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param strategy The strategy that has started execution
     * @param context The context of the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public actual open suspend fun onStrategyStarting(strategy: AIAgentStrategy<*, *, *>, context: AIAgentContext) {
        pipelineDelegate.onStrategyStarting(strategy, context)
    }

    /**
     * Notifies all registered strategy handlers that a strategy has finished execution.
     *
     * @param strategy The strategy that has finished execution
     * @param context The context of the strategy execution
     * @param result The result produced by the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public actual open suspend fun onStrategyCompleted(
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType,
    ) {
        pipelineDelegate.onStrategyCompleted(strategy, context, result, resultType)
    }

    //endregion Trigger Strategy Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param prompt The prompt that will be sent to the language model
     * @param tools The list of tool descriptors available for the LLM call
     * @param model The language model instance that will process the request
     */
    public actual open suspend fun onLLMCallStarting(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        pipelineDelegate.onLLMCallStarting(runId, prompt, model, tools)
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
    public actual open suspend fun onLLMCallCompleted(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult?
    ) {
        pipelineDelegate.onLLMCallCompleted(runId, prompt, model, tools, responses, moderationResponse)
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
    public actual open suspend fun onToolCallStarting(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: Any?
    ) {
        pipelineDelegate.onToolCallStarting(runId, toolCallId, tool, toolArgs)
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool for which validation failed
     * @param toolArgs The arguments that failed validation
     * @param error The validation error message
     */
    public actual open suspend fun onToolValidationFailed(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: Any?,
        error: String
    ) {
        pipelineDelegate.onToolValidationFailed(runId, toolCallId, tool, toolArgs, error)
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that failed
     * @param toolArgs The arguments provided to the tool
     * @param throwable The exception that caused the failure
     */
    public actual open suspend fun onToolCallFailed(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: Any?,
        throwable: Throwable
    ) {
        pipelineDelegate.onToolCallFailed(runId, toolCallId, tool, toolArgs, throwable)
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that was called
     * @param toolArgs The arguments that were provided to the tool
     * @param result The result produced by the tool, or null if no result was produced
     */
    public actual open suspend fun onToolCallCompleted(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: Any?,
        result: Any?
    ) {
        pipelineDelegate.onToolCallCompleted(runId, toolCallId, tool, toolArgs, result)
    }

    //endregion Trigger Tool Call Handlers

    //region Trigger LLM Streaming

    /**
     * Invoked before streaming from a language model begins.
     *
     * This method notifies all registered stream handlers that streaming is about to start,
     * allowing them to perform preprocessing or logging operations.
     *
     * @param runId The unique identifier for this streaming session
     * @param prompt The prompt being sent to the language model
     * @param model The language model being used for streaming
     * @param tools The list of available tool descriptors for this streaming session
     */
    public actual open suspend fun onLLMStreamingStarting(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        pipelineDelegate.onLLMStreamingStarting(runId, prompt, model, tools)
    }

    /**
     * Invoked when a stream frame is received during the streaming process.
     *
     * This method notifies all registered stream handlers about each incoming stream frame,
     * allowing them to process, transform, or aggregate the streaming content in real-time.
     *
     * @param runId The unique identifier for this streaming session
     * @param streamFrame The individual stream frame containing partial response data
     */
    public actual open suspend fun onLLMStreamingFrameReceived(runId: String, streamFrame: StreamFrame) {
        pipelineDelegate.onLLMStreamingFrameReceived(runId, streamFrame)
    }

    /**
     * Invoked if an error occurs during the streaming process.
     *
     * This method notifies all registered stream handlers about the streaming error,
     * allowing them to handle or log the error.
     *
     * @param runId The unique identifier for this streaming session
     * @param throwable The exception that occurred during streaming, if applicable
     */
    public actual open suspend fun onLLMStreamingFailed(runId: String, throwable: Throwable) {
        pipelineDelegate.onLLMStreamingFailed(runId, throwable)
    }

    /**
     * Invoked after streaming from a language model completes.
     *
     * This method notifies all registered stream handlers that streaming has finished,
     * allowing them to perform post-processing, cleanup, or final logging operations.
     *
     * @param runId The unique identifier for this streaming session
     * @param prompt The prompt that was sent to the language model
     * @param model The language model that was used for streaming
     * @param tools The list of tool descriptors that were available for this streaming session
     */
    public actual open suspend fun onLLMStreamingCompleted(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        pipelineDelegate.onLLMStreamingCompleted(runId, prompt, model, tools)
    }

    //endregion Trigger LLM Streaming

    //region Interceptors

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This method registers a transformer function that will be called when an agent environment
     * is being created, allowing the feature to customize the environment based on the agent context.
     *
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
    public actual open fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        transform: AgentEnvironmentTransformingContext.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        pipelineDelegate.interceptEnvironmentCreated(feature, transform)
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * @param handle The handler that processes agent creation events
     *
     * Example:
     * ```
     * pipeline.interceptAgentStarting(InterceptContext) {
     *     readStages { stages ->
     *         // Inspect agent stages
     *     }
     * }
     * ```
     */
    public actual open fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentStarting(feature, handle)
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * @param handle A suspend function providing custom logic to execute when the agent completes,
     *
     * Example:
     * ```
     * pipeline.interceptAgentCompleted(feature) { eventContext ->
     *     // Handle the completion result here, using the strategy name and the result.
     * }
     * ```
     */
    public actual open fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentCompleted(feature, handle)
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * @param handle A suspend function providing custom logic to execute when an error occurs,
     *
     * Example:
     * ```
     * pipeline.interceptAgentExecutionFailed(feature) { eventContext ->
     *     // Handle the error here, using the strategy name and the exception that occurred.
     * }
     * ```
     */
    public actual open fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentExecutionFailed(feature, handle)
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspendable function that is executed during the agent's pre-close phase.
     *
     * Example:
     * ```
     * pipeline.interceptAgentClosing(feature) { eventContext ->
     *     // Handle agent run before close event.
     * }
     * ```
     */
    public actual open fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    ) {
        pipelineDelegate.interceptAgentClosing(feature, handle)
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that processes the start of a strategy, accepting the strategy context
     *
     * Example:
     * ```
     * pipeline.interceptStrategyStarting(feature) { event ->
     *     val strategyName = event.strategy.name
     *     logger.info("Strategy $strategyName has started execution")
     * }
     * ```
     */
    public actual open fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptStrategyStarting(feature, handle)
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that processes the completion of a strategy.
     *
     * Example:
     * ```
     * pipeline.interceptStrategyCompleted(feature) { event ->
     *     // Handle the completion of the strategy here
     * }
     * ```
     */
    public actual open fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptStrategyCompleted(feature, handle)
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptLLMCallStarting(feature) { eventContext ->
     *     logger.info("About to make LLM call with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public actual open fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMCallStarting(feature, handle)
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptLLMCallCompleted(feature) { eventContext ->
     *     // Process or analyze the response
     * }
     * ```
     */
    public actual open fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMCallCompleted(feature, handle)
    }

    /**
     * Intercepts streaming operations before they begin to modify or log the streaming request.
     *
     * This method allows features to hook into the streaming pipeline before streaming starts,
     * enabling preprocessing, validation, or logging of streaming requests.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-stream events
     *
     * Example:
     * ```
     * pipeline.interceptLLMStreamingStarting(feature) { eventContext ->
     *     logger.info("About to start streaming with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public actual open fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingStarting(feature, handle)
    }

    /**
     * Intercepts stream frames as they are received during the streaming process.
     *
     * This method allows features to process individual stream frames in real-time,
     * enabling monitoring, transformation, or aggregation of streaming content.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes stream frame events
     *
     * Example:
     * ```
     * pipeline.interceptLLMStreamingFrameReceived(feature) { eventContext ->
     *     logger.debug("Received stream frame: ${eventContext.streamFrame}")
     * }
     * ```
     */
    public actual open fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingFrameReceived(feature, handle)
    }

    /**
     * Intercepts errors during the streaming process.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes stream errors
     */
    public actual open fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingFailed(feature, handle)
    }

    /**
     * Intercepts streaming operations after they complete to perform post-processing or cleanup.
     *
     * This method allows features to hook into the streaming pipeline after streaming finishes,
     * enabling post-processing, cleanup, or final logging of the streaming session.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-stream events
     *
     * Example:
     * ```
     * pipeline.interceptLLMStreamingCompleted(feature) { eventContext ->
     *     logger.info("Streaming completed for run: ${eventContext.runId}")
     * }
     * ```
     */
    public actual open fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptLLMStreamingCompleted(feature, handle)
    }

    /**
     * Intercepts and handles tool calls for the specified feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend lambda function that processes tool calls.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallStarting(feature) { eventContext ->
     *    // Process or log the tool call
     * }
     * ```
     */
    public actual open fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallStarting(feature, handle)
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspendable lambda function that will be invoked when a tool validation error occurs.
     *
     * Example:
     * ```
     * pipeline.interceptToolValidationFailed(feature) { eventContext ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    public actual open fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolValidationFailed(feature, handle)
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that is invoked when a tool call fails.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallFailed(feature) { eventContext ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    public actual open fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallFailed(feature, handle)
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspending function that defines the behavior to execute when a tool call result is intercepted.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallCompleted(feature) { eventContext ->
     *     // Handle the tool call result here
     * }
     * ```
     */
    public actual open fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        pipelineDelegate.interceptToolCallCompleted(feature, handle)
    }

    //endregion Interceptors

    //region Deprecated Interceptors

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     */
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

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     */
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

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     */
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

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     */
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

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     */
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

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     */
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

    /**
     * Intercepts LLM calls before they are made (deprecated name).
     */
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

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     */
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

    /**
     * Intercepts and handles tool calls for the specified feature and its implementation.
     * Updates the tool call handler for the given feature key with a custom handler.
     */
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

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     */
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

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     */
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

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     */
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

    protected actual inline fun <TContext : AgentLifecycleEventContext> createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        crossinline handle: suspend (TContext) -> Unit
    ): suspend (TContext) -> Unit = pipelineDelegate.createConditionalHandlerImpl(feature, handle)

    protected actual inline fun createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        crossinline handle: suspend AgentEnvironmentTransformingContext.(AIAgentEnvironment) -> AIAgentEnvironment
    ): suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment =
        pipelineDelegate.createConditionalHandlerImpl(feature, handle)

    //endregion Private Methods
    internal actual open suspend fun prepareFeatures() {
        pipelineDelegate.prepareFeatures()
    }

    internal actual open suspend fun closeFeaturesStreamProviders() {
        pipelineDelegate.closeFeaturesStreamProviders()
    }
}
