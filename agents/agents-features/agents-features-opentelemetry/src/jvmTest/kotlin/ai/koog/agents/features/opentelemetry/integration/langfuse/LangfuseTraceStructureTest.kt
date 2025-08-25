package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistry.Companion.invoke
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.assertMapsEqual
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A test class for verifying trace structures using the Langfuse exporter.
 */
// Explicitly ignore this test as we do not have env variables for Langfuse in CI to make these tests passed.
// Required env variables:
//   - LANGFUSE_SECRET_KEY
//   - LANGFUSE_PUBLIC_KEY
//   - LANGFUSE_HOST
//@Ignore
class LangfuseTraceStructureTest :
    TraceStructureTestBase(openTelemetryConfigurator = { addLangfuseExporter() }) {

    @Test
    fun testLLMCallToolCallLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("llm-tool-llm-strategy") {
                val llmRequest by nodeLLMRequest("LLM Request", allowToolCalls = true)
                val executeTool by nodeExecuteTool("Execute Tool")
                val sendToolResult by nodeLLMSendToolResult("Send Tool Result")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool onToolCall { true })
                edge(executeTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val toolCallArgs = TestGetWeatherTool.Args("Paris")
            val toolResponse = TestGetWeatherTool.DEFAULT_PARIS_RESULT
            val finalResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val toolCallId = "get-weather-tool-call-id"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(tool = TestGetWeatherTool, args = toolCallArgs, toolCallId = toolCallId) onRequestEquals userPrompt
                mockLLMAnswer(response = finalResponse) onRequestContains toolResponse
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter
            )

            // Assert collected spans
            val actualSpans = mockSpanExporter.collectedSpans

            val toolSpans = actualSpans.filter { it.name == "tool.Get whether" }
            assertEquals(1, toolSpans.size)

            val runNode = actualSpans.firstOrNull { it.name.startsWith("run.") }
            assertNotNull(runNode)

            val startNode = actualSpans.firstOrNull { it.name == "node.__start__" }
            assertNotNull(startNode)

            val llmRequestNode = actualSpans.firstOrNull { it.name == "node.LLM Request" }
            assertNotNull(llmRequestNode)

            val executeToolNode = actualSpans.firstOrNull { it.name == "node.Execute Tool" }
            assertNotNull(executeToolNode)

            val sendToolResultNode = actualSpans.firstOrNull { it.name == "node.Send Tool Result" }
            assertNotNull(sendToolResultNode)

            val toolCallSpan = actualSpans.firstOrNull { it.name == "tool.Get whether" }
            assertNotNull(toolCallSpan)

            // All nodes should have runNode as parent
            assertEquals(startNode.parentSpanId, runNode.spanId)
            assertEquals(llmRequestNode.parentSpanId, runNode.spanId)
            assertEquals(executeToolNode.parentSpanId, runNode.spanId)
            assertEquals(sendToolResultNode.parentSpanId, runNode.spanId)

            // Check LLM Call span with the initial call and tool call request
            val llmSpans = actualSpans.filter { it.name == "llm.test-prompt-id" }
            val actualInitialLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == llmRequestNode.spanId }
            assertNotNull(actualInitialLLMCallSpan)

            val actualInitialLLMCallSpanAttributes =
                actualInitialLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

//            "gen_ai.prompt.0.role" -> "system"
//            "gen_ai.prompt.0.content" -> "You are the application that predicts weather"
//            "gen_ai.prompt.1.role" -> "user"
//            "gen_ai.prompt.1.content" -> "What's the weather in Paris?"
//            "gen_ai.completion.0.role" -> "assistant"
//            "gen_ai.completion.0.tool_calls.0.id" -> "get-weather-tool-call-id"
//            "gen_ai.completion.0.tool_calls.0.function" -> "{"name":"Get whether","arguments":"{\"location\":\"Paris\"}"}"
//            "gen_ai.completion.0.tool_calls.0.type" -> "function"

            val expectedInitialLLMCallSpansAttributes = mapOf(
                "gen_ai.system" to model.provider.id,
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.temperature" to temperature,
                "gen_ai.request.model" to model.id,
                "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.ToolCalls.id),

                "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
                "gen_ai.completion.0.content" to "[{\"function\":{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"},\"id\":\"get-weather-tool-call-id\",\"type\":\"function\"}]",
                "gen_ai.completion.0.finish_reason" to SpanAttributes.Response.FinishReasonType.ToolCalls.id,
            )

            assertEquals(expectedInitialLLMCallSpansAttributes.size, actualInitialLLMCallSpanAttributes.size)
            assertMapsEqual(expectedInitialLLMCallSpansAttributes, actualInitialLLMCallSpanAttributes)

            // Check LLM Call span with the final LLM response after the tool is executed
            val actualFinalLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == sendToolResultNode.spanId }
            assertNotNull(actualFinalLLMCallSpan)

            val actualFinalLLMCallSpanAttributes =
                actualFinalLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedFinalLLMCallSpansAttributes = mapOf(
                "gen_ai.system" to model.provider.id,
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.temperature" to temperature,
                "gen_ai.request.model" to model.id,
                "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.Stop.id),

                "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.prompt.2.role" to Message.Role.Tool.name.lowercase(),
                "gen_ai.prompt.2.content" to "[{\"function\":{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"},\"id\":\"get-weather-tool-call-id\",\"type\":\"function\"}]",
                "gen_ai.prompt.3.role" to Message.Role.Tool.name.lowercase(),
                "gen_ai.prompt.3.content" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,

                "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
                "gen_ai.completion.0.content" to finalResponse,
            )

            assertEquals(expectedFinalLLMCallSpansAttributes.size, actualFinalLLMCallSpanAttributes.size)
            assertMapsEqual(expectedFinalLLMCallSpansAttributes, actualFinalLLMCallSpanAttributes)

            // Tool span should have executed tool node as parent
            assertTrue { executeToolNode.spanId == toolCallSpan.parentSpanId }
            val actualToolCallSpanAttributes =
                toolCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedToolCallSpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "gen_ai.tool.call.id" to toolCallId,
                "input.value" to "{\"location\":\"Paris\"}",
                "output.value" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,
            )

            assertEquals(expectedToolCallSpanAttributes.size, actualToolCallSpanAttributes.size)
            assertMapsEqual(expectedToolCallSpanAttributes, actualToolCallSpanAttributes)
        }
    }
}
