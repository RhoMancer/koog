package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public sealed interface FlowAgentInput {

    /**
     *
     */
    @Serializable
    public data class Int(public val data: kotlin.Int) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class Double(public val data: kotlin.Double) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class String(public val data: kotlin.String) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class Boolean(public val data: kotlin.Boolean) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class ArrayInt(public val data: Array<kotlin.Int>) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class ArrayDouble(public val data: Array<kotlin.Double>) : FlowAgentInput


    /**
     *
     */
    @Serializable
    public data class ArrayStrings(public val data: Array<kotlin.String>) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class ArrayBooleans(public val data: Array<kotlin.Boolean>) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class CritiqueResult(
        public val success: kotlin.Boolean,
        public val feedback: kotlin.String
    ) : FlowAgentInput
}
