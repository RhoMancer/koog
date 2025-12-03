package ai.koog.agents.core.feature.handler.streaming

import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame

/**
 * Represents the context for handling streaming-specific events within the framework.
 */
public interface LLMStreamingEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling a before-stream event.
 * This context is provided when streaming is about to begin.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this streaming session;
 * @property prompt The prompt that will be sent to the language model for streaming;
 * @property model The language model instance being used for streaming;
 * @property tools The list of tool descriptors available for the streaming call.
 */
public data class LLMStreamingStartingContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingStarting
    override val id: String get() = executionInfo.id
    override val parentId: String? get() = executionInfo.parentId
}

/**
 * Represents the context for handling individual stream frame events.
 * This context is provided when stream frames are sent out during the streaming process.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this streaming session;
 * @property prompt The prompt that was sent to the language model for streaming;
 * @property model The language model instance that was used for streaming;
 * @property streamFrame The individual stream frame containing partial response data from the LLM.
 */
public data class LLMStreamingFrameReceivedContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val streamFrame: StreamFrame,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFrameReceived
    override val id: String get() = executionInfo.id
    override val parentId: String? get() = executionInfo.parentId
}

/**
 * Represents the context for handling an error event during streaming.
 * This context is provided when an error occurs during streaming.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this streaming session.
 * @property prompt The prompt that was sent to the language model for streaming;
 * @property model The language model instance that was used for streaming;
 * @property exception The exception or error that occurred during streaming.
 */
public data class LLMStreamingFailedContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val exception: Throwable
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFailed
    override val id: String get() = executionInfo.id
    override val parentId: String? get() = executionInfo.parentId
}

/**
 * Represents the context for handling an after-stream event.
 * This context is provided when streaming is complete.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this streaming session;
 * @property prompt The prompt that was sent to the language model for streaming;
 * @property model The language model instance that was used for streaming;
 * @property tools The list of tool descriptors that were available for the streaming call.
 */
public data class LLMStreamingCompletedContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingCompleted
    override val id: String get() = executionInfo.id
    override val parentId: String? get() = executionInfo.parentId
}
