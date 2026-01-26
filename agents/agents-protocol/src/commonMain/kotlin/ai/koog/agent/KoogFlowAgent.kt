package ai.koog.agent

import ai.koog.model.FlowAgentConfigModel
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
    override val prompt: FlowAgentPrompt,
    override val input: FlowAgentInput,
    override val config: FlowAgentConfig
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
        return runtime.executeAgent(config ?: FlowAgentConfigModel())
    }
}
