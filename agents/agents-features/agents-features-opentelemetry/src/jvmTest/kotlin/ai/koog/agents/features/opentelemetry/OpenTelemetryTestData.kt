package ai.koog.agents.features.opentelemetry

import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class NodeInfo(val nodeName: String, val nodeId: String)

internal data class OpenTelemetryTestData(
    var result: String? = null,
    var collectedSpans: List<SpanData> = emptyList(),
    var collectedNodeIds: List<NodeInfo> = emptyList(),
    var collectedLLMEventIds: List<String> = emptyList(),
    var collectedToolEventIds: List<String> = emptyList(),
) {

    val runIds: List<String>
        get() = collectedSpans.mapNotNull { span ->
            span.attributes[AttributeKey.stringKey("gen_ai.conversation.id")]
        }.distinct()

    val lastRunId: String
        get() = runIds.last()

    fun singleNodeInfoByName(nodeName: String): NodeInfo = collectedNodeIds.singleOrNull { it.nodeName == nodeName }
        ?: throw NoSuchElementException("Expected collected node with name '$nodeName' to be present.")

    fun singleNodeIdByName(nodeName: String): String = singleNodeInfoByName(nodeName).nodeId

    fun filterCreateAgentSpans(): List<SpanData> {
        val createAgentAttribute = SpanAttributes.Operation.Name(OperationNameType.CREATE_AGENT)
        val attributeKey = AttributeKey.stringKey(createAgentAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == createAgentAttribute.value
        }
    }

    fun filterAgentInvokeSpans(): List<SpanData> {
        val invokeAgentAttribute = SpanAttributes.Operation.Name(OperationNameType.INVOKE_AGENT)
        val attributeKey = AttributeKey.stringKey(invokeAgentAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == invokeAgentAttribute.value
        }
    }

    fun filterInferenceSpans(): List<SpanData> {
        val chatAttribute = SpanAttributes.Operation.Name(OperationNameType.CHAT)
        val attributeKey = AttributeKey.stringKey(chatAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == chatAttribute.value
        }
    }

    fun filterExecuteToolSpans(): List<SpanData> {
        val executeToolOperationAttribute = SpanAttributes.Operation.Name(OperationNameType.EXECUTE_TOOL)
        val attributeKey = AttributeKey.stringKey(executeToolOperationAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == executeToolOperationAttribute.value
        }
    }

    fun filterNodeExecutionSpans(): List<SpanData> {
        val attributeKey = AttributeKey.stringKey("koog.node.id")
        return collectedSpans.filter { spanData -> spanData.attributes.get(attributeKey) != null }
    }

    fun filterSubgraphExecutionSpans(): List<SpanData> {
        val attributeKey = AttributeKey.stringKey("koog.subgraph.id")
        return collectedSpans.filter { spanData -> spanData.attributes.get(attributeKey) != null }
    }
}
