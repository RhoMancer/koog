package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.transition.FlowTransition

public object FlowUtil {

    public fun getFirstAgent(agents: List<FlowAgent>, transitions: List<FlowTransition>): FlowAgent? {
        return transitions.firstOrNull()?.let { firstTransaction ->
            agents.find { it.name == firstTransaction.from } ?: agents.firstOrNull()
        } ?: agents.firstOrNull()
    }
}
