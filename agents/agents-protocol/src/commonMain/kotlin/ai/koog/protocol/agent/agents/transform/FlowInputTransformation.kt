package ai.koog.protocol.agent.agents.transform

import ai.koog.protocol.agent.FlowAgentInput
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 *
 */
@Serializable
public data class FlowInputTransformation(
    val value: FlowAgentInput,
    val to: KClass<FlowAgentInput>
)
