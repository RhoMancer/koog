package ai.koog.model

import ai.koog._initial.flow.TransitionCondition
import ai.koog._initial.model.FlowAgentModel
import ai.koog._initial.tools.FlowTool
import kotlinx.serialization.Serializable


/**
 * Top level model for flow configuration deserialization.
 */
@Serializable
public data class FlowConfigModel(
    val id: String,
    val version: String,
    val agents: List<FlowAgentModel> = emptyList(),
    val tools: List<FlowToolModel> = emptyList(),
    val transitions: List<FlowTransitionModel> = emptyList()
)
