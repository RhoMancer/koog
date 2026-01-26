package ai.koog.agents.core.feature.handler.llm

import ai.koog.prompt.dsl.Prompt

/**
 * A handler responsible for managing the execution flow of a Large Language Model (LLM) call.
 * It allows customization of logic to be executed before and after the LLM is called,
 * as well as transformation of the prompt before it is sent to the model.
 */
public class LLMCallEventHandler {

    /**
     * A transformer that can modify the prompt before it is sent to the language model.
     *
     * This transformer enables features to implement patterns like:
     * - RAG (Retrieval-Augmented Generation): Query a database and add relevant context to the prompt
     * - Prompt templates: Apply standardized formatting or instructions
     * - Context injection: Add user-specific or session-specific information
     * - Content filtering: Modify or sanitize the prompt before sending
     *
     * Multiple transformers can be chained together.
     * Each transformer receives the prompt from the previous one and returns a modified version.
     *
     * By default, the transformer returns the prompt unchanged.
     */
    public var llmPromptTransformingHandler: LLMPromptTransformingHandler =
        LLMPromptTransformingHandler { _, prompt -> prompt }

    /**
     * A handler that is invoked before making a call to the Language Learning Model (LLM).
     *
     * This handler enables customization or preprocessing steps to be applied before querying the model.
     * It accepts the prompt, a list of tools, the model, and a session UUID as inputs, allowing
     * users to define specific logic or modifications to these inputs before the call is made.
     */
    public var llmCallStartingHandler: LLMCallStartingHandler =
        LLMCallStartingHandler { _ -> }

    /**
     * A handler invoked after a call to a language model (LLM) is executed.
     *
     * This variable represents a custom implementation of the `AfterLLMCallHandler` functional interface,
     * allowing post-processing or custom logic to be performed once the LLM has returned a response.
     *
     * The handler receives various pieces of information about the LLM call, including the original prompt,
     * the tools used, the model invoked, the responses returned by the model, and a unique run identifier.
     *
     * Customize this handler to implement specific behavior required immediately after LLM processing.
     */
    public var llmCallCompletedHandler: LLMCallCompletedHandler =
        LLMCallCompletedHandler { _ -> }

    /**
     * Transforms the provided prompt using the configured prompt transformer.
     *
     * This transformation occurs before [LLMCallStartingHandler] is invoked.
     *
     * @param context The context containing information about the prompt transformation
     * @param prompt The prompt to be transformed
     * @return The transformed prompt
     */
    public suspend fun transformRequest(
        context: LLMPromptTransformingContext,
        prompt: Prompt
    ): Prompt = llmPromptTransformingHandler.transform(context, prompt)
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

/**
 * A functional interface for transforming prompts before they are sent to the language model.
 *
 * This handler is invoked before [LLMCallStartingHandler], allowing prompt modification
 * prior to the LLM call event handlers being triggered.
 *
 * This handler enables features to implement patterns such as:
 * - RAG (Retrieval-Augmented Generation): Query a vector database and add relevant context
 * - Prompt augmentation: Add system instructions, user context, or conversation history
 * - Content filtering: Sanitize or modify prompts before sending
 * - Logging and auditing: Record prompts for compliance or debugging
 *
 * Multiple transformers can be registered and will be applied in sequence (chain pattern).
 * Each transformer receives the prompt from the previous one and returns a modified version.
 *
 * Example usage:
 * ```kotlin
 * LLMPromptTransformingHandler { context, prompt ->
 *     // Query database for relevant context
 *     val relevantDocs = database.search(prompt.messages.last().content)
 *     
 *     // Augment the prompt with retrieved context
 *     prompt.copy(
 *         messages = listOf(
 *             Message.System("Context: ${relevantDocs.joinToString()}"),
 *             *prompt.messages.toTypedArray()
 *         )
 *     )
 * }
 * ```
 */
public fun interface LLMPromptTransformingHandler {
    /**
     * Transforms the provided prompt based on the given context.
     *
     * @param context The context containing information about the LLM request, including
     *                the run ID, model, available tools, and agent context.
     * @param prompt The current prompt to be transformed.
     * @return The transformed prompt that will be sent to the language model
     *         (or passed to the next transformer in the chain).
     */
    public suspend fun transform(
        context: LLMPromptTransformingContext,
        prompt: Prompt
    ): Prompt
}
