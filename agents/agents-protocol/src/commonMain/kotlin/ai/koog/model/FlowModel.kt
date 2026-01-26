package ai.koog.model

import ai.koog.agent.FlowAgent
import ai.koog.flow.Flow
import ai.koog.flow.Transition
import ai.koog.tools.FlowTool
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowModel(
    override val id: String,
    override val agents: List<FlowAgent>,
    override val tools: List<FlowTool>,
    override val transitions: List<Transition>
) : Flow

/**
 * Represents a transformation operation.
 */
@Serializable
public data class Transformation(
    val input: String,
    val to: String
)
