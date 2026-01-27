package ai.koog._initial.runtime

import ai.koog._initial.agent.FlowAgent

/**
 *
 */
public class AgentFlowKoogRuntime : AgentFlowRuntime {

    override suspend fun executeAgent(agent: FlowAgent): String {
        return agent.execute()
    }
}
