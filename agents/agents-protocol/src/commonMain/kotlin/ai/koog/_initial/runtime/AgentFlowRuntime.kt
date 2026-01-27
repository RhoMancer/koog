package ai.koog._initial.runtime

import ai.koog._initial.agent.FlowAgent

/**
 *
 */
public interface AgentFlowRuntime {

    /**
     *
     */
    public suspend fun executeAgent(agent: FlowAgent): String
}
