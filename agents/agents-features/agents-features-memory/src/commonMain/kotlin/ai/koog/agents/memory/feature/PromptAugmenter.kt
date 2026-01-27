package ai.koog.agents.memory.feature

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo

/**
 * Utility object for augmenting prompts with relevant context.
 *
 * This object provides functionality to insert context into prompts using different strategies
 * defined by [ContextInsertionMode]. It is used by the Memory2 feature to augment prompts
 * with context retrieved from vector databases.
 *
 * @see ContextInsertionMode
 * @see Memory2
 */
public object PromptAugmenter {

    /**
     * Default template for the system message when using [ContextInsertionMode.SYSTEM_MESSAGE].
     * Use {relevant_context} placeholder.
     */
    public const val DEFAULT_SYSTEM_PROMPT_TEMPLATE: String = """Use the following information to answer the user's question.

{relevant_context}

Answer the user's question based on the above context. If the context doesn't contain relevant information, say so."""

    /**
     * Default template for user context when using [ContextInsertionMode.USER_MESSAGE_BEFORE_LAST]
     * or [ContextInsertionMode.AUGMENT_LAST_USER_MESSAGE].
     * Use {relevant_context} placeholder.
     */
    public const val DEFAULT_USER_CONTEXT_TEMPLATE: String = """Here is some relevant context:

{relevant_context}

Based on the above context, please answer the following question:"""

    /**
     * Augments the prompt with relevant context based on the specified insertion mode.
     *
     * @param originalPrompt The original prompt to augment.
     * @param relevantContext The list of relevant context strings to insert.
     * @param contextInsertionMode The mode defining how context should be inserted.
     * @param systemPromptTemplate The template for system message insertion (used with [ContextInsertionMode.SYSTEM_MESSAGE]).
     * @param userContextTemplate The template for user context insertion (used with other modes).
     * @return A new [Prompt] with the context inserted according to the specified mode.
     */
    public fun augmentPrompt(
        originalPrompt: Prompt,
        relevantContext: List<String>,
        contextInsertionMode: ContextInsertionMode,
        systemPromptTemplate: String = DEFAULT_SYSTEM_PROMPT_TEMPLATE,
        userContextTemplate: String = DEFAULT_USER_CONTEXT_TEMPLATE
    ): Prompt {
        val relevantContextText = if (relevantContext.isNotEmpty()) {
            "Relevant information:\n" + relevantContext.mapIndexed { index, content ->
                "[${index + 1}] $content"
            }.joinToString("\n\n")
        } else {
            ""
        }

        return when (contextInsertionMode) {
            ContextInsertionMode.SYSTEM_MESSAGE -> {
                insertAsSystemMessage(originalPrompt, relevantContextText, systemPromptTemplate)
            }
            ContextInsertionMode.USER_MESSAGE_BEFORE_LAST -> {
                insertAsUserMessageBeforeLast(originalPrompt, relevantContextText, userContextTemplate)
            }
            ContextInsertionMode.AUGMENT_LAST_USER_MESSAGE -> {
                augmentLastUserMessage(originalPrompt, relevantContextText, userContextTemplate)
            }
        }
    }

    /**
     * Inserts context as a system message at the beginning of the prompt.
     */
    private fun insertAsSystemMessage(
        originalPrompt: Prompt,
        relevantContextText: String,
        systemPromptTemplate: String
    ): Prompt {
        val contextMessage = formatTemplate(systemPromptTemplate, relevantContextText)
        if (contextMessage.isBlank()) return originalPrompt

        return originalPrompt.withMessages { messages ->
            val systemMessage = Message.System(contextMessage, RequestMetaInfo.Empty)
            // Insert system message at the beginning, or replace existing system message
            val existingSystemIndex = messages.indexOfFirst { it is Message.System }
            if (existingSystemIndex >= 0) {
                // Combine with existing system message
                val existingSystem = messages[existingSystemIndex] as Message.System
                val combinedContent = "${existingSystem.content}\n\n$contextMessage"
                messages.toMutableList().apply {
                    set(existingSystemIndex, Message.System(combinedContent, existingSystem.metaInfo))
                }
            } else {
                listOf<Message>(systemMessage) + messages
            }
        }
    }

    /**
     * Inserts context as a user message before the last user message.
     */
    private fun insertAsUserMessageBeforeLast(
        originalPrompt: Prompt,
        relevantContextText: String,
        userContextTemplate: String
    ): Prompt {
        val contextMessage = formatTemplate(userContextTemplate, relevantContextText)
        if (contextMessage.isBlank()) return originalPrompt

        return originalPrompt.withMessages { messages ->
            val lastUserIndex = messages.indexOfLast { it is Message.User }
            if (lastUserIndex >= 0) {
                messages.toMutableList().apply {
                    add(lastUserIndex, Message.User(contextMessage, RequestMetaInfo.Empty))
                }
            } else {
                // No user message found, add context at the end
                messages + Message.User(contextMessage, RequestMetaInfo.Empty)
            }
        }
    }

    /**
     * Augments the last user message by prepending context to it.
     */
    private fun augmentLastUserMessage(
        originalPrompt: Prompt,
        relevantContextText: String,
        userContextTemplate: String
    ): Prompt {
        val contextPrefix = formatTemplate(userContextTemplate, relevantContextText)
        if (contextPrefix.isBlank()) return originalPrompt

        return originalPrompt.withMessages { messages ->
            val lastUserIndex = messages.indexOfLast { it is Message.User }
            if (lastUserIndex >= 0) {
                val lastUserMessage = messages[lastUserIndex] as Message.User
                val augmentedContent = "$contextPrefix\n\n${lastUserMessage.content}"
                messages.toMutableList().apply {
                    set(lastUserIndex, Message.User(augmentedContent, lastUserMessage.metaInfo))
                }
            } else {
                messages
            }
        }
    }

    /**
     * Formats the template by replacing placeholders with actual content.
     */
    private fun formatTemplate(template: String, relevantContextText: String): String {
        return template
            .replace("{relevant_context}", relevantContextText)
            .trim()
    }
}
