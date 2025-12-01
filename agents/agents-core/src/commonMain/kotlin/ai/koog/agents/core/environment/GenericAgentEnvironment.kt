package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.withParent
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
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
    private val context: AIAgentContext,
) : AIAgentEnvironment {

    override suspend fun executeTools(runId: String, toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        logger.info {
            formatLog(runId, "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]")
        }

        val message = AgentToolCallsToEnvironmentMessage(
            runId = runId,
            content = toolCalls.map { call ->
                AgentToolCallToEnvironmentContent(
                    agentId = context.agentId,
                    runId = runId,
                    toolCallId = call.id,
                    toolName = call.tool,
                    toolArgs = call.contentJson
                )
            }
        )

        val results = processToolCallMultiple(message).mapToToolResult()
        logger.debug {
            "Received results from tools call (" +
                "tools: [${toolCalls.joinToString(", ") { it.tool }}], " +
                "results: [${results.joinToString(", ") { it.resultString() }}])"
        }

        return results
    }

    private fun ReceivedToolResult.resultString(): String =
        toolRegistry.tools.firstOrNull { it.name == tool }?.encodeResultToStringUnsafe(result) ?: "null"

    override suspend fun reportProblem(runId: String, exception: Throwable) {
        logger.error(exception) { formatLog(runId, "Reporting problem: ${exception.message}") }
        throw exception
    }

    private fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: JsonElement?
    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResult = result
    )

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(
        content: AgentToolCallToEnvironmentContent
    ): EnvironmentToolResultToAgentContent = withParent(context, "SD -- TODO") { parentId, id ->
        logger.debug { "Handling tool call sent by server..." }
        val tool = toolRegistry.getToolOrNull(content.toolName)
            ?: run {
                logger.error { "Tool \"${content.toolName}\" not found." }
                return@withParent toolResult(
                    message = "Tool \"${content.toolName}\" not found. Use one of the available tools.",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = context.agentId,
                    result = null
                )
            }

        val toolArgs = try {
            tool.decodeArgs(content.toolArgs)
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
            return@withParent toolResult(
                message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = context.agentId,
                result = null
            )
        }

        context.pipeline.onToolCallStarting(id, parentId, content.runId, content.toolCallId, tool, toolArgs)

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        } catch (e: ToolException) {
            context.pipeline.onToolValidationFailed(id, parentId, content.runId, content.toolCallId, tool, toolArgs, e.message)

            return@withParent toolResult(
                message = e.message,
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = context.agentId,
                result = null
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }

            context.pipeline.onToolCallFailed(id, parentId, content.runId, content.toolCallId, tool, toolArgs, e)

            return@withParent toolResult(
                message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = context.agentId,
                result = null
            )
        }

        context.pipeline.onToolCallCompleted(id, parentId, content.runId, content.toolCallId, tool, toolArgs, toolResult)

        logger.trace { "Completed execution of ${content.toolName} with result: $toolResult" }

        toolResult(
            toolCallId = content.toolCallId,
            toolName = content.toolName,
            agentId = context.agentId,
            message = tool.encodeResultToStringUnsafe(toolResult),
            result = tool.encodeResult(toolResult)
        )
    }

    private suspend fun processToolCallMultiple(
        message: AgentToolCallsToEnvironmentMessage
    ): EnvironmentToolResultMultipleToAgentMessage {
        val results = supervisorScope {
            message.content
                .map { call -> async { processToolCall(call) } }
                .awaitAll()
        }

        return EnvironmentToolResultMultipleToAgentMessage(
            runId = message.runId,
            content = results
        )
    }

    private fun formatLog(runId: String, message: String): String =
        "[agent id: ${context.agentId}, run id: $runId] $message"
}
