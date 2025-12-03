package ai.koog.agents.core.feature.message

import ai.koog.agents.core.agent.context.AgentExecutionInfo

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
     * Provides contextual information about the execution associated with a feature event.
     */
    public val executionInfo: AgentExecutionInfo
}
