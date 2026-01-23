package ai.koog.config.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Root JSON structure for flow configuration.
 */
@Serializable
public data class FlowJsonRoot(
    val agents: List<AgentJsonConfig>,
    val flow: FlowJsonDefinition
)

/**
 * JSON configuration for an agent.
 */
@Serializable
public data class AgentJsonConfig(
    val name: String,
    val type: String,
    val provider: String? = null,
    val config: AgentJsonSettings? = null,
    val prompt: PromptJsonConfig? = null,
    val input: AgentInputJson? = null
)

/**
 * JSON settings for agent configuration.
 */
@Serializable
public data class AgentJsonSettings(
    val model: String? = null,
    val temperature: Double? = null,
    @SerialName("max_iterations")
    val maxIterations: Int? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    val speculation: String? = null
)

/**
 * JSON configuration for prompt.
 */
@Serializable
public data class PromptJsonConfig(
    val system: String? = null,
    val user: String? = null
)

/**
 * JSON configuration for agent input.
 */
@Serializable
public data class AgentInputJson(
    val task: String? = null,
    val tools: List<String>? = null,
    val transformations: List<TransformationJson>? = null
)

/**
 * JSON configuration for transformation.
 */
@Serializable
public data class TransformationJson(
    val input: String,
    val to: String
)

/**
 * JSON definition for flow structure.
 */
@Serializable
public data class FlowJsonDefinition(
    val transitions: List<TransitionJson>
)

/**
 * JSON configuration for a transition.
 */
@Serializable
public data class TransitionJson(
    val from: String,
    val to: String,
    val condition: ConditionJson? = null
)

/**
 * JSON configuration for a condition.
 */
@Serializable
public data class ConditionJson(
    val variable: String,
    val operation: String,
    val value: JsonElement
)
