package ai.koog.flow

import ai.koog.agent.FlowAgent
import ai.koog.model.Transition
import ai.koog.tools.FlowTool

/**
 * Framework-agnostic interface for defining and executing agent flows.
 *
 * A flow represents a directed graph of agents connected by transitions.
 * Each agent performs a specific task, and transitions define how control
 * flows between agents based on conditions.
 */
public interface Flow {

    /**
     * Unique identifier for this flow.
     */
    public val id: String

    /**
     * List of agents that participate in this flow.
     */
    public val agents: List<FlowAgent>

    /**
     * List of tools available to agents in this flow.
     */
    public val tools: List<FlowTool>

    /**
     * List of transitions that define the flow between agents.
     */
    public val transitions: List<Transition>

    /**
     * Runs the flow with an optional input and returns the result.
     *
     * @param input Optional input to provide to the first agent (defaults to empty string)
     * @return The output from the final agent in the flow
     */
    public suspend fun run(input: String = ""): String
}
