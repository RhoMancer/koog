package ai.koog.agent

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowAgentPrompt(
    val system: String,
    val user: String
)
