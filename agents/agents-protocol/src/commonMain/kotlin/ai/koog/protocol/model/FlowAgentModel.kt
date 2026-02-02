package ai.koog.protocol.model

import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt
import ai.koog.protocol.agent.FlowAgentRuntimeKind
import ai.koog.protocol.agent.ToolChoiceKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * agent:
 *      name: string
 * 	    type: agent.type
 * 	    model: string (optional, falls back to flow's defaultModel)
 * 	    provider: agent.provider
 * 	    config: agent.config
 * 	    runtime: agent.runtime
 * 	    prompt: agent.prompt
 * 	    input: agent.input (task description for task/verify agents)
 * 	    output: agent.output
 * 	    features: [agent.feature]
 */
@Serializable
public data class FlowAgentModel(
    val name: String,
    val type: FlowAgentKind,
    val input: String,
    val model: String? = null,
    val runtime: FlowAgentRuntimeKind? = null,
    val config: FlowAgentConfig? = null,
    val prompt: FlowAgentPrompt? = null,
    val output: FlowAgentOutputModel? = null,
)

/**
 *
 */
@Serializable
public data class FlowAgentPromptModel(
    val system: String,
    val user: String? = null
)

/**
 *
 */
@Serializable
public data class FlowAgentOutputModel(
    val schema: String
)
