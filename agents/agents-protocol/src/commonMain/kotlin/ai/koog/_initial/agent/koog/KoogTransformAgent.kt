package ai.koog._initial.agent.koog

import ai.koog._initial.agent.FlowAgentConfig
import ai.koog._initial.agent.FlowAgentKind
import ai.koog._initial.model.FlowAgentInput
import ai.koog._initial.model.FlowAgentPrompt

/**
 * Transform agent that transforms data without using an LLM.
 * This agent is created when type is "transform" in the flow configuration.
 * It performs data transformations based on the defined input transformations.
 */
public class KoogTransformAgent(
    name: String,
    prompt: FlowAgentPrompt? = null,
    input: FlowAgentInput,
    config: FlowAgentConfig
) : KoogFlowAgent(
    name = name,
    type = FlowAgentKind.TRANSFORM,
    model = null,
    prompt = prompt,
    input = input,
    config = config
) {
    override suspend fun execute(): String {
        val transformations = input.transformations
        return if (transformations.isNullOrEmpty()) {
            input.task ?: ""
        } else {
            // Apply transformations: for now, return a description of the transformations
            transformations.joinToString("\n") { transformation ->
                "${transformation.input} -> ${transformation.to}"
            }
        }
    }
}
