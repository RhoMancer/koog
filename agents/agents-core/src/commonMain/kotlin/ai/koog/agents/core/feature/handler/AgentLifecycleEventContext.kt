package ai.koog.agents.core.feature.handler

/**
 * Represents the context in which event handlers operate, providing a foundational
 * interface for all event handling activities within the AI Agent framework.
 */
public interface AgentLifecycleEventContext {

    /**
     * Represents the unique identifier for the event context.
     *
     * This identifier is used to distinguish between different event contexts
     * within the lifecycle of an agent's execution. It provides traceability
     * and helps in logging or debugging event-processing workflows.
     */
    public val id: String

    /**
     * Represents the unique identifier of the parent context, if applicable, within the event
     * handling lifecycle. This can be used to trace hierarchical relationships between
     * different contexts, such as associating a subgraph execution with its parent graph.
     *
     * If no parent context exists, the value will be null.
     */
    public val parentId: String?

    /**
     * Represents the specific type of event handled within the event handler context,
     * categorizing the nature of agent-related or strategy-related events.
     */
    public val eventType: AgentLifecycleEventType
}
