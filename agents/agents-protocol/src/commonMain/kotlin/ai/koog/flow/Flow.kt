package ai.koog.flow

import ai.koog.agent.FlowAgent
import ai.koog.model.Transition
import ai.koog.tools.FlowTool

/**
 *
 */
public interface Flow {

    /**
     *
     */
    public val id: String

    /**
     *
     */
    public val agents: List<FlowAgent>

    /**
     *
     */
    public val tools: List<FlowTool>

    /**
     *
     */
    public val transitions: List<Transition>

    /**
     *
     */
    public fun run(): String
}
