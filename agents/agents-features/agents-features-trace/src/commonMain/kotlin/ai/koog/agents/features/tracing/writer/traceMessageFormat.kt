package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyStartingEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.model.events.ToolValidationFailedEvent
import ai.koog.agents.features.tracing.traceString

@Suppress("UnusedReceiverParameter")
internal val FeatureMessage.featureMessageFormat
    get() = "Feature message"

@Suppress("UnusedReceiverParameter")
internal val FeatureEvent.featureEventFormat
    get() = "Feature event"

internal val FeatureStringMessage.featureStringMessageFormat
    get() = "Feature string message (message: $message)"

internal val AgentStartingEvent.agentStartingEventFormat
    get() = "${this::class.simpleName} (agent id: $agentId, run id: $runId)"

internal val AgentCompletedEvent.agentCompletedEventFormat
    get() = "${this::class.simpleName} (agent id: $agentId, run id: $runId, result: $result)"

internal val AgentExecutionFailedEvent.agentExecutionFailedEventFormat
    get() = "${this::class.simpleName} (agent id: $agentId, run id: $runId, error: ${error?.message})"

internal val AgentClosingEvent.agentClosingEventFormat
    get() = "${this::class.simpleName} (agent id: $agentId)"

internal val StrategyStartingEvent.strategyStartingEventFormat
    get() = "${this::class.simpleName} (run id: $runId, strategy: $strategyName)"

internal val StrategyCompletedEvent.strategyCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, strategy: $strategyName, result: $result)"

internal val NodeExecutionStartingEvent.nodeExecutionStartingEventFormat
    get() = "${this::class.simpleName} (run id: $runId, node: $nodeName, input: $input)"

internal val NodeExecutionCompletedEvent.nodeExecutionCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, node: $nodeName, input: $input, output: $output)"

internal val NodeExecutionFailedEvent.nodeExecutionFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, node: $nodeName, error: ${error.message})"

internal val SubgraphExecutionStartingEvent.subgraphExecutionStartingEventFormat
    get() = "${this::class.simpleName} (run id: $runId, subgraph: $subgraphName, input: $input)"

internal val SubgraphExecutionCompletedEvent.subgraphExecutionCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, subgraph: $subgraphName, input: $input, output: $output)"

internal val SubgraphExecutionFailedEvent.subgraphExecutionFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, subgraph: $subgraphName, error: ${error.message})"

internal val LLMCallStartingEvent.llmCallStartingEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, tools: [${tools.joinToString()}])"

internal val LLMCallCompletedEvent.llmCallCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, responses: [${responses.joinToString { "{${it.traceString}}" }}])"

internal val LLMStreamingStartingEvent.llmStreamingStartingEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, tools: [${tools.joinToString()}])"

internal val LLMStreamingFrameReceivedEvent.llmStreamingFrameReceivedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, frame: $frame)"

internal val LLMStreamingCompletedEvent.llmStreamingCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, tools: [${tools.joinToString()}])"

internal val LLMStreamingFailedEvent.llmStreamingFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, error: ${error.message})"

internal val ToolCallStartingEvent.toolCallStartingEventForamt
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs)"

internal val ToolValidationFailedEvent.toolValidationFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs, validation error: $error)"

internal val ToolCallFailedEvent.toolCallFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs, error: ${error?.message})"

internal val ToolCallCompletedEvent.toolCallCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs, result: $result)"

internal val FeatureMessage.traceMessage: String
    get() {
        return when (this) {
            is AgentStartingEvent -> this.agentStartingEventFormat
            is AgentCompletedEvent -> this.agentCompletedEventFormat
            is AgentExecutionFailedEvent -> this.agentExecutionFailedEventFormat
            is AgentClosingEvent -> this.agentClosingEventFormat

            is StrategyStartingEvent -> this.strategyStartingEventFormat
            is StrategyCompletedEvent -> this.strategyCompletedEventFormat

            is NodeExecutionStartingEvent -> this.nodeExecutionStartingEventFormat
            is NodeExecutionCompletedEvent -> this.nodeExecutionCompletedEventFormat
            is NodeExecutionFailedEvent -> this.nodeExecutionFailedEventFormat

            is SubgraphExecutionStartingEvent -> this.subgraphExecutionStartingEventFormat
            is SubgraphExecutionCompletedEvent -> this.subgraphExecutionCompletedEventFormat
            is SubgraphExecutionFailedEvent -> this.subgraphExecutionFailedEventFormat

            is LLMCallStartingEvent -> this.llmCallStartingEventFormat
            is LLMCallCompletedEvent -> this.llmCallCompletedEventFormat

            is LLMStreamingStartingEvent -> this.llmStreamingStartingEventFormat
            is LLMStreamingFrameReceivedEvent -> this.llmStreamingFrameReceivedEventFormat
            is LLMStreamingCompletedEvent -> this.llmStreamingCompletedEventFormat
            is LLMStreamingFailedEvent -> this.llmStreamingFailedEventFormat

            is ToolCallStartingEvent -> this.toolCallStartingEventForamt
            is ToolValidationFailedEvent -> this.toolValidationFailedEventFormat
            is ToolCallFailedEvent -> this.toolCallFailedEventFormat
            is ToolCallCompletedEvent -> this.toolCallCompletedEventFormat

            is FeatureStringMessage -> this.featureStringMessageFormat
            is FeatureEvent -> this.featureEventFormat

            else -> this.featureMessageFormat
        }
    }
