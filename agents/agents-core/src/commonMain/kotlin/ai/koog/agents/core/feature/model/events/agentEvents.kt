package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.agent.context.AgentExecutionPath
import ai.koog.agents.core.feature.model.AIAgentError
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered when an AI agent starts executing a strategy.
 *
 * This event provides details about the agent's strategy, making it useful for
 * monitoring, debugging, and tracking the lifecycle of AI agents within the system.
 *
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentStartingEvent(
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentStartingEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentStartingEvent(executionInfo, agentId, runId)")
    )
    public constructor(
        agentId: String,
        runId: String
    ) : this(
        executionInfo = AgentExecutionInfo(
            id = AgentStartingEvent::class.simpleName.toString(),
            parentId = null,
            path = AgentExecutionPath.EMPTY
        ),
        agentId = agentId,
        runId = runId
    )
}

/**
 * Event representing the completion of an AI Agent's execution.
 *
 * This event is emitted when an AI Agent finishes executing a strategy, providing
 * information about the strategy and its result. It can be used for logging, tracing,
 * or monitoring the outcomes of agent operations.
 *
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property result The result of the strategy execution, or null if unavailable;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentCompletedEvent(
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    val result: String?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentCompletedEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentCompletedEvent(executionInfo, agentId, runId, result)")
    )
    public constructor(
        agentId: String,
        runId: String,
        result: String?
    ) : this(
        executionInfo = AgentExecutionInfo(
            id = AgentCompletedEvent::class.simpleName.toString(),
            parentId = null,
            path = AgentExecutionPath.EMPTY
        ),
        agentId = agentId,
        runId = runId,
        result = result
    )
}

/**
 * Represents an event triggered when an AI agent run encounters an error.
 *
 * This event is used to capture error information during the execution of an AI agent
 * strategy, including details of the strategy and the encountered error.
 *
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property error The [AIAgentError] instance encapsulating details about the encountered error,
 *                 such as its message, stack trace, and cause;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentExecutionFailedEvent(
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    val error: AIAgentError?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentExecutionFailedEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentExecutionFailedEvent(executionInfo, agentId, runId, error)")
    )
    public constructor(
        agentId: String,
        runId: String,
        error: AIAgentError
    ) : this(
        executionInfo = AgentExecutionInfo(
            id = AgentExecutionFailedEvent::class.simpleName.toString(),
            parentId = null,
            path = AgentExecutionPath.EMPTY
        ),
        agentId = agentId,
        runId = runId,
        error = error
    )
}

/**
 * Represents an event that signifies the closure or termination of an AI agent identified
 * by a unique `agentId`.
 *
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentClosingEvent(
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentClosingEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentClosingEvent(executionInfo, agentId)")
    )
    public constructor(
        agentId: String
    ) : this(
        executionInfo = AgentExecutionInfo(
            id = AgentClosingEvent::class.simpleName.toString(),
            parentId = null,
            path = AgentExecutionPath.EMPTY
        ),
        agentId = agentId
    )
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
