package ai.koog.agent

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public enum class FlowAgentKind {
    TASK,
    VERIFY,
    TRANSFORM,
    PARALLEL,
}
