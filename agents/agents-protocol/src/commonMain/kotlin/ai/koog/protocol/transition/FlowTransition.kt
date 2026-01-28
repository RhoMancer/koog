package ai.koog.protocol.transition

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowTransition(
    public val from: String,
    public val to: String,
    public val condition: FlowTransitionCondition? = null
)
