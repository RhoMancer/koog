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
     *
     * This identifier is used to identify the group of events within the coroutine
     * context and facilitate tracking, retrieval, or associating additional metadata
     * specific to the element during AI agent operations.
     */
    public val id: String

    /**
     * Represents the identifier of the parent context element associated with this instance.
     *
     * This property enables hierarchical tracking and association within the context of
     * an AI agent's execution environment. It is used to link the current context element
     * to its logical or structural parent, facilitating organization and retrieval of
     * related metadata during operations.
     */
    public val parentId: String?
}
