package ai.koog.agents.memory.strategy

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.memory.feature.nodes.nodeExtractAndSaveMemoryRecord
import ai.koog.agents.memory.feature.nodes.nodeRetrieveFromMemoryAndAugment
import ai.koog.agents.memory.feature.nodes.nodeLLMTransformPrompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser


/**
 * Creates a naive Retrieval-Augmented Generation strategy for an AI agent.
 *
 * This strategy retrieves relevant memory records at the start of the conversation
 * and augments the user prompt with context before processing.
 *
 * The flow consists of the following steps:
 * 1. Start the agent.
 * 2. Retrieve relevant memory records and augment the user prompt.
 * 3. Call the LLM with the augmented input.
 * 4. Execute a tool based on the LLM's response.
 * 5. Send the tool result back to the LLM.
 * 6. Repeat until LLM indicates no further tool calls are needed or the agent finishes.
 *
 * @param topK The maximum number of memory records to retrieve.
 * @param scoreThreshold The minimum score threshold for search results (default 0.0).
 * @return An instance of AIAgentGraphStrategy configured for memory retrieval.
 */
public fun ragNaiveStrategy(
    topK: Int,
    scoreThreshold: Double = 0.0,
): AIAgentGraphStrategy<String, String> = strategy("naive_memory_retrieval") {
    val nodeRetrieveAndAugment by nodeRetrieveFromMemoryAndAugment(topK, scoreThreshold)

    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo nodeRetrieveAndAugment)
    edge(nodeRetrieveAndAugment forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}

/**
 * Creates an advanced Retrieval-Augmented Generation strategy for an AI agent.
 *
 * This strategy extends the naive RAG approach by adding pre-retrieval and post-retrieval
 * transformation steps using LLM processing. This allows for query rewriting, translation,
 * context filtering, summarization, and other transformations.
 *
 * The flow consists of the following steps:
 * 1. Start the agent.
 * 2. Transform the user prompt using the pre-retrieval prompt (e.g., rewrite, translate).
 * 3. Retrieve relevant memory records and augment the transformed prompt.
 * 4. Transform the augmented prompt using the post-retrieval prompt (e.g., filter, summarize).
 * 5. Call the LLM with the processed input.
 * 6. Execute a tool based on the LLM's response.
 * 7. Send the tool result back to the LLM.
 * 8. Repeat until LLM indicates no further tool calls are needed or the agent finishes.
 *
 * @param topK The maximum number of memory records to retrieve.
 * @param preRetrievalPrompt The prompt for transforming the initial user prompt before retrieval
 *        (e.g., for query rewriting or translation to a different language).
 * @param postRetrievalPrompt The prompt for processing the output after retrieval
 *        (e.g., for filtering irrelevant content or summarizing the context).
 * @param scoreThreshold The minimum score threshold for search results (default 0.0).
 * @return An instance of AIAgentGraphStrategy configured for advanced memory retrieval.
 *
 * Example usage:
 * ```kotlin
 * val strategy = ragAdvancedStrategy(
 *     topK = 5,
 *     preRetrievalPrompt = "Rewrite the following query to be more specific and detailed for search.",
 *     postRetrievalPrompt = "Summarize the following context, keeping only the most relevant information."
 * )
 * ```
 */
public fun ragAdvancedStrategy(
    topK: Int,
    preRetrievalPrompt: String,
    postRetrievalPrompt: String,
    scoreThreshold: Double = 0.0,
): AIAgentGraphStrategy<String, String> = strategy("advanced_memory_retrieval") {
    val nodePreRetrievalTransform by nodeLLMTransformPrompt(
        transformationPrompt = preRetrievalPrompt,
        name = "pre-retrieval-transform"
    )
    val nodeRetrieveAndAugment by nodeRetrieveFromMemoryAndAugment(topK, scoreThreshold)
    val nodePostRetrievalTransform by nodeLLMTransformPrompt(
        transformationPrompt = postRetrievalPrompt,
        name = "post-retrieval-transform"
    )

    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo nodePreRetrievalTransform)
    edge(nodePreRetrievalTransform forwardTo nodeRetrieveAndAugment)
    edge(nodeRetrieveAndAugment forwardTo nodePostRetrievalTransform)
    edge(nodePostRetrievalTransform forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}

/**
 * Creates a memory extraction strategy for an AI agent.
 *
 * This strategy extracts structured information from the conversation at the end
 * and saves it to the memory record repository.
 *
 * The flow consists of the following steps:
 * 1. Start the agent.
 * 2. Call the LLM with the input.
 * 3. Execute a tool based on the LLM's response.
 * 4. Send the tool result back to the LLM.
 * 5. Repeat until LLM indicates no further tool calls are needed.
 * 6. Extract structured information from the conversation and save to memory.
 * 7. Finish the agent.
 *
 * @param S The type of the structured data to extract from the conversation.
 * @param extractionPrompt The prompt/instruction given to the LLM for extracting information.
 * @param extractionModel Optional LLM model to use for extraction. If null, uses the current session model.
 * @param examples Optional list of example outputs to help the LLM understand the expected format.
 * @param fixingParser Optional parser for handling malformed LLM responses.
 * @param messageRoles The roles of messages to include in the extraction. Defaults to User and Assistant.
 * @return An instance of AIAgentGraphStrategy configured for memory extraction.
 *
 * Example usage:
 * ```kotlin
 * @Serializable
 * data class UserPreference(
 *     val topic: String,
 *     val preference: String
 * )
 *
 * val strategy = memoryExtractionStrategy<UserPreference>(
 *     extractionPrompt = "Extract user preferences from the conversation"
 * )
 * ```
 */
public inline fun <reified S> memoryExtractionStrategy(
    extractionPrompt: String,
    extractionModel: LLModel? = null,
    examples: List<S> = emptyList(),
    fixingParser: StructureFixingParser? = null,
    messageRoles: Set<Message.Role> = setOf(Message.Role.User, Message.Role.Assistant),
): AIAgentGraphStrategy<String, String> = strategy("memory_extraction") {
    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()
    val nodeExtractAndSave by nodeExtractAndSaveMemoryRecord<String, S>(
        extractionPrompt = extractionPrompt,
        extractionModel = extractionModel,
        examples = examples,
        fixingParser = fixingParser,
        messageRoles = messageRoles
    )

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeExtractAndSave onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeExtractAndSave onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeExtractAndSave forwardTo nodeFinish)
}
