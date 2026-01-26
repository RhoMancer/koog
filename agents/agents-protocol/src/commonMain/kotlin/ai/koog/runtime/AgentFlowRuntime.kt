package ai.koog.runtime

import ai.koog.agent.FlowAgentConfig

/**
 *
 */
public interface AgentFlowRuntime {

    /**
     *
     */
    public fun executeAgent(config: FlowAgentConfig): String
}
