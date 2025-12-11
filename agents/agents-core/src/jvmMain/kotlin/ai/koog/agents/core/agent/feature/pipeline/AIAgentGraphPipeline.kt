@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import kotlinx.coroutines.future.await
import kotlinx.datetime.Clock
import kotlin.reflect.KType

public actual open class AIAgentGraphPipeline actual constructor(clock: Clock) : AIAgentPipeline(clock) {
    private val graphPipelineDelegate = AIAgentGraphPipelineImpl(clock)

    /**
     * Intercepts node execution before it starts.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-node events.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionStarting(feature, eventContext -> {
     *     logger.info("Node " + eventContext.getNode().getName()
     *         + " is about to execute with input: " + eventContext.getInput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: AsyncInterceptor<NodeExecutionStartingContext>
    ) {
        interceptNodeExecutionStarting(feature) {
            handle.intercept(it).await()
        }
    }

    /**
     * Intercepts node execution after it is successfully completed.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-node completion events.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionCompleted(feature, eventContext -> {
     *     logger.info("Node " + eventContext.getNode().getName()
     *         + " completed with output: " + eventContext.getOutput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: AsyncInterceptor<NodeExecutionCompletedContext>
    ) {
        interceptNodeExecutionCompleted(feature) {
            handle.intercept(it).await()
        }
    }

    /**
     * Intercepts node execution when it fails.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes node-failure events.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionFailed(feature, eventContext -> {
     *     logger.warn("Node " + eventContext.getNode().getName()
     *         + " failed with error: " + eventContext.getThrowable().getMessage());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: AsyncInterceptor<NodeExecutionFailedContext>
    ) {
        interceptNodeExecutionFailed(feature) {
            handle.intercept(it).await()
        }
    }

    /**
     * Intercepts subgraph execution before it starts.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-subgraph events.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionStarting(feature, eventContext -> {
     *     logger.info("Subgraph " + eventContext.getSubgraph().getName()
     *         + " is about to execute with input: " + eventContext.getInput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: AsyncInterceptor<SubgraphExecutionStartingContext>
    ) {
        interceptSubgraphExecutionStarting(feature) {
            handle.intercept(it).await()
        }
    }

    /**
     * Intercepts subgraph execution after it is successfully completed.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes subgraph-completion events.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionCompleted(feature, eventContext -> {
     *     logger.info("Subgraph " + eventContext.getSubgraph().getName()
     *         + " completed with output: " + eventContext.getOutput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: AsyncInterceptor<SubgraphExecutionCompletedContext>
    ) {
        interceptSubgraphExecutionCompleted(feature) {
            handle.intercept(it).await()
        }
    }

    /**
     * Intercepts subgraph execution when it fails.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes subgraph-failure events.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionFailed(feature, eventContext -> {
     *     logger.warn("Subgraph " + eventContext.getSubgraph().getName()
     *         + " failed with error: " + eventContext.getThrowable().getMessage());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptSubgraphExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: AsyncInterceptor<SubgraphExecutionFailedContext>
    ) {
        interceptSubgraphExecutionFailed(feature) {
            handle.intercept(it).await()
        }
    }

    public actual open fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AIAgentGraphFeature<TConfig, TFeatureImpl>,
        configure: TConfig.() -> Unit,
    ) {
        graphPipelineDelegate.install(feature, configure)
    }

    //region Trigger Node Handlers

    public actual open suspend fun onNodeExecutionStarting(
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    ) {
        graphPipelineDelegate.onNodeExecutionStarting(executionInfo, node, context, input, inputType)
    }

    public actual open suspend fun onNodeExecutionCompleted(
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType,
    ) {
        graphPipelineDelegate.onNodeExecutionCompleted(
            executionInfo,
            node,
            context,
            input,
            inputType,
            output,
            outputType
        )
    }

    public actual open suspend fun onNodeExecutionFailed(
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    ) {
        graphPipelineDelegate.onNodeExecutionFailed(executionInfo, node, context, input, inputType, throwable)
    }

    //endregion Trigger Node Handlers

    //region Interceptors

    public actual open suspend fun onSubgraphExecutionStarting(
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    ) {
        graphPipelineDelegate.onSubgraphExecutionStarting(executionInfo, subgraph, context, input, inputType)
    }

    public actual open suspend fun onSubgraphExecutionCompleted(
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType,
    ) {
        graphPipelineDelegate.onSubgraphExecutionCompleted(
            executionInfo,
            subgraph,
            context,
            input,
            inputType,
            output,
            outputType
        )
    }

    public actual open suspend fun onSubgraphExecutionFailed(
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    ) {
        graphPipelineDelegate.onSubgraphExecutionFailed(executionInfo, subgraph, context, input, inputType, throwable)
    }

    public actual open fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionStartingContext) -> Unit
    ) {
        graphPipelineDelegate.interceptNodeExecutionStarting(feature, handle)
    }

    public actual open fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionCompletedContext) -> Unit
    ) {
        graphPipelineDelegate.interceptNodeExecutionCompleted(feature, handle)
    }

    public actual open fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionFailedContext) -> Unit
    ) {
        graphPipelineDelegate.interceptNodeExecutionFailed(feature, handle)
    }

    public actual open fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit
    ) {
        graphPipelineDelegate.interceptSubgraphExecutionStarting(feature, handle)
    }

    public actual open fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit
    ) {
        graphPipelineDelegate.interceptSubgraphExecutionCompleted(feature, handle)
    }

    public actual open fun interceptSubgraphExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit
    ) {
        graphPipelineDelegate.interceptSubgraphExecutionFailed(feature, handle)
    }

    //endregion Interceptors
}
