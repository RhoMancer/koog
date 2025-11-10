package ai.koog.agents.core.agent.context.element

import kotlin.coroutines.CoroutineContext

/**
 * Represents a coroutine context element that holds metadata specific to a particular strategy
 * participating in the execution context of an AI agent strategy.
 * This metadata includes the strategy's identifier, parent strategy identifier, and the strategy's type.
 *
 * @property id The unique identifier for the strategy.
 * @property parentId The unique identifier for the parent strategy, if applicable.
 * @property strategyName The name of the strategy being executed by the agent in the current context.
 */
public data class StrategyInfoContextElement(
    override val id: String,
    override val parentId: String?,
    val strategyName: String,
) : AIAgentContextElementBase {

    /**
     * A companion object that serves as the key for the `StrategyInfoContextElement` in a `CoroutineContext`.
     * This key enables the retrieval of a `StrategyInfoContextElement` instance stored within a coroutine's context.
     *
     * Implements `CoroutineContext.Key` to provide a type-safe mechanism for accessing the associated context element.
     */
    public companion object Key : CoroutineContext.Key<StrategyInfoContextElement>

    override val key: CoroutineContext.Key<*> = Key
}
