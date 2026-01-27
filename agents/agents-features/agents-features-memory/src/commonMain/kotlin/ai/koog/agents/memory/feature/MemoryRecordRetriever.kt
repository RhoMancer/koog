package ai.koog.agents.memory.feature

import ai.koog.rag.vector.database.HybridSearchRequest
import ai.koog.rag.vector.database.KeywordSearchRequest
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.ScoredMemoryRecord
import ai.koog.rag.vector.database.SimilaritySearchRequest
import kotlin.jvm.JvmStatic

/**
 * Retriever of memory records during prompt augmentation.
 *
 * This is a functional interface (SAM) that can be implemented as a lambda
 * in both Kotlin and Java. It provides flexibility in how memory searches
 * are performed while maintaining type safety.
 *
 * Pre-built implementations are available for common search types:
 * - [SimilarityRecordRetriever] - Vector similarity search (semantic search)
 * - [KeywordRecordRetriever] - Full-text/keyword search
 * - [HybridRecordRetriever] - Combined vector and keyword search
 *
 * ### Usage Examples
 *
 * **Using pre-built retrievers (Kotlin):**
 * ```kotlin
 * // Similarity search with default parameters
 * val retriever = MemoryRecordRetriever.similarity()
 *
 * // Keyword search with custom topK
 * val keywordRetriever = MemoryRecordRetriever.keyword(topK = 5)
 *
 * // Hybrid search with custom alpha (balance between vector and keyword)
 * val hybridRetriever = MemoryRecordRetriever.hybrid(
 *     topK = 10,
 *     similarityThreshold = 0.7,
 *     alpha = 0.6
 * )
 * ```
 *
 * **Custom implementation as lambda (Kotlin):**
 * ```kotlin
 * val customRetriever = MemoryRecordRetriever { repository, query ->
 *     repository.search(SimilaritySearchRequest(
 *         query = query,
 *         topK = 5,
 *         similarityThreshold = 0.8
 *     ))
 * }
 * ```
 *
 * **Using pre-built retrievers (Java):**
 * ```java
 * // Similarity search with default parameters
 * MemoryRecordRetriever retriever = MemoryRecordRetriever.similarity();
 *
 * // Keyword search with custom topK
 * MemoryRecordRetriever keywordRetriever = MemoryRecordRetriever.keyword(5, 0.0, null);
 *
 * // Hybrid search with custom parameters
 * MemoryRecordRetriever hybridRetriever = MemoryRecordRetriever.hybrid(10, 0.7, 0.6, null);
 * ```
 *
 * **Custom implementation as lambda (Java):**
 * ```java
 * MemoryRecordRetriever customRetriever = (repository, query) ->
 *     repository.search(new SimilaritySearchRequest(query, 5, 0.8, null));
 * ```
 */
public fun interface MemoryRecordRetriever {
    /**
     * Searches the repository for relevant memory records.
     *
     * @param repository The memory record repository to search
     * @param query The user's query string (typically the last user message content)
     * @return List of scored memory records, sorted by relevance
     */
    public suspend fun retrieve(
        repository: MemoryRecordRepository,
        query: String
    ): List<ScoredMemoryRecord<MemoryRecord>>

    /**
     * Pre-defined retrievers.
     */
    public companion object {
        /**
         * Creates a similarity search mode with the given parameters.
         * Uses vector similarity (semantic) search.
         *
         * @param topK Maximum number of results to return
         * @param similarityThreshold Minimum similarity score (0.0 to 1.0)
         * @param filterExpression Optional metadata filter expression
         * @return A configured similarity search mode
         */
        @JvmStatic
        public fun similarity(
            topK: Int = 10,
            similarityThreshold: Double = 0.0,
            filterExpression: String? = null
        ): MemoryRecordRetriever = SimilarityRecordRetriever(topK, similarityThreshold, filterExpression)

        /**
         * Creates a keyword search mode with the given parameters.
         * Uses full-text/keyword matching.
         *
         * @param topK Maximum number of results to return
         * @param similarityThreshold Minimum similarity score (0.0 to 1.0)
         * @param filterExpression Optional metadata filter expression
         * @return A configured keyword search mode
         */
        @JvmStatic
        public fun keyword(
            topK: Int = 10,
            similarityThreshold: Double = 0.0,
            filterExpression: String? = null
        ): MemoryRecordRetriever = KeywordRecordRetriever(topK, similarityThreshold, filterExpression)

        /**
         * Creates a hybrid search mode with the given parameters.
         * Combines vector similarity and keyword search.
         *
         * @param topK Maximum number of results to return
         * @param similarityThreshold Minimum similarity score (0.0 to 1.0)
         * @param alpha Balance between vector (0.0) and keyword (1.0) search. Default 0.5 for equal weight.
         * @param filterExpression Optional metadata filter expression
         * @return A configured hybrid search mode
         */
        @JvmStatic
        public fun hybrid(
            topK: Int = 10,
            similarityThreshold: Double = 0.0,
            alpha: Double = 0.5,
            filterExpression: String? = null
        ): MemoryRecordRetriever = HybridRecordRetriever(topK, similarityThreshold, alpha, filterExpression)
    }
}

/**
 * Similarity search mode using vector embeddings for semantic search.
 *
 * This mode converts the query to a vector embedding and finds records
 * with similar embeddings in the vector store.
 *
 * @property topK Maximum number of results to return
 * @property similarityThreshold Minimum similarity score (0.0 to 1.0)
 * @property filterExpression Optional metadata filter expression for pre-filtering
 */
public class SimilarityRecordRetriever(
    public val topK: Int = 10,
    public val similarityThreshold: Double = 0.0,
    public val filterExpression: String? = null
) : MemoryRecordRetriever {
    override suspend fun retrieve(
        repository: MemoryRecordRepository,
        query: String
    ): List<ScoredMemoryRecord<MemoryRecord>> =
        repository.search(SimilaritySearchRequest(query, topK, similarityThreshold, filterExpression))
}

/**
 * Keyword search mode using full-text/lexical matching.
 *
 * This mode uses traditional text matching instead of vector similarity,
 * which can be useful for exact term matching or when semantic search
 * is not needed.
 *
 * @property topK Maximum number of results to return
 * @property similarityThreshold Minimum similarity score (0.0 to 1.0)
 * @property filterExpression Optional metadata filter expression for pre-filtering
 */
public class KeywordRecordRetriever(
    public val topK: Int = 10,
    public val similarityThreshold: Double = 0.0,
    public val filterExpression: String? = null
) : MemoryRecordRetriever {
    override suspend fun retrieve(
        repository: MemoryRecordRepository,
        query: String
    ): List<ScoredMemoryRecord<MemoryRecord>> =
        repository.search(KeywordSearchRequest(query, topK, similarityThreshold, filterExpression))
}

/**
 * Hybrid search mode combining vector similarity and keyword search.
 *
 * This mode balances semantic understanding (vector search) with exact
 * term matching (keyword search), often providing better results than
 * either approach alone.
 *
 * @property topK Maximum number of results to return
 * @property similarityThreshold Minimum similarity score (0.0 to 1.0)
 * @property alpha Balance between vector (0.0) and keyword (1.0) search. Default 0.5 for equal weight.
 * @property filterExpression Optional metadata filter expression for pre-filtering
 */
public class HybridRecordRetriever(
    public val topK: Int = 10,
    public val similarityThreshold: Double = 0.0,
    public val alpha: Double = 0.5,
    public val filterExpression: String? = null
) : MemoryRecordRetriever {
    init {
        require(alpha in 0.0..1.0) { "Alpha must be between 0.0 and 1.0, got $alpha" }
    }

    override suspend fun retrieve(
        repository: MemoryRecordRepository,
        query: String
    ): List<ScoredMemoryRecord<MemoryRecord>> =
        repository.search(HybridSearchRequest(query, null, alpha, topK, similarityThreshold, filterExpression))
}
