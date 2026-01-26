package ai.koog.flow

import ai.koog.agent.FlowAgent
import ai.koog.model.Transition
import ai.koog.tools.FlowTool

/**
 *
 */
public data class SimpleFlow(
    override val id: String,
    override val agents: List<FlowAgent>,
    override val tools: List<FlowTool>,
    override val transitions: List<Transition>
) : Flow

