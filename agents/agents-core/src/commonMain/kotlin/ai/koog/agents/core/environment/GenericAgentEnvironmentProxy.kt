package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentExecutionInfo
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

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
        withParent(context, partName = toolCall.tool) { executionInfo ->
            logger.trace { "Executing tool call (run id: ${context.runId}, tool call id: ${toolCall.id}, tool: ${toolCall.tool}, args: ${toolCall.contentJson})" }

            context.pipeline.onToolCallStarting(
                executionInfo, context.runId, toolCall.id, toolCall.tool, toolCall.contentJson
            )

            val toolResult = environment.executeTool(toolCall)
            processToolResult(executionInfo, toolResult)

            logger.trace { "Tool call completed (run id: ${context.runId}, tool call id: ${toolCall.id}, tool: ${toolCall.tool}, args: ${toolCall.contentJson}) with result: $toolResult" }
            toolResult
        }

    override suspend fun reportProblem(exception: Throwable) {
        environment.reportProblem(exception)
    }

    //region Private Methods

    private suspend fun processToolResult(
        executionInfo: AgentExecutionInfo,
        toolResult: ReceivedToolResult
    ) {
        when (val toolResultKind = toolResult.resultKind) {
            is ToolResultKind.Success -> {
                context.pipeline.onToolCallCompleted(
                    executionInfo, context.runId, toolResult.id, toolResult.tool, toolResult.toolArgs, toolResult.toolDescription, toolResult.result
                )
            }

            is ToolResultKind.Failure -> {
                context.pipeline.onToolCallFailed(
                    executionInfo, context.runId, toolResult.id, toolResult.tool, toolResult.toolArgs, toolResult.toolDescription,toolResult.content, toolResultKind.exception
                )
            }

            is ToolResultKind.ValidationError -> {
                context.pipeline.onToolValidationFailed(
                    executionInfo, context.runId, toolResult.id, toolResult.tool, toolResult.toolArgs, toolResult.toolDescription, toolResult.content, toolResultKind.exception
                )
            }
        }
    }

    //endregion Private Methods
}
