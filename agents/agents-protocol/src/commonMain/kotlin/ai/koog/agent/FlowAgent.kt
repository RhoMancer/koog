package ai.koog.agent

import ai.koog.runtime.AgentFlowRuntimeProvider

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
    public val provider: AgentFlowRuntimeProvider

    /**
     *
     */
    public val type: FlowAgentKind

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
    public fun execute(): String
}
