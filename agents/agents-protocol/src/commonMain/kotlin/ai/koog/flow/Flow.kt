package ai.koog.flow

import ai.koog.agent.FlowAgent

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
    public val tools: List<FlowTools>

    /**
     *
     */
    public val transitions: List<Transition>
}
