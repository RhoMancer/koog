package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.ToolException
import kotlinx.serialization.Serializable

/**
 * Represents the possible result types for a tool operation.
 */
@Serializable
public sealed class ToolResultType {

    /**
     * Represents a successful result in the context of a tool operation.
     */
    public object Success : ToolResultType()

    /**
     * Represents a failure result in the context of a tool operation.
     *
     * @property error The [Exception] that caused the failure. It can be null if no specific throwable information is available.
     */
    public data class Failure(public val error: Exception?) : ToolResultType()

    /**
     * Represents a validation error result in the context of a tool operation.
     *
     * @property error The specific tool exception that describes the details of the validation failure.
     */
    public data class ValidationError(public val error: ToolException) : ToolResultType()
}
