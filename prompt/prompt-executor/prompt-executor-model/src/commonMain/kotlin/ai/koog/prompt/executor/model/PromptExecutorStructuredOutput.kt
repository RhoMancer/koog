package ai.koog.prompt.executor.model

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import ai.koog.prompt.structure.buildStructuredRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

public suspend fun <T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    config: StructuredRequestConfig<T>,
    fixingParser: StructureFixingParser? = null,
): StructuredResponse<T> {
    val response = executeStructured(
        prompt = prompt,
        model = model,
        structuredRequest = config.structuredRequest(model),
    )

    return parseResponseToStructuredResponse(
        response,
        config,
        fixingParser
    )
}

public suspend inline fun <reified T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null,
): StructuredResponse<T> {
    return executeStructured(
        prompt = prompt,
        model = model,
        serializer = serializer<T>(),
        examples = examples,
        fixingParser = fixingParser
    )
}

@OptIn(InternalStructuredOutputApi::class)
public suspend fun <T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null,
): StructuredResponse<T> {
    val request = buildStructuredRequest(
        model,
        serializer,
        examples,
        getStandardJsonSchemaGenerator(model),
        getBasicJsonSchemaGenerator(model)
    )

    val response = executeStructured(
        prompt = prompt,
        model = model,
        structuredRequest = request,
    )

    return parseResponseToStructuredResponse(
        response,
        StructuredRequestConfig(default = request),
        fixingParser
    )
}

public suspend fun <T> PromptExecutor.parseResponseToStructuredResponse(
    response: StructuredResponse<T>,
    config: StructuredRequestConfig<T>,
    fixingParser: StructureFixingParser? = null,
): StructuredResponse<T> {
    when (response) {
        is StructuredResponse.Success -> return response
        is StructuredResponse.Failure -> {
            if (fixingParser == null) return response

            val fixingStructure = config.structure(fixingParser.model)
            return fixingParser.parse(this, fixingStructure, response)
        }
    }
}
