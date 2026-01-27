package ai.koog.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 *
 */
@Serializable(with = FlowToolModelSerializer::class)
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

/**
 * Custom serializer for [FlowToolModel] that uses the `type` field to determine
 * how to deserialize the `parameters` field.
 */
internal object FlowToolModelSerializer : KSerializer<FlowToolModel> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlowToolModel") {
        element<String>("name")
        element<FlowToolKind>("type")
        element<JsonElement>("parameters")
    }

    override fun serialize(encoder: Encoder, value: FlowToolModel) {
        require(encoder is JsonEncoder)
        val json = encoder.json

        val parametersJson = when (val params = value.parameters) {
            is FlowToolParameters.FlowToolStdioParametersModel ->
                json.encodeToJsonElement(FlowToolParameters.FlowToolStdioParametersModel.serializer(), params)
            is FlowToolParameters.FlowToolSSEParameters ->
                json.encodeToJsonElement(FlowToolParameters.FlowToolSSEParameters.serializer(), params)
            is FlowToolParameters.FlowLocalToolParameters ->
                json.encodeToJsonElement(FlowToolParameters.FlowLocalToolParameters.serializer(), params)
        }

        val jsonObject = buildJsonObject {
            put("name", JsonPrimitive(value.name))
            put("type", json.encodeToJsonElement(FlowToolKind.serializer(), value.type))
            put("parameters", parametersJson)
        }
        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): FlowToolModel {
        require(decoder is JsonDecoder)
        val json = decoder.json
        val jsonObject = decoder.decodeJsonElement().jsonObject

        val name = jsonObject["name"]!!.jsonPrimitive.content
        val type = json.decodeFromJsonElement(FlowToolKind.serializer(), jsonObject["type"]!!)
        val parametersJson = jsonObject["parameters"]!!.jsonObject

        val parameters: FlowToolParameters = when (type) {
            FlowToolKind.LOCAL -> json.decodeFromJsonElement(
                FlowToolParameters.FlowLocalToolParameters.serializer(), parametersJson
            )
            FlowToolKind.MCP -> {
                val transport = json.decodeFromJsonElement(
                    FlowMcpToolTransportKind.serializer(),
                    parametersJson["transport"]!!
                )
                when (transport) {
                    FlowMcpToolTransportKind.STDIO -> json.decodeFromJsonElement(
                        FlowToolParameters.FlowToolStdioParametersModel.serializer(), parametersJson
                    )
                    FlowMcpToolTransportKind.SSE -> json.decodeFromJsonElement(
                        FlowToolParameters.FlowToolSSEParameters.serializer(), parametersJson
                    )
                }
            }
        }

        return FlowToolModel(name, type, parameters)
    }
}
