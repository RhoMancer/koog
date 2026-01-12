package ai.koog.rag.vector.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
     * Test implementation of MemoryRecordRepository for testing ScopedMemoryRecordRepository.
     * Stores records in memory and supports basic filtering by scope.
     */
    private class TestMemoryRecordRepository : MemoryRecordRepository {
        val records = mutableListOf<MemoryRecord>()
        var lastSearchRequest: SearchRequest? = null
        var lastDeleteFilterExpression: String? = null

        override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
            this.records.addAll(records)
            return BatchOperationResult(records.mapIndexed { index, record -> record.id ?: "generated-id-$index" })
        }

        override suspend fun update(records: List<MemoryRecord>): BatchOperationResult {
            records.forEach { updatedRecord ->
                val index = this.records.indexOfFirst { it.id == updatedRecord.id }
                if (index >= 0) {
                    this.records[index] = updatedRecord
                }
            }
            return BatchOperationResult(records.mapNotNull { it.id })
        }

        override suspend fun getAll(ids: List<String>): List<MemoryRecord> {
            return records.filter { it.id in ids }
        }

        override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
            lastSearchRequest = request
            val query = when (request) {
                is SimilaritySearchRequest -> request.query
                is KeywordSearchRequest -> request.query
                is HybridSearchRequest -> request.query
                is VectorSearchRequest -> ""
            }

            // Filter by filterExpression if present (simple parsing for scope filter)
            val filteredRecords = if (request.filterExpression != null) {
                records.filter { record ->
                    // Simple filter parsing for testing: "key == \"value\""
                    val filterExpr = request.filterExpression!!
                    if (filterExpr.contains("memory_scope_id") || filterExpr.contains("custom_scope")) {
                        // Extract scope value from filter expression
                        val scopeMatch = Regex("""(memory_scope_id|custom_scope)\s*==\s*"([^"]+)"""").find(filterExpr)
                        if (scopeMatch != null) {
                            val scopeKey = scopeMatch.groupValues[1]
                            val scopeValue = scopeMatch.groupValues[2]
                            record.metadata[scopeKey]?.let { it == JsonPrimitive(scopeValue) } ?: false
                        } else {
                            true
                        }
                    } else {
                        true
                    }
                }
            } else {
                records
            }

            // Filter by query content if present
            val results = if (query.isNotBlank()) {
                filteredRecords.filter { it.content.contains(query, ignoreCase = true) }
            } else {
                filteredRecords
            }

            return results.take(request.limit).map { ScoredMemoryRecord(it, 1.0) }
        }

        override fun searchAsFlow(request: SearchRequest): Flow<ScoredMemoryRecord<MemoryRecord>> {
            lastSearchRequest = request
            return flow {
                search(request).forEach { emit(it) }
            }
        }

        override suspend fun deleteAll(ids: List<String>): BatchOperationResult {
            val deletedIds = mutableListOf<String>()
            ids.forEach { id ->
                val index = records.indexOfFirst { it.id == id }
                if (index >= 0) {
                    records.removeAt(index)
                    deletedIds.add(id)
                }
            }
            return BatchOperationResult(deletedIds)
        }

        override suspend fun deleteByFilter(filterExpression: String): Int {
            lastDeleteFilterExpression = filterExpression
            // Simple implementation: count records that would match the filter
            val initialSize = records.size
            records.removeAll { record ->
                if (filterExpression.contains("memory_scope_id") || filterExpression.contains("custom_scope")) {
                    val scopeMatch = Regex("""(memory_scope_id|custom_scope)\s*==\s*"([^"]+)"""").find(filterExpression)
                    if (scopeMatch != null) {
                        val scopeKey = scopeMatch.groupValues[1]
                        val scopeValue = scopeMatch.groupValues[2]
                        record.metadata[scopeKey]?.let { it == JsonPrimitive(scopeValue) } ?: false
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            return initialSize - records.size
        }
    }

    // ==========
    // Tests for add() operation
    // ==========

    @Test
    fun testAddAppliesScopeToRecords() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        val records = listOf(
            MemoryRecord(id = "1", content = "First record"),
            MemoryRecord(id = "2", content = "Second record")
        )

        scopedRepo.add(records)

        assertEquals(2, delegate.records.size)
        delegate.records.forEach { record ->
            assertEquals(
                JsonPrimitive("test-scope"),
                record.metadata[ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME],
                "Record should have scope metadata"
            )
        }
    }

    @Test
    fun testAddWithCustomScopeKey() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeKey = "custom_scope",
            scopeValue = "my-custom-scope"
        )

        val records = listOf(MemoryRecord(id = "1", content = "Test content"))

        scopedRepo.add(records)

        assertEquals(1, delegate.records.size)
        assertEquals(
            JsonPrimitive("my-custom-scope"),
            delegate.records[0].metadata["custom_scope"],
            "Record should have custom scope key"
        )
    }

    @Test
    fun testAddPreservesExistingMetadata() = runTest {
        val delegate = TestMemoryRecordRepository()
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

        val savedRecord = delegate.records[0]
        assertEquals(JsonPrimitive("important"), savedRecord.metadata["category"])
        assertEquals(JsonPrimitive(1), savedRecord.metadata["priority"])
        assertEquals(JsonPrimitive("test-scope"), savedRecord.metadata[ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME])
    }

    @Test
    fun testAddReturnsCorrectBatchResult() = runTest {
        val delegate = TestMemoryRecordRepository()
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
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        // First add a record
        delegate.records.add(MemoryRecord(id = "1", content = "Original content"))

        // Update via scoped repo
        val updatedRecords = listOf(MemoryRecord(id = "1", content = "Updated content"))
        scopedRepo.update(updatedRecords)

        val updatedRecord = delegate.records.find { it.id == "1" }
        assertEquals("Updated content", updatedRecord?.content)
        assertEquals(
            JsonPrimitive("test-scope"),
            updatedRecord?.metadata?.get(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME)
        )
    }

    // ==========
    // Tests for getAll() operation
    // ==========

    @Test
    fun testGetAllReturnsOnlyRecordsInScope() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "scope-A"
        )

        // Add records with different scopes directly to delegate
        delegate.records.addAll(
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
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        // Add records - one with scope, one without
        delegate.records.addAll(
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
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeKey = "custom_scope",
            scopeValue = "custom-value"
        )

        delegate.records.addAll(
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
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "search-scope"
        )

        // Add records with different scopes
        delegate.records.addAll(
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

        val results = scopedRepo.search(SimilaritySearchRequest(query = "Kotlin"))

        assertEquals(1, results.size)
        assertEquals("1", results[0].record.id)
    }

    @Test
    fun testSearchCombinesExistingFilterWithScopeFilter() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "test-scope"
        )

        val request = SimilaritySearchRequest(
            query = "test",
            filterExpression = "category == \"important\""
        )

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest
        val filterExpression = capturedRequest?.filterExpression
        assertNotNull(filterExpression)
        assertContains(filterExpression, "category == \"important\"")
        assertContains(filterExpression, "memory_scope_id == \"test-scope\"")
        assertContains(filterExpression, "category == \"important\"")
    }

    @Test
    fun testSearchWithNullFilterExpression() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        val request = SimilaritySearchRequest(query = "test", filterExpression = null)

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest
        assertEquals("memory_scope_id == \"my-scope\"", capturedRequest?.filterExpression)
    }

    @Test
    fun testSearchWithBlankFilterExpression() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        val request = SimilaritySearchRequest(query = "test", filterExpression = "   ")

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest
        assertEquals("memory_scope_id == \"my-scope\"", capturedRequest?.filterExpression)
    }

    @Test
    fun testSearchWithVectorSearchRequest() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "vector-scope"
        )

        val request = VectorSearchRequest(
            queryVector = listOf(0.1f, 0.2f, 0.3f),
            limit = 5
        )

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest as? VectorSearchRequest
        assertEquals("memory_scope_id == \"vector-scope\"", capturedRequest?.filterExpression)
        assertEquals(5, capturedRequest?.limit)
    }

    @Test
    fun testSearchWithKeywordSearchRequest() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "keyword-scope"
        )

        val request = KeywordSearchRequest(query = "test query")

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest as? KeywordSearchRequest
        assertEquals("memory_scope_id == \"keyword-scope\"", capturedRequest?.filterExpression)
    }

    @Test
    fun testSearchWithHybridSearchRequest() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "hybrid-scope"
        )

        val request = HybridSearchRequest(
            query = "test query",
            alpha = 0.7
        )

        scopedRepo.search(request)

        val capturedRequest = delegate.lastSearchRequest as? HybridSearchRequest
        assertEquals("memory_scope_id == \"hybrid-scope\"", capturedRequest?.filterExpression)
        assertEquals(0.7, capturedRequest?.alpha)
    }

    // ==========
    // Tests for searchAsFlow() operation
    // ==========

    @Test
    fun testSearchAsFlowAppliesScopeFilter() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "flow-scope"
        )

        delegate.records.addAll(
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

        val results = scopedRepo.searchAsFlow(SimilaritySearchRequest(query = "Flow")).toList()

        assertEquals(1, results.size)
        assertEquals("1", results[0].record.id)
    }

    // ==========
    // Tests for deleteAll() operation
    // ==========

    @Test
    fun testDeleteAllOnlyDeletesRecordsInScope() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "delete-scope"
        )

        delegate.records.addAll(
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
        assertEquals(1, delegate.records.size)
        assertEquals("2", delegate.records[0].id)
    }

    @Test
    fun testDeleteAllReturnsEmptyResultWhenNoRecordsInScope() = runTest {
        val delegate = TestMemoryRecordRepository()
        val scopedRepo = ScopedMemoryRecordRepository(
            delegate = delegate,
            scopeValue = "my-scope"
        )

        delegate.records.add(
            MemoryRecord(
                id = "1",
                content = "Record in different scope",
                metadata = mapOf(ScopedMemoryRecordRepository.DEFAULT_MEMORY_SCOPE_FIELD_NAME to JsonPrimitive("other-scope"))
            )
        )

        val result = scopedRepo.deleteAll(listOf("1"))

        assertEquals(0, result.successIds.size)
        assertEquals(1, delegate.records.size) // Record should not be deleted
    }

    // ==========
    // Tests for deleteByFilter() operation
    // ==========

    @Test
    fun testDeleteByFilterAppliesScopeFilter() = runTest {
        val delegate = TestMemoryRecordRepository()
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
        val delegate = TestMemoryRecordRepository()
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
        val delegate = TestMemoryRecordRepository()
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
        val delegate = TestMemoryRecordRepository()
        val scopeA = ScopedMemoryRecordRepository(delegate = delegate, scopeValue = "scope-A")
        val scopeB = ScopedMemoryRecordRepository(delegate = delegate, scopeValue = "scope-B")

        // Add similar content to both scopes
        scopeA.add(listOf(MemoryRecord(id = "a1", content = "Kotlin programming guide")))
        scopeB.add(listOf(MemoryRecord(id = "b1", content = "Kotlin programming tutorial")))

        // Search in each scope
        val resultsA = scopeA.search(SimilaritySearchRequest(query = "Kotlin"))
        val resultsB = scopeB.search(SimilaritySearchRequest(query = "Kotlin"))

        assertEquals(1, resultsA.size)
        assertEquals("a1", resultsA[0].record.id)

        assertEquals(1, resultsB.size)
        assertEquals("b1", resultsB[0].record.id)
    }
}
