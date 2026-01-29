package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public sealed interface FlowAgentInput {

    /**
     * Converts the FlowAgentInput to a String representation for use as agent input.
     */
    public fun toStringValue(): kotlin.String

    /**
     *
     */
    @Serializable
    public data class Int(public val data: kotlin.Int) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data.toString()
    }

    /**
     *
     */
    @Serializable
    public data class Double(public val data: kotlin.Double) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data.toString()
    }

    /**
     *
     */
    @Serializable
    public data class String(public val data: kotlin.String) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data
    }

    /**
     *
     */
    @Serializable
    public data class Boolean(public val data: kotlin.Boolean) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data.toString()
    }

    /**
     *
     */
    @Serializable
    public data class ArrayInt(public val data: Array<kotlin.Int>) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data.joinToString(", ")
    }

    /**
     *
     */
    @Serializable
    public data class ArrayDouble(public val data: Array<kotlin.Double>) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data.joinToString(", ")
    }


    /**
     *
     */
    @Serializable
    public data class ArrayStrings(public val data: Array<kotlin.String>) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data.joinToString(", ")
    }

    /**
     *
     */
    @Serializable
    public data class ArrayBooleans(public val data: Array<kotlin.Boolean>) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = data.joinToString(", ")
    }

    /**
     *
     */
    @Serializable
    public data class CritiqueResult(
        public val success: kotlin.Boolean,
        public val feedback: kotlin.String
    ) : FlowAgentInput {
        override fun toStringValue(): kotlin.String = "success=$success, feedback=$feedback"
    }

    public companion object {
        /**
         * Creates a FlowAgentInput.String from a raw string value.
         */
        public fun fromString(value: kotlin.String): FlowAgentInput = String(value)
    }
}
