package ai.koog.rag.vector.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory implementation of [MemoryRecordRepository] that stores records in a map.
 *
 * This implementation is useful for testing, development, and scenarios where persistence
 * is not required. All data is stored in memory and will be lost when the application stops.
 *
 * ## Features:
 * - Thread-safe operations using coroutine Mutex
 * - Supports keyword search and similarity search using simple substring matching
 * - Full CRUD operations
 *
 * ## Limitations:
 * - Data is not persisted and will be lost on application restart
 * - Similarity search uses simple substring matching (not actual vector similarity)
 * - Filter expressions are not supported
 *
 * @see MemoryRecordRepository
 */
public open class EphemeralMemoryRecordRepository : MemoryRecordRepository {

    private val mutex = Mutex()
    private val records = mutableMapOf<String, MemoryRecord>()

    // ==================== CREATE/UPDATE OPERATIONS ====================

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()

        mutex.withLock {
            for (record in records) {
                try {
                    val recordId = record.id ?: Uuid.random().toString()
                    val recordWithId = if (record.id == null) record.copy(id = recordId) else record
                    this.records[recordId] = recordWithId
                    successIds.add(recordId)
                } catch (e: Exception) {
                    val id = record.id ?: "unknown"
                    failedIds[id] = e.message ?: "Unknown error"
                }
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    override suspend fun update(records: List<MemoryRecord>): BatchOperationResult {
        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()

        mutex.withLock {
            for (record in records) {
                val recordId = record.id
                if (recordId == null) {
                    failedIds["unknown"] = "Record ID is required for update"
                    continue
                }

                try {
                    if (this.records.containsKey(recordId)) {
                        this.records[recordId] = record
                        successIds.add(recordId)
                    } else {
                        failedIds[recordId] = "Record not found"
                    }
                } catch (e: Exception) {
                    failedIds[recordId] = e.message ?: "Unknown error"
                }
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    // ==================== READ OPERATIONS ====================

    override suspend fun getAll(ids: List<String>): List<MemoryRecord> {
        if (ids.isEmpty()) return emptyList()

        return mutex.withLock {
            ids.mapNotNull { id -> records[id] }
        }
    }

    // ==================== SEARCH OPERATIONS ====================

    override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
        return when (request) {
            is KeywordSearchRequest -> searchByText( // TODO: use filterExpression after switching to Filter DSL
                request.query,
                request.limit,
                request.similarityThreshold
            )
            else -> throw UnsupportedOperationException("EphemeralMemoryRecordRepository supports only KeywordSearchRequest.")
        }
    }

    private suspend fun searchByText(
        query: String,
        limit: Int,
        similarityThreshold: Double
    ): List<ScoredMemoryRecord<MemoryRecord>> {
        val allRecords = mutex.withLock { records.values.toList() }
        val queryLower = query.lowercase()

        return allRecords
            .filter { it.content.lowercase().contains(queryLower) }
            .map { record -> ScoredMemoryRecord(record, 1.0) }
            .filter { it.similarity >= similarityThreshold }
            .take(limit)
    }

    // ==================== DELETE OPERATIONS ====================

    override suspend fun deleteAll(ids: List<String>): BatchOperationResult {
        if (ids.isEmpty()) return BatchOperationResult(emptyList())

        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()

        mutex.withLock {
            for (id in ids) {
                if (records.remove(id) != null) {
                    successIds.add(id)
                } else {
                    failedIds[id] = "Record not found"
                }
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    override suspend fun deleteByFilter(filterExpression: String): Int {
        // Filter expressions are not supported in this simple implementation
        return 0
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Clears all records from the repository.
     * Useful for testing purposes.
     */
    public suspend fun clear() {
        mutex.withLock {
            records.clear()
        }
    }

    /**
     * Returns the number of records in the repository.
     */
    public suspend fun size(): Int = mutex.withLock { records.size }
}
