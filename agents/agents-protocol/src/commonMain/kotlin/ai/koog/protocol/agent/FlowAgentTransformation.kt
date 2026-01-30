package ai.koog.protocol.agent

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Represents a transformation operation.
 */
@Serializable
public data class FlowAgentTransformation(
    val input: FlowAgentInput,
    val to: KClass<FlowAgentInput>
)
