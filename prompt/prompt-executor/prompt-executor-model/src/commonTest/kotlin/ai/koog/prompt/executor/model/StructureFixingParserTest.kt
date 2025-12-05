package ai.koog.prompt.executor.model.ai.koog.prompt.executor.model

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.LLMStructuredParsingError
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.getOrThrow
import ai.koog.prompt.structure.json.JsonStructure
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructureFixingParserTest {
    @Serializable
    private data class TestData(
        val a: String,
        val b: Int,
    )

    @Serializable
    private data class DataWithWildcard(
        val id: String,
        val payload: JsonElement
    )

    private val testData = TestData("test", 42)
    private val testDataJson = Json.Default.encodeToString(testData)
    private val testStructure = JsonStructure.Companion.create<TestData>()

    private fun buildMessage(content: String) = Message.Assistant(content, ResponseMetaInfo(Clock.System.now()))

    @Test
    fun testParseValidContentWithoutFixing() = runTest {
        val parser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4oMini,
            retries = 2,
        )
        val mockExecutor = getMockExecutor {}

        val response = StructuredResponse.Success(buildMessage(testDataJson), testData)

        val result = parser.parse(mockExecutor, testStructure, response)
        assertTrue(result.isSuccess)
        assertEquals(testData, result.getOrThrow().data)
    }

    @Test
    fun testFixInvalidContentInMultipleSteps() = runTest {
        val parser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4oMini,
            retries = 2,
        )

        val invalidContent = testDataJson
            .replace("\"a\"\\s*:".toRegex(), "")
            .replace("\"b\"\\s*:".toRegex(), "")

        val firstResponse = testDataJson
            .replace("\"a\"\\s*:".toRegex(), "")

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(firstResponse) onRequestContains invalidContent
            mockLLMAnswer(testDataJson) onRequestContains firstResponse
        }

        val result = parser.parse(
            mockExecutor,
            testStructure,
            StructuredResponse.Failure(
                buildMessage(invalidContent),
                LLMStructuredParsingError(message = "Failed to parse", cause = null)
            )
        )
        assertTrue(result.isSuccess)
        assertEquals(testData, result.getOrThrow().data)
    }

    @Test
    fun testFailToParseWhenFixingRetriesExceeded() = runTest {
        val parser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4oMini,
            retries = 2,
        )

        val invalidContent = testDataJson
            .replace("\"a\"\\s*:".toRegex(), "")
            .replace("\"b\"\\s*:".toRegex(), "")

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(invalidContent).asDefaultResponse
        }

        val result = parser.parse(
            mockExecutor,
            testStructure,
            StructuredResponse.Failure(
                buildMessage(invalidContent),
                LLMStructuredParsingError(message = "Failed to parse", cause = null)
            )
        )

        assertFalse(result.isSuccess)

        assertFailsWith<LLMStructuredParsingError> {
            result.getOrThrow()
        }
    }

    @Test
    fun testFixInvalidJsonElementContent() = runTest {
        val parser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4oMini,
            retries = 2,
        )

        val structure = JsonStructure.create<DataWithWildcard>()

        val invalidContent = """
            {
                "id": "test-id",
                "payload": { 
                    unquotedKey: "someValue",
                    brokenArray: [1, 2 
                }
            }
        """.trimIndent()

        val fixedContent = """
            {
                "id": "test-id",
                "payload": { 
                    "unquotedKey": "someValue",
                    "brokenArray": [1, 2] 
                }
            }
        """.trimIndent()

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(fixedContent) onRequestContains "unquotedKey"
        }

        val response = StructuredResponse.Failure<DataWithWildcard>(
            message = buildMessage(invalidContent),
            exception = Exception("Failed to parse")
        )

        val result = parser.parse(mockExecutor, structure, response).getOrThrow().data

        assertEquals("test-id", result.id)
        assertTrue(result.payload is JsonObject)

        val payloadObj = result.payload
        assertEquals(JsonPrimitive("someValue"), payloadObj["unquotedKey"])
    }
}
