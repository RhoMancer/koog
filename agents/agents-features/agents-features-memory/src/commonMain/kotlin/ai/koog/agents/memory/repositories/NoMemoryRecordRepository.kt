package ai.koog.agents.memory.repositories

import ai.koog.rag.vector.database.BatchOperationResult
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.ScoredMemoryRecord
import ai.koog.rag.vector.database.SearchRequest

public object NoMemoryRecordRepository : MemoryRecordRepository {

    override suspend fun add(records: List<MemoryRecord>): BatchOperationResult =
        BatchOperationResult(successIds = emptyList())

    override suspend fun update(records: List<MemoryRecord>): BatchOperationResult =
        BatchOperationResult(successIds = emptyList())

    override suspend fun getAll(ids: List<String>): List<MemoryRecord> = emptyList()

    override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> = emptyList()

    override suspend fun deleteAll(ids: List<String>): BatchOperationResult =
        BatchOperationResult(successIds = emptyList())

    override suspend fun deleteByFilter(filterExpression: String): Int = 0
}
