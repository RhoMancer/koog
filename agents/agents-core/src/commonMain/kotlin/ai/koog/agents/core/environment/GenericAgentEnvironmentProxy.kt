package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.withParent
import ai.koog.prompt.message.Message

internal class GenericAgentEnvironmentProxy(
    val environment: AIAgentEnvironment,
    val context: AIAgentContext,
) : AIAgentEnvironment {

    override suspend fun executeTool(runId: String, toolCall: Message.Tool.Call): ReceivedToolResult =
        withParent(context, toolCall.tool) { parentId, id ->

            context.pipeline.onToolCallStarting(
                id, parentId, runId, toolCall.id, toolCall.tool, toolCall.contentJson
            )

            val toolResult = environment.executeTool(runId, toolCall)


            toolResult
        }

    override suspend fun reportProblem(runId: String, exception: Throwable) {
        environment.reportProblem(runId, exception)
    }

    //region Private Methods

    private suspend fun processToolResult(
        id: String,
        parentId: String,
        toolResult: ReceivedToolResult
    ) {
        when (toolResult.resultType) {
            ToolResultType.SUCCESS -> {
                context.pipeline.onToolCallCompleted(
                    id, parentId, context.runId, toolResult.id, toolResult.tool, toolResult.content, toolResult.result
                )
            }
            ToolResultType.FAILURE -> {
                context.pipeline.onToolCallFailed(
                    id, parentId, context.runId, toolResult.id, toolResult.tool, toolResult.content, toolResult.error
                )
            }

        }
    }

    //endregion Private Methods
}
