package ai.koog.agents.core.feature.handler.tool

import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents the context for handling tool-specific events within the framework.
 */
public interface ToolCallEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling a tool call event.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The tool name that is being executed;
 * @property toolArgs The arguments provided for the tool execution, adhering to the tool's expected input structure.
 */
public data class ToolCallStartingContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallStarting
}

/**
 * Represents the context for handling validation errors that occur during the execution of a tool.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The name of the tool associated with the validation error;
 * @property toolArgs The arguments passed to the tool when the error occurred;
 * @property error The error message describing the validation issue.
 */
public data class ToolValidationFailedContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val message: String,
    val error: ToolException
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolValidationFailed
}

/**
 * Represents the context provided to handle a failure during the execution of a tool.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The name of the tool being executed when the failure occurred;
 * @property toolArgs The arguments that were passed to the tool during execution;
 * @property message A message describing the failure that occurred.
 * @property exception The [Throwable] instance describing the tool call failure.
 */
public data class ToolCallFailedContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val message: String,
    val exception: Throwable?
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallFailed
}

/**
 * Represents the context used when handling the result of a tool call.
 *
 * @property executionInfo The execution information containing id, parentId, and execution path.
 * @property runId The unique identifier for this tool call session;
 * @property toolCallId The unique identifier for this tool call;
 * @property toolName The name of the tool being executed;
 * @property toolArgs The arguments required by the tool for execution;
 * @property toolResult An optional result produced by the tool after execution can be null if not applicable.
 */
public data class ToolCallCompletedContext(
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val toolResult: JsonElement?
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallCompleted
}
