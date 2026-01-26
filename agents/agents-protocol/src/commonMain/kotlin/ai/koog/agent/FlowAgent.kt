package ai.koog.agent

import ai.koog.runtime.AgentFlowRuntime

/**
 * agent:
 *      name: string
 * 	    type: agent.type
 * 	    provider: agent.provider
 * 	    config: agent.config
 * 	    prompt: agent.prompt
 * 	    input: agent.input
 * 	    output: agent.output
 * 	    features: [agent.feature]
 */
public interface FlowAgent {

    /**
     * Agent identifier (maps to "name" in JSON).
     */
    public val name: String

    /**
     *
     */
    public val runtime: AgentFlowRuntime

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
    public val input: FlowAgentInput?

    /**
     *
     */
    public val config: FlowAgentConfig?

    /**
     *
     */
    public fun execute(): String
}
