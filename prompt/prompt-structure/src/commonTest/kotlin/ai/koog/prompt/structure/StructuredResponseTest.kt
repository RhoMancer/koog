package ai.koog.prompt.structure

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.structure.json.JsonStructure
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructuredResponseTest {

    @Serializable
    data class TestData(
        val name: String,
        val value: Int
    )

    private val testStructure = JsonStructure.create<TestData>()
    private val metaInfo = ResponseMetaInfo(Instant.DISTANT_PAST)

    @Test
    fun `test parsing structured response from response with single correct assistant message should succeed`() {
        val assistantMessage = Message.Assistant(
            content = """{ "name": "test", "value": 42 }""",
            metaInfo = metaInfo
        )
        val response = listOf(assistantMessage)

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isSuccess)
        assertIs<StructuredResponse.Success<TestData>>(result)
        assertEquals(assistantMessage, result.message)
        assertEquals("test", result.data.name)
        assertEquals(42, result.data.value)
    }

    @Test
    fun `test parsing structured response from response with two correct assistant message should use first message`() {
        val firstAssistant = Message.Assistant(
            content = """{ "name": "first", "value": 1 }""",
            metaInfo = metaInfo
        )
        val secondAssistant = Message.Assistant(
            content = """{ "name": "second", "value": 2 }""",
            metaInfo = metaInfo
        )
        val response = listOf(firstAssistant, secondAssistant)

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isSuccess)
        assertIs<StructuredResponse.Success<TestData>>(result)
        assertEquals(firstAssistant, result.message)
        assertEquals("first", result.data.name)
        assertEquals(1, result.data.value)
    }

    @Test
    fun `test parsing structured response from response with reasoning and correct assistant message should use assistant message`() {
        val reasoningMessage = Message.Reasoning(
            content = "Let me think about this...",
            metaInfo = metaInfo
        )
        val assistantMessage = Message.Assistant(
            content = """{ "name": "result", "value": 99 }""",
            metaInfo = metaInfo
        )
        val response = listOf(reasoningMessage, assistantMessage)

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isSuccess)
        assertIs<StructuredResponse.Success<TestData>>(result)
        assertEquals(assistantMessage, result.message)
        assertEquals("result", result.data.name)
        assertEquals(99, result.data.value)
    }

    @Test
    fun `test parsing structured response from response with no assistant message should fail`() {
        val reasoningMessage = Message.Reasoning(
            content = "Just thinking...",
            metaInfo = metaInfo
        )
        val toolCall = Message.Tool.Call(
            id = "tool1",
            tool = "testTool",
            content = "tool call",
            metaInfo = metaInfo
        )
        val response = listOf(reasoningMessage, toolCall)

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isFailure)
        assertIs<StructuredResponse.Failure<TestData>>(result)
        assertNull(result.message)
        assertIs<StructuredOutputParsingException>(result.exception)
        assertContains(result.exception.message!!, "Unable to parse structured output")
        assertContains(result.exception.cause!!.message!!, "Response for structured output must be an assistant message")
    }

    @Test
    fun `test parsing structured response from empty response should fail`() {
        val response = emptyList<Message.Response>()

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isFailure)
        assertIs<StructuredResponse.Failure<TestData>>(result)
        assertNull(result.message)
        assertContains(result.exception.message!!, "Unable to parse structured output")
        assertContains(result.exception.cause!!.message!!, "Response for structured output must be an assistant message")
    }

    @Test
    fun `test parsing structured response from response with single incorrect assistant message should fail`() {
        val assistantMessage = Message.Assistant(
            content = "This is not valid JSON",
            metaInfo = metaInfo
        )
        val response = listOf(assistantMessage)

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isFailure)
        assertIs<StructuredResponse.Failure<TestData>>(result)
        assertEquals(assistantMessage, result.message)
        assertIs<StructuredOutputParsingException>(result.exception)
        assertContains(result.exception.message!!, "Unable to parse structured output")
        assertContains(result.exception.cause!!.message!!, "Unexpected JSON token at offset 0")
    }

    @Test
    fun `test parsing structured response from response with assistant message missing required field`() {
        val assistantMessage = Message.Assistant(
            content = """{ "name": "test" }""", // missing required "value" field
            metaInfo = metaInfo
        )
        val response = listOf(assistantMessage)

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isFailure)
        assertIs<StructuredResponse.Failure<TestData>>(result)
        assertEquals(assistantMessage, result.message)
        assertIs<StructuredOutputParsingException>(result.exception)
        assertContains(result.exception.message!!, "Unable to parse structured output")
        assertContains(result.exception.cause!!.message!!, "Field 'value' is required")
    }

    @Test
    fun `test parsing structured response from response with assistant message with code block should succeed`() {
        val assistantMessage = Message.Assistant(
            content = """```json
            { "name": "markdown", "value": 456 }
            ```""",
            metaInfo = metaInfo
        )
        val response = listOf(assistantMessage)

        val result = parseStructuredResponse(testStructure, response)

        assertTrue(result.isSuccess)
        assertIs<StructuredResponse.Success<TestData>>(result)
        assertEquals(assistantMessage, result.message)
        assertEquals("markdown", result.data.name)
        assertEquals(456, result.data.value)
    }
}
