package ai.koog.model

import ai.koog._initial.agent.FlowAgentConfig
import ai.koog._initial.agent.FlowAgentKind
import ai.koog._initial.agent.ToolChoiceKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * agent:
 *      name: string
 * 	    type: agent.type
 * 	    provider: agent.provider
 * 	    config: agent.config
 * 	    runtime: agent.runtime
 * 	    prompt: agent.prompt
 * 	    input: agent.input
 * 	    output: agent.output
 * 	    features: [agent.feature]
 */
@Serializable
public data class FlowAgentModel(
    val name: String,
    val type: FlowAgentKind,
    val model: String,
    val input: JsonElement,
    val runtime: FlowAgentRuntimeModel? = null,
    val config: FlowAgentConfigModel? = null,
    val prompt: FlowAgentPromptModel? = null,
    val output: FlowAgentOutputModel? = null,
)

/**
 *
 */
@Serializable
public enum class FlowAgentTypeModel {
    @SerialName("task")
    TASK,

    @SerialName("verify")
    VERIFY,

    @SerialName("transform")
    TRANSFORM,

    @SerialName("parallel")
    PARALLEL,
}

/**
 *
 */
@Serializable
public enum class FlowAgentRuntimeModel(public val id: String) {
    KOOG("koog"),
    LANG_CHAIN("lang_chain"),
    CLAUDE_CODE("claude-code"),
    CODEX("codex")
}

/**
 * config:
 *      model: string,
 *      temperature: number
 *      max_iterations: number
 *      max_tokens: number
 *      top_p: number
 * 	    tool_choice: agent.config.tool_choice
 * 	    speculation: string
 */
@Serializable
public data class FlowAgentConfigModel(
    val model: String? = null,
    val temperature: Double? = null,
    val maxIterations: Int? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val toolChoice: ToolChoiceKind? = null,
    val speculation: String? = null,
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

public fun FlowAgentConfigModel?.toFlowAgentConfig(): FlowAgentConfig =
    this?.let {
        FlowAgentConfig(
            model = model,
            temperature = temperature,
            maxIterations = maxIterations,
            maxTokens = maxTokens,
            topP = topP,
            toolChoice = toolChoice,
            speculation = speculation
        )
    } ?: FlowAgentConfig()
