package ai.koog.runtime

import ai.koog.model.FlowAgentConfigModel

/**
 *
 */
public interface AgentFlowRuntime {

    /**
     *
     */
    public fun executeAgent(config: FlowAgentConfigModel): String
}
