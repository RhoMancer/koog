package ai.koog.flow

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class Transition(

    public val from: String,

    public val to: String,

    public val condition: TransitionCondition<String>
)
