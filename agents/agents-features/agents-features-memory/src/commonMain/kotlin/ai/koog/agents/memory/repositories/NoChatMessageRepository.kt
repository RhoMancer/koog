package ai.koog.agents.memory.repositories

import ai.koog.prompt.message.Message


public object NoChatMessageRepository : ChatMessageRepository {

    override suspend fun add(id: String, messages: List<Message>) {

    }

    override suspend fun get(id: String): List<Message> {
        return emptyList()
    }
}
