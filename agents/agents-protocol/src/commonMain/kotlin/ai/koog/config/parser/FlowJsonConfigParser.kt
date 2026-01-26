package ai.koog.config.parser

import ai.koog.agent.FlowAgent
import ai.koog.agent.KoogFlowAgent
import ai.koog.flow.FlowConfig
import ai.koog.model.FlowAgentInput
import ai.koog.model.FlowAgentModel
import ai.koog.model.FlowConfigModel
import ai.koog.model.toFlowAgentConfig
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
        val model = json.decodeFromString<FlowConfigModel>(input)
        return FlowConfig(
            id = model.id,
            version = model.version,
            agents = model.agents.map { it.toFlowAgent() },
            transitions = model.transitions
        )
    }

    private fun FlowAgentModel.toFlowAgent(): FlowAgent {
        return when (runtime) {
            "koog", null -> KoogFlowAgent(
                name = name,
                type = type,
                model = model,
                prompt = prompt,
                input = input ?: FlowAgentInput(),
                config = config.toFlowAgentConfig()
            )
            else -> error("Unknown runtime: $runtime")
        }
    }
}
