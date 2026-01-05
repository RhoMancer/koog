package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import ai.koog.agents.features.opentelemetry.extension.setEvents
import ai.koog.agents.features.opentelemetry.extension.setSpanStatus
import io.github.oshai.kotlinlogging.KotlinLogging
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
     * Tree node representing a span and its children in the trace tree.
     */
    data class SpanNode(
        val path: AgentExecutionInfo,
        val span: GenAIAgentSpan,
        val children: MutableList<SpanNode> = mutableListOf()
    )

    /**
     * A read-write lock to ensure thread-safe access to the span collections.
     */
    private val spansLock = ReentrantReadWriteLock()

    /**
     * Map of path string to a list of SpanNodes for O(1) lookups by execution path.
     * Multiple spans can share the same path but have different span IDs.
     */
    private val pathToNodeMap = mutableMapOf<String, MutableList<SpanNode>>()

    /**
     * Root nodes of the span tree (spans without parent execution info).
     */
    private val rootNodes = mutableListOf<SpanNode>()

    /**
     * The number of active spans in the tree.
     */
    internal val activeSpansCount: Int
        get() = pathToNodeMap.values.sumOf { it.size }

            /**
     * Starts a new span with the given details and adds it to the tree structure.
     *
     * @param span The span to start.
     * @param path The execution path for this span.
     * @param instant The start timestamp for the span. Defaults to the current time if null.
     */
    fun startSpan(
        span: GenAIAgentSpan,
        path: AgentExecutionInfo,
        instant: Instant? = null,
    ) {
        logger.debug { "Starting span (name: ${span.name}, id: ${span.id}, path: ${path.path()})" }

        val spanKind = span.kind
        val parentContext = span.parentSpan?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(span.name)
            .setStartTimestamp(instant ?: Instant.now())
            .setSpanKind(spanKind)
            .setParent(parentContext)

        spanBuilder.setAttributes(span.attributes, verbose)

        val startedSpan = spanBuilder.startSpan()

        // Update span context and span properties
        span.span = startedSpan
        span.context = startedSpan.storeInContext(parentContext)

        // Add to the tree structure
        addSpanToTree(span, path)

        logger.debug { "Span has been started (id: ${span.id}, name: ${span.name})" }
    }

    /**
     * Ends a span with the given status.
     *
     * @param span The span to end.
     * @param spanEndStatus Optional status to set when ending the span.
     */
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

        logger.debug { "Span has been finished (id: ${span.id})" }
    }

    fun getSpan(path: AgentExecutionInfo, filter: ((SpanNode) -> Boolean)? = null): GenAIAgentSpan? = spansLock.read get@{
        val spanNodes = pathToNodeMap[path.path()]

        if (spanNodes.isNullOrEmpty()) {
            return@get null
        }

        logger.trace { "Found ${spanNodes.size} span nodes for path: ${path.path()}" }
        val filter = filter ?: { true }
        return spanNodes.singleOrNull(filter)?.span
    }

    /**
     * Retrieves a span by its ID and casts it to the specified type.
     * Returns null if the span is not found or cannot be cast to the specified type.
     *
     * @param T The type to cast the span to.
     * @param path The execution path for the span.
     * @param filter Optional filter for spans to search.
     * @return The span cast to type T if found and castable, null otherwise.
     */
    inline fun <reified T : GenAIAgentSpan> getSpanCatching(
        path: AgentExecutionInfo,
        noinline filter: ((SpanNode) -> Boolean)? = null,
    ): T? = try {
        getSpan(path, filter) as? T
    } catch (e: Exception) {
        logger.warn(e) { "Failed to get span with path: ${path.path()}" }
        null
    }

    /**
     * Ends all unfinished spans that match the given predicate.
     * If no predicate is provided, ends all spans.
     * Spans are closed from leaf nodes up to parent nodes to maintain proper hierarchy.
     *
     * @param filter Optional filter for spans to end.
     */
    fun endUnfinishedSpans(filter: ((GenAIAgentSpan) -> Boolean)? = null) {
        val spansToEnd = spansLock.read {
            // Traverse tree depth-first (post-order) to get leaf nodes before parents
            val collectedNodes = mutableListOf<SpanNode>()

            fun traversePostOrder(node: SpanNode) {
                // Visit children depth-first
                node.children.forEach { traversePostOrder(it) }

                // Add the node itself
                if (filter == null || filter(node.span)) {
                    collectedNodes.add(node)
                }
            }

            // Start traversal from all root nodes
            rootNodes.forEach { traversePostOrder(it) }

            collectedNodes
        }

        spansToEnd.forEach { spanNode ->
            try {
                endSpan(spanNode.span)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to end span: ${spanNode.span.id}" }
            }
        }
    }

    /**
     * Clears all spans from the collector.
     */
    fun clear() = spansLock.write {
        pathToNodeMap.clear()
        rootNodes.clear()
        logger.debug { "All spans are cleared in span collector" }
    }

    //region Private Methods

    /**
     * Adds a span to the tree structure based on its execution path.
     * Automatically links the span to its parent node or adds it as a root.
     * Supports multiple spans with the same path but different span IDs.
     *
     * @param span The span to add.
     * @param path The execution path for this span.
     */
    private fun addSpanToTree(span: GenAIAgentSpan, path: AgentExecutionInfo) = spansLock.write add@{
        val node = SpanNode(path, span)

        // Add to path map - append to list for this path
        pathToNodeMap.getOrPut(path.path()) { mutableListOf() }.add(node)

        // Find parent node from the agent execution path instance
        val parentPath = path.parent

        // Add root node
        if (parentPath == null) {
            rootNodes.add(node)
            logger.debug { "Added root span '${node.span.name}'" }
            return@add
        }

        // Add the node as a parent's child
        val parentNodes = pathToNodeMap[parentPath.path()]
            ?: error("Parent span node not found for node path: ${path.path()}")

        val parentNode = span.parentSpan?.let { parentSpan ->
            parentNodes.find { it.span.id == parentSpan.id }
        } ?: parentNodes.single()

        parentNode.children.add(node)
        logger.debug { "Added child span: '${node.span.name}', for parent: '${parentPath.path()}'" }
    }

    //endregion Private Methods
}
