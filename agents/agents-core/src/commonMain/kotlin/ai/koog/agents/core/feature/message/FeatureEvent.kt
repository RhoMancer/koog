package ai.koog.agents.core.feature.message

import ai.koog.agents.core.agent.context.AgentExecutionPath

/**
 * Represents a specialized type of feature message that corresponds to an event in the system.
 * A feature event typically carries information uniquely identifying the event, alongside other
 * data provided by the [FeatureMessage] interface.
 *
 * Implementations of this interface are intended to detail specific events in the feature
 * processing workflow.
 */
public interface FeatureEvent : FeatureMessage {

    /**
     * Represents a unique identifier for this feature event or a group of events.
     */
    public val id: String

    /**
     * Represents the identifier of the immediate parent event, if applicable, in the hierarchy
     * of feature events or messages.
     */
    public val parentId: String?

    /**
     * Represents the execution path of the event within the feature processing workflow.
     *
     * The execution path provides a hierarchical view of the execution flow, allowing traceability
     * and contextual understanding of the current point of execution within the feature processing workflow.
     */
    public val executionPath: AgentExecutionPath
}
