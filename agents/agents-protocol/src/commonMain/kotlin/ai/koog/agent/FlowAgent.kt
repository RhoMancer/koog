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
     *
     */
    public val id: String

    /**
     *
     */
    public val runtime: AgentFlowRuntime

    /**
     *
     */
    public val kind: FlowAgentKind

    /**
     *
     */
    public val model: String

    /**
     *
     */
    public val input: String

    /**
     *
     */
    public val config: FlowAgentConfig

    /**
     *
     */
    public fun execute(): String
}
