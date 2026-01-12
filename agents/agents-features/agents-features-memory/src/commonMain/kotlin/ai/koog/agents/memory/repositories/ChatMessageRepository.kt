package ai.koog.agents.memory.repositories

import ai.koog.prompt.message.Message

/**
 * This interface defines operations for adding and retrieving chat messages from a provided storage.
 */
public interface ChatMessageRepository {
    /**
     * Add messages to memory. Depending on the implementation it can insert or overwrite messages.
     *
     * @param id identifier to associate messages with. Depending on a use case it can be simple (e.g. userId, agentId) or compound (e.g. conversationId)
     * @param messages a list of messages to store
     */
    public suspend fun add(id: String, messages: List<Message>)

    /**
     * Retrieve messages from memory. Depending on the implementation it can return all messages or only some of them.
     *
     * @param id identifier to associate messages with. Depending on a use case it can be simple (e.g. userId, agentId) or compound (e.g. conversationId)
     */
    public suspend fun get(id: String): List<Message>

}
