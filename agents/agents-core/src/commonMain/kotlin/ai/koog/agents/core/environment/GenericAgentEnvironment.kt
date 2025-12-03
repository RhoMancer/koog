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
import kotlinx.serialization.json.JsonObject

internal class GenericAgentEnvironment(
    private val agentId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
) : AIAgentEnvironment {

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {

        val message = AgentToolCallsToEnvironmentMessage(
            content = AgentToolCallToEnvironmentContent(
                toolCallId = toolCall.id,
                toolName = toolCall.tool,
                toolArgs = toolCall.contentJson
            )
        )

        return processToolCall(toolCallContent = message.content).toResult()
    }

    override suspend fun reportProblem(exception: Throwable) {
        logger.error(exception) { formatLog(agentId, "Reporting problem: ${exception.message}") }
        throw exception
    }

    suspend fun executeTools(runId: String, toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        logger.info {
            formatLog(runId, "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]")
        }

        val toolCallEnvironmentMessages = toolCalls.map { call ->
            AgentToolCallsToEnvironmentMessage(
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
        toolArgs: JsonObject,
        agentId: String,
        message: String,
        toolResultKind: ToolResultKind,
        result: JsonElement?
    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        toolArgs = toolArgs,
        agentId = agentId,
        message = message,
        toolResultKind = toolResultKind,
        toolResult = result
    )

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(
        toolCallContent: AgentToolCallToEnvironmentContent
    ): EnvironmentToolResultToAgentContent {
        logger.debug { "Handling tool call sent by server..." }
        val tool = toolRegistry.getToolOrNull(toolCallContent.toolName)
            ?: run {
                logger.error { "Tool \"${toolCallContent.toolName}\" not found." }
                return toolResult(
                    message = "Tool \"${toolCallContent.toolName}\" not found. Use one of the available tools.",
                    toolCallId = toolCallContent.toolCallId,
                    toolName = toolCallContent.toolName,
                    toolArgs = toolCallContent.toolArgs,
                    agentId = agentId,
                    toolResultKind = ToolResultKind.Failure(null),
                    result = null
                )
            }

        val toolArgs = try {
            tool.decodeArgs(toolCallContent.toolArgs)
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${toolCallContent.toolArgs}" }
            return toolResult(
                message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                toolCallId = toolCallContent.toolCallId,
                toolName = toolCallContent.toolName,
                toolArgs = toolCallContent.toolArgs,
                agentId = agentId,
                toolResultKind = ToolResultKind.Failure(e),
                result = null
            )
        }

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        } catch (e: ToolException) {
            return toolResult(
                message = e.message,
                toolCallId = toolCallContent.toolCallId,
                toolName = toolCallContent.toolName,
                toolArgs = toolCallContent.toolArgs,
                agentId = agentId,
                toolResultKind = ToolResultKind.ValidationError(e),
                result = null
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${toolCallContent.toolArgs}" }
            return toolResult(
                message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                toolCallId = toolCallContent.toolCallId,
                toolName = toolCallContent.toolName,
                toolArgs = toolCallContent.toolArgs,
                agentId = agentId,
                toolResultKind = ToolResultKind.Failure(e),
                result = null
            )
        }

        logger.trace { "Completed execution of ${toolCallContent.toolName} with result: $toolResult" }
        return toolResult(
            toolCallId = toolCallContent.toolCallId,
            toolName = toolCallContent.toolName,
            toolArgs = toolCallContent.toolArgs,
            agentId = agentId,
            message = tool.encodeResultToStringUnsafe(toolResult),
            toolResultKind = ToolResultKind.Success,
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

    private fun formatLog(agentId: String, message: String): String =
        "[agent id: $agentId] $message"

    //endregion Private Methods
}
