package ai.koog.agents.core.agent.context.element

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

internal suspend fun <TElement : AIAgentContextElementBase> getAIAgentContextElement(key: CoroutineContext.Key<TElement>): TElement? =
    currentCoroutineContext()[key]

internal suspend fun <TElement : AIAgentContextElementBase> getAIAgentContextElementOrThrow(key: CoroutineContext.Key<TElement>): TElement =
    getAIAgentContextElement(key) ?: error(
        "Unable to retrieve the AIAgent context element with key '$key' from CoroutineContext. " +
            "Please make sure the element is added to the current CoroutineContext."
    )

/**
 * Retrieves the `AgentRunInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `AgentRunInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getAgentRunInfoElement(): AgentRunInfoContextElement? =
    getAIAgentContextElement(AgentRunInfoContextElement.Key)

/**
 * Retrieves the `AgentRunInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `AgentRunInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getAgentRunInfoElementOrThrow(): AgentRunInfoContextElement =
    getAIAgentContextElementOrThrow(AgentRunInfoContextElement.Key)

/**
 * Retrieves the `StrategyInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `StrategyInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getStrategyInfoElement(): StrategyInfoContextElement? =
    getAIAgentContextElement(StrategyInfoContextElement.Key)

/**
 * Retrieves the `StrategyInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `StrategyInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getStrategyInfoElementOrThrow(): StrategyInfoContextElement =
    getAIAgentContextElementOrThrow(StrategyInfoContextElement.Key)

/**
 * Retrieves the `NodeInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `NodeInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getNodeInfoElement(): NodeInfoContextElement? =
    getAIAgentContextElement(NodeInfoContextElement.Key)

/**
 * Retrieves the `NodeInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `NodeInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getNodeInfoElementOrThrow(): NodeInfoContextElement =
    getAIAgentContextElementOrThrow(NodeInfoContextElement.Key)

/**
 * Retrieves the `SubgraphInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `SubgraphInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getSubgraphInfoElement(): SubgraphInfoContextElement? =
    getAIAgentContextElement(SubgraphInfoContextElement.Key)

/**
 * Retrieves the `SubgraphInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `SubgraphInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getSubgraphInfoElementOrThrow(): SubgraphInfoContextElement =
    getAIAgentContextElementOrThrow(SubgraphInfoContextElement.Key)
