package ai.koog.agent

import kotlinx.serialization.Serializable

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
public data class FlowAgentConfig(
    val model: String? = null,
    val temperature: Double = 0.7,
    val maxIterations: Int = 10,
    val maxTokens: Int = 4096,
    val topP: Double = 1.0,
    val toolChoice: ToolChoiceKind = ToolChoiceKind.Auto(),
    val speculation: String = "",
)
