package ai.koog.prompt.structure

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.JsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.serialization.KSerializer

/**
 * Defines how structured outputs should be generated.
 *
 * Can be [StructuredRequest.Manual] or [StructuredRequest.Native]
 *
 * @param T The type of structured data.
 */
public sealed interface StructuredRequest<T> {
    /**
     * The definition of a structure.
     */
    public val structure: Structure<T, *>

    /**
     * Instructs the model to produce structured output through explicit prompting.
     *
     * Uses an additional user message containing [Structure.definition] to guide
     * the model in generating correctly formatted responses.
     *
     * @property structure The structure definition to be used in output generation.
     */
    public data class Manual<T>(override val structure: Structure<T, *>) : StructuredRequest<T>

    /**
     * Leverages a model's built-in structured output capabilities.
     *
     * Uses [Structure.schema] to define the expected response format through the model's
     * native structured output functionality.
     *
     * Note: [Structure.examples] are not used with this mode, only the schema is sent via parameters.
     *
     * @property structure The structure definition to be used in output generation.
     */
    public data class Native<T>(override val structure: Structure<T, *>) : StructuredRequest<T>
}

/**
 * Defines how structured outputs should be generated.
 *
 * @param model The model to be used for generating structured outputs.
 * @param serializer The serializer for the structured data type.
 * @param examples Optional list of examples to be used for schema generation.
 * @param standardJsonSchemaGenerator The generator for standard JSON schemas.
 * @param basicJsonSchemaGenerator The generator for basic JSON schemas.
 * @return A StructuredRequest instance representing the selected structured output mode.
 */
public fun <T> buildStructuredRequest(
    model: LLModel,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList(),
    standardJsonSchemaGenerator: StandardJsonSchemaGenerator,
    basicJsonSchemaGenerator: BasicJsonSchemaGenerator,
): StructuredRequest<T> {
    @Suppress("UNCHECKED_CAST")
    val id = serializer.descriptor.serialName.substringAfterLast(".")

    val structuredRequest = when {
        LLMCapability.Schema.JSON.Standard in model.capabilities -> StructuredRequest.Native(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = standardJsonSchemaGenerator
            )
        )

        LLMCapability.Schema.JSON.Basic in model.capabilities -> StructuredRequest.Native(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = basicJsonSchemaGenerator
            )
        )

        else -> StructuredRequest.Manual(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = StandardJsonSchemaGenerator,
                examples = examples,
            )
        )
    }

    return structuredRequest
}

/**
 * Configures structured output behavior.
 * Defines which structures in which modes should be used for each provider when requesting a structured output.
 *
 * @property default Fallback [StructuredRequest] to be used when there's no suitable structure found in [byProvider]
 * for a requested [LLMProvider]. Defaults to `null`, meaning structured output would fail with error in such a case.
 *
 * @property byProvider A map matching [LLMProvider] to compatible [StructuredRequest] definitions. Each provider may
 * require different schema formats. E.g. for [JsonStructure] this means you have to use the appropriate
 * [JsonSchemaGenerator] implementation for each provider for [StructuredRequest.Native], or fallback to [StructuredRequest.Manual]
 *
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 */
public data class StructuredRequestConfig<T>(
    public val default: StructuredRequest<T>? = null,
    public val byProvider: Map<LLMProvider, StructuredRequest<T>> = emptyMap(),
) {
    /**
     * Retrieves the structured data configuration for a specific large language model (LLM).
     *
     * The method determines the appropriate structured data setup based on the given LLM
     * instance, ensuring compatibility with the model's provider and capabilities.
     *
     * @param model The large language model (LLM) instance used to identify the structured data configuration.
     * @return The structured data configuration represented as a `StructuredData` instance.
     */
    public fun structure(model: LLModel): Structure<T, *> {
        return structuredRequest(model).structure
    }

    /**
     * Retrieves the structured output configuration for a specific large language model (LLM).
     *
     * The method determines the appropriate structured output instance based on the model's provider.
     * If no specific configuration is found for the provider, it falls back to the default configuration.
     * Throws an exception if no default configuration is available.
     *
     * @param model The large language model (LLM) used to identify the structured output configuration.
     * @return An instance of `StructuredOutput` that represents the structured output configuration for the model.
     * @throws IllegalArgumentException if no configuration is found for the provider and no default configuration is set.
     */
    public fun structuredRequest(model: LLModel): StructuredRequest<T> {
        return byProvider[model.provider]
            ?: default
            ?: throw IllegalArgumentException("No structure found for provider ${model.provider}")
    }
}

public fun requireStructuredOutputCapabilities(model: LLModel, request: StructuredRequest<*>) {
    if (request is StructuredRequest.Manual) return
    require(model.capabilities.any { it == LLMCapability.Schema.JSON.Standard || it == LLMCapability.Schema.JSON.Basic }) {
        "Model ${model.id} does not support structured output"
    }
}
