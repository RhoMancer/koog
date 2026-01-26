package ai.koog.agent

import ai.koog.model.FlowAgentInput
import ai.koog.model.FlowAgentPrompt

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
     * TODO: SD -- fix this
     */
    override fun execute(): String {
        return input.task.toString()
    }
}
