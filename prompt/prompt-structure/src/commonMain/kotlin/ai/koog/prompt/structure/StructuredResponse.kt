package ai.koog.prompt.structure

import ai.koog.prompt.message.Message

/**
 * Represents a container for structured data parsed from a response message.
 *
 * This class is designed to encapsulate both the parsed structured output and the original raw
 * text as returned from a processing step, such as a language model execution.
 *
 * @param T The type of the structured data contained within this response.
 * @property structure The structure used for the response.
 * @property message The original assistant message from which the structure was parsed.
 */
public sealed interface StructuredResponse<T> {
    public val message: Message.Assistant?
    public val isSuccess: Boolean
    public val isFailure: Boolean

    public data class Success<T>(
        override val message: Message.Assistant,
        val data: T,
    ) : StructuredResponse<T> {
        override val isSuccess: Boolean = true
        override val isFailure: Boolean = false
    }

    public data class Failure<T>(
        override val message: Message.Assistant?,
        val exception: Exception,
    ) : StructuredResponse<T> {
        override val isSuccess: Boolean = false
        override val isFailure: Boolean = true
    }
}

/**
 * Returns the [StructuredResponse.Success] value if this is a [StructuredResponse.Success],
 * or throws an exception if this is a [StructuredResponse.Failure].
 */
public fun <T> StructuredResponse<T>.getOrThrow(): StructuredResponse.Success<T> {
    return when (this) {
        is StructuredResponse.Success -> this
        is StructuredResponse.Failure -> throw exception
    }
}

/**
 * Returns the [StructuredResponse.Success] value if this is a [StructuredResponse.Success],
 * or `null` if this is a [StructuredResponse.Failure].
 */
public fun <T> StructuredResponse<T>.getOrNull(): StructuredResponse.Success<T>? {
    return when (this) {
        is StructuredResponse.Success -> this
        is StructuredResponse.Failure -> null
    }
}

/**
 * Executes the given [block] if this is a [StructuredResponse.Success].
 * @return The original [StructuredResponse] instance.
 */
public fun <T> StructuredResponse<T>.onSuccess(block: (StructuredResponse.Success<T>) -> Unit): StructuredResponse<T> {
    if (this is StructuredResponse.Success) block(this)
    return this
}

/**
 * Executes the given [block] if this is a [StructuredResponse.Failure].
 * @return The original [StructuredResponse] instance.
 */
public fun <T> StructuredResponse<T>.onFailure(block: (StructuredResponse.Failure<T>) -> Unit): StructuredResponse<T> {
    if (this is StructuredResponse.Failure) block(this)
    return this
}

/** Parses the structured output from a model response.
 *
 * @param structure The structure of the structured output to be parsed.
 * @param response The list of model responses.
 * @return A [StructuredResponse] containing the parsed structured data or failure details.
 */
public fun <T> parseStructuredResponse(
    structure: Structure<T, *>,
    response: List<Message.Response>
): StructuredResponse<T> {
    val message = response
        .filterIsInstance<Message.Assistant>()
        .firstOrNull()

    try {
        requireNotNull(message) { "Response for structured output must be an assistant message" }
        return StructuredResponse.Success(
            message = message,
            data = structure.parse(message.content),
        )
    } catch (e: Exception) {
        return StructuredResponse.Failure(
            message = message,
            exception = StructuredOutputParsingException("Unable to parse structured output", e),
        )
    }
}
