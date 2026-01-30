package ai.koog.protocol.parser

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentPrompt
import ai.koog.protocol.agent.FlowAgentRuntimeKind
import ai.koog.protocol.agent.KoogFlowAgent
import ai.koog.protocol.flow.FlowConfig
import ai.koog.protocol.model.FlowAgentModel
import ai.koog.protocol.model.FlowModel
import ai.koog.protocol.model.toFlowAgentInput
import kotlinx.serialization.json.Json

/**
 * Parser for JSON flow configuration files.
 */
public class FlowJsonConfigParser : FlowConfigParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun parse(input: String): FlowConfig {
        // Decode JSON description
        val model = json.decodeFromString<FlowModel>(input)

        return FlowConfig(
            id = model.id,
            version = model.version,
            agents = model.agents.map { agentModel -> agentModel.toFlowAgent() },
            tools = model.tools.map { toolModel -> toolModel.toFlowTool() },
            transitions = model.transitions.map { transitionModel -> transitionModel.toFlowTransition() }
        )
    }

    //region Private Methods

    private fun FlowAgentModel.toFlowAgent(): FlowAgent {
        return when (runtime) {
            FlowAgentRuntimeKind.KOOG,
            null -> createKoogFlowAgent()

            else -> error("Unknown runtime: $runtime")
        }
    }

    private fun FlowAgentModel.createKoogFlowAgent(): FlowAgent {
        return KoogFlowAgent(
            name = name,
            type = type,
            model = model,
            prompt = prompt ?: FlowAgentPrompt(""),
            input = input.toFlowAgentInput()
                ?: error("Unable to parse input for agent '$name': $input"),
            config = config ?: FlowAgentConfig()
        )
    }

    //endregion Private Methods
}
