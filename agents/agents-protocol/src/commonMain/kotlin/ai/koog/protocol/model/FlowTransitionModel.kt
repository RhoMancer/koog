package ai.koog.protocol.model

import ai.koog.protocol.transition.FlowTransition
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlinx.serialization.Serializable

/**
 * Flow transitions section.
 */
@Serializable
public data class FlowTransitionModel(
    public val from: String,
    public val to: String,
    public val condition: FlowTransitionCondition? = null
) {

    /**
     *
     */
    public fun toFlowTransition(): FlowTransition {
        return FlowTransition(from, to, condition)
    }
}
