package ai.koog.agents.memory.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.memory.repositories.ChatMessageRepository
import ai.koog.agents.memory.repositories.NoChatMessageRepository
import ai.koog.agents.memory.repositories.NoMemoryRecordRepository
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.SimilaritySearchRequest
import ai.koog.rag.vector.database.records
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.collections.emptyMap

/**
 * Memory feature that incorporates persistent storage of message history and memory records (documents) in vector databases
 */
@OptIn(InternalAgentsApi::class)
public class Memory2(
    private val chatMessageRepository: ChatMessageRepository,//for future use
    private val memoryRecordRepository: MemoryRecordRepository
) {

    /**
     * Configuration for the Memory2 feature.
     *
     * This class allows configuring:
     * - The message repository
     * - The record repository
     */
    public class Config : FeatureConfig() {
        /**
         * The provider that handles the actual storage and retrieval of chat messages.
         * Defaults to [NoChatMessageRepository], which doesn't store anything.
         */
        public var chatMessageRepository: ChatMessageRepository = NoChatMessageRepository

        /**
         * The provider that handles the actual storage and retrieval of memory records.
         * Defaults to [NoMemoryRecordRepository], which doesn't store anything.
         */
        public var memoryRecordRepository: MemoryRecordRepository = NoMemoryRecordRepository
    }

    /**
     * Companion object implementing agent feature, handling [Memory2] creation and installation.
     */
    public companion object Feature : AIAgentGraphFeature<Config, Memory2>, AIAgentFunctionalFeature<Config, Memory2> {
        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<Memory2> = createStorageKey<Memory2>("ai-agent-memory-feature")

        override fun createInitialConfig(): Config = Config()

        /**
         * Create a feature implementation using the provided configuration.
         */
        private fun createFeature(
            config: Config,
            pipeline: AIAgentPipeline,//TODO: should we pipeline.interceptStrategyStarting?
        ): Memory2 {
            return Memory2(config.chatMessageRepository, config.memoryRecordRepository)
        }

        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline,
        ): Memory2 = createFeature(config, pipeline)

        override fun install(
            config: Config,
            pipeline: AIAgentFunctionalPipeline,
        ): Memory2 = createFeature(config, pipeline)
    }

    /**
     * Stores string contents in the memory record repository.
     *
     * @param memoryRecordsContents The list of raw strings to store.
     * @param typeMetadataFieldName The field name in metadata for storing a simple name of the original class.
     * @param typeMetadataValue The simple name of the class of saved content. Null by default for raw strings.
     */
    @PublishedApi
    internal suspend fun storeRawMemoryContent(
        memoryRecordsContents: List<String>,
        typeMetadataFieldName: String = "class_name",
        typeMetadataValue: String? = null
    ) {
        if (memoryRecordsContents.isEmpty()) {
            return
        }

        val metadata = if (typeMetadataValue != null) {
            mapOf(typeMetadataFieldName to JsonPrimitive(typeMetadataValue))
        } else {
            emptyMap()
        }

        val memoryRecords = memoryRecordsContents.map { MemoryRecord(content = it, metadata = metadata) }

        logger.debug { "Storing ${memoryRecordsContents.size} memory record(s)" }
        val batchOperationResult = memoryRecordRepository.add(memoryRecords)
        logger.debug { "batchOperationResult: $batchOperationResult" }
    }

    /**
     * Stores memory records with type information in the memory record repository.
     *
     * @param T The type of the content to store. Must be serializable.
     * @param contents The list of typed content to store.
     * @param json The JSON serializer to use. Defaults to a lenient configuration.
     */
    public suspend inline fun <reified T> store(
        contents: List<T>,
        json: Json = Json { ignoreUnknownKeys = true }
    ) {
        if (contents.isEmpty()) {
            return
        }

        val serializer = serializer<T>()
        val jsonStrings = contents.map { content ->
            json.encodeToString(serializer, content)
        }

        storeRawMemoryContent(memoryRecordsContents = jsonStrings, typeMetadataValue = T::class.simpleName)
        //T::class.qualifiedName is not supported in Kotlin/JS
    }


    /**
     * Internal method that searches for memory records matching the given query.
     *
     * @param query The search query string.
     * @param topK The maximum number of results to return.
     * @param scoreThreshold The minimum score threshold for results (default 0.0).
     * @param filterExpression Metadata filter expression.
     * @return A list of strings from the memory repository that matches the search criteria.
     */
    @PublishedApi
    internal suspend fun searchRawMemoryContent(
        query: String,
        topK: Int,
        scoreThreshold: Double = 0.0,
        filterExpression: String? = null
    ): List<String> {
        logger.debug { "Searching memory records with query: ${query.shortened()}" }

        val memoryRecords = memoryRecordRepository.search(
            SimilaritySearchRequest(
                query,
                topK,
                scoreThreshold,
                filterExpression
            )
        ).records()

        return memoryRecords.map { it.content }
    }

    /**
     * Searches for typed memory records matching the given query.
     *
     * This function searches for memory records and deserializes their content
     * to the specified type. Records that fail to deserialize are skipped.
     *
     * @param T The type to deserialize the content to. Must be serializable.
     * @param query The search query string.
     * @param topK The maximum number of results to return.
     * @param scoreThreshold The minimum score threshold for results (default 0.0).
     * @param filterExpression Metadata filter expression.
     * @param json The JSON deserializer to use. Defaults to a lenient configuration.
     * @return A list of deserialized content matching the search criteria.
     */
    public suspend inline fun <reified T> search(
        query: String,
        topK: Int,
        scoreThreshold: Double = 0.0,
        filterExpression: String? = null,
        json: Json = Json { ignoreUnknownKeys = true }
    ): List<T> {
        val memoryRecordContents =
            searchRawMemoryContent(query, topK, scoreThreshold, filterExpression) // TODO: should filter by type
        val serializer = serializer<T>()

        return memoryRecordContents.mapNotNull { content ->
            try {
                json.decodeFromString(serializer, content)
            } catch (e: Exception) {
                null // TODO: log warning about unserialized records
            }
        }
    }
}

/**
 * Utility function to shorten a string for display purposes.
 * Takes the first line and truncates it to 100 characters.
 */
private fun String.shortened() = lines().first().take(100) + "..."

/**
 * Extension function to access the Memory2 feature from a AIAgentStageContext.
 *
 * This provides a convenient way to access memory operations within agent nodes.
 *
 * Example usage:
 * ```kotlin
 * val rememberUserPreference by node {
 *     // Access memory directly
 *     val memory = stageContext.memory2()
 *     // Use memory operations...
 * }
 * ```
 *
 * @return The Memory2 instance for this agent context
 */
public fun AIAgentContext.memory2(): Memory2 = featureOrThrow(Memory2)

/**
 * Extension function to perform memory operations within a AIAgentStageContext.
 *
 * This provides a convenient way to use memory operations within agent nodes
 * with a more concise syntax using the `withMemory` block.
 *
 * Example usage:
 * ```kotlin
 * val loadUserPreferences by node {
 *     // Use memory operations in a block
 *     stageContext.withMemory2 {
 *         loadMemoryRecordsToChat(
 *             searchQuery = = "User's preferred programming language")
 *         )
 *     }
 * }
 * ```
 *
 * @param action The memory operations to perform
 * @return The result of the action
 */
public suspend fun <T> AIAgentContext.withMemory2(action: suspend Memory2.() -> T): T = memory2().action()
