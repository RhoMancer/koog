package ai.koog.rag.vector.database

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive

/**
 * A decorator for [MemoryRecordRepository] that automatically applies a memory scope ID
 * to all operations.
 *
 * This class wraps an existing [MemoryRecordRepository] and:
 * - For [add] and [update] operations: adds the [scopeValue] as a metadata field to all records
 * - For [search] operations: adds a filter expression to only return records matching the [scopeValue]
 *
 * @property delegate The underlying [MemoryRecordRepository] to delegate operations to
 * @property scopeKey The metadata field name used to store the memory scope ID, defaults to [DEFAULT_MEMORY_SCOPE_FIELD_NAME]
 * @property scopeValue The scope ID to apply to all operations
 */
public class ScopedMemoryRecordRepository(
    private val delegate: MemoryRecordRepository,
    private val scopeKey: String = DEFAULT_MEMORY_SCOPE_FIELD_NAME,
    private val scopeValue: String
) : MemoryRecordRepository by delegate {

    /**
     * The companion object with constants.
     */
    public companion object {
        /**
         * The default metadata field name used to store the memory scope ID.
         */
        public const val DEFAULT_MEMORY_SCOPE_FIELD_NAME: String = "memory_scope_id"
    }

    /**
     * Adds memory records to the store.
     * The [scopeValue] will be added as a metadata field to each record.
     *
     * @param records The records to add
     * @return Result containing successful and failed record IDs
     */
    override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
        val scopedRecords = applyMemoryScopeToRecords(records)
        return delegate.add(scopedRecords)
    }

    /**
     * Updates existing records in the store.
     * The [scopeValue] will be added as a metadata field to each record.
     * Records must have non-null IDs.
     *
     * @param records The records to update
     * @return Result containing successful and failed record IDs
     */
    override suspend fun update(records: List<MemoryRecord>): BatchOperationResult {
        val scopedRecords = applyMemoryScopeToRecords(records)
        return delegate.update(scopedRecords)
    }

    /**
     * Retrieves multiple records by their IDs.
     * Only returns records that belong to this scope.
     *
     * @param ids The record IDs
     * @return List of found records that belong to this scope
     */
    override suspend fun getAll(ids: List<String>): List<MemoryRecord> {
        return delegate.getAll(ids).filter { record ->
            record.metadata[scopeKey]?.let { it == JsonPrimitive(scopeValue) } ?: false
        }
    }

    /**
     * Performs a search and returns results with similarity scores.
     * The [scopeValue] will be added to the filter expression to only
     * return records matching the scope.
     *
     * @param request The search request with all parameters
     * @return List of scored records, sorted by similarity (highest first)
     */
    override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
        val scopedRequest = applyMemoryScopeToSearchRequest(request)
        return delegate.search(scopedRequest)
    }

    /**
     * Streams search results for large result sets.
     * The [scopeValue] will be added to the filter expression to only
     * return records matching the scope.
     *
     * @param request The search request
     * @return Flow of scored records
     */
    override fun searchAsFlow(request: SearchRequest): Flow<ScoredMemoryRecord<MemoryRecord>> {
        val scopedRequest = applyMemoryScopeToSearchRequest(request)
        return delegate.searchAsFlow(scopedRequest)
    }

    /**
     * Deletes multiple records by their IDs.
     * Only deletes records that belong to this scope.
     *
     * @param ids The record IDs to delete
     * @return Result containing successful and failed deletions
     */
    override suspend fun deleteAll(ids: List<String>): BatchOperationResult {
        // First, get the records to verify they belong to this scope
        val recordsInScope = delegate.getAll(ids).filter { record ->
            record.metadata[scopeKey]?.let { it == JsonPrimitive(scopeValue) } ?: false
        }
        val idsInScope = recordsInScope.mapNotNull { it.id }

        return if (idsInScope.isEmpty()) {
            BatchOperationResult(successIds = emptyList())
        } else {
            delegate.deleteAll(idsInScope)
        }
    }

    /**
     * Deletes records matching a filter expression.
     * The [scopeValue] will be added to the filter expression to only
     * delete records matching the scope.
     *
     * @param filterExpression The filter expression
     * @return Number of records deleted
     */
    override suspend fun deleteByFilter(filterExpression: String): Int {
        val scopedFilter = applyMemoryScopeToFilterExpression(filterExpression)
        return delegate.deleteByFilter(scopedFilter)
    }

    /**
     * Applies the memory scope ID to a list of records.
     */
    private fun applyMemoryScopeToRecords(records: List<MemoryRecord>): List<MemoryRecord> {
        return records.map { applyMemoryScopeToRecord(it) }
    }

    /**
     * Applies the memory scope ID to a single record.
     */
    private fun applyMemoryScopeToRecord(record: MemoryRecord): MemoryRecord {
        return record.copy(
            metadata = record.metadata + (scopeKey to JsonPrimitive(scopeValue))
        )
    }

    /**
     * Applies the memory scope ID filter to a search request.
     */
    private fun applyMemoryScopeToSearchRequest(request: SearchRequest): SearchRequest {
        val combinedFilter = applyMemoryScopeToFilterExpression(request.filterExpression)

        return when (request) {
            is SimilaritySearchRequest -> request.copy(filterExpression = combinedFilter)
            is VectorSearchRequest -> request.copy(filterExpression = combinedFilter)
            is KeywordSearchRequest -> request.copy(filterExpression = combinedFilter)
            is HybridSearchRequest -> request.copy(filterExpression = combinedFilter)
        }
    }

    /**
     * Applies the memory scope ID filter to a filter expression.
     */
    private fun applyMemoryScopeToFilterExpression(filterExpression: String?): String {
        val scopeFilter = "$scopeKey == \"$scopeValue\""
        return when {
            filterExpression.isNullOrBlank() -> scopeFilter
            else -> "($filterExpression) AND ($scopeFilter)"
        }
    }
}
