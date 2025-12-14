@file:Suppress("MissingKDocForPublicAPI")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AfterLLMCallContext
import ai.koog.agents.core.feature.handler.AgentBeforeCloseContext
import ai.koog.agents.core.feature.handler.AgentFinishedContext
import ai.koog.agents.core.feature.handler.AgentRunErrorContext
import ai.koog.agents.core.feature.handler.AgentStartContext
import ai.koog.agents.core.feature.handler.BeforeLLMCallContext
import ai.koog.agents.core.feature.handler.NodeAfterExecuteContext
import ai.koog.agents.core.feature.handler.NodeBeforeExecuteContext
import ai.koog.agents.core.feature.handler.NodeExecutionErrorContext
import ai.koog.agents.core.feature.handler.StrategyFinishedContext
import ai.koog.agents.core.feature.handler.StrategyStartContext
import ai.koog.agents.core.feature.handler.ToolCallContext
import ai.koog.agents.core.feature.handler.ToolCallFailureContext
import ai.koog.agents.core.feature.handler.ToolCallResultContext
import ai.koog.agents.core.feature.handler.ToolValidationErrorContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.pipeline.Interceptor
import ai.koog.agents.core.utils.submitToMainDispatcher
import kotlinx.coroutines.future.await

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual open class EventHandlerConfig actual constructor() : FeatureConfig() {
    private val delegate = EventHandlerConfigImpl()

    // Java Specific Handlers:

    /**
     * Registers a handler for the subgraph execution starting event. This method allows asynchronous
     * interception of the event, enabling users to execute custom logic during the beginning of a
     * subgraph execution.
     *
     * @param handler The asynchronous interceptor that processes the SubgraphExecutionStartingContext.
     */
    @JavaAPI
    public fun onSubgraphExecutionStarting(handler: Interceptor<SubgraphExecutionStartingContext>) {
        onSubgraphExecutionStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when the execution of a subgraph is completed.
     *
     * @param handler An asynchronous interceptor that processes the subgraph execution
     *                completion context. It provides a mechanism to inspect or modify
     *                the context as needed before completion.
     */
    @JavaAPI
    public fun onSubgraphExecutionCompleted(handler: Interceptor<SubgraphExecutionCompletedContext>) {
        onSubgraphExecutionCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Handles an event where the execution of a subgraph has failed.
     *
     * @param handler An asynchronous interceptor that processes the subgraph execution failure context.
     */
    @JavaAPI
    public fun onSubgraphExecutionFailed(handler: Interceptor<SubgraphExecutionFailedContext>) {
        onSubgraphExecutionFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an agent is started.
     */
    @JavaAPI
    public fun onAgentStarting(handler: Interceptor<AgentStartingContext>) {
        onAgentStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    @JavaAPI
    public fun onAgentCompleted(handler: Interceptor<AgentCompletedContext>) {
        onAgentCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    @JavaAPI
    public fun onAgentExecutionFailed(handler: Interceptor<AgentExecutionFailedContext>) {
        onAgentExecutionFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    @JavaAPI
    public fun onAgentClosing(handler: Interceptor<AgentClosingContext>) {
        onAgentClosing { eventContext ->
            eventContext.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a strategy starts execution.
     */
    @JavaAPI
    public fun onStrategyStarting(handler: Interceptor<StrategyStartingContext>) {
        onStrategyStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    @JavaAPI
    public fun onStrategyCompleted(handler: Interceptor<StrategyCompletedContext>) {
        onStrategyCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    @JavaAPI
    public fun onNodeExecutionStarting(handler: Interceptor<NodeExecutionStartingContext>) {
        onNodeExecutionStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    @JavaAPI
    public fun onNodeExecutionCompleted(handler: Interceptor<NodeExecutionCompletedContext>) {
        onNodeExecutionCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    @JavaAPI
    public fun onNodeExecutionFailed(handler: Interceptor<NodeExecutionFailedContext>) {
        onNodeExecutionFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called before a call is made to the language model.
     */
    @JavaAPI
    public fun onLLMCallStarting(handler: Interceptor<LLMCallStartingContext>) {
        onLLMCallStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    @JavaAPI
    public fun onLLMCallCompleted(handler: Interceptor<LLMCallCompletedContext>) {
        onLLMCallCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool is about to be called.
     */
    @JavaAPI
    public fun onToolCallStarting(handler: Interceptor<ToolCallStartingContext>) {
        onToolCallStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    @JavaAPI
    public fun onToolValidationFailed(handler: Interceptor<ToolValidationFailedContext>) {
        onToolValidationFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    @JavaAPI
    public fun onToolCallFailed(handler: Interceptor<ToolCallFailedContext>) {
        onToolCallFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    @JavaAPI
    public fun onToolCallCompleted(handler: Interceptor<ToolCallCompletedContext>) {
        onToolCallCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked before streaming from a language model begins.
     */
    @JavaAPI
    public fun onLLMStreamingStarting(handler: Interceptor<LLMStreamingStartingContext>) {
        onLLMStreamingStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when stream frames are received during streaming.
     */
    @JavaAPI
    public fun onLLMStreamingFrameReceived(handler: Interceptor<LLMStreamingFrameReceivedContext>) {
        onLLMStreamingFrameReceived { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when an error occurs during streaming.
     */
    @JavaAPI
    public fun onLLMStreamingFailed(handler: Interceptor<LLMStreamingFailedContext>) {
        onLLMStreamingFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked after streaming from a language model completes.
     */
    @JavaAPI
    public fun onLLMStreamingCompleted(handler: Interceptor<LLMStreamingCompletedContext>) {
        onLLMStreamingCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    // Default Multiplafrorm Handlers:

    /**
     * Append handler called when an agent is started.
     */
    public actual open fun onAgentStarting(handler: suspend (AgentStartingContext) -> Unit) {
        delegate.onAgentStarting(handler)
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    public actual open fun onAgentCompleted(handler: suspend (AgentCompletedContext) -> Unit) {
        delegate.onAgentCompleted(handler)
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    public actual open fun onAgentExecutionFailed(handler: suspend (AgentExecutionFailedContext) -> Unit) {
        delegate.onAgentExecutionFailed(handler)
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    public actual open fun onAgentClosing(handler: suspend (AgentClosingContext) -> Unit) {
        delegate.onAgentClosing(handler)
    }

    /**
     * Append handler called when a strategy starts execution.
     */
    public actual open fun onStrategyStarting(handler: suspend (StrategyStartingContext) -> Unit) {
        delegate.onStrategyStarting(handler)
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    public actual open fun onStrategyCompleted(handler: suspend (StrategyCompletedContext) -> Unit) {
        delegate.onStrategyCompleted(handler)
    }

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    public actual open fun onNodeExecutionStarting(handler: suspend (NodeExecutionStartingContext) -> Unit) {
        delegate.onNodeExecutionStarting(handler)
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    public actual open fun onNodeExecutionCompleted(handler: suspend (NodeExecutionCompletedContext) -> Unit) {
        delegate.onNodeExecutionCompleted(handler)
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    public actual open fun onNodeExecutionFailed(handler: suspend (NodeExecutionFailedContext) -> Unit) {
        delegate.onNodeExecutionFailed(handler)
    }

    /**
     * Append handler called before a call is made to the language model.
     */
    public actual open fun onLLMCallStarting(handler: suspend (LLMCallStartingContext) -> Unit) {
        delegate.onLLMCallStarting(handler)
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    public actual open fun onLLMCallCompleted(handler: suspend (LLMCallCompletedContext) -> Unit) {
        delegate.onLLMCallCompleted(handler)
    }

    /**
     * Append handler called when a tool is about to be called.
     */
    public actual open fun onToolCallStarting(handler: suspend (ToolCallStartingContext) -> Unit) {
        delegate.onToolCallStarting(handler)
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    public actual open fun onToolValidationFailed(handler: suspend (ToolValidationFailedContext) -> Unit) {
        delegate.onToolValidationFailed(handler)
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    public actual open fun onToolCallFailed(handler: suspend (ToolCallFailedContext) -> Unit) {
        delegate.onToolCallFailed(handler)
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    public actual open fun onToolCallCompleted(handler: suspend (ToolCallCompletedContext) -> Unit) {
        delegate.onToolCallCompleted(handler)
    }

    /**
     * Registers a handler to be invoked before streaming from a language model begins.
     *
     * This handler is called immediately before starting a streaming operation,
     * allowing you to perform preprocessing, validation, or logging of the streaming request.
     *
     * @param handler The handler function that receives a [LLMStreamingStartingContext] containing
     *                the run ID, prompt, model, and available tools for the streaming session.
     *
     * Example:
     * ```
     * onLLMStreamingStarting { eventContext ->
     *     logger.info("Starting stream for run: ${eventContext.runId}")
     *     logger.debug("Prompt: ${eventContext.prompt}")
     * }
     * ```
     */
    public actual open fun onLLMStreamingStarting(handler: suspend (LLMStreamingStartingContext) -> Unit) {
        delegate.onLLMStreamingStarting(handler)
    }

    /**
     * Registers a handler to be invoked when stream frames are received during streaming.
     *
     * This handler is called for each stream frame as it arrives from the language model,
     * enabling real-time processing, monitoring, or aggregation of streaming content.
     *
     * @param handler The handler function that receives a [LLMStreamingFrameReceivedContext] containing
     *                the run ID and the stream frame with partial response data.
     *
     * Example:
     * ```
     * onLLMStreamingFrameReceived { eventContext ->
     *     when (val frame = eventContext.streamFrame) {
     *         is StreamFrame.Append -> processText(frame.text)
     *         is StreamFrame.ToolCall -> processTool(frame)
     *     }
     * }
     * ```
     */
    public actual open fun onLLMStreamingFrameReceived(handler: suspend (LLMStreamingFrameReceivedContext) -> Unit) {
        delegate.onLLMStreamingFrameReceived(handler)
    }

    /**
     * Registers a handler to be invoked when an error occurs during streaming.
     *
     * This handler is called when an error occurs during streaming,
     * allowing you to perform error handling or logging.
     *
     * @param handler The handler function that receives a [LLMStreamingFailedContext] containing
     *                the run ID, prompt, model, and tools that were used for the streaming session.
     *
     * Example:
     * ```
     * onLLMStreamingFailed { eventContext ->
     *     logger.error("Stream error for run: ${eventContext.runId}")
     * }
     * ```
     */
    public actual open fun onLLMStreamingFailed(handler: suspend (LLMStreamingFailedContext) -> Unit) {
        delegate.onLLMStreamingFailed(handler)
    }

    /**
     * Registers a handler to be invoked after streaming from a language model completes.
     *
     * This handler is called when the streaming operation finishes,
     * allowing you to perform post-processing, cleanup, or final logging operations.
     *
     * @param handler The handler function that receives an [LLMStreamingCompletedContext] containing
     *                the run ID, prompt, model, and tools that were used for the streaming session.
     *
     * Example:
     * ```
     * onLLMStreamingCompleted { eventContext ->
     *     logger.info("Stream completed for run: ${eventContext.runId}")
     *     // Perform any cleanup or aggregation of collected stream data
     * }
     * ```
     */
    public actual open fun onLLMStreamingCompleted(handler: suspend (LLMStreamingCompletedContext) -> Unit) {
        delegate.onLLMStreamingCompleted(handler)
    }

    /**
     * Append handler called when an agent is started.
     */
    @Deprecated(
        message = "Use onAgentStarting instead",
        ReplaceWith("onAgentStarting(handler)", "ai.koog.agents.core.feature.handler.AgentStartingContext")
    )
    public actual open fun onBeforeAgentStarted(handler: suspend (AgentStartContext) -> Unit) {
        delegate.onBeforeAgentStarted(handler)
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    @Deprecated(
        message = "Use onAgentCompleted instead",
        ReplaceWith("onAgentCompleted(handler)", "ai.koog.agents.core.feature.handler.AgentCompletedContext")
    )
    public actual open fun onAgentFinished(handler: suspend (AgentFinishedContext) -> Unit) {
        delegate.onAgentFinished(handler)
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    @Deprecated(
        message = "Use onAgentExecutionFailed instead",
        ReplaceWith(
            "onAgentExecutionFailed(handler)",
            "ai.koog.agents.core.feature.handler.AgentExecutionFailedContext"
        )
    )
    public actual open fun onAgentRunError(handler: suspend (AgentRunErrorContext) -> Unit) {
        delegate.onAgentRunError(handler)
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    @Deprecated(
        message = "Use onAgentClosing instead",
        ReplaceWith("onAgentClosing(handler)", "ai.koog.agents.core.feature.handler.AgentClosingContext")
    )
    public actual open fun onAgentBeforeClose(handler: suspend (AgentBeforeCloseContext) -> Unit) {
        delegate.onAgentBeforeClose(handler)
    }

    /**
     * Append handler called when a strategy starts execution.
     */
    @Deprecated(
        message = "Use onStrategyStarting instead",
        ReplaceWith("onStrategyStarting(handler)", "ai.koog.agents.core.feature.handler.StrategyStartingContext")
    )
    public actual open fun onStrategyStarted(handler: suspend (StrategyStartContext) -> Unit) {
        delegate.onStrategyStarted(handler)
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    @Deprecated(
        message = "Use onStrategyCompleted instead",
        ReplaceWith("onStrategyCompleted(handler)", "ai.koog.agents.core.feature.handler.StrategyCompletedContext")
    )
    public actual open fun onStrategyFinished(handler: suspend (StrategyFinishedContext) -> Unit) {
        delegate.onStrategyFinished(handler)
    }

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    @Deprecated(
        message = "Use onNodeExecutionStarting instead",
        ReplaceWith(
            "onNodeExecutionStarting(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionStartingContext"
        )
    )
    public actual open fun onBeforeNode(handler: suspend (NodeBeforeExecuteContext) -> Unit) {
        delegate.onBeforeNode(handler)
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    @Deprecated(
        message = "Use onNodeExecutionCompleted instead",
        ReplaceWith(
            "onNodeExecutionCompleted(handler)",
            "ai.koog.agents.core.feature.handler.NodeExecutionCompletedContext"
        )
    )
    public actual open fun onAfterNode(handler: suspend (NodeAfterExecuteContext) -> Unit) {
        delegate.onAfterNode(handler)
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    @Deprecated(
        message = "Use onNodeExecutionError instead",
        ReplaceWith("onNodeExecutionFailed(handler)", "ai.koog.agents.core.feature.handler.NodeExecutionFailedContext")
    )
    public actual open fun onNodeExecutionError(handler: suspend (NodeExecutionErrorContext) -> Unit) {
        delegate.onNodeExecutionError(handler)
    }

    /**
     * Append handler called before a call is made to the language model.
     */
    @Deprecated(
        message = "Use onLLMCallStarting instead",
        ReplaceWith("onLLMCallStarting(handler)", "ai.koog.agents.core.feature.handler.LLMCallStartingContext")
    )
    public actual open fun onBeforeLLMCall(handler: suspend (BeforeLLMCallContext) -> Unit) {
        delegate.onBeforeLLMCall(handler)
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    @Deprecated(
        message = "Use onLLMCallCompleted instead",
        ReplaceWith("onLLMCallCompleted(handler)", "ai.koog.agents.core.feature.handler.LLMCallCompletedContext")
    )
    public actual open fun onAfterLLMCall(handler: suspend (AfterLLMCallContext) -> Unit) {
        delegate.onAfterLLMCall(handler)
    }

    /**
     * Append handler called when a tool is about to be called.
     */
    @Deprecated(
        message = "Use onToolCallStarting instead",
        ReplaceWith("onToolCallStarting(handler)", "ai.koog.agents.core.feature.handler.ToolCallStartingContext")
    )
    public actual open fun onToolCall(handler: suspend (ToolCallContext) -> Unit) {
        delegate.onToolCall(handler)
    }

    /**
     * Append handler called when some tool validation fails.
     */
    @Deprecated(
        message = "Use onToolValidationFailed instead",
        ReplaceWith(
            "onToolValidationFailed(handler)",
            "ai.koog.agents.core.feature.handler.ToolValidationFailedContext"
        )
    )
    public actual open fun onToolValidationError(handler: suspend (ToolValidationErrorContext) -> Unit) {
        delegate.onToolValidationError(handler)
    }


    /**
     * Append handler called when some tool execution fails.
     */
    @Deprecated(
        message = "Use onToolCallFailed instead",
        ReplaceWith("onToolCallFailed(handler)", "ai.koog.agents.core.feature.handler.ToolCallFailedContext")
    )
    public actual open fun onToolCallFailure(handler: suspend (ToolCallFailureContext) -> Unit) {
        delegate.onToolCallFailure(handler)
    }


    /**
     * Append handler called when a tool execution finishes with successful result.
     */
    @Deprecated(
        message = "Use onToolCallCompleted instead",
        ReplaceWith("onToolCallCompleted(handler)", "ai.koog.agents.core.feature.handler.ToolCallCompletedContext")
    )
    public actual open fun onToolCallResult(handler: suspend (ToolCallResultContext) -> Unit) {
        delegate.onToolCallResult(handler)
    }

    /**
     * Invoke handlers for an event when an agent is started.
     */
    internal actual open suspend fun invokeOnAgentStarting(eventContext: AgentStartingContext) {
        delegate.invokeOnAgentStarting(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal actual open suspend fun invokeOnAgentCompleted(eventContext: AgentCompletedContext) {
        delegate.invokeOnAgentCompleted(eventContext)
    }

    /**
     * Invoke handlers for an event when an error occurs during agent execution.
     */
    internal actual open suspend fun invokeOnAgentExecutionFailed(eventContext: AgentExecutionFailedContext) {
        delegate.invokeOnAgentExecutionFailed(eventContext)
    }

    /**
     * Invokes the handler associated with the event that occurs before an agent is closed.
     */
    internal actual open suspend fun invokeOnAgentClosing(eventContext: AgentClosingContext) {
        delegate.invokeOnAgentClosing(eventContext)
    }

    /**
     * Invoke handlers for an event when strategy starts execution.
     */
    internal actual open suspend fun invokeOnStrategyStarting(eventContext: StrategyStartingContext) {
        delegate.invokeOnStrategyStarting(eventContext)
    }

    /**
     * Invoke handlers for an event when a strategy finishes execution.
     */
    internal actual open suspend fun invokeOnStrategyCompleted(eventContext: StrategyCompletedContext) {
        delegate.invokeOnStrategyCompleted(eventContext)
    }

    /**
     * Invoke handlers for before a node in the agent's execution graph is processed event.
     */
    internal actual open suspend fun invokeOnNodeExecutionStarting(eventContext: NodeExecutionStartingContext) {
        delegate.invokeOnNodeExecutionStarting(eventContext)
    }

    /**
     * Invoke handlers for after a node in the agent's execution graph has been processed event.
     */
    internal actual open suspend fun invokeOnNodeExecutionCompleted(eventContext: NodeExecutionCompletedContext) {
        delegate.invokeOnNodeExecutionCompleted(eventContext)
    }

    internal actual open suspend fun invokeOnNodeExecutionFailed(interceptContext: NodeExecutionFailedContext) {
        delegate.invokeOnNodeExecutionFailed(interceptContext)
    }

    internal actual open suspend fun invokeOnLLMCallStarting(eventContext: LLMCallStartingContext) {
        delegate.invokeOnLLMCallStarting(eventContext)
    }

    internal actual open suspend fun invokeOnLLMCallCompleted(eventContext: LLMCallCompletedContext) {
        delegate.invokeOnLLMCallCompleted(eventContext)
    }

    internal actual open suspend fun invokeOnToolCallStarting(eventContext: ToolCallStartingContext) {
        delegate.invokeOnToolCallStarting(eventContext)
    }

    internal actual open suspend fun invokeOnToolValidationFailed(eventContext: ToolValidationFailedContext) {
        delegate.invokeOnToolValidationFailed(eventContext)
    }

    internal actual open suspend fun invokeOnToolCallFailed(eventContext: ToolCallFailedContext) {
        delegate.invokeOnToolCallFailed(eventContext)
    }

    internal actual open suspend fun invokeOnToolCallCompleted(eventContext: ToolCallCompletedContext) {
        delegate.invokeOnToolCallCompleted(eventContext)
    }

    internal actual open suspend fun invokeOnLLMStreamingStarting(eventContext: LLMStreamingStartingContext) {
        delegate.invokeOnLLMStreamingStarting(eventContext)
    }

    internal actual open suspend fun invokeOnLLMStreamingFrameReceived(eventContext: LLMStreamingFrameReceivedContext) {
        delegate.invokeOnLLMStreamingFrameReceived(eventContext)
    }

    internal actual open suspend fun invokeOnLLMStreamingFailed(eventContext: LLMStreamingFailedContext) {
        delegate.invokeOnLLMStreamingFailed(eventContext)
    }

    internal actual open suspend fun invokeOnLLMStreamingCompleted(eventContext: LLMStreamingCompletedContext) {
        delegate.invokeOnLLMStreamingCompleted(eventContext)
    }

    internal actual open suspend fun invokeOnSubgraphExecutionStarting(eventContext: SubgraphExecutionStartingContext) {
        delegate.invokeOnSubgraphExecutionStarting(eventContext)
    }

    internal actual open suspend fun invokeOnSubgraphExecutionCompleted(eventContext: SubgraphExecutionCompletedContext) {
        delegate.invokeOnSubgraphExecutionCompleted(eventContext)
    }

    internal actual open suspend fun invokeOnSubgraphExecutionFailed(interceptContext: SubgraphExecutionFailedContext) {
        delegate.invokeOnSubgraphExecutionFailed(interceptContext)
    }

    public actual open fun onSubgraphExecutionStarting(handler: suspend (SubgraphExecutionStartingContext) -> Unit) {
        delegate.onSubgraphExecutionStarting(handler)
    }

    public actual open fun onSubgraphExecutionCompleted(handler: suspend (SubgraphExecutionCompletedContext) -> Unit) {
        delegate.onSubgraphExecutionCompleted(handler)
    }

    public actual open fun onSubgraphExecutionFailed(handler: suspend (SubgraphExecutionFailedContext) -> Unit) {
        delegate.onSubgraphExecutionFailed(handler)
    }
}
