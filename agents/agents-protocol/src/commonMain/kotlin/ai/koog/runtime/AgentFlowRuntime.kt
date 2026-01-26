package ai.koog.runtime

import ai.koog.model.FlowAgentConfig

/**
 *
 */
public interface AgentFlowRuntime {

    /**
     *
     */
    public fun executeAgent(config: FlowAgentConfig): String
}
