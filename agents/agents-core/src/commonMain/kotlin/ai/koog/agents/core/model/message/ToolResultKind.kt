package ai.koog.agents.core.model.message

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi

/**
 * Enum representing the possible result types for a tool execution.
 */
@InternalAgentToolsApi
internal enum class ToolResultKind {
    COMPLETED,
    FAILED,
    VALIDATION_ERROR
}
