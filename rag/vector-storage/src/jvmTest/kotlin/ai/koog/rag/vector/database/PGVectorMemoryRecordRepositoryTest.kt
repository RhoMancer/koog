package ai.koog.rag.vector.database

import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class PGVectorMemoryRecordRepositoryTest {

    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var repository: PGVectorMemoryRecordRepository

    @BeforeAll
    fun setUp() {
        // Use pgvector image which has the vector extension pre-installed
        postgres = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        postgres.start()

        val db = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )

        // Use smaller vector dimension for tests
        repository = PGVectorMemoryRecordRepository(
            database = db,
            tableName = "memory_records_test",
            vectorDimension = 3
        )

        runBlocking {
            repository.migrate()
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    @BeforeEach
    fun clearTable() {
        runBlocking {
            repository.deleteByFilter("1=1")
        }
    }

    // ==================== CREATE/UPDATE TESTS ====================

    @Test
    fun `add multiple records`() = runBlocking {
        val records = listOf(
            MemoryRecord(id = "batch-1", content = "First"),
            MemoryRecord(id = "batch-2", content = "Second"),
            MemoryRecord(id = "batch-3", content = "Third")
        )

        val result = repository.add(records)

        assertTrue(result.isFullySuccessful)
        assertEquals(3, result.successIds.size)
        assertEquals(3, repository.getAll(listOf("batch-1", "batch-2", "batch-3")).size)
    }

    @Test
    fun `add record with same id updates existing (upsert)`() = runBlocking {
        val original = MemoryRecord(id = "upsert-test", content = "Original")
        repository.add(listOf(original))

        val updated = MemoryRecord(id = "upsert-test", content = "Updated")
        repository.add(listOf(updated))

        val retrieved = repository.getAll(listOf("upsert-test")).firstOrNull()
        assertNotNull(retrieved)
        assertEquals("Updated", retrieved.content)
    }

    @Test
    fun `update records`() = runBlocking {
        val record = MemoryRecord(id = "update-test", content = "Before update")
        repository.add(listOf(record))

        val updated = MemoryRecord(
            id = "update-test",
            content = "After update",
            embedding = listOf(0.5f, 0.5f, 0.5f)
        )
        val result = repository.update(listOf(updated))

        assertTrue(result.isFullySuccessful)
        val retrieved = repository.getAll(listOf("update-test")).firstOrNull()
        assertNotNull(retrieved)
        assertEquals("After update", retrieved.content)
        assertEquals(listOf(0.5f, 0.5f, 0.5f), retrieved.embedding)
    }

    @Test
    fun `update record without id fails`() = runBlocking {
        val record = MemoryRecord(content = "No ID")

        val result = repository.update(listOf(record))

        assertFalse(result.isFullySuccessful)
        assertEquals(1, result.failedIds.size)
    }

    // ==================== READ TESTS ====================

    @Test
    fun `getAll existing record`() = runBlocking {
        val record = MemoryRecord(id = "get-test", content = "Test content")
        repository.add(listOf(record))

        val retrieved = repository.getAll(listOf("get-test")).firstOrNull()

        assertNotNull(retrieved)
        assertEquals("get-test", retrieved.id)
        assertEquals("Test content", retrieved.content)
    }

    @Test
    fun `getAll non-existing record returns empty list`() = runBlocking {
        val retrieved = repository.getAll(listOf("non-existing"))

        assertTrue(retrieved.isEmpty())
    }

    @Test
    fun `getAll returns matching records`() = runBlocking {
        repository.add(
            listOf(
                MemoryRecord(id = "all-1", content = "First"),
                MemoryRecord(id = "all-2", content = "Second"),
                MemoryRecord(id = "all-3", content = "Third")
            )
        )

        val resultIds = repository.getAll(listOf("all-1", "all-3", "non-existing")).map { it.id }

        assertEquals(2, resultIds.size)
        assertContains(resultIds, "all-1")
        assertContains(resultIds, "all-3")
    }

    @Test
    fun `getAll with empty list returns empty map`() = runBlocking {
        val result = repository.getAll(emptyList())

        assertTrue(result.isEmpty())
    }

    // ==================== SEARCH TESTS ====================

    @Test
    fun `vector search returns similar records`() = runBlocking {
        // Add records with embeddings
        repository.add(
            listOf(
                MemoryRecord(id = "vec-1", content = "Similar to query", embedding = listOf(0.9f, 0.1f, 0.0f)),
                MemoryRecord(id = "vec-2", content = "Different", embedding = listOf(0.0f, 0.1f, 0.9f)),
                MemoryRecord(id = "vec-3", content = "Also similar", embedding = listOf(0.8f, 0.2f, 0.0f))
            )
        )

        val results = repository.search(
            VectorSearchRequest(
                queryVector = listOf(1.0f, 0.0f, 0.0f),
                limit = 2
            )
        )

        assertEquals(2, results.size)
        // Most similar should be first
        assertTrue(results[0].similarity > results[1].similarity)
    }

    @Test
    fun `vector search with similarity threshold filters results`() = runBlocking {
        repository.add(
            listOf(
                MemoryRecord(id = "thresh-1", content = "Very similar", embedding = listOf(0.99f, 0.01f, 0.0f)),
                MemoryRecord(id = "thresh-2", content = "Not similar", embedding = listOf(0.0f, 0.0f, 1.0f))
            )
        )

        val results = repository.search(
            VectorSearchRequest(
                queryVector = listOf(1.0f, 0.0f, 0.0f),
                limit = 10,
                similarityThreshold = 0.9
            )
        )

        assertEquals(1, results.size)
        assertEquals("thresh-1", results[0].record.id)
    }

    @Test
    fun `keyword search finds matching content`() = runBlocking {
        repository.add(
            listOf(
                MemoryRecord(id = "kw-1", content = "The quick brown fox"),
                MemoryRecord(id = "kw-2", content = "The lazy dog"),
                MemoryRecord(id = "kw-3", content = "Another fox story")
            )
        )

        val results = repository.search(
            KeywordSearchRequest(
                query = "fox",
                limit = 10
            )
        )

        assertEquals(2, results.size)
        assertTrue(results.all { it.record.content.contains("fox", ignoreCase = true) })
    }

    @Test
    fun `similarity search throws exception`() {
        runBlocking {
            assertFailsWith<MemoryRecordException> {
                repository.search(SimilaritySearchRequest(query = "test"))
            }
        }
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `deleteAll removes multiple records`() = runBlocking {
        repository.add(
            listOf(
                MemoryRecord(id = "delall-1", content = "First"),
                MemoryRecord(id = "delall-2", content = "Second"),
                MemoryRecord(id = "delall-3", content = "Third")
            )
        )

        val result = repository.deleteAll(listOf("delall-1", "delall-3"))

        assertEquals(2, result.successIds.size)
        assertEquals(1, repository.getAll(listOf("delall-2")).size)
        assertTrue(repository.getAll(listOf("delall-1")).isEmpty())
        assertTrue(repository.getAll(listOf("delall-3")).isEmpty())
    }

    @Test
    fun `deleteByFilter removes matching records`() = runBlocking {
        repository.add(
            listOf(
                MemoryRecord(id = "filter-1", content = "Keep this"),
                MemoryRecord(id = "filter-2", content = "Delete this"),
                MemoryRecord(id = "filter-3", content = "Delete that")
            )
        )

        repository.deleteByFilter("content LIKE '%Delete%'")

        assertEquals(1, repository.getAll(listOf("filter-1")).size)
        assertTrue(repository.getAll(listOf("filter-2")).isEmpty())
        assertTrue(repository.getAll(listOf("filter-3")).isEmpty())
    }
}
