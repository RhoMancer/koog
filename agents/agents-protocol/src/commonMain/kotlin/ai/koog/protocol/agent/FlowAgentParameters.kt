package ai.koog.protocol.agent

import ai.koog.protocol.transition.FlowTransition

public sealed interface FlowAgentParameters {

    public data class Transformations(val transformations: List<FlowTransition>) : FlowAgentParameters {

    }
}
