package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel

/**
 * Interface defining and managing configurations or metadata for supported Large Language Models (LLMs).
 * This serves as a contract for providing LLM-specific definitions, capabilities, and configurations that are
 * needed during interactions with LLM providers. Typically, implementations of this interface represent
 * contextual information about various LLMs.
 */
public interface LLModelDefinitions {

    /**
     * List all models under this definition
     */
    public val models: List<LLModel>
}
