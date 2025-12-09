package ai.koog.prompt.executor.clients

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.buildStructuredRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Executes a prompt and parses the response into a structured output of type [T].
 *
 * @param prompt The prompt to be executed.
 * @param model The language model to execute the request.
 * @param examples Optional list of example objects of type [T] to guide the model's structured output generation.
 * @return A [StructuredResponse] containing the parsed structured output of type [T].
 */
public suspend inline fun <reified T> LLMClient.executeStructured(
    prompt: Prompt,
    model: LLModel,
    examples: List<T> = emptyList(),
): StructuredResponse<T> {
    return executeStructured(
        prompt = prompt,
        model = model,
        serializer = serializer<T>(),
        examples = examples,
    )
}

/**
 * Executes a prompt and returns a structured response parsed into the given format.
 * This method utilizes schema generation and the model's capabilities to enhance the prompt
 * with structured output instructions.
 *
 * @param T The type of the structure to be returned.
 * @param prompt The prompt to execute against the model.
 * @param model The LLM model to use for execution.
 * @param serializer A serializer for the type `T` to parse the structured response.
 * @param examples Optional list of example instances of type `T` to guide the model's response.
 * @return A [StructuredResponse] containing the parsed structured data or failure details.
 */
public suspend fun <T> LLMClient.executeStructured(
    prompt: Prompt,
    model: LLModel,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList(),
): StructuredResponse<T> {
    val structuredRequest = buildStructuredRequest(
        model = model,
        serializer = serializer,
        examples = examples,
        standardJsonSchemaGenerator = getStandardJsonSchemaGenerator(model),
        basicJsonSchemaGenerator = getBasicJsonSchemaGenerator(model),
    )

    return executeStructured(
        prompt = prompt,
        model = model,
        structuredRequest = structuredRequest,
    )
}
