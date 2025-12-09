package ai.koog.prompt.structure

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StructuredRequestTest {

    @Serializable
    data class TestData(
        val name: String,
        val value: Int
    )

    private val testSerializer: KSerializer<TestData> = serializer()
    private val standardGenerator = StandardJsonSchemaGenerator.Default
    private val basicGenerator = BasicJsonSchemaGenerator.Default
    private val examples = listOf(TestData("example", 42))

    @Test
    fun `buildStructuredRequest should return Native with standard schema when model supports Standard JSON capability`() {
        val model = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4",
            capabilities = listOf(LLMCapability.Schema.JSON.Standard),
            contextLength = 128000L
        )

        val result = buildStructuredRequest(
            model = model,
            serializer = testSerializer,
            examples = examples,
            standardJsonSchemaGenerator = standardGenerator,
            basicJsonSchemaGenerator = basicGenerator
        )

        assertIs<StructuredRequest.Native<TestData>>(result)
        assertIs<JsonStructure<TestData>>(result.structure)
        assertEquals("TestData", result.structure.id)
    }

    @Test
    fun `buildStructuredRequest should return Native with basic schema when model supports only Basic JSON capability`() {
        val model = LLModel(
            provider = LLMProvider.Google,
            id = "gemini-1.5-pro",
            capabilities = listOf(LLMCapability.Schema.JSON.Basic),
            contextLength = 1000000L
        )

        val result = buildStructuredRequest(
            model = model,
            serializer = testSerializer,
            examples = examples,
            standardJsonSchemaGenerator = standardGenerator,
            basicJsonSchemaGenerator = basicGenerator
        )

        assertIs<StructuredRequest.Native<TestData>>(result)
        assertIs<JsonStructure<TestData>>(result.structure)
        assertEquals("TestData", result.structure.id)
    }

    @Test
    fun `buildStructuredRequest should prefer Standard over Basic when model supports both capabilities`() {
        val model = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4",
            capabilities = listOf(
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Tools
            ),
            contextLength = 128000L
        )

        val result = buildStructuredRequest(
            model = model,
            serializer = testSerializer,
            examples = examples,
            standardJsonSchemaGenerator = standardGenerator,
            basicJsonSchemaGenerator = basicGenerator
        )

        assertIs<StructuredRequest.Native<TestData>>(result)
        assertIs<JsonStructure<TestData>>(result.structure)
        assertEquals("TestData", result.structure.id)
    }

    @Test
    fun `buildStructuredRequest should return Manual when model supports no JSON schema capabilities`() {
        val model = LLModel(
            provider = LLMProvider.Meta,
            id = "llama-3.1-8b",
            capabilities = listOf(LLMCapability.Tools, LLMCapability.Temperature),
            contextLength = 128000L
        )

        val result = buildStructuredRequest(
            model = model,
            serializer = testSerializer,
            examples = examples,
            standardJsonSchemaGenerator = standardGenerator,
            basicJsonSchemaGenerator = basicGenerator
        )

        assertIs<StructuredRequest.Manual<TestData>>(result)
        assertIs<JsonStructure<TestData>>(result.structure)
        assertEquals("TestData", result.structure.id)
    }

    @Test
    fun `buildStructuredRequest should extract correct id from fully qualified serialName`() {
        @Serializable
        data class ComplexData(val field: String)

        val complexSerializer: KSerializer<ComplexData> = serializer()
        val model = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4",
            capabilities = listOf(LLMCapability.Schema.JSON.Standard),
            contextLength = 128000L
        )

        val result = buildStructuredRequest(
            model = model,
            serializer = complexSerializer,
            standardJsonSchemaGenerator = standardGenerator,
            basicJsonSchemaGenerator = basicGenerator
        )

        assertEquals("ComplexData", result.structure.id)
    }

    @Test
    fun `buildStructuredRequest should work with empty examples list`() {
        val model = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4",
            capabilities = listOf(LLMCapability.Schema.JSON.Standard),
            contextLength = 128000L
        )

        val result = buildStructuredRequest(
            model = model,
            serializer = testSerializer,
            examples = emptyList(),
            standardJsonSchemaGenerator = standardGenerator,
            basicJsonSchemaGenerator = basicGenerator
        )

        assertIs<StructuredRequest.Native<TestData>>(result)
        assertEquals("TestData", result.structure.id)
    }

    @Test
    fun `buildStructuredRequest should include examples for Manual structured request`() {
        val model = LLModel(
            provider = LLMProvider.Meta,
            id = "llama-3.1-8b",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 128000L
        )

        val result = buildStructuredRequest(
            model = model,
            serializer = testSerializer,
            examples = examples,
            standardJsonSchemaGenerator = standardGenerator,
            basicJsonSchemaGenerator = basicGenerator
        )

        assertIs<StructuredRequest.Manual<TestData>>(result)
        val jsonStructure = result.structure as JsonStructure<TestData>
        assertTrue(jsonStructure.examples.isNotEmpty())
        assertEquals(examples, jsonStructure.examples)
    }
}
