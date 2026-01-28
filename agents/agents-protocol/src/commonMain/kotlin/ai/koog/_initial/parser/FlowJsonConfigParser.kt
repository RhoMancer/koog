package ai.koog._initial.parser

import ai.koog._initial.agent.FlowAgent
import ai.koog._initial.agent.FlowAgentKind
import ai.koog._initial.agent.koog.KoogTaskAgent
import ai.koog._initial.agent.koog.KoogTransformAgent
import ai.koog._initial.agent.koog.KoogVerifyAgent
import ai.koog._initial.flow.FlowConfig
import ai.koog._initial.model.FlowAgentInput
import ai.koog._initial.model.FlowAgentModel
import ai.koog._initial.model.FlowConfigModel
import ai.koog._initial.model.toFlowAgentConfig
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
            "koog", null -> createKoogAgent()
            else -> error("Unknown runtime: $runtime")
        }
    }

    private fun FlowAgentModel.createKoogAgent(): FlowAgent {
        return when (type) {
            FlowAgentKind.TASK -> KoogTaskAgent(
                name = name,
                model = model,
                prompt = prompt,
                input = input ?: FlowAgentInput(),
                config = config.toFlowAgentConfig()
            )
            FlowAgentKind.VERIFY -> KoogVerifyAgent(
                name = name,
                model = model,
                prompt = prompt,
                input = input ?: FlowAgentInput(),
                config = config.toFlowAgentConfig()
            )
            FlowAgentKind.TRANSFORM -> KoogTransformAgent(
                name = name,
                prompt = prompt,
                input = input ?: FlowAgentInput(),
                config = config.toFlowAgentConfig()
            )
            FlowAgentKind.PARALLEL -> error("Parallel agent type is not yet supported")
        }
    }
}
