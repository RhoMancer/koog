import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredOutputPrompts.appendStructuredOutputInstructions
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.buildStructuredRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * This is a simple version of the full `executeStructured`. Unlike the full version, it does not require specifying
 * struct definitions and structured output modes manually. It attempts to find the best approach to provide a structured
 * output based on the defined [model] capabilities.
 *
 * For example, it chooses which JSON schema to use (simple or full) and with which mode (native or manual).
 *
 * @param T The structure to request.
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
 * understand the format better.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
public suspend inline fun <reified T> LLMClient.executeStructured(
    prompt: Prompt,
    model: LLModel,
    examples: List<T> = emptyList(),
): Result<StructuredResponse<T>> {
    return executeStructured(
        prompt = prompt,
        model = model,
        serializer = serializer<T>(),
        examples = examples,
    )
}

public suspend fun <T> LLMClient.executeStructured(
    prompt: Prompt,
    model: LLModel,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList(),
): Result<StructuredResponse<T>> {
    val structuredOutput = buildStructuredRequest(
        model = model,
        serializer = serializer,
        examples = examples,
        standardJsonSchemaGenerator = getStandardJsonSchemaGenerator(model),
        basicJsonSchemaGenerator = getBasicJsonSchemaGenerator(model),
    )
    return executeStructured(
        prompt = prompt,
        model = model,
        structuredRequest = structuredOutput,
    )
}

/**
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * **Note**: While many language models advertise support for structured output via JSON schema,
 * the actual level of support varies between models and even between versions
 * of the same model. Some models may produce malformed outputs or deviate from
 * the schema in subtle ways, especially with complex structures like polymorphic types.
 *
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param structuredRequest The structured output to request.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
public suspend fun <T> LLMClient.executeStructured(
    prompt: Prompt,
    model: LLModel,
    structuredRequest: StructuredRequest<T>,
): Result<StructuredResponse<T>> {
    val updatedPrompt = appendStructuredOutputInstructions(prompt, structuredRequest)
    val message = this.execute(prompt = updatedPrompt, model = model).single()

    return runCatching {
        require(message is Message.Assistant) { "Response for structured output must be an assistant message, got ${message::class.simpleName} instead" }
        StructuredResponse(
            data = structuredRequest.structure.parse(message.content),
            structure = structuredRequest.structure,
            message = message
        )
    }
}
