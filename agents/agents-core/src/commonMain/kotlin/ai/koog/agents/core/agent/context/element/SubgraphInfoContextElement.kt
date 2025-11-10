package ai.koog.agents.core.agent.context.element

import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KType

/**
 * Represents a coroutine context element that holds metadata specific to a particular subgraph
 * participating in the execution context of an AI agent strategy.
 * This metadata includes the subgraph's identifier, parent subgraph identifier, and the subgraph's type.
 *
 * @property id The unique identifier for the subgraph.
 * @property parentId The unique identifier for the parent subgraph, if applicable.
 */
public data class SubgraphInfoContextElement(
    override val id: String,
    override val parentId: String?,
    val subgraphName: String,
    val input: Any?,
    val inputType: KType
) : AIAgentContextElementBase {

    /**
     * A companion object that serves as the key for the `SubgraphInfoContextElement` in a `CoroutineContext`.
     * This key enables the retrieval of a `SubgraphInfoContextElement` instance stored within a coroutine's context.
     *
     * Implements `CoroutineContext.Key` to provide a type-safe mechanism for accessing the associated context element.
     */
    public companion object Key : CoroutineContext.Key<SubgraphInfoContextElement>

    override val key: CoroutineContext.Key<*> = Key
}
