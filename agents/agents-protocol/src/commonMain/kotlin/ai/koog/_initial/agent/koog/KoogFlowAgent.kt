package ai.koog._initial.agent.koog

import ai.koog._initial.agent.FlowAgent
import ai.koog._initial.agent.FlowAgentConfig
import ai.koog._initial.agent.FlowAgentKind
import ai.koog._initial.model.FlowAgentInput
import ai.koog._initial.model.FlowAgentPrompt

/**
 * Base class for Koog flow agents that provides common functionality.
 */
public abstract class KoogFlowAgent(
    override val name: String,
    override val type: FlowAgentKind,
    override val model: String? = null,
    override val prompt: FlowAgentPrompt? = null,
    override val input: FlowAgentInput,
    override val config: FlowAgentConfig
) : FlowAgent
