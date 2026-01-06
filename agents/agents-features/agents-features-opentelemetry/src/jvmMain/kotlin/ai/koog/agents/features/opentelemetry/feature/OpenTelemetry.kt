package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.ModerationResponseEvent
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.ExecuteToolSpan
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan
import ai.koog.agents.features.opentelemetry.span.InvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.NodeExecuteSpan
import ai.koog.agents.features.opentelemetry.span.SpanCollector
import ai.koog.agents.features.opentelemetry.span.SpanEndStatus
import ai.koog.agents.features.opentelemetry.span.StrategySpan
import ai.koog.agents.features.opentelemetry.span.SubgraphExecuteSpan
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import kotlin.reflect.KType

/**
 * Represents the OpenTelemetry integration feature for tracking and managing spans and contexts
 * within the AI Agent framework. This class manages the lifecycle of spans for various operations,
 * including agent executions, node processing, LLM calls, and tool calls.
 */
public class OpenTelemetry {

    /**
     * Companion object implementing agent feature, handling [OpenTelemetry] creation and installation.
     */
    public companion object Feature : AIAgentGraphFeature<OpenTelemetryConfig, OpenTelemetry> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(): OpenTelemetryConfig {
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentGraphPipeline,
        ): OpenTelemetry {
            val openTelemetry = OpenTelemetry()
            val tracer = config.tracer
            val spanCollector = SpanCollector(tracer = tracer, verbose = config.isVerbose)
            val spanAdapter = config.spanAdapter

            //region Agent

            pipeline.interceptAgentStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before agent started handler" }

                // Create CreateAgentSpan
                val createAgentSpan = CreateAgentSpan(
                    parentSpan = null,
                    id = eventContext.eventId,
                    name = eventContext.context.agentId,
                    model = eventContext.agent.agentConfig.model,
                    agentId = eventContext.context.agentId
                )

                spanAdapter?.onBeforeSpanStarted(createAgentSpan)
                spanCollector.startSpan(createAgentSpan, patchExecutionInfo(eventContext.executionInfo, null))

                // Create InvokeAgentSpan
                val invokeAgentSpan = InvokeAgentSpan(
                    parentSpan = createAgentSpan,
                    id = eventContext.eventId,
                    name = eventContext.agent.id,
                    provider = eventContext.agent.agentConfig.model.provider,
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                )

                spanAdapter?.onBeforeSpanStarted(invokeAgentSpan)
                // Patch the agent execution info to include runId in the path.
                // This is required to create a path structure that matches the span structure in the OTel feature.
                spanCollector.startSpan(invokeAgentSpan, patchExecutionInfo(eventContext.executionInfo, eventContext.runId))
            }

            pipeline.interceptAgentCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry agent finished handler" }

                // Find parent span - InvokeAgentSpan
                val invokeAgentSpan = spanCollector.getStartedSpan<InvokeAgentSpan>(eventContext, eventContext.runId) ?: return@intercept

                spanAdapter?.onBeforeSpanFinished(invokeAgentSpan)
                spanCollector.endSpan(span = invokeAgentSpan)
            }

            pipeline.interceptAgentExecutionFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry agent run error handler" }

                // Finish current InvokeAgentSpan
                val invokeAgentSpan = spanCollector.getStartedSpan<InvokeAgentSpan>(eventContext, eventContext.runId) ?: return@intercept

                invokeAgentSpan.addAttribute(
                    attribute = SpanAttributes.Response.FinishReasons(
                        listOf(SpanAttributes.Response.FinishReasonType.Error)
                    )
                )

                spanAdapter?.onBeforeSpanFinished(invokeAgentSpan)
                spanCollector.endSpan(
                    span = invokeAgentSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.throwable.message)
                )
            }

            pipeline.interceptAgentClosing(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before agent closed handler" }

                // Stop all unfinished spans, except the AgentCreateSpan
                spanCollector.endUnfinishedSpans { span ->
                    span !is CreateAgentSpan &&
                    span.id != eventContext.eventId
                }

                // Stop agent create span
                val agentSpan = spanCollector.getStartedSpan<CreateAgentSpan>(eventContext, null) ?: return@intercept

                spanAdapter?.onBeforeSpanFinished(agentSpan)
                spanCollector.endSpan(span = agentSpan)

                // Just in case we miss some spans, stop them as well
                if (spanCollector.activeSpansCount > 0) {
                    logger.warn { "Found <${spanCollector.activeSpansCount}> active span(s) after agent closing. Stopping them." }
                    spanCollector.endUnfinishedSpans()
                }
            }

            //endregion Agent

            //region Strategy

            pipeline.interceptStrategyStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before subgraph handler" }

                val parentSpan = spanCollector.getParentSpanForEvent<InvokeAgentSpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                ) ?: return@intercept

                val strategySpan = StrategySpan(
                    parentSpan = parentSpan,
                    id = eventContext.executionInfo.path(),
                    name = eventContext.strategy.name,
                    runId = eventContext.context.runId,
                    strategyName = eventContext.strategy.name
                )

                spanAdapter?.onBeforeSpanStarted(strategySpan)
                spanCollector.startSpan(strategySpan, eventContext.executionInfo)
            }

            pipeline.interceptStrategyCompleted(this) intercept@{ eventContext ->

                // Find current Strategy Span
                val strategySpan = spanCollector.getStartedSpan<StrategySpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                ) ?: return@intercept

                spanAdapter?.onBeforeSpanFinished(strategySpan)
                spanCollector.endSpan(span = strategySpan)
            }

            //endregion Strategy

            //region Node

            pipeline.interceptNodeExecutionStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry node starting handler" }

                val parentSpan = spanCollector.getParentSpanForEvent<GenAIAgentSpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                ) ?: return@intercept

                val nodeInput = nodeDataToString(eventContext.input, eventContext.inputType)

                val nodeExecuteSpan = NodeExecuteSpan(
                    parentSpan = parentSpan,
                    id = eventContext.executionInfo.path(),
                    name = eventContext.node.name,
                    runId = eventContext.context.runId,
                    nodeId = eventContext.node.id,
                    nodeInput = nodeInput
                )

                spanAdapter?.onBeforeSpanStarted(nodeExecuteSpan)
                spanCollector.startSpan(nodeExecuteSpan, eventContext.executionInfo)
            }

            pipeline.interceptNodeExecutionCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry node completed handler" }

                // Find existing span (Node Execute Span)
                val nodeExecuteSpan = spanCollector.getStartedSpan<NodeExecuteSpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                ) ?: return@intercept

                val attributesToAdd = buildList {
                    nodeDataToString(eventContext.output, eventContext.outputType)?.let { nodeOutput ->
                        add(CustomAttribute(key = "koog.node.output", value = HiddenString(nodeOutput)))
                    }
                }

                nodeExecuteSpan.addAttributes(attributesToAdd)

                spanAdapter?.onBeforeSpanFinished(nodeExecuteSpan)
                spanCollector.endSpan(nodeExecuteSpan)
            }

            pipeline.interceptNodeExecutionFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry node execution failed handler" }

                // Find existing span (Node Execute Span)
                val nodeExecuteSpan = spanCollector.getStartedSpan<NodeExecuteSpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                ) ?: return@intercept

                spanAdapter?.onBeforeSpanFinished(nodeExecuteSpan)
                spanCollector.endSpan(
                    span = nodeExecuteSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.throwable.message)
                )
            }

            //endregion Node

            //region Subgraph

            pipeline.interceptSubgraphExecutionStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before subgraph handler" }

                val parentSpan = spanCollector.getParentSpanForEvent<GenAIAgentSpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                ) ?: return@intercept

                val subgraphInput = nodeDataToString(eventContext.input, eventContext.inputType)

                val subgraphExecuteSpan = SubgraphExecuteSpan(
                    parentSpan = parentSpan,
                    id = eventContext.executionInfo.path(),
                    name = eventContext.subgraph.name,
                    runId = eventContext.context.runId,
                    subgraphId = eventContext.subgraph.id,
                    subgraphInput = subgraphInput
                )

                spanAdapter?.onBeforeSpanStarted(subgraphExecuteSpan)
                spanCollector.startSpan(subgraphExecuteSpan, eventContext.executionInfo)
            }

            pipeline.interceptSubgraphExecutionCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry after subgraph handler" }

                // Find the existing span (Subgraph Execute Span)
                val subgraphExecuteSpan = spanCollector.getStartedSpan<SubgraphExecuteSpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                )
                    ?: return@intercept

                val attributesToAdd = buildList {
                    nodeDataToString(eventContext.output, eventContext.outputType)?.let { nodeOutput ->
                        add(CustomAttribute(key = "koog.subgraph.output", value = HiddenString(nodeOutput)))
                    }
                }

                subgraphExecuteSpan.addAttributes(attributesToAdd)

                spanAdapter?.onBeforeSpanFinished(subgraphExecuteSpan)
                spanCollector.endSpan(subgraphExecuteSpan)
            }

            pipeline.interceptSubgraphExecutionFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry subgraph execution error handler" }

                // Find the existing span (Subgraph Execute Span)
                val subgraphExecuteSpan = spanCollector.getStartedSpan<SubgraphExecuteSpan>(
                    eventContext = eventContext,
                    runId = eventContext.context.runId
                ) ?: return@intercept

                spanAdapter?.onBeforeSpanFinished(subgraphExecuteSpan)
                spanCollector.endSpan(
                    span = subgraphExecuteSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.throwable.message)
                )
            }

            //endregion Subgraph

            //region LLM Call

            pipeline.interceptLLMCallStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before LLM call handler" }

                val provider = eventContext.model.provider
                val parentSpan = spanCollector.getParentSpanForEvent<NodeExecuteSpan>(
                    eventContext = eventContext,
                    runId = eventContext.runId
                ) ?: return@intercept

                val inferenceSpan = InferenceSpan(
                    parentSpan = parentSpan,
                    id = eventContext.executionInfo.path(),
                    name = "inference",
                    provider = eventContext.model.provider,
                    runId = eventContext.runId,
                    model = eventContext.model,
                    content = eventContext.prompt.messages.joinToString("\n") { it.toString() },
                    temperature = eventContext.prompt.params.temperature,
                    maxTokens = eventContext.prompt.params.maxTokens
                )

                // Add events to the InferenceSpan after the span is created
                val eventsFromMessages = eventContext.prompt.messages.map { message ->
                    when (message) {
                        is Message.System -> {
                            SystemMessageEvent(provider, message)
                        }

                        is Message.User -> {
                            UserMessageEvent(provider, message)
                        }

                        is Message.Assistant, is Message.Reasoning -> {
                            AssistantMessageEvent(provider, message)
                        }

                        is Message.Tool.Call -> {
                            ChoiceEvent(provider, message, arguments = message.contentJson)
                        }

                        is Message.Tool.Result -> {
                            ToolMessageEvent(
                                provider = provider,
                                toolCallId = message.id,
                                content = message.content
                            )
                        }
                    }
                }

                inferenceSpan.addEvents(eventsFromMessages)

                // Start span
                spanAdapter?.onBeforeSpanStarted(inferenceSpan)
                spanCollector.startSpan(inferenceSpan, eventContext.executionInfo)
            }

            pipeline.interceptLLMCallCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry after LLM call handler" }

                // Find the current span (Inference Span)
                val inferenceSpan = spanCollector.getStartedSpan<InferenceSpan>(
                    eventContext = eventContext,
                    runId = eventContext.runId
                ) ?: return@intercept

                val provider = eventContext.model.provider

                // Add attributes to the InferenceSpan before finishing the span
                val attributesToAdd = buildList {
                    eventContext.responses.lastOrNull()?.let { message ->
                        message.metaInfo.inputTokensCount?.let { inputTokensCount ->
                            add(SpanAttributes.Usage.InputTokens(inputTokensCount))
                        }
                        message.metaInfo.outputTokensCount?.let { outputTokensCount ->
                            add(SpanAttributes.Usage.OutputTokens(outputTokensCount))
                        }
                        message.metaInfo.totalTokensCount?.let { totalTokensCount ->
                            add(SpanAttributes.Usage.TotalTokens(totalTokensCount))
                        }
                    }
                }

                inferenceSpan.addAttributes(attributesToAdd)

                // Add events to the InferenceSpan before finishing the span
                val eventsToAdd = buildList {
                    eventContext.responses.mapIndexed { index, message ->
                        when (message) {
                            is Message.Assistant, is Message.Reasoning -> {
                                add(AssistantMessageEvent(provider, message))
                            }

                            is Message.Tool.Call -> {
                                add(ChoiceEvent(provider, message, arguments = message.contentJson, index = index))
                            }
                        }
                    }

                    eventContext.moderationResponse?.let { response ->
                        add(ModerationResponseEvent(provider, response))
                    }
                }

                inferenceSpan.addEvents(eventsToAdd)

                // Add attributes to InferenceSpan

                // Finish Reasons Attribute
                eventContext.responses.lastOrNull()?.let { message ->
                    val finishReasonsAttribute = when (message) {
                        is Message.Assistant, is Message.Reasoning -> {
                            SpanAttributes.Response.FinishReasons(reasons = listOf(SpanAttributes.Response.FinishReasonType.Stop))
                        }

                        is Message.Tool.Call -> {
                            SpanAttributes.Response.FinishReasons(reasons = listOf(SpanAttributes.Response.FinishReasonType.ToolCalls))
                        }
                    }

                    inferenceSpan.addAttribute(finishReasonsAttribute)
                }

                // Stop InferenceSpan
                spanAdapter?.onBeforeSpanFinished(inferenceSpan)
                spanCollector.endSpan(inferenceSpan)
            }

            //endregion LLM Call

            //region Tool Call

            pipeline.interceptToolCallStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool call handler" }

                val parentSpan = spanCollector.getParentSpanForEvent<NodeExecuteSpan>(
                    eventContext = eventContext,
                    runId = eventContext.runId
                ) ?: return@intercept

                val executeToolSpan = ExecuteToolSpan(
                    parentSpan = parentSpan,
                    id = eventContext.executionInfo.path(),
                    name = eventContext.toolName,
                    toolName = eventContext.toolName,
                    toolArgs = eventContext.toolArgs.toString(),
                    toolDescription = eventContext.toolDescription,
                    toolCallId = eventContext.toolCallId
                )

                spanAdapter?.onBeforeSpanStarted(executeToolSpan)
                spanCollector.startSpan(executeToolSpan, eventContext.executionInfo)
            }

            pipeline.interceptToolCallCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool result handler" }

                // Get the current ExecuteToolSpan
                val executeToolSpan = spanCollector.getStartedSpan<ExecuteToolSpan>(
                    eventContext = eventContext,
                    runId = eventContext.runId
                ) ?: return@intercept

                eventContext.toolDescription?.let { toolDescription ->
                    executeToolSpan.addAttribute(
                        // gen_ai.tool.description
                        attribute = SpanAttributes.Tool.Description(description = toolDescription)
                    )
                }

                // End the ExecuteToolSpan span
                eventContext.toolResult?.let { result ->
                    executeToolSpan.addAttribute(
                        // gen_ai.tool.output.value
                        attribute = SpanAttributes.Tool.OutputValue(result.toString())
                    )
                }

                eventContext.toolDescription?.let { description ->
                    executeToolSpan.addAttribute(
                        attribute = SpanAttributes.Tool.Description(description)
                    )
                }

                spanAdapter?.onBeforeSpanFinished(span = executeToolSpan)
                spanCollector.endSpan(span = executeToolSpan)
            }

            pipeline.interceptToolCallFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool call failure handler" }

                // Get the current ExecuteToolSpan using executionInfo.path()
                val executeToolSpan = spanCollector.getStartedSpan<ExecuteToolSpan>(
                    eventContext = eventContext,
                    runId = eventContext.runId
                ) ?: return@intercept

                eventContext.toolDescription?.let { toolDescription ->
                    executeToolSpan.addAttribute(
                        // gen_ai.tool.description
                        attribute = SpanAttributes.Tool.Description(description = toolDescription)
                    )
                }

                executeToolSpan.addAttribute(
                    attribute = CommonAttributes.Error.Type(eventContext.message)
                )

                eventContext.toolDescription?.let { description ->
                    executeToolSpan.addAttribute(
                        attribute = SpanAttributes.Tool.Description(description)
                    )
                }

                // End the ExecuteToolSpan span
                spanAdapter?.onBeforeSpanFinished(executeToolSpan)
                spanCollector.endSpan(
                    span = executeToolSpan,
                    spanEndStatus = SpanEndStatus(
                        code = StatusCode.ERROR,
                        description = eventContext.error?.message
                    )
                )
            }

            pipeline.interceptToolValidationFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool validation error handler" }

                // Get the current ExecuteToolSpan using executionInfo.path()
                val executeToolSpan = spanCollector.getStartedSpan<ExecuteToolSpan>(
                    eventContext = eventContext,
                    runId = eventContext.runId
                ) ?: return@intercept

                eventContext.toolDescription?.let { toolDescription ->
                    executeToolSpan.addAttribute(
                        // gen_ai.tool.description
                        attribute = SpanAttributes.Tool.Description(description = toolDescription)
                    )
                }

                executeToolSpan.addAttribute(
                    attribute = CommonAttributes.Error.Type(eventContext.message)
                )

                eventContext.toolDescription?.let { description ->
                    executeToolSpan.addAttribute(
                        attribute = SpanAttributes.Tool.Description(description)
                    )
                }

                // End the ExecuteToolSpan span
                spanAdapter?.onBeforeSpanFinished(executeToolSpan)
                spanCollector.endSpan(
                    span = executeToolSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.message)
                )
            }

            //endregion Tool Call

            return openTelemetry
        }

        //region Private Methods

        /**
         * Retrieves the [String] representation of the given data based on its type.
         */
        private fun nodeDataToString(data: Any?, dataType: KType): String? {
            data ?: return null

            @OptIn(InternalAgentsApi::class)
            return SerializationUtils.encodeDataToStringOrDefault(data, dataType)
        }

        /**
         * Retrieves a starting span that matches the specific execution information and event ID.
         *
         * @param eventContext The context of the agent lifecycle event.
         * @return The matching span if found, or null otherwise.
         */
        private inline fun <reified T : GenAIAgentSpan> SpanCollector.getStartedSpan(eventContext: AgentLifecycleEventContext, runId: String?): T? {
            val executionInfo = patchExecutionInfo(eventContext.executionInfo, runId)
            val eventId = eventContext.eventId

            return this.getSpanCatching<T>(path =  executionInfo) { node ->
                node.span.id == eventId
            }
        }

        /**
         * Retrieves the parent span for a given event context if available.
         * Checks if the event ID matches any child spans of the parent span node.
         *
         * @param eventContext The context of the agent lifecycle event.
         * @return The parent span associated with the event if found, or null if no matching parent is found.
         */
        private inline fun <reified T : GenAIAgentSpan> SpanCollector.getParentSpanForEvent(eventContext: AgentLifecycleEventContext, runId: String?): T? {
            // TODO: This should get closest parent.
            //  Not the exact match !!!
            val executionInfo = patchExecutionInfo(eventContext.executionInfo, runId)
            val eventId = eventContext.eventId

            val parentPath = executionInfo.parent ?: return null
            return this.getSpanCatching<T>(path = parentPath) filter@{ node ->
                if (node.children.isEmpty()) { return@filter true }
                node.children.any { it.span.id == eventId }
            }
        }

        /**
         * Patches the execution information of the current event context by injecting the given run ID
         * into the hierarchy of `AgentExecutionInfo` instances. This ensures that data gathered from the
         * agent execution into matches spans composition: Agent | Run | Strategy | ...
         *
         * @param runId The run identifier to be injected into the execution information hierarchy.
         *              Can be null for creating top level agent spans, @see [CreateAgentSpan].
         * @return The updated [AgentExecutionInfo] object, reflecting the injected run ID where applicable.
         */
        internal fun patchExecutionInfo(executionInfo: AgentExecutionInfo, runId: String?): AgentExecutionInfo {
            runId ?: return executionInfo

            fun update(info: AgentExecutionInfo): AgentExecutionInfo {
                // Top level path
                if (info.parent == null) {
                    // Inject the agent run id path to match the OTel span structure and handle the Invoke Agent Span.
                    return AgentExecutionInfo(info, runId)
                }

                // Update the parent and reconstruct the node.
                val updatedParent = update(info.parent!!)
                return AgentExecutionInfo(updatedParent, info.partName)
            }

            return update(executionInfo)
        }

        //endregion Private Methods
    }
}
