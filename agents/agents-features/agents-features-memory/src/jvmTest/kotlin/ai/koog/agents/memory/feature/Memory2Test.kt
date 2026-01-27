package ai.koog.agents.memory.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.rag.vector.database.EphemeralMemoryRecordRepository
import ai.koog.rag.vector.database.KeywordSearchRequest
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.ScoredMemoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for Memory2 feature
 */
@OptIn(ExperimentalAgentsApi::class)
class Memory2Test {

    // ==========================================
    // Tests for createFeature prompt augmentation
    // ==========================================

    /**
     * Test that verifies the createFeature method properly augments the prompt with vector store context.
     * 
     * This test:
     * 1. Configures Memory2 with a searchFunction that returns relevant documents
     * 2. Runs the agent and verifies the LLM receives the augmented prompt containing vector store context
     */
    @Test
    fun testCreateFeatureAugmentsPromptWithVectorStoreContext() = runTest {
        // Track whether the prompt was augmented by checking all messages
        var promptWasAugmented = false
        var searchFunctionCalled = false

        // Create a custom executor that checks the FULL prompt content (not just the last message)
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                // Check all messages in the prompt for augmentation
                val allContent = prompt.messages.joinToString("\n") { it.content }
                println("[DEBUG_LOG] Full prompt content: $allContent")

                val containsKotlinInfo = allContent.contains("Kotlin was developed by JetBrains")
                val containsRelevantInfo = allContent.contains("Relevant information")
                promptWasAugmented = containsKotlinInfo && containsRelevantInfo
                println("[DEBUG_LOG] Contains Kotlin info: $containsKotlinInfo, Contains relevant info: $containsRelevantInfo")

                val response = if (promptWasAugmented) "AUGMENTED_WITH_VECTOR_STORE" else "NOT_AUGMENTED"
                return listOf(Message.Assistant(response, ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = throw UnsupportedOperationException("Not needed for this test")

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a simple strategy that calls the LLM
        val strategy =
            strategy<String, String>("test-augment-vector", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { it.content })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        // Create agent with Memory2 configured to augment from vector store using retriever
        // The retriever directly returns the relevant documents (simulating a vector store search)
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                retriever = MemoryRecordRetriever { _, query ->
                    searchFunctionCalled = true
                    println("[DEBUG_LOG] Search function called with query: $query")
                    // Return relevant documents directly (simulating vector store search results)
                    listOf(
                        ScoredMemoryRecord(MemoryRecord(content = "Kotlin was developed by JetBrains"), 1.0),
                        ScoredMemoryRecord(MemoryRecord(content = "Kotlin is 100% interoperable with Java"), 0.9)
                    )
                }
            }
        }

        val result = agent.run("Tell me about Kotlin")

        println("[DEBUG_LOG] Result: $result")
        println("[DEBUG_LOG] Search function called: $searchFunctionCalled")
        println("[DEBUG_LOG] Prompt was augmented: $promptWasAugmented")

        // Verify the search function was called
        assertTrue(searchFunctionCalled, "The search function should have been called")
        // Verify the prompt was augmented with vector store context
        assertTrue(promptWasAugmented, "The prompt should have been augmented with vector store context")
        assertEquals("AUGMENTED_WITH_VECTOR_STORE", result)
    }

    /**
     * Test that verifies the createFeature method does NOT augment the prompt when
     * searchFunction is null.
     */
    @Test
    fun testCreateFeatureDoesNotAugmentWhenSearchFunctionIsNull() = runTest {
        // Track whether the prompt was incorrectly augmented
        var wasAugmented = false

        // Create a mock executor that checks if the prompt contains augmented context
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("INCORRECTLY_AUGMENTED") onCondition { request ->
                wasAugmented = request.contains("Relevant information")
                println("[DEBUG_LOG] Request content: $request")
                println("[DEBUG_LOG] Was augmented: $wasAugmented")
                wasAugmented
            }
            mockLLMAnswer("NOT_AUGMENTED").asDefaultResponse
        }

        // Create a vector store with data (but searchFunction will be null)
        val vectorStore = EphemeralMemoryRecordRepository()
        vectorStore.add(
            listOf(
                MemoryRecord(content = "Some context that should not appear")
            )
        )

        // Create a simple strategy that calls the LLM
        val strategy = strategy<String, String>("test-no-augment", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        // Create agent with Memory2 configured with searchFunction = null (default)
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = vectorStore
                // searchFunction is null by default - no augmentation should happen
            }
        }

        val result = agent.run("Hello")

        println("[DEBUG_LOG] Result: $result")
        println("[DEBUG_LOG] Was augmented: $wasAugmented")

        // Verify the prompt was NOT augmented
        assertTrue(!wasAugmented, "The prompt should NOT have been augmented when searchFunction is null")
        assertEquals("NOT_AUGMENTED", result)
    }

    /**
     * Test that verifies Memory2 Config defaults.
     */
    @Test
    fun testMemory2ConfigDefaults() {
        val config = Memory2.Config()
        assertTrue(config.memoryRecordRepository is EphemeralMemoryRecordRepository)
        assertEquals(false, config.persistAssistantResponses)
        assertEquals(null, config.retriever)
        assertEquals(ContextInsertionMode.SYSTEM_MESSAGE, config.contextInsertionMode)
    }

    /**
     * Test that verifies Memory2 Config custom values.
     */
    @Test
    fun testMemory2ConfigCustomValues() {
        val testRecordRepo = EphemeralMemoryRecordRepository()
        val testRetriever = SimilarityRecordRetriever(topK = 5, similarityThreshold = 0.7)

        val config = Memory2.Config().apply {
            memoryRecordRepository = testRecordRepo
            persistAssistantResponses = true
            retriever = testRetriever
            contextInsertionMode = ContextInsertionMode.AUGMENT_LAST_USER_MESSAGE
        }

        assertEquals(testRecordRepo, config.memoryRecordRepository)
        assertEquals(true, config.persistAssistantResponses)
        assertEquals(testRetriever, config.retriever)
        assertEquals(ContextInsertionMode.AUGMENT_LAST_USER_MESSAGE, config.contextInsertionMode)
    }

    /**
     * Test that verifies persistAssistantMessagesAsMemoryRecords stores assistant messages.
     */
    @Test
    fun testPersistAssistantMessagesAsMemoryRecords() = runTest {
        val memoryRepository = EphemeralMemoryRecordRepository()

        // Create a mock executor that returns a specific response
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("This is the assistant response to store").asDefaultResponse
        }

        // Create a simple strategy that calls the LLM
        val strategy = strategy<String, String>("test-persist", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = memoryRepository
                persistAssistantResponses = true
            }
        }

        agent.run("Hello")

        // Verify that the assistant message was stored in the repository
        println("[DEBUG_LOG] Records in repository: ${memoryRepository.size()}")
        val searchResults = memoryRepository.search(KeywordSearchRequest(query = "assistant response"))
        searchResults.forEach { result ->
            println("[DEBUG_LOG] Record content: ${result.record.content}")
        }

        assertTrue(memoryRepository.size() > 0, "At least one record should be stored")
        assertTrue(
            searchResults.any { it.record.content.contains("assistant response to store") },
            "The assistant response should be stored in the repository"
        )
    }

    /**
     * Test that verifies searchFunction is correctly used to retrieve context.
     * This test uses a realistic search function that calls vectorStore.search(KeywordSearchRequest(query))
     * similar to how users would configure it in production.
     */
    @Test
    fun testSearchFunctionIsCorrectlyUsed() = runTest {
        var searchFunctionCalled = false
        var searchQuery: String? = null

        // Create a vector store with pre-populated data
        val vectorStore = EphemeralMemoryRecordRepository()
        vectorStore.add(
            listOf(
                MemoryRecord(content = "The weather in Paris is sunny today"),
                MemoryRecord(content = "Tokyo weather forecast shows rain"),
                MemoryRecord(content = "Kotlin is a programming language")
            )
        )

        // Create a mock executor that checks if the prompt was augmented
        var promptWasAugmented = false
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                val allContent = prompt.messages.joinToString("\n") { it.content }
                println("[DEBUG_LOG] Full prompt content: $allContent")
                promptWasAugmented = allContent.contains("weather") && allContent.contains("Relevant information")
                return listOf(Message.Assistant("Response with context", ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = throw UnsupportedOperationException("Not needed for this test")

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a simple strategy that calls the LLM
        val strategy =
            strategy<String, String>("test-search-function", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { it.content })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = vectorStore
                retriever = MemoryRecordRetriever { repo, query ->
                    searchFunctionCalled = true
                    searchQuery = query
                    println("[DEBUG_LOG] Realistic search mode called with query: $query")
                    // This is the realistic pattern users would use:
                    repo.search(KeywordSearchRequest(query))
                }
            }
        }

        agent.run("weather")

        // Verify the search function was called with the user's query
        assertTrue(searchFunctionCalled, "The search function should have been called")
        assertEquals("weather", searchQuery, "The search function should receive the user's query")
        // Verify the prompt was augmented with the search results
        assertTrue(promptWasAugmented, "The prompt should have been augmented with vector store search results")
    }

    /**
     * Test that verifies persistAssistantMessagesAsMemoryRecords=false does not store messages.
     */
    @Test
    fun testPersistAssistantMessagesDisabledDoesNotStore() = runTest {
        val memoryRepository = EphemeralMemoryRecordRepository()

        // Create a mock executor that returns a specific response
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("This response should NOT be stored").asDefaultResponse
        }

        // Create a simple strategy that calls the LLM
        val strategy = strategy<String, String>("test-no-persist", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = memoryRepository
                persistAssistantResponses = false // Explicitly disabled
            }
        }

        agent.run("Hello")

        // Verify that no records were stored
        println("[DEBUG_LOG] Records in repository: ${memoryRepository.size()}")
        assertEquals(
            0,
            memoryRepository.size(),
            "No records should be stored when persistAssistantMessagesAsMemoryRecords is false"
        )
    }
}
