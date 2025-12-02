package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.withParent
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging

internal class GenericAgentEnvironmentProxy(
    val environment: AIAgentEnvironment,
    val context: AIAgentContext,
) : AIAgentEnvironment {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun executeTool(runId: String, toolCall: Message.Tool.Call): ReceivedToolResult =
        withParent(context, toolCall.tool) { parentId, id ->
            logger.trace { "Executing tool call (run id: $runId, tool call id: ${toolCall.id}, tool: ${toolCall.tool}, args: ${toolCall.contentJson})" }

            context.pipeline.onToolCallStarting(
                id, parentId, runId, toolCall.id, toolCall.tool, toolCall.contentJson
            )

            val toolResult = environment.executeTool(runId, toolCall)
            processToolResult(id, parentId, toolResult)

            logger.trace { "Tool call completed (run id: $runId, tool call id: ${toolCall.id}, tool: ${toolCall.tool}, args: ${toolCall.contentJson}) with result: $toolResult" }
            toolResult
        }

    override suspend fun reportProblem(runId: String, exception: Throwable) {
        environment.reportProblem(runId, exception)
    }

    //region Private Methods

    private suspend fun processToolResult(
        id: String,
        parentId: String?,
        toolResult: ReceivedToolResult
    ) {
        val toolResultType = toolResult.resultType

        when (toolResultType) {
            is ToolResultType.Success -> {
                context.pipeline.onToolCallCompleted(
                    id, parentId, context.runId, toolResult.id, toolResult.tool, toolResult.toolArgs, toolResult.result
                )
            }

            is ToolResultType.Failure -> {
                context.pipeline.onToolCallFailed(
                    id, parentId, context.runId, toolResult.id, toolResult.tool, toolResult.toolArgs, toolResult.content, toolResultType.error
                )
            }

            is ToolResultType.ValidationError -> {
                context.pipeline.onToolValidationFailed(
                    id, parentId, context.runId, toolResult.id, toolResult.tool, toolResult.toolArgs, toolResult.content, toolResultType.error
                )
            }

        }
    }

    //endregion Private Methods
}
