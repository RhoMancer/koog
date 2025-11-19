package ai.koog.agents.core.agent.context.element

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import kotlin.coroutines.CoroutineContext

/**
 * Represents a coroutine context element that holds execution metadata for an agent's run.
 * This metadata includes the agent's identifier, session details, and the strategy used.
 *
 * This class implements `CoroutineContext.Element`, allowing it to be used as a part of
 * a coroutine's context and enabling retrieval of the run-related information within
 * coroutine scopes.
 *
 * @property agentId The unique identifier for the agent running in the current context.
 * @property runId The identifier for the session associated with the current agent run.
 */
public data class AgentRunInfoContextElement(
    override val id: String,
    override val parentId: String?,
    val agentId: String,
    val runId: String,
) : AIAgentContextElementBase {

    /**
     * A companion object that serves as the key for the `AgentRunInfoContextElement` in a `CoroutineContext`.
     * This key allows retrieval of the `AgentRunInfoContextElement` instance stored in a coroutine's context.
     *
     * Implements `CoroutineContext.Key` to provide a type-safe mechanism for accessing the associated context element.
     */
    public companion object Key : CoroutineContext.Key<AgentRunInfoContextElement>

    override val key: CoroutineContext.Key<*> = Key
}
