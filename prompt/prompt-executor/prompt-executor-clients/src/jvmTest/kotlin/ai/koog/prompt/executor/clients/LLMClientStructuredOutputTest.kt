package ai.koog.prompt.executor.clients

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.getOrThrow
import ai.koog.prompt.structure.json.JsonStructure
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LLMClientStructuredOutputTest {

    private val testModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "test-model",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Standard
        ),
        contextLength = 4096
    )

    private val modelWithoutStructuredOutput = LLModel(
        provider = LLMProvider.OpenAI,
        id = "test-model-no-structured",
        capabilities = listOf(
            LLMCapability.Completion
        ),
        contextLength = 4096
    )

    private val testPrompt = prompt("test-prompt") {
        system("Test system message")
        user("Test user message")
    }

    private val testMetaInfo = ResponseMetaInfo.create(Clock.System)

    private val testPerson = Person(
        name = "John",
        age = 20,
        children = listOf(
            Person(name = "Jane", age = 10)
        )
    )

    @Serializable
    data class Person(
        val name: String,
        val age: Int,
        val children: List<Person> = emptyList()
    )

    @Test
    fun `test structured output manual on correct response should succeed`() = runTest {
        val mockClient = mockk<LLMClient>(relaxed = true)

        coEvery { mockClient.execute(any(), any(), any()) } returns
            listOf(Message.Assistant(content = Json.encodeToString(testPerson), testMetaInfo))

        val response = mockClient.executeStructured(
            prompt = testPrompt,
            model = testModel,
            structuredRequest = StructuredRequest.Manual(JsonStructure.create<Person>())
        )

        assertTrue(response.isSuccess, "Structured output should be successfully resieaved")
        val structuredData = response.getOrThrow().data
        assertEquals(structuredData, testPerson)
    }

    @Test
    fun `test structured output native on correct response should succeed`() = runTest {
        val mockClient = mockk<LLMClient>()

        coEvery { mockClient.execute(any(), any(), any()) } returns
            listOf(Message.Assistant(content = Json.encodeToString(testPerson), testMetaInfo))

        val response = mockClient.executeStructured(
            prompt = testPrompt,
            model = testModel,
            structuredRequest = StructuredRequest.Native(JsonStructure.create<Person>())
        )

        assertTrue(response.isSuccess, "Structured output should be successfully resieaved")
        val structuredData = response.getOrThrow().data
        assertEquals(structuredData, testPerson)
    }

    @Test
    fun `test structured output manual with invalid JSON should fail`() = runTest {
        val mockClient = mockk<LLMClient>()

        coEvery { mockClient.execute(any(), any(), any()) } returns
            listOf(Message.Assistant(content = "invalid json content", testMetaInfo))

        val response = mockClient.executeStructured(
            prompt = testPrompt,
            model = testModel,
            structuredRequest = StructuredRequest.Manual(JsonStructure.create<Person>())
        )

        assertFalse(response.isSuccess, "Structured output should fail with invalid JSON")
    }

    @Test
    fun `test structured output native with invalid JSON should fail`() = runTest {
        val mockClient = mockk<LLMClient>()

        coEvery { mockClient.execute(any(), any(), any()) } returns
            listOf(Message.Assistant(content = "invalid json content", testMetaInfo))

        val response = mockClient.executeStructured(
            prompt = testPrompt,
            model = testModel,
            structuredRequest = StructuredRequest.Native(JsonStructure.create<Person>())
        )

        assertFalse(response.isSuccess, "Structured output should fail with invalid JSON")
    }

    @Test
    fun `test structured output native with model not supporting structured output should fall back to manual`() = runTest {
        val mockClient = mockk<LLMClient>()

        coEvery { mockClient.execute(any(), any(), any()) } returns
            listOf(Message.Assistant(content = Json.encodeToString(testPerson), testMetaInfo))

        val response = mockClient.executeStructured(
            prompt = testPrompt,
            model = modelWithoutStructuredOutput,
            structuredRequest = StructuredRequest.Native(JsonStructure.create<Person>())
        )

        assertTrue(response.isSuccess, "Structured output should fall back to manual mode")
        val structuredData = response.getOrThrow().data
        assertEquals(structuredData, testPerson)
    }

    @Test
    fun `test structured output manual with model not supporting structured output should succeed`() = runTest {
        val mockClient = mockk<LLMClient>()

        coEvery { mockClient.execute(any(), any(), any()) } returns
            listOf(Message.Assistant(content = Json.encodeToString(testPerson), testMetaInfo))

        val response = mockClient.executeStructured(
            prompt = testPrompt,
            model = modelWithoutStructuredOutput,
            structuredRequest = StructuredRequest.Manual(JsonStructure.create<Person>())
        )

        assertTrue(response.isSuccess, "Manual structured output should work with any model")
        val structuredData = response.getOrThrow().data
        assertEquals(structuredData, testPerson)
    }
}
