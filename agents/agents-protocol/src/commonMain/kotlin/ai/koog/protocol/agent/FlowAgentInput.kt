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
    public data class InputInt(public val data: Int) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class InputDouble(public val data: Double) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class InputString(public val data: String) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class InputBoolean(public val data: Boolean) : FlowAgentInput

    /**
     *
     */
    @Serializable
    public data class InputArrayInt(public val data: Array<Int>) : FlowAgentInput {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayInt).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     *
     */
    @Serializable
    public data class InputArrayDouble(public val data: Array<Double>) : FlowAgentInput {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayDouble).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     *
     */
    @Serializable
    public data class InputArrayStrings(public val data: Array<String>) : FlowAgentInput {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayStrings).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     *
     */
    @Serializable
    public data class InputArrayBooleans(public val data: Array<Boolean>) : FlowAgentInput {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayBooleans).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     *
     */
    @Serializable
    public data class InputCritiqueResult(
        public val success: Boolean,
        public val feedback: String
    ) : FlowAgentInput
}
