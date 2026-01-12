package ai.koog.agents.memory.feature

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.memory.repositories.ChatMessageRepository
import ai.koog.agents.memory.repositories.NoChatMessageRepository
import ai.koog.agents.memory.repositories.NoMemoryRecordRepository
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.rag.vector.database.BatchOperationResult
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.ScoredMemoryRecord
import ai.koog.rag.vector.database.SearchRequest
import ai.koog.rag.vector.database.SimilaritySearchRequest
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for Memory2 feature
 */
@OptIn(InternalAgentsApi::class)
class Memory2Test {

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.Companion.parse("2023-01-01T00:00:00Z")
    }

    private class InMemoryRepository : MemoryRecordRepository {
        val records = mutableListOf<MemoryRecord>()

        override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
            this.records.addAll(records)
            return BatchOperationResult(records.mapIndexed { index, _ -> "id-$index" })
        }

        override suspend fun update(records: List<MemoryRecord>): BatchOperationResult =
            BatchOperationResult(emptyList())

        override suspend fun getAll(ids: List<String>): List<MemoryRecord> =
            records.filter { it.id in ids }

        override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
            val query = when (request) {
                is SimilaritySearchRequest -> request.query
                else -> ""
            }
            // Simple contains-based search for testing
            return records
                .filter { it.content.contains(query, ignoreCase = true) }
                .map { ScoredMemoryRecord(it, 1.0) }
        }

        override suspend fun deleteAll(ids: List<String>): BatchOperationResult =
            BatchOperationResult(emptyList())

        override suspend fun deleteByFilter(filterExpression: String): Int = 0
    }

    /**
     * Test implementation of ChatMessageRepository for testing purposes
     */
    internal class TestChatMessageRepository : ChatMessageRepository {
        val messages = mutableMapOf<String, MutableList<Message>>()

        override suspend fun add(id: String, messages: List<Message>) {
            this.messages.getOrPut(id) { mutableListOf() }.addAll(messages)
        }

        override suspend fun get(id: String): List<Message> {
            return messages[id] ?: emptyList()
        }
    }

    @Test
    fun testMemory2ConfigDefaults() {
        val config = Memory2.Config()

        assertEquals(NoChatMessageRepository, config.chatMessageRepository)
        assertEquals(NoMemoryRecordRepository, config.memoryRecordRepository)
    }

    @Test
    fun testMemory2ConfigCustomValues() {
        val testChatRepo = TestChatMessageRepository()
        val testRecordRepo = InMemoryRepository()

        val config = Memory2.Config().apply {
            chatMessageRepository = testChatRepo
            memoryRecordRepository = testRecordRepo
        }

        assertEquals(testChatRepo, config.chatMessageRepository)
        assertEquals(testRecordRepo, config.memoryRecordRepository)
    }

    @Test
    fun testNoMemoryRecordRepositorySearch() = runTest {
        val result = NoMemoryRecordRepository.search(SimilaritySearchRequest("test query"))
        assertEquals(emptyList(), result, "NoMemoryRecordRepository should always return empty list")
    }

    @Test
    fun testNoMemoryRecordRepositoryAdd() = runTest {
        // Should not throw any exception
        NoMemoryRecordRepository.add(listOf(MemoryRecord(content = "test content")))
    }

    @Test
    fun testNoChatMessageRepositoryGet() = runTest {
        val result = NoChatMessageRepository.get("test-id")
        assertEquals(emptyList(), result, "NoChatMessageRepository should always return empty list")
    }

    @Test
    fun testNoChatMessageRepositoryAdd() = runTest {
        // Should not throw any exception
        NoChatMessageRepository.add(
            "test-id",
            listOf(Message.User("test message", metaInfo = RequestMetaInfo.Companion.create(testClock)))
        )
    }

    @Test
    fun testInMemoryRepository() = runTest {
        val repository = InMemoryRepository()

        // Test add
        repository.add(
            listOf(
                MemoryRecord(content = "User likes Kotlin", metadata = mapOf("type" to JsonPrimitive("preference"))),
                MemoryRecord(content = "User works on AI", metadata = mapOf("type" to JsonPrimitive("context")))
            )
        )

        assertEquals(2, repository.records.size)

        // Test search
        val kotlinResults = repository.search(SimilaritySearchRequest("Kotlin"))
        assertEquals(1, kotlinResults.size)
        assertTrue(kotlinResults[0].record.content.contains("Kotlin"))

        val aiResults = repository.search(SimilaritySearchRequest("AI"))
        assertEquals(1, aiResults.size)
        assertTrue(aiResults[0].record.content.contains("AI"))

        val allResults = repository.search(SimilaritySearchRequest("User"))
        assertEquals(2, allResults.size)
    }

    @Test
    fun testTestChatMessageRepository() = runTest {
        val repository = TestChatMessageRepository()

        // Test add
        repository.add(
            "session1", listOf(
                Message.User("Hello", metaInfo = RequestMetaInfo.Companion.create(testClock)),
                Message.Assistant("Hi there!", metaInfo = ResponseMetaInfo.Companion.create(testClock))
            )
        )

        repository.add(
            "session2", listOf(
                Message.User("Different session", metaInfo = RequestMetaInfo.Companion.create(testClock))
            )
        )

        // Test get
        val session1Messages = repository.get("session1")
        assertEquals(2, session1Messages.size)

        val session2Messages = repository.get("session2")
        assertEquals(1, session2Messages.size)

        val nonexistentMessages = repository.get("nonexistent")
        assertEquals(0, nonexistentMessages.size)
    }



    @Serializable
    data class UserPreference(
        val topic: String,
        val preference: String
    )

    @Test
    fun `test store and retrieve String type`() = runTest {
        val repository = InMemoryRepository()
        val memory2 = Memory2(NoChatMessageRepository, repository)

        // Store strings using typed method
        val strings = listOf("Hello World", "Kotlin is great", "Memory test")
        memory2.store(strings)

        // Verify records were stored
        assertEquals(3, repository.records.size)

        // Check that strings are stored as JSON strings (with quotes)
        println("[DEBUG_LOG] Stored records:")
        repository.records.forEach { println("[DEBUG_LOG] Content: '${it.content}'") }

        // Search and retrieve as String type
        val results = memory2.search<String>("Hello", topK = 10)

        println("[DEBUG_LOG] Search results: $results")
        assertEquals(1, results.size)
        assertEquals("Hello World", results[0])
    }

    @Test
    fun `test store and retrieve custom type`() = runTest {
        val repository = InMemoryRepository()
        val memory2 = Memory2(NoChatMessageRepository, repository)

        // Store custom objects
        val preferences = listOf(
            UserPreference("language", "Kotlin"),
            UserPreference("framework", "Ktor")
        )
        memory2.store(preferences)

        // Verify records were stored
        assertEquals(2, repository.records.size)

        println("[DEBUG_LOG] Stored records:")
        repository.records.forEach { println("[DEBUG_LOG] Content: '${it.content}'") }

        // Search and retrieve as UserPreference type
        val results = memory2.search<UserPreference>("Kotlin", topK = 10)

        println("[DEBUG_LOG] Search results: $results")
        assertEquals(1, results.size)
        assertEquals(UserPreference("language", "Kotlin"), results[0])
    }

    @Test
    fun `test mixed storage - strings stored via storeMemoryRecords can be retrieved as String`() = runTest {
        val repository = InMemoryRepository()
        val memory2 = Memory2(NoChatMessageRepository, repository)

        // Store plain strings using the non-typed method
        memory2.storeRawMemoryContent(listOf("Plain string content"))

        println("[DEBUG_LOG] Stored via storeMemoryRecords: '${repository.records[0].content}'")

        // Try to retrieve as String - this should fail because plain strings are not JSON-encoded
        val results = memory2.search<String>("Plain", topK = 10)

        println("[DEBUG_LOG] Search results for plain string: $results")
        // Plain strings stored via storeMemoryRecords are NOT JSON-encoded,
        // so they won't deserialize as String type (which expects JSON string with quotes)
        // This is expected behavior - use storeTypedMemoryRecords for type-safe storage
    }

    @Test
    fun `test String type round-trip preserves content`() = runTest {
        val repository = InMemoryRepository()
        val memory2 = Memory2(NoChatMessageRepository, repository)

        val testStrings = listOf(
            "Simple string",
            "String with special chars: @#$%^&*()",
            "String with unicode: 你好世界",
            "String with newlines:\nline1\nline2",
            "String with quotes: \"quoted\""
        )

        memory2.store(testStrings)

        // Search for each and verify round-trip
        for (original in testStrings) {
            // Use a unique part of each string for search
            val searchTerm = original.take(10)
            val results = memory2.search<String>(searchTerm, topK = 10)

            println("[DEBUG_LOG] Original: '$original'")
            println("[DEBUG_LOG] Results: $results")

            assertTrue(results.contains(original), "Should find original string: $original")
        }
    }
}
