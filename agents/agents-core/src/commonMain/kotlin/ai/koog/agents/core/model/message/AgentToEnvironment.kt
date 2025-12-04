package ai.koog.agents.core.model.message

import ai.koog.agents.core.model.AgentServiceError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a message sent from an agent to the environment.
 * This is a base interface for all communication from agents to their respective environments.
 * Each message under this interface is tied to a specific run identified by a universally unique identifier.
 */
@Serializable
public sealed interface AgentToEnvironmentMessage

/**
 * Marker interface for tool calls (single and multiple)
 */
@Serializable
public sealed interface AgentToolCallToEnvironmentMessage : AgentToEnvironmentMessage

/**
 * Content of tool call messages sent from the agent.
 *
 * @property toolName Name of the tool to call.
 * @property toolArgs Arguments for the called tool.
 * @property toolCallId The unique id to identify tool call when calling multiple tools at once.
 * Not all implementations support it, it will be `null` in this case.
 */
@Serializable
public data class AgentToolCallToEnvironmentContent(
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
)

/**
 * Represents a message sent from the server to the environment to perform multiple tool calls.
 *
 * @property content List of individual tool call requests, each containing details about
 * the agent, tool name, arguments, and an optional tool call identifier.
 */
@Serializable
public data class AgentToolCallsToEnvironmentMessage(
    val content: AgentToolCallToEnvironmentContent
) : AgentToolCallToEnvironmentMessage

/**
 * Represents an error response from the server.
 * These may occur for several reasons:
 *
 * - [Sending unsupported types of messages][ai.koog.agents.core.model.AgentServiceErrorType.UNEXPECTED_MESSAGE_TYPE];
 * - [Sending incorrect or incomplete messages][ai.koog.agents.core.model.AgentServiceErrorType.MALFORMED_MESSAGE];
 * - [Trying to use an agent that is not available][ai.koog.agents.core.model.AgentServiceErrorType.AGENT_NOT_FOUND];
 * - [Other, unexpected errors][ai.koog.agents.core.model.AgentServiceErrorType.UNEXPECTED_ERROR].
 *
 * @property error Error details.
 */
@Serializable
@SerialName("ERROR")
public data class AgentErrorToEnvironmentMessage(
    val error: AgentServiceError
) : AgentToEnvironmentMessage
