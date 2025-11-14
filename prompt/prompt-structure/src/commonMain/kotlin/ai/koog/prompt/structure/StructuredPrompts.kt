package ai.koog.prompt.structure

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

/**
 * An object that provides utilities for formatting structured output prompts.
 */
public object StructuredOutputPrompts {

    /**
     * Updates a given prompt to configure structured output using the specified large language model (LLM).
     * Depending on the model's support for structured outputs, the prompt is updated either manually or natively.
     *
     * @param prompt The original prompt to be updated with the structured output configuration.
     * @param structuredRequest The structured output configuration to be applied to the prompt.
     * @return A new prompt reflecting the updated structured output configuration.
     */
    public fun appendStructuredOutputInstructions(prompt: Prompt, structuredRequest: StructuredRequest<*>): Prompt {
        return when (structuredRequest) {
            // Don't set schema parameter in prompt and coerce the model manually with user message to provide a structured response.
            is StructuredRequest.Manual -> {
                prompt(prompt) {
                    user(
                        markdown {
                            markdown {
                                h2("NEXT MESSAGE OUTPUT FORMAT")
                                +"The output in the next message MUST ADHERE TO ${structuredRequest.structure.id} format."
                                br()

                                structuredRequest.structure.definition(this)
                            }
                        }
                    )
                }
            }

            // Rely on built-in model capabilities to provide structured response.
            is StructuredRequest.Native -> {
                prompt.withUpdatedParams { schema = structuredRequest.structure.schema }
            }
        }
    }
}
