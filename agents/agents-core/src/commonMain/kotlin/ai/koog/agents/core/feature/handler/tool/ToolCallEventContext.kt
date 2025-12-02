package ai.koog.agents.core.feature.handler.tool

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolException
import kotlinx.serialization.json.JsonElement

/**
 * Represents the context for handling tool-specific events within the framework.
 */
public interface ToolCallEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling a tool call event.
 *
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The tool name that is being executed;
 * @property toolArgs The arguments provided for the tool execution, adhering to the tool's expected input structure.
 */
public data class ToolCallStartingContext(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonElement?
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallStarting
}

/**
 * Represents the context for handling validation errors that occur during the execution of a tool.
 *
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The name of the tool associated with the validation error;
 * @property toolArgs The arguments passed to the tool when the error occurred;
 * @property error The error message describing the validation issue.
 */
public data class ToolValidationFailedContext(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonElement,
    val message: String,
    val error: ToolException
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolValidationFailed
}

/**
 * Represents the context provided to handle a failure during the execution of a tool.
 *
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The name of the tool being executed when the failure occurred;
 * @property toolArgs The arguments that were passed to the tool during execution;
 * @property message A message describing the failure that occurred.
 * @property error The error message describing the tool call failure.
 */
public data class ToolCallFailedContext(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: Any?,
    val message: String,
    val error: Exception?
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallFailed
}

/**
 * Represents the context used when handling the result of a tool call.
 *
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The name of the tool being executed;
 * @property toolArgs The arguments required by the tool for execution;
 * @property toolResult An optional result produced by the tool after execution can be null if not applicable.
 */
public data class ToolCallCompletedContext(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: Any?,
    val toolResult: Any?
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallCompleted
}
