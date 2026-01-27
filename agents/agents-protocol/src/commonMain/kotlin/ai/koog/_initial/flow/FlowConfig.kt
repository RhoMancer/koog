package ai.koog._initial.flow

import ai.koog._initial.agent.FlowAgent
import ai.koog._initial.model.Transition
import kotlinx.serialization.Serializable

/**
 * flow:
 *      id: string
 *      version: string
 */
@Serializable
public data class FlowConfig(
    val id: String? = null,
    val version: String? = null,
    val agents: List<FlowAgent> = emptyList(),
    val transitions: List<Transition> = emptyList()
)
