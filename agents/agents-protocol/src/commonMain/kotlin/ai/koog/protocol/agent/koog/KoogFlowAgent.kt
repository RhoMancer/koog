package ai.koog.protocol.agent.koog

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt

public data class KoogFlowAgent(
    override val name: String,
    override val type: FlowAgentKind,
    override val model: String? = null,
    override val prompt: FlowAgentPrompt? = null,
    override val input: FlowAgentInput,
    override val config: FlowAgentConfig
) : FlowAgent {


}
