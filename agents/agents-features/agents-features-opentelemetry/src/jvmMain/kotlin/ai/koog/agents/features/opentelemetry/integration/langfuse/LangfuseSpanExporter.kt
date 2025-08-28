package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class LangfuseSpanExporter(
    private val delegateExporter: SpanExporter
) : SpanExporter {

    override fun export(spans: Collection<SpanData?>): CompletableResultCode? {




        val modifiedSpans = spans.filterNotNull().map {}

        return delegateExporter.export(modifiedSpans)
    }

    override fun flush(): CompletableResultCode? {
        return delegateExporter.flush()
    }

    override fun shutdown(): CompletableResultCode? {
        return delegateExporter.shutdown()
    }


    //region Private Methods

    inline fun <reified T>SpanData.convertEventToAttribute(
        eventKey: String,
    ) {
        events.filterNotNull().find { eventData ->
            // Find a particular event
            val eventAttributes = eventData.attributes.asMap()
            val attributes = eventAttributes.filter { entry ->
                entry.key.key == eventKey
            }


        }
    }

    //endregion Private Methods
}
