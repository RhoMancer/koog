package ai.koog.rag.vector.database

import ai.koog.embeddings.base.Embedder
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet
import java.util.*

/**
 * PostgreSQL-specific implementation of [MemoryRecordRepository] using pgvector extension
 * for efficient vector similarity search.
 *
 * This implementation stores memory records with their embeddings in PostgreSQL and uses
 * pgvector's cosine distance operator for similarity search.
 *
 * ## Features:
 * - Stores embeddings as pgvector VECTOR type for efficient similarity search
 * - Supports cosine similarity search using pgvector's `<=>` operator
 * - Stores metadata as JSONB for flexible filtering
 * - Automatic schema migration with pgvector extension creation
 * - Optional embedder for automatic embedding generation when records don't have embeddings
 *
 * ## Requirements:
 * - PostgreSQL 11+ with pgvector extension installed
 * - The pgvector extension must be available (CREATE EXTENSION vector)
 *
 * @param database The Exposed Database instance connected to PostgreSQL
 * @param tableName Name of the table to store memory records (default: "memory_records")
 * @param vectorDimension The dimension of embedding vectors (default: 1536 for OpenAI embeddings)
 * @param embedder Optional embedder for generating embeddings when records don't have them.
 *                 If null and a record without embedding is added, an exception will be thrown.
 * @param json JSON serializer for metadata
 */
public class PGVectorMemoryRecordRepository(
    private val database: Database,
    private val tableName: String = "memory_records",
    private val vectorDimension: Int = 1536,
    private val embedder: Embedder? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MemoryRecordRepository {

    private val migrator = PGVectorSchemaMigrator(database, tableName, vectorDimension)

    /**
     * Initializes the repository by running schema migrations.
     * This should be called before using the repository.
     */
    public suspend fun migrate() {
        migrator.migrate()
    }

    private suspend fun <T> dbTransaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }

    private fun getConnection() = TransactionManager.current().connection.connection as java.sql.Connection

    // ==================== CREATE/UPDATE OPERATIONS ====================

    override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()

        for (record in records) {
            val recordId = record.id ?: UUID.randomUUID().toString()
            try {
                val embedding = getOrComputeEmbedding(record)
                val embeddingStr = embedding?.let { formatVectorForPgVector(it) }
                val metadataJson = json.encodeToString(record.metadata)

                dbTransaction {
                    val conn = getConnection()
                    val sql = if (embeddingStr != null) {
                        """
                        INSERT INTO $tableName (id, content, embedding, metadata)
                        VALUES (?, ?, ?::vector, ?::jsonb)
                        ON CONFLICT (id) DO UPDATE SET
                            content = EXCLUDED.content,
                            embedding = EXCLUDED.embedding,
                            metadata = EXCLUDED.metadata
                        """.trimIndent()
                    } else {
                        """
                        INSERT INTO $tableName (id, content, embedding, metadata)
                        VALUES (?, ?, NULL, ?::jsonb)
                        ON CONFLICT (id) DO UPDATE SET
                            content = EXCLUDED.content,
                            embedding = EXCLUDED.embedding,
                            metadata = EXCLUDED.metadata
                        """.trimIndent()
                    }

                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, recordId)
                        stmt.setString(2, record.content)
                        if (embeddingStr != null) {
                            stmt.setString(3, embeddingStr)
                            stmt.setString(4, metadataJson)
                        } else {
                            stmt.setString(3, metadataJson)
                        }
                        stmt.executeUpdate()
                    }
                }
                successIds.add(recordId)
            } catch (e: Exception) {
                failedIds[recordId] = e.message ?: "Unknown error"
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    private suspend fun getOrComputeEmbedding(record: MemoryRecord): List<Float>? {
        if (record.embedding != null) {
            return record.embedding
        }
        if (embedder != null) {
            return embedder.embed(record.content).values.map { it.toFloat() } // TODO: Vector should contain float array
        }
        return null
    }

    override suspend fun update(records: List<MemoryRecord>): BatchOperationResult {
        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()

        for (record in records) {
            val recordId = record.id
            if (recordId == null) {
                failedIds[UUID.randomUUID().toString()] = "Record ID is required for update"
                continue
            }

            try {
                val embedding = getOrComputeEmbedding(record)
                val embeddingStr = embedding?.let { formatVectorForPgVector(it) }
                val metadataJson = json.encodeToString(record.metadata)

                dbTransaction {
                    val conn = getConnection()
                    val sql = if (embeddingStr != null) {
                        """
                        UPDATE $tableName 
                        SET content = ?, 
                            embedding = ?::vector,
                            metadata = ?::jsonb
                        WHERE id = ?
                        """.trimIndent()
                    } else {
                        """
                        UPDATE $tableName 
                        SET content = ?, 
                            embedding = NULL,
                            metadata = ?::jsonb
                        WHERE id = ?
                        """.trimIndent()
                    }

                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, record.content)
                        if (embeddingStr != null) {
                            stmt.setString(2, embeddingStr)
                            stmt.setString(3, metadataJson)
                            stmt.setString(4, recordId)
                        } else {
                            stmt.setString(2, metadataJson)
                            stmt.setString(3, recordId)
                        }
                        stmt.executeUpdate()
                    }
                }
                successIds.add(recordId)
            } catch (e: Exception) {
                failedIds[recordId] = e.message ?: "Unknown error"
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    // ==================== READ OPERATIONS ====================

    override suspend fun getAll(ids: List<String>): List<MemoryRecord> {
        if (ids.isEmpty()) return emptyList()

        val placeholders = ids.joinToString(",") { "?" }
        return dbTransaction {
            val conn = getConnection()
            val result = mutableListOf<MemoryRecord>()
            conn.prepareStatement(
                "SELECT id, content, embedding::text, metadata::text FROM $tableName WHERE id IN ($placeholders)"
            ).use { stmt ->
                ids.forEachIndexed { index, id ->
                    stmt.setString(index + 1, id)
                }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(parseMemoryRecord(rs))
                    }
                }
            }
            result
        }
    }

    // ==================== SEARCH OPERATIONS ====================

    override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
        return when (request) {
            is VectorSearchRequest -> searchByVectorInternal(
                request.queryVector,
                request.limit,
                request.similarityThreshold,
                request.filterExpression
            )

            is SimilaritySearchRequest -> {
                // For SimilaritySearchRequest, we need an embedder to compute the query vector
                val currentEmbedder = embedder
                    ?: throw MemoryRecordException(
                        "SimilaritySearchRequest requires an embedder to be configured. " +
                            "Either provide an embedder when creating PGVectorMemoryRecordRepository " +
                            "or use VectorSearchRequest with pre-computed embeddings instead."
                    )
                val queryVector = currentEmbedder.embed(request.query).values.map { it.toFloat() }
                searchByVectorInternal(
                    queryVector,
                    request.limit,
                    request.similarityThreshold,
                    request.filterExpression
                )
            }

            is KeywordSearchRequest -> searchByKeyword(
                request.query,
                request.limit,
                request.filterExpression
            )

            is HybridSearchRequest -> {
                // Hybrid search combines vector and keyword search
                val queryVector = request.queryVector ?: embedder?.let { emb ->
                    emb.embed(request.query).values.map { it.toFloat() }
                }
                if (queryVector != null) {
                    // If we have a vector, do vector search weighted by alpha
                    val vectorResults = searchByVectorInternal(
                        queryVector,
                        request.limit * 2,
                        request.similarityThreshold,
                        request.filterExpression
                    )
                    val keywordResults = searchByKeyword(
                        request.query,
                        request.limit * 2,
                        request.filterExpression
                    )

                    // Combine results using alpha weighting
                    combineHybridResults(vectorResults, keywordResults, request.alpha, request.limit)
                } else {
                    // Fall back to keyword search if no vector provided and no embedder
                    searchByKeyword(request.query, request.limit, request.filterExpression)
                }
            }
        }
    }

    private suspend fun searchByVectorInternal(
        queryVector: List<Float>,
        limit: Int,
        similarityThreshold: Double,
        filterExpression: String?
    ): List<ScoredMemoryRecord<MemoryRecord>> {
        val vectorStr = formatVectorForPgVector(queryVector)
        val filterClause = filterExpression?.let { " AND ($it)" } ?: ""

        return dbTransaction {
            val conn = getConnection()
            val results = mutableListOf<ScoredMemoryRecord<MemoryRecord>>()
            conn.prepareStatement(
                """
                SELECT id, content, embedding::text, metadata::text,
                       1 - (embedding <=> ?::vector) as similarity
                FROM $tableName
                WHERE embedding IS NOT NULL $filterClause
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, vectorStr)
                stmt.setString(2, vectorStr)
                stmt.setInt(3, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val similarity = rs.getDouble("similarity")
                        if (similarity >= similarityThreshold) {
                            val record = parseMemoryRecord(rs)
                            results.add(ScoredMemoryRecord(record, similarity))
                        }
                    }
                }
            }
            results
        }
    }

    private suspend fun searchByKeyword(
        query: String,
        limit: Int,
        filterExpression: String?
    ): List<ScoredMemoryRecord<MemoryRecord>> {
        val filterClause = filterExpression?.let { " AND ($it)" } ?: ""
        val searchPattern = "%${query.lowercase()}%"

        return dbTransaction {
            val conn = getConnection()
            val results = mutableListOf<ScoredMemoryRecord<MemoryRecord>>()
            conn.prepareStatement(
                """
                SELECT id, content, embedding::text, metadata::text,
                       CASE WHEN LOWER(content) LIKE ? THEN 1.0 ELSE 0.0 END as similarity
                FROM $tableName
                WHERE LOWER(content) LIKE ? $filterClause
                ORDER BY similarity DESC
                LIMIT ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, searchPattern)
                stmt.setString(2, searchPattern)
                stmt.setInt(3, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val record = parseMemoryRecord(rs)
                        val similarity = rs.getDouble("similarity")
                        results.add(ScoredMemoryRecord(record, similarity))
                    }
                }
            }
            results
        }
    }

    private fun combineHybridResults(
        vectorResults: List<ScoredMemoryRecord<MemoryRecord>>,
        keywordResults: List<ScoredMemoryRecord<MemoryRecord>>,
        alpha: Double,
        limit: Int
    ): List<ScoredMemoryRecord<MemoryRecord>> {
        val vectorScores = vectorResults.associateBy({ it.record.id }, { it.similarity })
        val keywordScores = keywordResults.associateBy({ it.record.id }, { it.similarity })

        val allRecords = (vectorResults.map { it.record } + keywordResults.map { it.record })
            .distinctBy { it.id }

        return allRecords.map { record ->
            val vectorScore = vectorScores[record.id] ?: 0.0
            val keywordScore = keywordScores[record.id] ?: 0.0
            val combinedScore = (1 - alpha) * vectorScore + alpha * keywordScore
            ScoredMemoryRecord(record, combinedScore)
        }
            .sortedByDescending { it.similarity }
            .take(limit)
    }

    // ==================== DELETE OPERATIONS ====================

    override suspend fun deleteAll(ids: List<String>): BatchOperationResult {
        if (ids.isEmpty()) return BatchOperationResult(emptyList())

        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()

        for (id in ids) {
            try {
                val deleted = dbTransaction {
                    val conn = getConnection()
                    conn.prepareStatement(
                        "DELETE FROM $tableName WHERE id = ?"
                    ).use { stmt ->
                        stmt.setString(1, id)
                        stmt.executeUpdate() > 0
                    }
                }
                if (deleted) {
                    successIds.add(id)
                } else {
                    failedIds[id] = "Record not found"
                }
            } catch (e: Exception) {
                failedIds[id] = e.message ?: "Unknown error"
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    override suspend fun deleteByFilter(filterExpression: String): Int {
        return dbTransaction {
            val conn = getConnection()
            conn.prepareStatement(
                "DELETE FROM $tableName WHERE $filterExpression"
            ).use { stmt ->
                stmt.executeUpdate()
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private fun formatVectorForPgVector(vector: List<Float>): String {
        return "[${vector.joinToString(",")}]"
    }

    private fun parseVectorFromPgVector(vectorStr: String?): List<Float>? {
        if (vectorStr == null) return null
        return vectorStr
            .trim('[', ']')
            .split(",")
            .map { it.trim().toFloat() }
    }

    private fun parseMemoryRecord(rs: ResultSet): MemoryRecord {
        val id = rs.getString("id")
        val content = rs.getString("content")
        val embeddingStr = rs.getString(3) // embedding::text
        val metadataStr = rs.getString(4) // metadata::text

        val embedding = parseVectorFromPgVector(embeddingStr)
        val metadata: Map<String, JsonPrimitive> = if (metadataStr != null && metadataStr != "null") {
            try {
                json.decodeFromString(metadataStr)
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        return MemoryRecord(
            id = id,
            content = content,
            embedding = embedding,
            metadata = metadata
        )
    }
}

/**
 * Schema migrator for PGVector memory records table.
 * Creates the pgvector extension and the memory records table with vector support.
 */
public class PGVectorSchemaMigrator(
    private val database: Database,
    private val tableName: String,
    private val vectorDimension: Int
) {
    /**
     * Runs the schema migration to create the pgvector extension and memory records table.
     */
    public suspend fun migrate() {
        transaction(database) {
            // Create pgvector extension if not exists
            exec("CREATE EXTENSION IF NOT EXISTS vector")

            // Create the memory records table
            exec(
                """
                CREATE TABLE IF NOT EXISTS $tableName (
                    id VARCHAR(255) PRIMARY KEY,
                    content TEXT NOT NULL,
                    embedding vector($vectorDimension),
                    metadata JSONB DEFAULT '{}'::jsonb
                )
                """.trimIndent()
            )

            // Create index for vector similarity search using HNSW
            exec(
                """
                CREATE INDEX IF NOT EXISTS idx_${tableName}_embedding 
                ON $tableName USING hnsw (embedding vector_cosine_ops)
                """.trimIndent()
            )

            // Create GIN index for metadata JSONB queries
            exec(
                """
                CREATE INDEX IF NOT EXISTS idx_${tableName}_metadata 
                ON $tableName USING gin (metadata)
                """.trimIndent()
            )
        }
    }
}
