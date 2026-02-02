package ai.koog.protocol.transition

import ai.koog.protocol.flow.ConditionOperationKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 *
 */
@Serializable
public data class FlowTransitionCondition(
    val variable: String, // FlowAgentInput,
    val operation: ConditionOperationKind,
    val value: JsonPrimitive
)
