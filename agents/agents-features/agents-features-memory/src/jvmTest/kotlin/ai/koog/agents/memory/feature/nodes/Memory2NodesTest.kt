package ai.koog.agents.memory.feature.nodes

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.memory.feature.Memory2
import ai.koog.agents.memory.repositories.NoChatMessageRepository
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.rag.vector.database.BatchOperationResult
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.ScoredMemoryRecord
import ai.koog.rag.vector.database.SearchRequest
import ai.koog.rag.vector.database.SimilaritySearchRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for MemoryRecordNodes functions
 */
@OptIn(InternalAgentsApi::class)
class Memory2NodesTest {

    /**
     * Test implementation of MemoryRecordRepository for testing purposes
     */
    internal class TestMemoryRecordRepository : MemoryRecordRepository {
        val records = mutableListOf<MemoryRecord>()

        override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
            println("[DEBUG_LOG] Adding ${records.size} records to repository")
            records.forEach { record ->
                println("[DEBUG_LOG] Adding record: ${record.content}")
            }
            this.records.addAll(records)
            println("[DEBUG_LOG] Total records now: ${this.records.size}")
            return BatchOperationResult(records.mapNotNull { it.id ?: "no-id" })
        }

        override suspend fun update(records: List<MemoryRecord>): BatchOperationResult =
            BatchOperationResult(emptyList())

        override suspend fun getAll(ids: List<String>): List<MemoryRecord> =
            records.filter { it.id in ids }

        override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
            val query = when (request) {
                is SimilaritySearchRequest -> request.query
                else -> ""
            }
            println("[DEBUG_LOG] Searching for: $query")
            val results = records.filter { it.content.contains(query, ignoreCase = true) }
            println("[DEBUG_LOG] Found ${results.size} results")
            return results.map { ScoredMemoryRecord(it, 1.0) }
        }

        override suspend fun deleteAll(ids: List<String>): BatchOperationResult =
            BatchOperationResult(emptyList())

        override suspend fun deleteByFilter(filterExpression: String): Int = 0
    }

    private fun createMockExecutor() = getMockExecutor {
        mockLLMAnswer("Extracted fact: User prefers Kotlin programming language") onRequestContains "Extract"
        mockLLMAnswer("Default response").asDefaultResponse
    }

    @Test
    fun testMemoryRecordRepositorySearchWithMultipleMatches() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        // Add multiple records that match the same query
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "User prefers Kotlin for backend development"),
                MemoryRecord(content = "User uses Kotlin for Android apps"),
                MemoryRecord(content = "User likes Python for data science")
            )
        )

        val kotlinResults = memoryRepository.search(SimilaritySearchRequest("Kotlin"))
        assertEquals(2, kotlinResults.size, "Should find 2 Kotlin-related records")

        val pythonResults = memoryRepository.search(SimilaritySearchRequest("Python"))
        assertEquals(1, pythonResults.size, "Should find 1 Python-related record")

        val userResults = memoryRepository.search(SimilaritySearchRequest("User"))
        assertEquals(3, userResults.size, "Should find all 3 records containing 'User'")
    }

    // ==========
    // Tests for nodeRetrieveFromMemoryAndAugment (RAG scenarios)
    // ==========

    /**
     * Test implementation of MemoryRecordRepository that supports topK limiting for RAG scenarios.
     * Uses word-based matching to simulate semantic search behavior.
     */
    internal class TopKMemoryRecordRepository : MemoryRecordRepository {
        val records = mutableListOf<MemoryRecord>()

        override suspend fun add(records: List<MemoryRecord>): BatchOperationResult {
            println("[DEBUG_LOG] Adding ${records.size} records to TopK repository")
            this.records.addAll(records.map { MemoryRecord(content = it.content, metadata = mapOf("memory_scope_id" to JsonPrimitive("test-agent"))) })
            return BatchOperationResult(records.mapNotNull { it.id ?: "no-id" })
        }

        override suspend fun update(records: List<MemoryRecord>): BatchOperationResult =
            BatchOperationResult(emptyList())

        override suspend fun getAll(ids: List<String>): List<MemoryRecord> =
            records.filter { it.id in ids }

        override suspend fun search(request: SearchRequest): List<ScoredMemoryRecord<MemoryRecord>> {
            val query = when (request) {
                is SimilaritySearchRequest -> request.query
                else -> ""
            }
            println("[DEBUG_LOG] Searching for: $query with limit: ${request.limit}")
            // Split query into words and check if any word matches content (simulating semantic search)
            val queryWords = query.lowercase().split(Regex("\\s+"))
                .filter { it.length > 2 } // Filter out short words like "me", "a", "is"
            println("[DEBUG_LOG] Query words: $queryWords")
            val matchingRecords = records.filter { record ->
                val contentLower = record.content.lowercase()
                queryWords.any { word -> contentLower.contains(word) }
            }
            val results = matchingRecords.take(request.limit)
            println("[DEBUG_LOG] Found ${matchingRecords.size} matches, returning ${results.size} results")
            return results.map { ScoredMemoryRecord(it, 1.0) }
        }

        override suspend fun deleteAll(ids: List<String>): BatchOperationResult =
            BatchOperationResult(emptyList())

        override suspend fun deleteByFilter(filterExpression: String): Int = 0
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentBasic() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()

        // Pre-populate the repository with relevant context
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "Kotlin is a modern programming language developed by JetBrains"),
                MemoryRecord(content = "Kotlin supports both object-oriented and functional programming paradigms")
            )
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 5,
                name = "retrieveAndAugment"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val result = agent.run("Tell me about Kotlin")

        // Verify that the prompt was augmented with relevant context
        assertTrue(result.contains("Relevant context:"), "Result should contain 'Relevant context:' prefix")
        assertTrue(result.contains("Kotlin"), "Result should contain the original query")
        assertTrue(result.contains("JetBrains") || result.contains("programming"), "Result should contain memory content")
        println("[DEBUG_LOG] Augmented result: $result")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentEmptyMemory() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()
        // Repository is empty - no records

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 5,
                name = "retrieveAndAugmentEmpty"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val userPrompt = "What is machine learning?"
        val result = agent.run(userPrompt)

        // When no records found, the original prompt should be returned unchanged
        assertEquals(userPrompt, result, "Result should be the original prompt when no memory records found")
        println("[DEBUG_LOG] Result with empty memory: $result")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentBlankPrompt() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()
        memoryRepository.add(
            listOf(MemoryRecord(content = "Some context that should not be retrieved"))
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 5,
                name = "retrieveAndAugmentBlank"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val blankPrompt = "   "
        val result = agent.run(blankPrompt)

        // When prompt is blank, it should be returned unchanged (no memory retrieval)
        assertEquals(blankPrompt, result, "Blank prompt should be returned unchanged")
        println("[DEBUG_LOG] Result with blank prompt: '$result'")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentTopKLimit() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()

        // Add multiple records about machine learning
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "Machine learning fact 1: Machine learning was coined in 1959"),
                MemoryRecord(content = "Machine learning fact 2: Supervised learning uses labeled data"),
                MemoryRecord(content = "Machine learning fact 3: Deep learning uses neural networks"),
                MemoryRecord(content = "Machine learning fact 4: Natural language processing uses machine learning"),
                MemoryRecord(content = "Machine learning fact 5: Computer vision uses machine learning")
            )
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 2, // Only retrieve top 2 records
                name = "retrieveAndAugmentTopK"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val result = agent.run("Tell me about machine learning")

        // Verify that the result contains context but is limited by topK
        assertTrue(result.contains("Relevant context:"), "Result should contain 'Relevant context:' prefix")
        assertTrue(result.contains("Machine learning fact 1"), "Result should contain first fact")
        assertTrue(result.contains("Machine learning fact 2"), "Result should contain second fact")
        // Due to topK=2, facts 3, 4, 5 should not be included
        assertTrue(!result.contains("Machine learning fact 3"), "Result should NOT contain third fact (topK limit)")
        println("[DEBUG_LOG] TopK limited result: $result")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentRAGWorkflow() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()

        // Simulate a knowledge base with domain-specific information
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "Company policy: All employees must complete security training annually"),
                MemoryRecord(content = "Company policy: Remote work is allowed up to 3 days per week"),
                MemoryRecord(content = "Company policy: Vacation requests must be submitted 2 weeks in advance"),
                MemoryRecord(content = "Technical documentation: The API uses OAuth 2.0 for authentication"),
                MemoryRecord(content = "Technical documentation: Rate limiting is set to 100 requests per minute")
            )
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 3,
                name = "ragRetrieval"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant that answers questions based on company knowledge base")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        // Query about company policy
        val policyResult = agent.run("What is the company policy on remote work?")

        assertTrue(policyResult.contains("Relevant context:"), "Policy result should contain context prefix")
        assertTrue(policyResult.contains("policy"), "Policy result should contain policy-related content")
        assertTrue(policyResult.contains("remote work"), "Policy result should contain the original query")
        println("[DEBUG_LOG] RAG policy result: $policyResult")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentNoMatchingRecords() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()

        // Add records that won't match the query "blockchain cryptocurrency"
        // These records contain completely unrelated topics
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "Cats are popular pets worldwide"),
                MemoryRecord(content = "Recipe for chocolate cake with vanilla frosting"),
                MemoryRecord(content = "Weather forecast shows sunny skies tomorrow")
            )
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 5,
                name = "retrieveAndAugmentNoMatch"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        // Query with words that don't appear in any records
        val userPrompt = "Explain blockchain cryptocurrency"
        val result = agent.run(userPrompt)

        // When no matching records found, the original prompt should be returned
        assertEquals(userPrompt, result, "Result should be the original prompt when no matching records found")
        println("[DEBUG_LOG] Result with no matching records: $result")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentMultipleContextRecords() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()

        // Add multiple related records
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "Python is a high-level programming language known for its readability"),
                MemoryRecord(content = "Python was created by Guido van Rossum and first released in 1991"),
                MemoryRecord(content = "Python supports multiple programming paradigms including procedural, object-oriented, and functional")
            )
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 10,
                name = "retrieveAndAugmentMultiple"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val result = agent.run("Tell me everything about Python programming")

        // Verify all matching records are included in the augmented prompt
        assertTrue(result.contains("Relevant context:"), "Result should contain context prefix")
        assertTrue(result.contains("high-level programming language"), "Result should contain first record content")
        assertTrue(result.contains("Guido van Rossum"), "Result should contain second record content")
        assertTrue(result.contains("multiple programming paradigms"), "Result should contain third record content")
        assertTrue(result.contains("Python programming"), "Result should contain the original query")
        println("[DEBUG_LOG] Multiple context result: $result")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentMaxContextLength() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()

        // Add multiple records with known lengths
        // Record 1: 50 chars, Record 2: 50 chars, Record 3: 50 chars
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "Kotlin fact 1: Kotlin is a modern language....."), // ~50 chars
                MemoryRecord(content = "Kotlin fact 2: Kotlin runs on the JVM platform."), // ~50 chars
                MemoryRecord(content = "Kotlin fact 3: Kotlin supports null safety....") // ~50 chars
            )
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 10,
                maxContextLength = 100, // Only allow ~100 chars of context (should include first 2 records)
                name = "retrieveAndAugmentMaxContext"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val result = agent.run("Tell me about Kotlin")

        // Verify context is limited by maxContextLength
        assertTrue(result.contains("Relevant context:"), "Result should contain context prefix")
        assertTrue(result.contains("Kotlin fact 1"), "Result should contain first record")
        assertTrue(result.contains("Kotlin fact 2"), "Result should contain second record")
        // Third record should be excluded due to maxContextLength limit
        assertTrue(!result.contains("Kotlin fact 3"), "Result should NOT contain third record (maxContextLength limit)")
        assertTrue(result.contains("Tell me about Kotlin"), "Result should contain the original query")
        println("[DEBUG_LOG] MaxContextLength limited result: $result")
    }

    @Test
    fun testNodeRetrieveFromMemoryAndAugmentMaxContextLengthFirstRecordTooLarge() = runTest {
        val memoryRepository = TopKMemoryRecordRepository()

        // Add a record that exceeds maxContextLength
        memoryRepository.add(
            listOf(
                MemoryRecord(content = "This is a very long Kotlin description that exceeds the maximum context length limit and should cause the function to return the original prompt without any context augmentation")
            )
        )

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val retrieveAndAugment by nodeRetrieveFromMemoryAndAugment(
                topK = 10,
                maxContextLength = 50, // Very small limit - first record exceeds this
                name = "retrieveAndAugmentTooLarge"
            )

            edge(nodeStart forwardTo retrieveAndAugment)
            edge(retrieveAndAugment forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val userPrompt = "Tell me about Kotlin"
        val result = agent.run(userPrompt)

        // When first record exceeds maxContextLength, original prompt should be returned
        assertEquals(userPrompt, result, "Result should be the original prompt when first record exceeds maxContextLength")
        println("[DEBUG_LOG] Result when first record too large: $result")
    }

    // ==========
    // Tests for nodeExtractAndSaveMemoryRecord
    // ==========

    @Serializable
    data class UserPreference(
        val topic: String,
        val preference: String
    )

    @Serializable
    data class ConversationSummary(
        val mainTopics: List<String>,
        val sentiment: String
    )

    private fun createMockExecutorForExtraction() = getMockExecutor {
        // Match against specific content in the XML conversation (last message)
        mockLLMAnswer("""{"topic":"programming","preference":"Kotlin"}""") onRequestContains "programming in Kotlin"
        mockLLMAnswer("""{"topic":"programming","preference":"Kotlin"}""") onRequestContains "backend development"
        mockLLMAnswer("""{"mainTopics":["weather","travel"],"sentiment":"positive"}""") onRequestContains "weather"
        mockLLMAnswer("Default response").asDefaultResponse
    }

    @Test
    fun testNodeExtractAndSaveMemoryRecordBasic() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val extractAndSave by nodeExtractAndSaveMemoryRecord<String, UserPreference>(
                extractionPrompt = "Extract user preferences from the conversation",
                name = "extractAndSave"
            )

            edge(nodeStart forwardTo extractAndSave)
            edge(extractAndSave forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
                user("I really love programming in Kotlin")
                assistant("That's great! Kotlin is a wonderful language.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForExtraction(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "test input"
        val result = agent.run(input)

        // Verify the input is passed through unchanged
        assertEquals(input, result, "Input should be passed through unchanged")

        // Verify that a record was saved to the repository
        assertEquals(1, memoryRepository.records.size, "One record should be saved")
        assertTrue(memoryRepository.records[0].content.contains("topic"), "Record should contain extracted data")
        println("[DEBUG_LOG] Saved record: ${memoryRepository.records[0].content}")
    }

    @Test
    fun testNodeExtractAndSaveMemoryRecordWithCustomRoles() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val extractAndSave by nodeExtractAndSaveMemoryRecord<String, UserPreference>(
                extractionPrompt = "Extract user preferences from the conversation",
                messageRoles = setOf(Message.Role.User), // Only include User messages
                name = "extractUserOnly"
            )

            edge(nodeStart forwardTo extractAndSave)
            edge(extractAndSave forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
                user("I prefer Kotlin for backend development")
                assistant("Kotlin is excellent for backend!")
                user("Yes, especially with coroutines")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForExtraction(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "filter test"
        val result = agent.run(input)

        assertEquals(input, result, "Input should be passed through unchanged")
        assertEquals(1, memoryRepository.records.size, "One record should be saved")
        println("[DEBUG_LOG] Saved record with User-only filter: ${memoryRepository.records[0].content}")
    }

    @Test
    fun testNodeExtractAndSaveMemoryRecordPassesThroughInput() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-agent", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val extractAndSave by nodeExtractAndSaveMemoryRecord<String, ConversationSummary>(
                extractionPrompt = "Summarize the main topics discussed",
                name = "extractSummary"
            )

            edge(nodeStart forwardTo extractAndSave)
            edge(extractAndSave forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
                user("What's the weather like today?")
                assistant("It's sunny and warm!")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForExtraction(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val complexInput = "This is a complex input that should be preserved exactly as-is"
        val result = agent.run(complexInput)

        assertEquals(complexInput, result, "Complex input should be passed through exactly unchanged")
        assertTrue(memoryRepository.records.isNotEmpty(), "At least one record should be saved")
        println("[DEBUG_LOG] Input preserved: $result")
    }

    // ==========================================
    // Tests for nodeLLMTransformPrompt
    // ==========================================

    private fun createMockExecutorForTransformation() = getMockExecutor {
        // Mock LLM responses for transformation tests
        mockLLMAnswer("TRANSFORMED: Hello World") onRequestContains "Hello World"
        mockLLMAnswer("REWRITTEN: What is the weather?") onRequestContains "weather"
        mockLLMAnswer("SUMMARIZED: Brief summary") onRequestContains "summarize"
        mockLLMAnswer("Default transformation response").asDefaultResponse
    }

    @Test
    fun testNodeLLMTransformPromptWithValidPrompt() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-transform", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val transformNode by nodeLLMTransformPrompt(
                transformationPrompt = "Transform the following input by adding 'TRANSFORMED:' prefix",
                name = "transform"
            )

            edge(nodeStart forwardTo transformNode)
            edge(transformNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForTransformation(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "Hello World"
        val result = agent.run(input)

        println("[DEBUG_LOG] Transform result: $result")
        assertEquals("TRANSFORMED: Hello World", result, "Input should be transformed by LLM")
    }

    @Test
    fun testNodeLLMTransformPromptWithNullPrompt() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-transform-null", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val transformNode by nodeLLMTransformPrompt(
                transformationPrompt = null,
                name = "transform-null"
            )

            edge(nodeStart forwardTo transformNode)
            edge(transformNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForTransformation(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "Original input should pass through"
        val result = agent.run(input)

        println("[DEBUG_LOG] Null prompt result: $result")
        assertEquals(input, result, "Input should pass through unchanged when transformationPrompt is null")
    }

    @Test
    fun testNodeLLMTransformPromptWithBlankPrompt() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-transform-blank", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val transformNode by nodeLLMTransformPrompt(
                transformationPrompt = "   ",
                name = "transform-blank"
            )

            edge(nodeStart forwardTo transformNode)
            edge(transformNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForTransformation(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "Original input should pass through with blank prompt"
        val result = agent.run(input)

        println("[DEBUG_LOG] Blank prompt result: $result")
        assertEquals(input, result, "Input should pass through unchanged when transformationPrompt is blank")
    }

    @Test
    fun testNodeLLMTransformPromptWithEmptyPrompt() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-transform-empty", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val transformNode by nodeLLMTransformPrompt(
                transformationPrompt = "",
                name = "transform-empty"
            )

            edge(nodeStart forwardTo transformNode)
            edge(transformNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForTransformation(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "Original input should pass through with empty prompt"
        val result = agent.run(input)

        println("[DEBUG_LOG] Empty prompt result: $result")
        assertEquals(input, result, "Input should pass through unchanged when transformationPrompt is empty")
    }

    @Test
    fun testNodeLLMTransformPromptPreservesOriginalPromptAndModel() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-transform-preserve", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val transformNode by nodeLLMTransformPrompt(
                transformationPrompt = "Rewrite the query",
                name = "transform-preserve"
            )

            edge(nodeStart forwardTo transformNode)
            edge(transformNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
                user("Previous context message")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForTransformation(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "What is the weather today?"
        val result = agent.run(input)

        println("[DEBUG_LOG] Preserve context result: $result")
        // The transformation should work and return the transformed result
        assertEquals("REWRITTEN: What is the weather?", result, "Input should be transformed")
    }

    @Test
    fun testNodeLLMTransformPromptDefaultParameterValue() = runTest {
        val memoryRepository = TestMemoryRecordRepository()

        val strategy = strategy<String, String>("test-transform-default", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            // Using nodeLLMTransformPrompt without specifying transformationPrompt (uses default null)
            val transformNode by nodeLLMTransformPrompt(
                name = "transform-default"
            )

            edge(nodeStart forwardTo transformNode)
            edge(transformNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutorForTransformation(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion.EMPTY
        ) {
            install(Memory2.Feature) {
                chatMessageRepository = NoChatMessageRepository
                memoryRecordRepository = memoryRepository
            }
        }

        val input = "Input with default parameter"
        val result = agent.run(input)

        println("[DEBUG_LOG] Default parameter result: $result")
        assertEquals(input, result, "Input should pass through unchanged when using default null transformationPrompt")
    }
}
