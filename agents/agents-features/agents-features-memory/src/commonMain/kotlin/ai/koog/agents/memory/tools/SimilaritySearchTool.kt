package ai.koog.agents.memory.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.SimilaritySearchRequest
import ai.koog.rag.vector.database.formatResults
import kotlinx.serialization.Serializable

/**
 * Arguments for the similarity search tool.
 *
 * @property query The text query to search for similar memory records
 * @property limit Maximum number of results to return (default: 10)
 * @property similarityThreshold Minimum similarity score for results, from 0.0 to 1.0 (default: 0.0)
 * @property filterExpression Optional metadata filter expression for pre-filtering results
 */
@Serializable
public data class SimilaritySearchArgs(
    @property:LLMDescription("The text query to search for similar memory records")
    val query: String,
    @property:LLMDescription("Maximum number of results to return")
    val limit: Int = 10,
    @property:LLMDescription("Minimum similarity score for results, from 0.0 to 1.0")
    val similarityThreshold: Double = 0.0,
    @property:LLMDescription("Optional metadata filter expression for pre-filtering results")
    val filterExpression: String? = null
)

/**
 * A tool that performs similarity search on memory records using vector embeddings.
 * 
 * This tool searches for memory records that are semantically similar to the provided query
 * by using vector similarity search. The query text is embedded and compared against
 * stored memory record embeddings.
 *
 * @property repository The memory record repository to search in
 */
public class SimilaritySearchTool(
    private val repository: MemoryRecordRepository
) : SimpleTool<SimilaritySearchArgs>(
    argsSerializer = SimilaritySearchArgs.serializer(),
    name = "similarity_search",
    description = "Search for memory records that are semantically similar to the provided query using vector similarity search"
) {
    override suspend fun execute(args: SimilaritySearchArgs): String {
        val request = SimilaritySearchRequest(
            query = args.query,
            limit = args.limit,
            similarityThreshold = args.similarityThreshold,
            filterExpression = args.filterExpression
        )

        val results = repository.search(request)

        if (results.isEmpty()) {
            return "No similar memory records found for query: ${args.query}"
        }

        return results.formatResults()
    }
}
