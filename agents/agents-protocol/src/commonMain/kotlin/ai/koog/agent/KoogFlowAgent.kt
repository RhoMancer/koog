package ai.koog.agent

import ai.koog.runtime.AgentFlowKoogRuntime
import ai.koog.runtime.AgentFlowRuntime
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class KoogFlowAgent(
    override val id: String,
    override val kind: FlowAgentKind,
    override val model: String,
    override val input: String,
    override val config: FlowAgentConfig
) : FlowAgent {

    /**
     *
     */
    override val runtime: AgentFlowRuntime = AgentFlowKoogRuntime()

    /**
     *
     */
    override fun execute(): String {
        return runtime.executeAgent(config)
    }
}
