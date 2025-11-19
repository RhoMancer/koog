package ai.koog.agents.core.agent.context.element

import kotlin.coroutines.CoroutineContext

/**
 * Represents the base interface for context elements used in the execution environment
 * of an AI agent. This interface extends `CoroutineContext.Element`, making it suitable
 * for integration with coroutine contexts to provide execution metadata or contextual
 * information for AI agent operations.
 */
public interface AIAgentContextElementBase : CoroutineContext.Element {

    /**
     * A unique identifier associated with a context element.
     */
    public val id: String

    /**
     * Represents the identifier of the parent context element associated with this instance.
     */
    public val parentId: String?
}
