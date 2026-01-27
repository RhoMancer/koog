package ai.koog._initial.flow

import ai.koog._initial.agent.FlowAgent
import ai.koog._initial.model.Transition
import ai.koog._initial.tools.FlowTool

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
     * Runs the flow and returns the result.
     */
    public suspend fun run(): String
}
