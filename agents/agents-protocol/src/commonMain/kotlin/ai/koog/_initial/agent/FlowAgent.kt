package ai.koog._initial.agent

import ai.koog._initial.model.FlowAgentInput
import ai.koog._initial.model.FlowAgentPrompt

/**
 *
 */
public interface FlowAgent {

    /**
     * Agent identifier (maps to "name" in JSON).
     */
    public val name: String

    /**
     * Agent type (maps to "type" in JSON).
     */
    public val type: FlowAgentKind

    /**
     * Model identifier (e.g., "openai/gpt-4o").
     */
    public val model: String?

    /**
     * Agent prompt configuration.
     */
    public val prompt: FlowAgentPrompt?

    /**
     * Agent input configuration.
     */
    public val input: FlowAgentInput

    /**
     *
     */
    public val config: FlowAgentConfig

    /**
     * Executes the flow agent and returns the result.
     */
    public suspend fun execute(): String
}
