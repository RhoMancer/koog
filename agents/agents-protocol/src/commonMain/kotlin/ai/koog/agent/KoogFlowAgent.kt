package ai.koog.agent

import ai.koog.model.FlowAgentConfig
import ai.koog.model.FlowAgentInput
import ai.koog.model.FlowAgentPrompt
import ai.koog.runtime.AgentFlowKoogRuntime
import ai.koog.runtime.AgentFlowRuntime
import kotlinx.serialization.Transient

/**
 *
 */
public data class KoogFlowAgent(
    override val name: String,
    override val type: FlowAgentKind,
    override val model: String? = null,
    override val prompt: FlowAgentPrompt? = null,
    override val input: FlowAgentInput? = null,
    override val config: FlowAgentConfig? = null
) : FlowAgent {

    /**
     *
     */
    @Transient
    override val runtime: AgentFlowRuntime = AgentFlowKoogRuntime()

    /**
     *
     */
    override fun execute(): String {
        return runtime.executeAgent(config ?: FlowAgentConfig())
    }
}
