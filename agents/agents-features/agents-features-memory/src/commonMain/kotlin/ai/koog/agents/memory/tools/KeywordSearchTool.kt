package ai.koog.agents.memory.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.rag.vector.database.KeywordSearchRequest
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.formatResults
import kotlinx.serialization.Serializable

/**
 * Arguments for the keyword search tool.
 *
 * @property query The text query for keyword matching
 * @property limit Maximum number of results to return (default: 10)
 * @property similarityThreshold Minimum similarity score for results, from 0.0 to 1.0 (default: 0.0)
 * @property filterExpression Optional metadata filter expression for pre-filtering results
 */
@Serializable
public data class KeywordSearchArgs(
    @property:LLMDescription("The text query for keyword matching")
    val query: String,
    @property:LLMDescription("Maximum number of results to return")
    val limit: Int = 10,
    @property:LLMDescription("Minimum similarity score for results, from 0.0 to 1.0")
    val similarityThreshold: Double = 0.0,
    @property:LLMDescription("Optional metadata filter expression for pre-filtering results")
    val filterExpression: String? = null
)

/**
 * A tool that performs keyword search on memory records using traditional text matching.
 * 
 * This tool searches for memory records that contain the specified keywords
 * using full-text/keyword search instead of vector similarity search.
 *
 * @property repository The memory record repository to search in
 */
public class KeywordSearchTool(
    private val repository: MemoryRecordRepository //TODO: ScopedMemoryRecordRepository?
) : SimpleTool<KeywordSearchArgs>(
    argsSerializer = KeywordSearchArgs.serializer(),
    name = "keyword_search",
    description = "Search for memory records using keyword/full-text matching"
) {
    override suspend fun execute(args: KeywordSearchArgs): String {
        val request = KeywordSearchRequest(
            query = args.query,
            limit = args.limit,
            similarityThreshold = args.similarityThreshold,
            filterExpression = args.filterExpression
        )
        
        val results = repository.search(request)
        
        if (results.isEmpty()) {
            return "No memory records found matching keywords: ${args.query}"
        }
        
        return results.formatResults()
    }
}
