package ai.koog.agents.memory.feature.nodes

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.memory.feature.withMemory2
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser


// ==========
// Memory2 helper functions
// ==========


/**
 * Creates a node that retrieves relevant memory records and augments the user prompt with context.
 *
 * This node searches for memory records matching the user prompt, filters them by context length,
 * and prepends the relevant context to the original prompt.
 *
 * @param topK The maximum number of memory records to retrieve.
 * @param scoreThreshold The minimum score threshold for search results (default 0.0).
 * @param maxContextLength The maximum total length of context to include (default 4000).
 * @param name Optional name for the node.
 * @return An AIAgentNodeDelegate that takes a user prompt and returns an augmented prompt.
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeRetrieveFromMemoryAndAugment(
    topK: Int,
    scoreThreshold: Double = 0.0,
    maxContextLength: Int = 4000,
    name: String? = null,
): AIAgentNodeDelegate<String, String> = node(name) { userPrompt ->
    withMemory2 {
        augmentPrompt(
            memoryRecordContents = searchRawMemoryContent(userPrompt, topK, scoreThreshold),
            originalUserPrompt = userPrompt,
            maxCharacters = maxContextLength
        )
    }
}

/**
 * Augments the user prompt with relevant context from memory record contents.
 *
 * This function filters memory record contents by context length to prevent
 * exceeding LLM context windows, and prepends the relevant context to the original prompt.
 *
 * @param memoryRecordContents Content strings from memory records to append.
 * @param originalUserPrompt The original user prompt to augment.
 * @param maxCharacters The maximum total length of context to include (default 4000).
 * @return The augmented prompt with relevant context, or the original prompt if no relevant records found.
 */
internal fun augmentPrompt(
    memoryRecordContents: List<String>,
    originalUserPrompt: String,
    maxCharacters: Int = 4000 // TODO: make it work with tokens instead of characters
): String {
    if (memoryRecordContents.isEmpty()) {
        return originalUserPrompt
    }

    // Apply context length limiting to prevent exceeding LLM context window
    var currentLength = 0
    val filteredResults = memoryRecordContents.takeWhile { content ->
        currentLength += content.length
        currentLength <= maxCharacters
    }

    if (filteredResults.isEmpty()) {
        return originalUserPrompt
    }

    val augmentedPrompt = buildString {
        appendLine("Relevant context:")
        filteredResults.forEach { content ->
            appendLine(content)
        }
        append(originalUserPrompt)
    }

    return augmentedPrompt
}

/**
 * Creates a node that extracts structured information from the current chat history using an LLM
 * and saves it to the memory record repository.
 *
 * This node processes the conversation history, filters messages by specified roles,
 * uses an LLM to extract structured information based on the provided extraction prompt,
 * and stores the extracted data as a JSON string in the memory repository.
 *
 * @param T The type of the node input, which is passed through unchanged.
 * @param S The type of the structured data to extract from the conversation.
 * @param extractionPrompt The prompt/instruction given to the LLM for extracting information.
 * @param extractionModel Optional LLM model to use for extraction. If null, uses the current session model.
 * @param examples Optional list of example outputs to help the LLM understand the expected format.
 * @param fixingParser Optional parser for handling malformed LLM responses.
 * @param messageRoles The roles of messages to include in the extraction. Defaults to User and Assistant.
 * @param name Optional name for the node.
 * @return An AIAgentNodeDelegate that extracts and saves memory records, passing through the input unchanged.
 *
 * Example usage:
 * ```kotlin
 * @Serializable
 * data class UserPreference(
 *     val topic: String,
 *     val preference: String
 * )
 *
 * val extractPreferences by nodeExtractAndSaveMemoryRecord<String, UserPreference>(
 *     extractionPrompt = "Extract user preferences from the conversation",
 *     messageRoles = setOf(Message.Role.User, Message.Role.Assistant)
 * )
 * ```
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified T, reified S> AIAgentSubgraphBuilderBase<*, *>.nodeExtractAndSaveMemoryRecord(
    extractionPrompt: String,
    extractionModel: LLModel? = null,
    examples: List<S> = emptyList(),
    fixingParser: StructureFixingParser? = null,
    messageRoles: Set<Message.Role> = setOf(Message.Role.User, Message.Role.Assistant),
    name: String? = null,
): AIAgentNodeDelegate<T, T> = node(name) { nodeInput ->
    llm.writeSession {
        val initialPrompt = prompt.copy()
        val initialModel = model

        // Build XML-tagged conversation history from filtered messages
        val combinedMessage = buildConversationXml(initialPrompt.messages, messageRoles)

        prompt = prompt("extract-memory") {
            system(extractionPrompt)
            user(combinedMessage)
        }

        if (extractionModel != null) {
            model = extractionModel
        }

        try {
            val extracted = requestLLMStructured<S>(
                examples = examples,
                fixingParser = fixingParser
            ).getOrThrow().data

            withMemory2 {
                store(listOf(extracted))
            }
        } finally {
            // Restore original prompt and model
            prompt = initialPrompt
            model = initialModel
        }

        nodeInput
    }
}

/**
 * Creates a node that transforms an input string using an LLM with a given system prompt.
 *
 * This node is useful for pre-processing or post-processing text in RAG pipelines,
 * such as rewriting queries, translating to different languages, filtering irrelevant content,
 * or summarizing retrieved context.
 *
 * @param transformationPrompt The system prompt that instructs the LLM how to transform the input.
 *        If null or blank, the original input is returned immediately without any transformation.
 * @param transformationModel Optional LLM model to use for transformation. If null, uses the current session model.
 * @param name Optional name for the node.
 * @return An AIAgentNodeDelegate that takes an input string and returns the transformed string.
 *
 * Example usage:
 * ```kotlin
 * val preRetrievalTransform by nodeLLMTransformPrompt(
 *     transformationPrompt = "Rewrite the following query to be more specific and detailed for search."
 * )
 *
 * val postRetrievalTransform by nodeLLMTransformPrompt(
 *     transformationPrompt = "Summarize the following context, keeping only the most relevant information."
 * )
 * ```
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMTransformPrompt(
    transformationPrompt: String? = null,
    transformationModel: LLModel? = null,
    name: String? = null,
): AIAgentNodeDelegate<String, String> = node(name) { input ->
    // If transformationPrompt is null or blank, return the original input immediately
    if (transformationPrompt.isNullOrBlank()) {
        return@node input
    }

    llm.writeSession {
        val initialPrompt = prompt.copy()
        val initialModel = model

        prompt = prompt("transform-prompt") {
            system(transformationPrompt)
            user(input)
        }

        if (transformationModel != null) {
            model = transformationModel
        }

        try {
            val response = requestLLMWithoutTools()
            (response as? Message.Assistant)?.content ?: input
        } finally {
            // Restore original prompt and model
            prompt = initialPrompt
            model = initialModel
        }
    }
}

/**
 * Builds an XML-formatted string from conversation messages, filtered by the specified roles.
 *
 * @param messages The list of messages to process.
 * @param rolesToInclude The set of message roles to include.
 * @return An XML-formatted string containing the filtered conversation history.
 */
@PublishedApi
internal fun buildConversationXml(messages: List<Message>, rolesToInclude: Set<Message.Role>): String {
    return buildString {
        append("<previous_conversation>\n")
        messages.filter { it.role in rolesToInclude }.forEach { message ->
            when (message) {
                is Message.System -> append("<system>\n${message.content}\n</system>\n")
                is Message.User -> append("<user>\n${message.content}\n</user>\n")
                is Message.Assistant -> append("<assistant>\n${message.content}\n</assistant>\n")
                is Message.Reasoning -> append("<thinking>\n${message.content}\n</thinking>\n")
                is Message.Tool.Call -> append(
                    "<tool_call tool=${message.tool}>\n${message.content}\n</tool_call>\n"
                )

                is Message.Tool.Result -> append(
                    "<tool_result tool=${message.tool}>\n${message.content}\n</tool_result>\n"
                )
            }
        }
        append("</previous_conversation>\n")
    }
}
