package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.feature.model.AIAgentError
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered when an AI agent starts executing a strategy.
 *
 * This event provides details about the agent's strategy, making it useful for
 * monitoring, debugging, and tracking the lifecycle of AI agents within the system.
 *
 * @property id A unique identifier for the group of events associated with the agent execution event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentStartingEvent(
    override val id: String,
    override val parentId: String?,
    val agentId: String,
    val runId: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentStartingEvent].
     * Note! Do not relay on [id] and [parentId] parameters with this constructor.
     */
    @Deprecated(
        message = "Please use constructor with id and parentId parameters",
        replaceWith = ReplaceWith("AgentStartingEvent(id, parentId, agentId, runId)")
    )
    public constructor(
        agentId: String,
        runId: String
    ) : this(AgentStartingEvent::class.simpleName.toString(), null, agentId, runId)
}

/**
 * Event representing the completion of an AI Agent's execution.
 *
 * This event is emitted when an AI Agent finishes executing a strategy, providing
 * information about the strategy and its result. It can be used for logging, tracing,
 * or monitoring the outcomes of agent operations.
 *
 * @property id A unique identifier for the group of events associated with the agent execution event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property result The result of the strategy execution, or null if unavailable;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentCompletedEvent(
    override val id: String,
    override val parentId: String?,
    val agentId: String,
    val runId: String,
    val result: String?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentCompletedEvent].
     * Note! Do not relay on [id] and [parentId] parameters with this constructor.
     */
    @Deprecated(
        message = "Please use constructor with id and parentId parameters",
        replaceWith = ReplaceWith("AgentCompletedEvent(id, parentId, agentId, runId, result)")
    )
    public constructor(
        agentId: String,
        runId: String,
        result: String?
    ) : this(AgentCompletedEvent::class.simpleName.toString(), null, agentId, runId, result)
}

/**
 * Represents an event triggered when an AI agent run encounters an error.
 *
 * This event is used to capture error information during the execution of an AI agent
 * strategy, including details of the strategy and the encountered error.
 *
 * @property id A unique identifier for the group of events associated with the agent execution event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property error The [AIAgentError] instance encapsulating details about the encountered error,
 *                 such as its message, stack trace, and cause;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentExecutionFailedEvent(
    override val id: String,
    override val parentId: String?,
    val agentId: String,
    val runId: String,
    val error: AIAgentError?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentExecutionFailedEvent].
     * Note! Do not relay on [id] and [parentId] parameters with this constructor.
     */
    @Deprecated(
        message = "Please use constructor with id and parentId parameters",
        replaceWith = ReplaceWith("AgentExecutionFailedEvent(id, parentId, agentId, runId, error)")
    )
    public constructor(
        agentId: String,
        runId: String,
        error: AIAgentError
    ) : this(AgentExecutionFailedEvent::class.simpleName.toString(), null, agentId, runId, error)
}

/**
 * Represents an event that signifies the closure or termination of an AI agent identified
 * by a unique `agentId`.
 *
 * @property id A unique identifier for the group of events associated with the agent execution event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property agentId The unique identifier of the AI agent;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentClosingEvent(
    override val id: String,
    override val parentId: String?,
    val agentId: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentClosingEvent].
     * Note! Do not relay on [id] and [parentId] parameters with this constructor.
     */
    @Deprecated(
        message = "Please use constructor with id and parentId parameters",
        replaceWith = ReplaceWith("AgentClosingEvent(id, parentId, agentId)")
    )
    public constructor(
        agentId: String
    ) : this(AgentClosingEvent::class.simpleName.toString(), null, agentId)
}

//region Deprecated

@Deprecated(
    message = "Use AgentStartingEvent instead",
    replaceWith = ReplaceWith("AgentStartingEvent")
)
public typealias AIAgentStartedEvent = AgentStartingEvent

@Deprecated(
    message = "Use AgentCompletedEvent instead",
    replaceWith = ReplaceWith("AgentCompletedEvent")
)
public typealias AIAgentFinishedEvent = AgentCompletedEvent

@Deprecated(
    message = "Use AgentExecutionFailedEvent instead",
    replaceWith = ReplaceWith("AgentExecutionFailedEvent")
)
public typealias AIAgentRunErrorEvent = AgentExecutionFailedEvent

@Deprecated(
    message = "Use AgentClosingEvent instead",
    replaceWith = ReplaceWith("AgentClosingEvent")
)
public typealias AIAgentBeforeCloseEvent = AgentClosingEvent

//endregion Deprecated
