package ai.koog.rag.vector.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScopedMemoryRecordRepositoryTest {

    /**
     * A spy implementation that extends [EphemeralMemoryRecordRepository] to capture
     * the arguments passed to each method. This allows tests to verify that
     * [ScopedMemoryRecordRepository] correctly applies scope to all delegate calls.
     */
    private class SpyMemoryRecordRepository : EphemeralMemoryRecordRepository() {
        val addedRecords = mutableListOf<MemoryRecord>()
        val updatedRecords = mutableListOf<MemoryRecord>()
        var lastSearchRequest: SearchRequest? = null
        var lastDeleteFilterExpression: String? = null

        override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
            addedRecords.addAll(records)
            return super.add(records)
        }

        override suspend fun update(records: List<MemoryRecord>): BatchOperationResult {
            updatedRecords.addAll(records)
            return super.update(records)
        }

        override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
            lastSearchRequest = request
            return super.search(request)
        }

        override fun searchAsFlow(request: SearchRequest): Flow<ScoredMemoryRecord<MemoryRecord>> {
            lastSearchRequest = request
            return super.searchAsFlow(request)
        }

        override suspend fun deleteByFilter(filterExpression: String): Int {
            lastDeleteFilterExpression = filterExpression
            return super.deleteByFilter(filterExpression)
        }
    }

    // ==========
    // Tests for add() operation
    // ==========

    @Test
    fun testAddAppliesScopeToRecords() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        val records = listOf(
            MemoryRecord(id = "1", content = "First record"),
            MemoryRecord(id = "2", content = "Second record")
        )

        scopedRepo.add(records)

        assertEquals(2, delegate.addedRecords.size)
        delegate.addedRecords.forEach { record ->
            assertEquals(
                JsonPrimitive("test-scope"),
                record.metadata[ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME],
                "Record should have scope metadata"
            )
        }
    }

    @Test
    fun testAddWithCustomScopeKey() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeKey = "custom_scope",
            scopeValue = "my-custom-scope"
        )

        val records = listOf(MemoryRecord(id = "1", content = "Test content"))

        scopedRepo.add(records)

        assertEquals(1, delegate.addedRecords.size)
        assertEquals(
            JsonPrimitive("my-custom-scope"),
            delegate.addedRecords[0].metadata["custom_scope"],
            "Record should have custom scope key"
        )
    }

    @Test
    fun testAddPreservesExistingMetadata() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        val records = listOf(
            MemoryRecord(
                id = "1",
                content = "Record with metadata",
                metadata = mapOf(
                    "category" to JsonPrimitive("important"),
                    "priority" to JsonPrimitive(1)
                )
            )
        )

        scopedRepo.add(records)

        val savedRecord = delegate.addedRecords[0]
        assertEquals(JsonPrimitive("important"), savedRecord.metadata["category"])
        assertEquals(JsonPrimitive(1), savedRecord.metadata["priority"])
        assertEquals(JsonPrimitive("test-scope"), savedRecord.metadata[ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME])
    }

    @Test
    fun testAddReturnsCorrectBatchResult() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        val records = listOf(
            MemoryRecord(id = "id-1", content = "First"),
            MemoryRecord(id = "id-2", content = "Second")
        )

        val result = scopedRepo.add(records)

        assertEquals(2, result.successIds.size)
        assertTrue(result.isFullySuccessful)
    }

    // ==========
    // Tests for update() operation
    // ==========

    @Test
    fun testUpdateAppliesScopeToRecords() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        // First add a record directly to delegate
        delegate.add(listOf(MemoryRecord(id = "1", content = "Original content")))

        // Update via scoped repo
        val updatedRecords = listOf(MemoryRecord(id = "1", content = "Updated content"))
        scopedRepo.update(updatedRecords)

        // Verify the update was called with scoped records
        assertEquals(1, delegate.updatedRecords.size)
        val updatedRecord = delegate.updatedRecords[0]
        assertEquals("Updated content", updatedRecord.content)
        assertEquals(
            JsonPrimitive("test-scope"),
            updatedRecord.metadata[ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME]
        )
    }

    // ==========
    // Tests for getAll() operation
    // ==========

    @Test
    fun testGetAllReturnsOnlyRecordsInScope() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "scope-A"
        )

        // Add records with different scopes directly to delegate
        delegate.add(
            listOf(
                MemoryRecord(
                    id = "1",
                    content = "Record in scope A",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("scope-A"))
                ),
                MemoryRecord(
                    id = "2",
                    content = "Record in scope B",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("scope-B"))
                ),
                MemoryRecord(
                    id = "3",
                    content = "Another record in scope A",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("scope-A"))
                )
            )
        )

        val result = scopedRepo.getAll(listOf("1", "2", "3"))

        assertEquals(2, result.size, "Should only return records in scope-A")
        assertTrue(result.all { it.metadata[ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME] == JsonPrimitive("scope-A") })
    }

    @Test
    fun testGetAllExcludesRecordsWithoutScopeMetadata() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        // Add records - one with scope, one without
        delegate.add(
            listOf(
                MemoryRecord(
                    id = "1",
                    content = "Record with scope",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("my-scope"))
                ),
                MemoryRecord(
                    id = "2",
                    content = "Record without scope",
                    metadata = emptyMap()
                )
            )
        )

        val result = scopedRepo.getAll(listOf("1", "2"))

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun testGetAllWithCustomScopeKey() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeKey = "custom_scope",
            scopeValue = "custom-value"
        )

        delegate.add(
            listOf(
                MemoryRecord(
                    id = "1",
                    content = "Record with custom scope",
                    metadata = mapOf("custom_scope" to JsonPrimitive("custom-value"))
                ),
                MemoryRecord(
                    id = "2",
                    content = "Record with default scope key",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("custom-value"))
                )
            )
        )

        val result = scopedRepo.getAll(listOf("1", "2"))

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    // ==========
    // Tests for search() operation
    // ==========

    @Test
    fun testSearchAppliesScopeFilter() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "search-scope"
        )

        // Add records with different scopes
        delegate.add(
            listOf(
                MemoryRecord(
                    id = "1",
                    content = "Kotlin programming",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("search-scope"))
                ),
                MemoryRecord(
                    id = "2",
                    content = "Kotlin coroutines",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("other-scope"))
                )
            )
        )

        scopedRepo.search(KeywordSearchRequest(query = "Kotlin"))

        // Verify that the search request was modified to include scope filter
        val capturedRequest = delegate.lastSearchRequest
        assertNotNull(capturedRequest)
        assertContains(capturedRequest.filterExpression!!, "memory_scope_id == \"search-scope\"")
    }

    @Test
    fun testSearchCombinesExistingFilterWithScopeFilter() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        val request = KeywordSearchRequest(
            query = "test",
            filterExpression = "category == \"important\""
        )

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest
        val filterExpression = capturedRequest?.filterExpression
        assertNotNull(filterExpression)
        assertContains(filterExpression, "category == \"important\"")
        assertContains(filterExpression, "memory_scope_id == \"test-scope\"")
    }

    @Test
    fun testSearchWithNullFilterExpression() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        val request = KeywordSearchRequest(query = "test", filterExpression = null)

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest
        assertEquals("memory_scope_id == \"my-scope\"", capturedRequest?.filterExpression)
    }

    @Test
    fun testSearchWithBlankFilterExpression() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        val request = KeywordSearchRequest(query = "test", filterExpression = "   ")

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest
        assertEquals("memory_scope_id == \"my-scope\"", capturedRequest?.filterExpression)
    }

    @Test
    fun testSearchWithKeywordSearchRequest() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "keyword-scope"
        )

        val request = KeywordSearchRequest(query = "test query")

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest as? KeywordSearchRequest
        assertEquals("memory_scope_id == \"keyword-scope\"", capturedRequest?.filterExpression)
    }

    // ==========
    // Tests for searchAsFlow() operation
    // ==========

    @Test
    fun testSearchAsFlowAppliesScopeFilter() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "flow-scope"
        )

        delegate.add(
            listOf(
                MemoryRecord(
                    id = "1",
                    content = "Flow test content",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("flow-scope"))
                ),
                MemoryRecord(
                    id = "2",
                    content = "Flow test other",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("other-scope"))
                )
            )
        )

        scopedRepo.searchAsFlow(KeywordSearchRequest(query = "Flow")).toList()

        // Verify that the search request was modified to include scope filter
        val capturedRequest = delegate.lastSearchRequest
        assertNotNull(capturedRequest)
        assertContains(capturedRequest.filterExpression!!, "memory_scope_id == \"flow-scope\"")
    }

    // ==========
    // Tests for deleteAll() operation
    // ==========

    @Test
    fun testDeleteAllOnlyDeletesRecordsInScope() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "delete-scope"
        )

        delegate.add(
            listOf(
                MemoryRecord(
                    id = "1",
                    content = "Record in scope",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("delete-scope"))
                ),
                MemoryRecord(
                    id = "2",
                    content = "Record in other scope",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("other-scope"))
                ),
                MemoryRecord(
                    id = "3",
                    content = "Another record in scope",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("delete-scope"))
                )
            )
        )

        val result = scopedRepo.deleteAll(listOf("1", "2", "3"))

        // Should only delete records 1 and 3 (in delete-scope)
        assertEquals(2, result.successIds.size)
        assertTrue(result.successIds.contains("1"))
        assertTrue(result.successIds.contains("3"))

        // Record 2 should still exist
        assertEquals(1, delegate.size())
        val remainingRecords = delegate.getAll(listOf("2"))
        assertEquals(1, remainingRecords.size)
        assertEquals("2", remainingRecords[0].id)
    }

    @Test
    fun testDeleteAllReturnsEmptyResultWhenNoRecordsInScope() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        delegate.add(
            listOf(
                MemoryRecord(
                    id = "1",
                    content = "Record in different scope",
                    metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("other-scope"))
                )
            )
        )

        val result = scopedRepo.deleteAll(listOf("1"))

        assertEquals(0, result.successIds.size)
        assertEquals(1, delegate.size()) // Record should not be deleted
    }

    // ==========
    // Tests for deleteByFilter() operation
    // ==========

    @Test
    fun testDeleteByFilterAppliesScopeFilter() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "filter-delete-scope"
        )

        scopedRepo.deleteByFilter("category == \"old\"")

        val capturedFilter = delegate.lastDeleteFilterExpression
        assertNotNull(capturedFilter)
        assertContains(capturedFilter, "category == \"old\"")
        assertContains(capturedFilter, "memory_scope_id == \"filter-delete-scope\"")
    }

    @Test
    fun testDeleteByFilterWithEmptyExpression() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        scopedRepo.deleteByFilter("")

        val capturedFilter = delegate.lastDeleteFilterExpression
        assertEquals("memory_scope_id == \"my-scope\"", capturedFilter)
    }

    // ==========
    // Tests for DEFAULT_MEMORY_SCOPE_FIELD_NAME constant
    // ==========

    @Test
    fun testDefaultMemoryScopeFieldName() {
        assertEquals("memory_scope_id", ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME)
    }

    // ==========
    // Tests for multiple scopes isolation
    // ==========

    @Test
    fun testMultipleScopesAreIsolated() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopeA = ScopedMemoryRecordRepository(delegate = delegate, scopeValue = "scope-A")
        val scopeB = ScopedMemoryRecordRepository(delegate = delegate, scopeValue = "scope-B")

        // Add records through different scopes
        scopeA.add(listOf(MemoryRecord(id = "a1", content = "Record from scope A")))
        scopeB.add(listOf(MemoryRecord(id = "b1", content = "Record from scope B")))

        // Verify isolation
        val resultsA = scopeA.getAll(listOf("a1", "b1"))
        val resultsB = scopeB.getAll(listOf("a1", "b1"))

        assertEquals(1, resultsA.size)
        assertEquals("a1", resultsA[0].id)

        assertEquals(1, resultsB.size)
        assertEquals("b1", resultsB[0].id)
    }

    @Test
    fun testSearchIsolationBetweenScopes() = runTest {
        val delegate = SpyMemoryRecordRepository()
        val scopeA = ScopedMemoryRecordRepository(delegate = delegate, scopeValue = "scope-A")
        val scopeB = ScopedMemoryRecordRepository(delegate = delegate, scopeValue = "scope-B")

        // Add similar content to both scopes
        scopeA.add(listOf(MemoryRecord(id = "a1", content = "Kotlin programming guide")))
        scopeB.add(listOf(MemoryRecord(id = "b1", content = "Kotlin programming tutorial")))

        // Search in each scope - verify the request has the correct scope filter
        scopeA.search(KeywordSearchRequest(query = "Kotlin"))
        val requestA = delegate.lastSearchRequest
        assertNotNull(requestA)
        assertContains(requestA.filterExpression!!, "memory_scope_id == \"scope-A\"")

        scopeB.search(KeywordSearchRequest(query = "Kotlin"))
        val requestB = delegate.lastSearchRequest
        assertNotNull(requestB)
        assertContains(requestB.filterExpression!!, "memory_scope_id == \"scope-B\"")
    }
}
