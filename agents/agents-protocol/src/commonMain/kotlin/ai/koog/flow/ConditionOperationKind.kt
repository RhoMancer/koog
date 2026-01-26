package ai.koog.flow

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public enum class ConditionOperationKind(public val text: String) {
    EQUALS("equals"),
    NOT_EQUALS("not equals"),
    MORE("more than"),
    LESS("less than"),
    MORE_OR_EQUAL("more or equal than"),
    LESS_OR_EQUAL("less or equal than"),
    NOT("not"),
    AND("and"),
    OR("or");
}
