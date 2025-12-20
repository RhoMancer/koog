package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import ai.koog.agents.features.opentelemetry.extension.setEvents
import ai.koog.agents.features.opentelemetry.extension.setSpanStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class SpanCollector(
    private val tracer: Tracer,
    private val verbose: Boolean = false
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Maps span ID to its parent span, enabling tracking of hierarchical relationships.
     */
    private val spanParents = mutableMapOf<String, GenAIAgentSpan>()

    /**
     * Set of currently active (open) spans.
     */
    private val activeSpans = mutableSetOf<GenAIAgentSpan>()

    /**
     * A read-write lock to ensure thread-safe access to the span collections.
     */
    private val spansLock = ReentrantReadWriteLock()

    val activeSpansCount: Int
        get() = spansLock.read { activeSpans.size }

    /**
     * Returns the most recently opened span that is still active (not yet ended).
     * This represents the current "active" span in the execution context.
     *
     * @return The last active span, or null if no spans are currently active.
     */
    fun getLastActiveSpan(): GenAIAgentSpan? =
        spansLock.read { activeSpans.lastOrNull() }

    /**
     * Returns the most recently opened span of a specific type that is still active.
     *
     * @return The last active span of type T, or null if no matching span is active.
     */
    inline fun <reified T : GenAIAgentSpan> getLastActiveSpan(): T? =
        spansLock.read { activeSpans.lastOrNull { it is T } as? T }

    /**
     * Returns all currently active spans.
     *
     * @return A list of all active spans.
     */
    fun getActiveSpans(filter: (GenAIAgentSpan) -> Boolean = { true }): List<GenAIAgentSpan> =
        spansLock.read { activeSpans.filter(filter).toList() }

    /**
     * Adds a list of events to a specific span identified by its ID.
     *
     * @param spanId The ID of the span to which events should be added.
     * @param events The list of events to add to the span.
     */
    fun addEventsToSpan(spanId: String, events: List<GenAIAgentEvent>) =
        spansLock.read { getSpanCatching<GenAIAgentSpan>(spanId)?.addEvents(events) }

    /**
     * Starts a new span with the given details and adds it to the active spans set.
     *
     * @param span The span to start.
     * @param instant The start timestamp for the span. Defaults to the current time if null.
     */
    fun startSpan(
        span: GenAIAgentSpan,
        instant: Instant? = null,
    ) {
        logger.debug { "Starting span (name: ${span.name}, id: ${span.id})" }

        val spanKind = span.kind
        val parentContext = span.parentSpan?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(span.name)
            .setStartTimestamp(instant ?: Instant.now())
            .setSpanKind(spanKind)
            .setParent(parentContext)

        spanBuilder.setAttributes(span.attributes, verbose)

        val startedSpan = spanBuilder.startSpan()

        // Store newly started span (with thread-safe check inside)
        val wasAdded = addSpan(span)
        if (!wasAdded) {
            logger.warn { "Span with id '${span.id}' already started" }
            startedSpan.end()
            return
        }

        // Update span context and span properties
        span.span = startedSpan
        span.context = startedSpan.storeInContext(parentContext)

        logger.debug { "Span has been started (name: ${span.name}, id: ${span.id})" }
    }

    fun endSpan(
        span: GenAIAgentSpan,
        spanEndStatus: SpanEndStatus? = null
    ) {
        logger.debug { "Finishing the span (id: ${span.id})" }

        val spanToFinish = span.span

        spanToFinish.setAttributes(span.attributes, verbose)
        spanToFinish.setEvents(span.events, verbose)
        spanToFinish.setSpanStatus(spanEndStatus)
        spanToFinish.end()

        spansLock.write {
            if (!activeSpans.remove(span)) {
                logger.warn {
                    "Span with id '${span.id}' not found. Make sure you do not delete span with same id several times"
                }
                return@write
            }

            spanParents.remove(span.id)
        }
    }

    inline fun <reified T : GenAIAgentSpan> getSpan(spanId: String): T? {
        return spansLock.read { activeSpans.firstOrNull { it.id == spanId } as? T }
    }

    inline fun <reified T : GenAIAgentSpan> getSpanOrThrow(spanId: String): T {
        val span = spansLock.read { activeSpans.firstOrNull { it.id == spanId } }
            ?: error("Span with id: $spanId not found")

        return span as? T
            ?: error(
                "Span with id <$spanId> is not of expected type. Expected: <${T::class.simpleName}>, actual: <${span::class.simpleName}>"
            )
    }

    inline fun <reified T : GenAIAgentSpan> getSpanCatching(spanId: String): T? {
        val getSpanResult = runCatching { getSpanOrThrow<T>(spanId) }
        if (getSpanResult.isSuccess) {
            return getSpanResult.getOrNull()
        }

        val throwable = getSpanResult.exceptionOrNull()
        logger.error(throwable) { "Unable to get a span with id: $spanId. Error: ${throwable?.message}" }
        return null
    }

    fun endUnfinishedSpans(filter: (GenAIAgentSpan) -> Boolean = { true }) {
        // Take snapshot to avoid ConcurrentModificationException
        val spansToEnd = spansLock.read {
            activeSpans.filter(filter).toList()
        }

        spansToEnd.forEach { span ->
            logger.warn { "Force close span with id: ${span.id}" }
            endSpan(
                span = span,
                spanEndStatus = SpanEndStatus(StatusCode.UNSET)
            )
        }
    }

    //region Private Methods

    /**
     * Adds a span to the active spans set and tracks its parent.
     *
     * @return true if successfully added, false if it already exists.
     */
    private fun addSpan(span: GenAIAgentSpan): Boolean = spansLock.write {
        // Check if already exists
        if (activeSpans.any { it.id == span.id }) {
            return@write false
        }

        // Track parent relationship
        span.parentSpan?.let { parent ->
            spanParents[span.id] = parent
        }

        activeSpans.add(span)
        true
    }

    //endregion Private Methods
}
