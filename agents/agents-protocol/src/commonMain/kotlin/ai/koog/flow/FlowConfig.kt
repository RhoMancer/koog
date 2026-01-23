package ai.koog.flow

import ai.koog.agent.FlowAgent

/**
 * flow:
 *      id: string
 *      version: string
 */
public data class FlowConfig(
    val id: String,
    val version: String? = null,
    val steps: List<FlowAgent> = emptyList() // or strategy
)
