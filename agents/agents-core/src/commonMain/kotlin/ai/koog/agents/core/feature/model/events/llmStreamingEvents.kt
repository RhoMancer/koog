package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.utils.ModelInfo
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered when a language model (LLM) streaming operation is starting.
 *
 * This event holds metadata related to the initiation of the LLM streaming process, including
 * details about the run, the input prompt, the model used, and the tools involved.
 *
 * @property id A unique identifier for the group of events associated with the Streaming LLM event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property runId Unique identifier for the LLM run;
 * @property prompt The input prompt provided for the LLM operation;
 * @property model The description of the LLM model used during the call. Use the format: 'llm_provider:model_id';
 * @property tools A list of associated tools or resources that are part of the operation;
 * @property timestamp The time when the event occurred, represented in epoch milliseconds.
 */
@Serializable
public data class LLMStreamingStartingEvent(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val prompt: Prompt,
    val model: ModelInfo,
    val tools: List<String>,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with id, parentId parameters, and model parameter of type [ModelInfo]:
     *             LLMStreamingStartingEvent(id, parentId, runId, callId, prompt, model, tools, timestamp)
     */
    @Deprecated(
        message = "Please use constructor with id, parentId parameters, and model parameter of type [ModelInfo]: LLMStreamingStartingEvent(id, parentId, runId, callId, prompt, model, tools, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingStartingEvent(id, parentId, runId, callId, prompt, model, tools, timestamp)")
    )
    public constructor(
        runId: String,
        prompt: Prompt,
        model: String,
        tools: List<String>,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(LLMStreamingStartingEvent::class.simpleName.toString(), null, runId, prompt, ModelInfo.fromString(model), tools, timestamp)
}

/**
 * Event representing the receipt of a streaming frame from a Language Learning Model (LLM).
 *
 * This event occurs as part of the streaming interaction with the LLM, where individual
 * frames of data are sent incrementally. The event contains details about the specific
 * frame received, as well as metadata related to the event's timing and identity.
 *
 * @property id A unique identifier for the group of events associated with the Streaming LLM event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property runId The unique identifier for the LLM run or session associated with this event;
 * @property frame The frame data received as part of the streaming response. This can include textual
 *                 content, tool invocations, or signaling the end of the stream;
 * @property timestamp The timestamp of when the event was created, represented in milliseconds since the Unix epoch.
 */
@Serializable
public data class LLMStreamingFrameReceivedEvent(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val frame: StreamFrame,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with [id] and [parentId] parameters
     */
    @Deprecated(
        message = "Please use constructor with id and parentId parameters: LLMStreamingStartingEvent(id, parentId, runId, callId, prompt, model, tools, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingFrameReceivedEvent(id, parentId, runId, frame, timestamp)")
    )
    public constructor(
        runId: String,
        frame: StreamFrame,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(LLMStreamingFrameReceivedEvent::class.simpleName.toString(), null, runId, frame, timestamp)
}

/**
 * Represents an event indicating a failure in the streaming process of a Language Learning Model (LLM).
 *
 * This event captures details of the failure encountered during the streaming operation.
 * It includes information such as the unique identifier of the operation run, a detailed
 * error description, and inherits common properties such as event ID and timestamp.
 *
 * @property id A unique identifier for the group of events associated with the Streaming LLM event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property runId A unique identifier representing the specific operation or run in which the failure occurred;
 * @property error An instance of [AIAgentError], containing information about the error encountered, including its
 *                 message, stack trace, and cause, if available;
 * @property timestamp A timestamp indicating when the event occurred, represented in milliseconds since the Unix epoch.
 */
@Serializable
public data class LLMStreamingFailedEvent(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val error: AIAgentError,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with [id] and [parentId] parameters
     */
    @Deprecated(
        message = "Please use constructor with id and parentId parameters: LLMStreamingFailedEvent(id, parentId, runId, error, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingFailedEvent(id, parentId, runId, error, timestamp)")
    )
    public constructor(
        runId: String,
        error: AIAgentError,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(LLMStreamingFailedEvent::class.simpleName.toString(), null, runId, error, timestamp)
}

/**
 * Represents an event that occurs when the streaming process of a Large Language Model (LLM) call is completed.
 *
 * @property id A unique identifier for the group of events associated with the Streaming LLM event.
 * @property parentId The unique identifier of the parent event, if applicable.
 * @property runId The unique identifier of the LLM run;
 * @property prompt The prompt associated with the LLM call;
 * @property model The description of the LLM model used during the call. Use the format: 'llm_provider:model_id';
 * @property tools A list of tools used or invoked during the LLM call;
 * @property timestamp The timestamp indicating when the event occurred, represented in milliseconds since the epoch, defaulting to the current system time.
 */
@Serializable
public data class LLMStreamingCompletedEvent(
    override val id: String,
    override val parentId: String?,
    val runId: String,
    val prompt: Prompt,
    val model: ModelInfo,
    val tools: List<String>,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with id, parentId parameters, and model parameter of type [ModelInfo]:
     *             LLMStreamingCompletedEvent(id, parentId, runId, callId, prompt, model, tools, timestamp)
     */
    @Deprecated(
        message = "Please use constructor with id, parentId parameters, and model parameter of type [ModelInfo]: LLMStreamingCompletedEvent(id, parentId, runId, callId, prompt, model, tools, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingCompletedEvent(id, parentId, runId, callId, prompt, model, tools, timestamp)")
    )
    public constructor(
        runId: String,
        prompt: Prompt,
        model: String,
        tools: List<String>,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(runId, null, runId, prompt, ModelInfo.fromString(model), tools, timestamp)
}
