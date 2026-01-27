package ai.koog.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowToolModel(
    public val name: String,
    public val type: FlowToolKind,
    public val parameters: FlowToolParameters
)

/**
 *
 */
@Serializable
public enum class FlowToolKind(public val id: String) {
    @SerialName("mcp")
    MCP("mcp"),

    @SerialName("local")
    LOCAL("local")
}

/**
 *
 */
@Serializable
public enum class FlowMcpToolTransportKind(public val id: String) {
    @SerialName("stdio")
    STDIO("stdio"),

    @SerialName("sse")
    SSE("sse")
}

/**
 *
 */
@Serializable
public sealed interface FlowToolParameters {

    /**
     *
     */
    @Serializable
    public sealed interface FlowMcpToolParameters : FlowToolParameters {
        /**
         *
         */
        public val transport: FlowMcpToolTransportKind
    }

    /**
     *
     */
    @Serializable
    public data class FlowToolStdioParametersModel(
        val command: String,
        val args: List<String> = emptyList()
    ) : FlowMcpToolParameters {
        override val transport: FlowMcpToolTransportKind = FlowMcpToolTransportKind.STDIO
    }

    /**
     *
     */
    @Serializable
    public data class FlowToolSSEParameters(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    ) : FlowMcpToolParameters {
        override val transport: FlowMcpToolTransportKind = FlowMcpToolTransportKind.SSE
    }

    /**
     *
     */
    @Serializable
    public data class FlowLocalToolParameters(
        public val path: String
    ) : FlowToolParameters
}
