package ai.koog.protocol.agent.agents.transform

import ai.koog.protocol.agent.FlowAgentInput
import kotlin.reflect.KClass

/**
 *
 */
public data class FlowInputTransformation(
    val value: FlowAgentInput,
    val to: KClass<FlowAgentInput>
)
