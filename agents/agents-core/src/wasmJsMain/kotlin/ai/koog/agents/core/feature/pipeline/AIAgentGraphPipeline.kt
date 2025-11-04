package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import kotlinx.datetime.Clock
import kotlin.reflect.KType

/**
 * Represents a pipeline for AI agent graph execution, extending the functionality of `AIAgentPipeline`.
 * This class manages the execution of specific nodes in the pipeline using registered handlers.
 *
 * @property clock The clock used for time-based operations within the pipeline
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual open class AIAgentGraphPipeline actual constructor(clock: Clock) : AIAgentPipeline(clock) {
    private val graphPipelineDelegate = AIAgentGraphPipelineImpl(clock)

    /**
     * Provides access to the [Clock] used in the pipeline.
     */
    public actual open val clock: Clock get() = graphPipelineDelegate.clock


    /**
     * Installs a feature into the pipeline with the provided configuration.
     *
     * This method initializes the feature with a custom configuration and registers it in the pipeline.
     * The feature's message processors are initialized during installation.
     *
     * @param TConfig The type of the feature configuration
     * @param TFeature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public actual open fun <TConfig : FeatureConfig, TFeature : Any> install(
        feature: AIAgentGraphFeature<TConfig, TFeature>,
        configure: TConfig.() -> Unit
    ) {
        graphPipelineDelegate.install(feature, configure)
    }

    /**
     * Notifies all registered node handlers before a node is executed.
     *
     * @param node The node that is about to be executed
     * @param context The agent context in which the node is being executed
     * @param input The input data for the node execution
     * @param inputType The type of the input data provided to the node
     */
    public actual open suspend fun onNodeExecutionStarting(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContext,
        input: Any?,
        inputType: KType
    ) {
        graphPipelineDelegate.onNodeExecutionStarting(node, context, input, inputType)
    }

    /**
     * Notifies all registered node handlers after a node has been executed.
     *
     * @param node The node that was executed
     * @param context The agent context in which the node was executed
     * @param input The input data that was provided to the node
     * @param inputType The type of the input data provided to the node
     * @param output The output data produced by the node execution
     * @param outputType The type of the output data produced by the node execution
     */
    public actual open suspend fun onNodeExecutionCompleted(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContext,
        input: Any?,
        output: Any?,
        inputType: KType,
        outputType: KType
    ) {
        graphPipelineDelegate.onNodeExecutionCompleted(node, context, input, output, inputType, outputType)
    }

    /**
     * Handles errors occurring during the execution of a node by invoking all registered node execution error handlers.
     *
     * @param node The instance of the node where the error occurred.
     * @param context The context associated with the AI agent executing the node.
     * @param input The input data provided to the node.
     * @param inputType The type of the input data provided to the node.
     * @param throwable The exception or error that occurred during node execution.
     */
    public actual open suspend fun onNodeExecutionFailed(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContext,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    ) {
        graphPipelineDelegate.onNodeExecutionFailed(node, context, input, inputType, throwable)
    }

    /**
     * Intercepts node execution before it starts.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-node events
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionStarting(feature) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} is about to execute with input: ${eventContext.input}")
     * }
     * ```
     */
    public actual open fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (NodeExecutionStartingContext) -> Unit
    ) {
        graphPipelineDelegate.interceptNodeExecutionStarting(feature, handle)
    }

    /**
     * Intercepts node execution after it completes.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-node events
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionCompleted(feature) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} executed with input: ${eventContext.input} and produced output: ${eventContext.output}")
     * }
     * ```
     */
    public actual open fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (NodeExecutionCompletedContext) -> Unit
    ) {
        graphPipelineDelegate.interceptNodeExecutionCompleted(feature, handle)
    }

    /**
     * Intercepts and handles node execution errors for a given feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that processes the node execution error.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionFailed(feature) { eventContext ->
     *     logger.error("Node ${eventContext.node.name} execution failed with error: ${eventContext.throwable}")
     * }
     * ```
     */
    public actual open fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (NodeExecutionFailedContext) -> Unit
    ) {
        graphPipelineDelegate.interceptNodeExecutionFailed(feature, handle)
    }

}
