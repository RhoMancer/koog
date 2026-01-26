package ai.koog.runtime

import ai.koog.agent.FlowAgent

/**
 *
 */
public interface AgentFlowRuntime {

    /**
     *
     */
    public fun executeAgent(agent: FlowAgent): String
}
