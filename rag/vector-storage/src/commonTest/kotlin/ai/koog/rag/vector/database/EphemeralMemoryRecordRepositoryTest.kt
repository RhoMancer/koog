package ai.koog.rag.vector.database

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EphemeralMemoryRecordRepositoryTest {

    @Test
    fun testAddRecordsWithoutId() = runTest {
        val repository = EphemeralMemoryRecordRepository()

        val result = repository.add(
            listOf(
                MemoryRecord(content = "Test content 1"),
                MemoryRecord(content = "Test content 2")
            )
        )

        assertEquals(2, result.successIds.size)
        assertTrue(result.failedIds.isEmpty())
        assertEquals(2, repository.size())
    }

    @Test
    fun testAddRecordsWithId() = runTest {
        val repository = EphemeralMemoryRecordRepository()

        val result = repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Test content 1"),
                MemoryRecord(id = "id-2", content = "Test content 2")
            )
        )

        assertEquals(listOf("id-1", "id-2"), result.successIds)
        assertTrue(result.failedIds.isEmpty())
    }

    @Test
    fun testGetAllByIds() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Content 1"),
                MemoryRecord(id = "id-2", content = "Content 2"),
                MemoryRecord(id = "id-3", content = "Content 3")
            )
        )

        val records = repository.getAll(listOf("id-1", "id-3"))

        assertEquals(2, records.size)
        assertEquals("Content 1", records.find { it.id == "id-1" }?.content)
        assertEquals("Content 3", records.find { it.id == "id-3" }?.content)
    }

    @Test
    fun testGetAllWithEmptyIds() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Content 1")))

        val records = repository.getAll(emptyList())

        assertTrue(records.isEmpty())
    }

    @Test
    fun testUpdateExistingRecord() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Original content")))

        val result = repository.update(listOf(MemoryRecord(id = "id-1", content = "Updated content")))

        assertEquals(listOf("id-1"), result.successIds)
        assertTrue(result.failedIds.isEmpty())

        val records = repository.getAll(listOf("id-1"))
        assertEquals("Updated content", records.first().content)
    }

    @Test
    fun testUpdateNonExistingRecord() = runTest {
        val repository = EphemeralMemoryRecordRepository()

        val result = repository.update(listOf(MemoryRecord(id = "non-existing", content = "Content")))

        assertTrue(result.successIds.isEmpty())
        assertEquals("Record not found", result.failedIds["non-existing"])
    }

    @Test
    fun testUpdateRecordWithoutId() = runTest {
        val repository = EphemeralMemoryRecordRepository()

        val result = repository.update(listOf(MemoryRecord(content = "Content without ID")))

        assertTrue(result.successIds.isEmpty())
        assertEquals("Record ID is required for update", result.failedIds["unknown"])
    }

    @Test
    fun testSearchByKeyword() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Kotlin is a programming language"),
                MemoryRecord(id = "id-2", content = "Java is also a programming language"),
                MemoryRecord(id = "id-3", content = "Python is popular for data science")
            )
        )

        val results = repository.search(KeywordSearchRequest(query = "programming"))

        assertEquals(2, results.size)
        assertTrue(results.all { it.record.content.contains("programming") })
    }

    @Test
    fun testSearchWithLimit() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Test content 1"),
                MemoryRecord(id = "id-2", content = "Test content 2"),
                MemoryRecord(id = "id-3", content = "Test content 3")
            )
        )

        val results = repository.search(KeywordSearchRequest(query = "Test", limit = 2))

        assertEquals(2, results.size)
    }

    @Test
    fun testSearchCaseInsensitive() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "KOTLIN is great"),
                MemoryRecord(id = "id-2", content = "kotlin is awesome")
            )
        )

        val results = repository.search(KeywordSearchRequest(query = "Kotlin"))

        assertEquals(2, results.size)
    }

    @Test
    fun testDeleteAllByIds() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Content 1"),
                MemoryRecord(id = "id-2", content = "Content 2"),
                MemoryRecord(id = "id-3", content = "Content 3")
            )
        )

        val result = repository.deleteAll(listOf("id-1", "id-3"))

        assertEquals(listOf("id-1", "id-3"), result.successIds)
        assertTrue(result.failedIds.isEmpty())
        assertEquals(1, repository.size())
    }

    @Test
    fun testDeleteNonExistingRecord() = runTest {
        val repository = EphemeralMemoryRecordRepository()

        val result = repository.deleteAll(listOf("non-existing"))

        assertTrue(result.successIds.isEmpty())
        assertEquals("Record not found", result.failedIds["non-existing"])
    }

    @Test
    fun testDeleteAllWithEmptyIds() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Content 1")))

        val result = repository.deleteAll(emptyList())

        assertTrue(result.successIds.isEmpty())
        assertTrue(result.failedIds.isEmpty())
        assertEquals(1, repository.size())
    }

    @Test
    fun testClear() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Content 1"),
                MemoryRecord(id = "id-2", content = "Content 2")
            )
        )

        repository.clear()

        assertEquals(0, repository.size())
    }

    @Test
    fun testDeleteByFilterReturnsZero() = runTest {
        val repository = EphemeralMemoryRecordRepository()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Content 1")))

        val deletedCount = repository.deleteByFilter("any filter")

        assertEquals(0, deletedCount)
    }
}
