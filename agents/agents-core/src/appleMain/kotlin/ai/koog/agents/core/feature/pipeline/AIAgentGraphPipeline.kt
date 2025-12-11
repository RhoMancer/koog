@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

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
import kotlinx.datetime.Clock
import kotlin.reflect.KType

public actual open class AIAgentGraphPipeline actual constructor(clock: Clock) : AIAgentPipeline(clock) {
    private val graphPipelineDelegate = AIAgentGraphPipelineImpl(clock)

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
