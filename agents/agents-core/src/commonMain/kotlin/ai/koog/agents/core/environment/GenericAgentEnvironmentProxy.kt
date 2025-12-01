package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.withParent
import ai.koog.prompt.message.Message

internal class GenericAgentEnvironmentProxy(
    val environment: AIAgentEnvironment,
    val context: AIAgentContext,
) : AIAgentEnvironment {

    override suspend fun executeTools(runId: String, toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> =
        withParent(context, "TODO: SD -- ") { parentId, id ->
            context.pipeline.onToolCallStarting(id, parentId, runId, toolCalls.firstOrNull()?.id, null, null)
            val toolResult = environment.executeTools(runId, toolCalls)

            when (toolResult.size) {}

            toolResult
        }
}
