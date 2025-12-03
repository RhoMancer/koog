package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.ToolException
import kotlinx.serialization.Serializable

/**
 * Represents the possible result types for a tool operation.
 */
@Serializable
public sealed class ToolResultKind {

    /**
     * Represents a successful result in the context of a tool operation.
     */
    public object Success : ToolResultKind()

    /**
     * Represents a failure result in the context of a tool operation.
     *
     * @property exception The [Throwable] that caused the failure. It can be null if no specific throwable information is available.
     */
    public data class Failure(public val exception: Throwable?) : ToolResultKind()

    /**
     * Represents a validation error result in the context of a tool operation.
     *
     * @property exception The specific tool exception that describes the details of the validation failure.
     */
    public data class ValidationError(public val exception: ToolException) : ToolResultKind()
}
