package ai.koog.runtime

import ai.koog.agent.FlowAgent

/**
 *
 */
public class AgentFlowKoogRuntime : AgentFlowRuntime {

    override fun executeAgent(agent: FlowAgent): String {
        return agent.execute()
    }
}
