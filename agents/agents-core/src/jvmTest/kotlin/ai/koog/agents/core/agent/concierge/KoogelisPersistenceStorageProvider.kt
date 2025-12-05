package ai.koog.agents.core.agent.concierge

internal interface KoogelisPersistenceStorageProvider {
    fun getCheckpoints(agentId: String): Array<String>
    fun saveCheckpoint(agentId: String, agentCheckpointData: String)
    fun getLatestCheckpoint(agentId: String): String?
}
