package ai.koog.protocol.flow

/**
 *
 */
public enum class ConditionOperationKind(public val text: String) {
    EQUALS("equals"),
    NOT_EQUALS("not equals"),
    MORE("more"),
    LESS("less"),
    MORE_OR_EQUAL("more or equal"),
    LESS_OR_EQUAL("less or equal"),
    NOT("not"),
    AND("and"),
    OR("or")
}
