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
    val model: String,
    val temperature: Double,
    val maxIterations: Int,
    val maxTokens: Int,
    val topP: Double,
    public val toolChoice: ToolChoiceKind,
    val speculation: String,
)
