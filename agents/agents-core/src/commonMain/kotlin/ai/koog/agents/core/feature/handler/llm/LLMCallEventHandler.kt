package ai.koog.agents.core.feature.handler.llm

/**
 * A handler responsible for managing the execution flow of a Large Language Model (LLM) call.
 * It allows customization of logic to be executed before and after the LLM is called.
 */
public class LLMCallEventHandler { // : AgentLifecycleEventHandler {

    private val llmCallStartingHandlers: MutableList<LLMCallStartingHandler> =
        mutableListOf()

    private val llmCallCompletedHandlers: MutableList<LLMCallCompletedHandler> =
        mutableListOf()

    /**
     * Registers a handler to be invoked before a call is made to the Large Language Model (LLM).
     *
     * @param handler The logic to be executed before the LLM call.
     */
    public fun addLLMCallStartingHandler(handler: LLMCallStartingHandler) {
        llmCallStartingHandlers.add(handler)
    }

    /**
     * Registers a handler to be invoked after a call to the Large Language Model (LLM) is completed.
     *
     * @param handler The logic to be executed after the LLM call is completed.
     */
    public fun addLLMCallCompletedHandler(handler: LLMCallCompletedHandler) {
        llmCallCompletedHandlers.add(handler)
    }

    /**
     * Invokes all registered handlers for the event triggered before making a call to the LLM.
     *
     * @param eventContext The context for the LLM call starting event.
     */
    public suspend fun invokeOnLLMCallStartingHandlers(eventContext: LLMCallStartingContext) {
        llmCallStartingHandlers.forEach { handler -> handler.handle(eventContext) }
    }

    /**
     * Invokes all registered handlers associated with the completion of a call to the LLM.
     *
     * @param eventContext The context for the LLM call completion event.
     */
    public suspend fun invokeOnLLMCallCompletedHandlers(eventContext: LLMCallCompletedContext) {
        llmCallCompletedHandlers.forEach { handler -> handler.handle(eventContext) }
    }
}

/**
 * A functional interface implemented to handle logic that occurs before invoking a large language model (LLM).
 * It allows preprocessing steps or validation based on the provided prompt, available tools, targeted LLM model,
 * and a unique run identifier.
 *
 * This can be particularly useful for custom input manipulation, logging, validation, or applying
 * configurations to the LLM request based on external context.
 */
public fun interface LLMCallStartingHandler {
    /**
     * Handles a language model interaction by processing the given prompt, tools, model, and sess
     *
     * @param eventContext The context for the event
     */
    public suspend fun handle(eventContext: LLMCallStartingContext)
}

/**
 * Represents a functional interface for handling operations or logic that should occur after a call
 * to a large language model (LLM) is made. The implementation of this interface provides a mechanism
 * to perform custom logic or processing based on the provided inputs, such as the prompt, tools,
 * model, and generated responses.
 */
public fun interface LLMCallCompletedHandler {
    /**
     * Handles the post-processing of a prompt and its associated data after a language model call.
     *
     * @param eventContext The context for the event
     */
    public suspend fun handle(eventContext: LLMCallCompletedContext)
}
