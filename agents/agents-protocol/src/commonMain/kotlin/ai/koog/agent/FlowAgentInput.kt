package ai.koog.agent

import kotlinx.serialization.Serializable

/**
 * Represents input for a flow agent.
 */
@Serializable
public data class FlowAgentInput(
    val task: String? = null,
    val transformations: List<Transformation>? = null
)

/**
 * Represents a transformation operation.
 */
@Serializable
public data class Transformation(
    val input: String,
    val to: String
)
