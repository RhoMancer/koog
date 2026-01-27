package ai.koog.agents.memory.feature

/**
 * Defines how retrieved context should be inserted into the prompt.
 */
public enum class ContextInsertionMode {
    /**
     * Insert context as a system message at the beginning of the prompt.
     */
    SYSTEM_MESSAGE,

    /**
     * Insert context as a user message before the last user message.
     */
    USER_MESSAGE_BEFORE_LAST,

    /**
     * Augment the last user message by prepending context to it.
     * Useful for RAG scenarios where context should be part of the user's question.
     */
    AUGMENT_LAST_USER_MESSAGE
}
