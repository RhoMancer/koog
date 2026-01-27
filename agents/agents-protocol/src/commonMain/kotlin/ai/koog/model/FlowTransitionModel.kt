package ai.koog.model

import ai.koog._initial.flow.TransitionCondition
import kotlinx.serialization.Serializable

/**
 * Flow transitions section.
 */
@Serializable
public data class FlowTransitionModel(
    public val from: String,
    public val to: String,
    public val condition: TransitionCondition? = null
)
