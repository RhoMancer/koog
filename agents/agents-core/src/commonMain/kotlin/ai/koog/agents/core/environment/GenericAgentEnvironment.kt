package ai.koog.agents.core.environment

import ai.koog.agents.core.environment.AIAgentEnvironmentUtils.mapToToolResult
import ai.koog.agents.core.model.message.AIAgentEnvironmentToolResultToAgentContent
import ai.koog.agents.core.model.message.AgentToolCallToEnvironmentContent
import ai.koog.agents.core.model.message.AgentToolCallsToEnvironmentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultMultipleToAgentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonElement

internal class GenericAgentEnvironment(
    private val agentId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
) : AIAgentEnvironment {

    override suspend fun executeTool(runId: String, toolCall: Message.Tool.Call): ReceivedToolResult {

        val message = AgentToolCallsToEnvironmentMessage(
            runId = runId,
            content = AgentToolCallToEnvironmentContent(
                toolCallId = toolCall.id,
                toolName = toolCall.tool,
                toolArgs = toolCall.contentJson
            )
        )

        val content = message.content

        logger.debug { "Handling tool call sent by server..." }
        val tool = toolRegistry.getToolOrNull(content.toolName)
            ?: run {
                logger.error { "Tool \"${content.toolName}\" not found." }
                return toolResult(
                    message = "Tool \"${content.toolName}\" not found. Use one of the available tools.",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = agentId,
                    resultType = ToolResultType.FAILURE,
                    result = null
                ).toResult()
            }

        val toolArgs = try {
            tool.decodeArgs(content.toolArgs)
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
            return toolResult(
                message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentId,
                resultType = ToolResultType.FAILURE,
                result = null
            ).toResult()
        }

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        } catch (e: ToolException) {
            return toolResult(
                message = e.message,
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentId,
                resultType = ToolResultType.VALIDATION_ERROR,
                result = null
            ).toResult()
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }
            return toolResult(
                message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentId,
                resultType = ToolResultType.FAILURE,
                result = null
            ).toResult()
        }

        logger.trace { "Completed execution of ${content.toolName} with result: $toolResult" }
        return toolResult(
            toolCallId = content.toolCallId,
            toolName = content.toolName,
            agentId = agentId,
            message = tool.encodeResultToStringUnsafe(toolResult),
            resultType = ToolResultType.SUCCESS,
            result = tool.encodeResult(toolResult)
        ).toResult()
    }

    override suspend fun reportProblem(runId: String, exception: Throwable) {
        logger.error(exception) { formatLog(runId, "Reporting problem: ${exception.message}") }
        throw exception
    }

    suspend fun executeTools(runId: String, toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        logger.info {
            formatLog(runId, "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]")
        }

        val toolCallEnvironmentMessages = toolCalls.map { call ->
            AgentToolCallsToEnvironmentMessage(
                runId = runId,
                content = AgentToolCallToEnvironmentContent(
                    toolCallId = call.id,
                    toolName = call.tool,
                    toolArgs = call.contentJson
                )
            )
        }

        val results = processToolCallMultiple(
            runId = runId,
            messages = toolCallEnvironmentMessages
        ).mapToToolResult()

        logger.debug {
            "Received results from tools call (" +
                "tools: [${toolCalls.joinToString(", ") { it.tool }}], " +
                "results: [${results.joinToString(", ") { it.resultString() }}])"
        }

        return results
    }

    //region Private Methods

    private fun ReceivedToolResult.resultString(): String =
        toolRegistry.tools.firstOrNull { it.name == tool }?.encodeResultToStringUnsafe(result) ?: "null"

    private fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        resultType: ToolResultType,
        result: JsonElement?
    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResultType = resultType,
        toolResult = result
    )

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(
        content: AgentToolCallToEnvironmentContent
    ): EnvironmentToolResultToAgentContent {
        logger.debug { "Handling tool call sent by server..." }
        val tool = toolRegistry.getToolOrNull(content.toolName)
            ?: run {
                logger.error { "Tool \"${content.toolName}\" not found." }
                return toolResult(
                    message = "Tool \"${content.toolName}\" not found. Use one of the available tools.",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = agentId,
                    resultType = ToolResultType.FAILURE,
                    result = null
                )
            }

        val toolArgs = try {
            tool.decodeArgs(content.toolArgs)
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
            return toolResult(
                message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentId,
                resultType = ToolResultType.FAILURE,
                result = null
            )
        }

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        } catch (e: ToolException) {
            return toolResult(
                message = e.message,
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentId,
                resultType = ToolResultType.VALIDATION_ERROR,
                result = null
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }
            return toolResult(
                message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentId,
                resultType = ToolResultType.FAILURE,
                result = null
            )
        }

        logger.trace { "Completed execution of ${content.toolName} with result: $toolResult" }
        return toolResult(
            toolCallId = content.toolCallId,
            toolName = content.toolName,
            agentId = agentId,
            message = tool.encodeResultToStringUnsafe(toolResult),
            resultType = ToolResultType.SUCCESS,
            result = tool.encodeResult(toolResult)
        )
    }


    private suspend fun processToolCallMultiple(
        runId: String,
        messages: List<AgentToolCallsToEnvironmentMessage>
    ): EnvironmentToolResultMultipleToAgentMessage {
        val results = supervisorScope {
            messages
                .map { message ->
                    async { processToolCall(message.content) }
                }
                .awaitAll()
        }

        return EnvironmentToolResultMultipleToAgentMessage(
            runId = runId,
            content = results
        )
    }

    private fun formatLog(runId: String, message: String): String =
        "[run id: $runId] $message"

    //endregion Private Methods
}
