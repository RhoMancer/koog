package ai.koog.flow

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class TransitionCondition<TVariable>(
    val variable: TVariable,
    val operation: ConditionOperationKind,
    val value: TVariable
) where TVariable : String
