package ai.koog.agents.core.feature.handler.llm

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * Represents the context for handling LLM-specific events within the framework.
 */
public interface LLMCallEventContext : AgentLifecycleEventContext

/**
 * Represents the context for transforming a prompt before it is sent to the language model.
 * This context is used by features that need to modify the prompt, such as adding context from
 * a database, implementing RAG (Retrieval-Augmented Generation), or applying prompt templates.
 *
 * Prompt transformation occurs before [LLMCallStartingContext] is triggered, allowing
 * modifications to be applied prior to the LLM call event handlers.
 *
 * @property executionInfo The execution information containing parentId and current execution path.
 * @property runId The unique identifier for this LLM call session.
 * @property prompt The prompt that will be transformed. This is the current state of the prompt
 *                  after any previous transformations.
 * @property model The language model instance that will be used for the call.
 * @property context The AI agent context providing access to agent state and configuration.
 */
public data class LLMPromptTransformingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val context: AIAgentContext
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMPromptTransforming
}

/**
 * Represents the context for handling a before LLM call event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this LLM call session.
 * @property prompt The prompt that will be sent to the language model.
 * @property model The language model instance being used.
 * @property tools The list of tool descriptors available for the LLM call.
 */
public data class LLMCallStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
    val context: AIAgentContext
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallStarting
}

/**
 * Represents the context for handling an after LLM call event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this LLM call session.
 * @property prompt The prompt that was sent to the language model.
 * @property model The language model instance that was used.
 * @property tools The list of tool descriptors that were available for the LLM call.
 * @property responses The response messages received from the language model.
 * @property moderationResponse The moderation response, if any, received from the language model.
 */
public data class LLMCallCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
    val responses: List<Message.Response>,
    val moderationResponse: ModerationResult?,
    val context: AIAgentContext
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallCompleted
}
