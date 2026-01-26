package ai.koog.flow

import ai.koog.agent.FlowAgent
import kotlinx.serialization.Serializable

/**
 * flow:
 *      id: string
 *      version: string
 */
@Serializable
public data class FlowConfig(
    val id: String,
    val version: String? = null,
    val steps: List<FlowAgent> = emptyList() // or strategy
)
