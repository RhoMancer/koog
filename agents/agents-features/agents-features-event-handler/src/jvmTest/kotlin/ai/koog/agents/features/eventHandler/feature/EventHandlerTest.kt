package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionEventContext
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.eventString
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.io.use
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class EventHandlerTest {

    @Test
    fun `test event handler for agent without nodes and tools`() = runTest {
        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"
        val agentInput = "Hello, world!!!"

        val strategy = strategy<String, String>(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            agent.run(agentInput)
        }

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: test-agent-id, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $agentResult)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $agentResult, output: $agentResult)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentCompleted (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentClosing (agent id: test-agent-id)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node without tools`() = runTest {
        val eventsCollector = TestEventsCollector()
        val agentId = "test-agent-id"

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"
        val testLLMResponse = "Test LLM call prompt"

        val strategyName = "tracing-test-strategy"
        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: $testLLMResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, tools: [])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, model: ${model.eventString}, tools: [], responses: [role: ${Message.Role.Assistant}, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: $testLLMResponse, output: " +
                "Assistant(parts=[Text(text=Default test response)], metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null), finishReason=null))",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $agentResult)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $agentResult, output: $agentResult)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentCompleted (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node with tools`() = runTest {
        val eventsCollector = TestEventsCollector()

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Call the dummy tool with argument: test"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val strategyName = "test-strategy"

        val mockResponse = "Return test result"

        val agentId = "test-agent-id"
        val model = OpenAIModels.Chat.GPT4o

        val strategy = strategy(strategyName) {
            val nodeSendInput by nodeLLMRequest("test-llm-call")
            val nodeExecuteTool by nodeExecuteTool("test-tool-call")
            val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        }

        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMToolCall(dummyTool, DummyTool.Args("test")) onRequestEquals userPrompt
            mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
        }

        createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            model = model,
        ) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }.use { agent ->
            agent.run(userPrompt)
        }

        val runId = eventsCollector.runId
        val dummyToolName = dummyTool.name
        val dummyToolDescription = dummyTool.descriptor.description
        val dummyToolArgsEncoded = dummyTool.encodeArgs(DummyTool.Args("test"))
        val dummyToolResultEncoded = dummyTool.encodeResult(dummyTool.result)

        val dummyToolReceivedToolResult = ReceivedToolResult(
            id = null,
            tool = dummyToolName,
            toolArgs = dummyToolArgsEncoded,
            toolDescription = dummyToolDescription,
            content = dummyTool.result,
            resultKind = ToolResultKind.Success,
            result = dummyToolResultEncoded
        )

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $userPrompt)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $userPrompt, output: $userPrompt)",
            "OnNodeExecutionStarting (run id: $runId, node: test-llm-call, input: $userPrompt)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt" +
                "}], temperature: $temperature, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt" +
                "}], temperature: $temperature, model: ${model.eventString}, tools: [$dummyToolName], responses: [role: ${Message.Role.Tool}, message: {\"dummy\":\"test\"}])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-llm-call, input: $userPrompt, output: " +
                "Call(id=null, tool=$dummyToolName, parts=[Text(text=$dummyToolArgsEncoded)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null)))",
            "OnNodeExecutionStarting (run id: $runId, node: test-tool-call, input: " +
                "Call(id=null, tool=$dummyToolName, parts=[Text(text=$dummyToolArgsEncoded)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null)))",
            "OnToolCallStarting (run id: $runId, tool: $dummyToolName, args: $dummyToolArgsEncoded)",
            "OnToolCallCompleted (run id: $runId, tool: $dummyToolName, args: $dummyToolArgsEncoded, result: $dummyToolResultEncoded)",
            "OnNodeExecutionCompleted (run id: $runId, node: test-tool-call, input: " +
                "Call(id=null, tool=$dummyToolName, parts=[Text(text=$dummyToolArgsEncoded)], " +
                "metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null)), output: $dummyToolReceivedToolResult)",
            "OnNodeExecutionStarting (run id: $runId, node: test-node-llm-send-tool-result, input: $dummyToolReceivedToolResult)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Tool}, message: $dummyToolArgsEncoded, " +
                "role: ${Message.Role.Tool}, message: ${dummyTool.result}" +
                "}], temperature: $temperature, tools: [$dummyToolName])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Tool}, message: $dummyToolArgsEncoded, " +
                "role: ${Message.Role.Tool}, message: ${dummyTool.result}" +
                "}], temperature: $temperature, model: openai:gpt-4o, tools: [$dummyToolName], responses: [role: ${Message.Role.Assistant}, message: Return test result])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-node-llm-send-tool-result, " +
                "input: $dummyToolReceivedToolResult, " +
                "output: Assistant(parts=[Text(text=$mockResponse)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null), finishReason=null))",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $mockResponse)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $mockResponse, output: $mockResponse)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $mockResponse)",
            "OnAgentCompleted (agent id: $agentId, run id: $runId, result: $mockResponse)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler several nodes`() = runTest {
        val eventsCollector = TestEventsCollector()

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"

        val strategyName = "tracing-test-strategy"
        val testLLMResponse = "Test LLM call prompt"
        val llmCallWithToolsResponse = "Test LLM call with tools prompt"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { llmCallWithToolsResponse })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { agentResult })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val agent = createAgent(
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            toolRegistry = toolRegistry,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: test-agent-id, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: $testLLMResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}], responses: [role: ${Message.Role.Assistant}, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: $testLLMResponse, output: " +
                "Assistant(parts=[Text(text=Default test response)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null), finishReason=null))",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call with tools, input: $llmCallWithToolsResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: Test LLM call prompt, " +
                "role: ${Message.Role.Assistant}, message: Default test response, " +
                "role: ${Message.Role.User}, message: $llmCallWithToolsResponse" +
                "}], temperature: $temperature, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: Test LLM call prompt, " +
                "role: ${Message.Role.Assistant}, message: Default test response, " +
                "role: ${Message.Role.User}, message: $llmCallWithToolsResponse" +
                "}], temperature: $temperature, model: openai:gpt-4o, tools: [${toolRegistry.tools.joinToString { it.name }}], responses: [role: ${Message.Role.Assistant}, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call with tools, input: $llmCallWithToolsResponse, output: " +
                "Assistant(parts=[Text(text=Default test response)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null), finishReason=null))",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $agentResult)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $agentResult, output: $agentResult)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentCompleted (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentClosing (agent id: test-agent-id)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler for agent with node execution error`() = runTest {
        val eventsCollector = TestEventsCollector()

        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val agentInput = "Hello, world!!!"
        val agentResult = "Done"

        val errorNodeName = "Node with error"
        val testErrorMessage = "Test error"

        val strategy = strategy<String, String>(strategyName) {
            val nodeWithError by node<String, String>(errorNodeName) {
                throw IllegalStateException(testErrorMessage)
            }

            edge(nodeStart forwardTo nodeWithError)
            edge(nodeWithError forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(
            agentId = agentId,
            strategy = strategy,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            val throwable = assertThrows<IllegalStateException> { agent.run(agentInput) }
            assertEquals(testErrorMessage, throwable.message)
        }

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: $errorNodeName, input: $agentInput)",
            "OnNodeExecutionFailed (run id: $runId, node: $errorNodeName, input: $agentInput, error: $testErrorMessage)",
            "OnAgentExecutionFailed (agent id: $agentId, run id: $runId, error: $testErrorMessage)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler with multiple handlers`() = runTest {
        val collectedEvents = mutableListOf<String>()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy<String, String>(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentResult })
        }

        var runId = ""

        val agent = createAgent(
            agentId = "test-agent-id",
            strategy = strategy,
            installFeatures = {
                install(EventHandler) {
                    onAgentStarting { eventContext ->
                        runId = eventContext.runId
                        collectedEvents.add(
                            "OnAgentStarting first (agent id: ${eventContext.agent.id})"
                        )
                    }

                    onAgentStarting { eventContext ->
                        collectedEvents.add(
                            "OnAgentStarting second (agent id: ${eventContext.agent.id})"
                        )
                    }

                    onAgentCompleted { eventContext ->
                        collectedEvents.add(
                            "OnAgentCompleted (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, result: $agentResult)"
                        )
                    }
                }
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnAgentStarting first (agent id: ${agent.id})",
            "OnAgentStarting second (agent id: ${agent.id})",
            "OnAgentCompleted (agent id: ${agent.id}, run id: $runId, result: $agentResult)",
        )

        assertEquals(expectedEvents.size, collectedEvents.size)
        assertContentEquals(expectedEvents, collectedEvents)
    }

    @Disabled
    @Test
    fun testEventHandlerWithErrors() = runTest {
        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeException("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(DummyTool()) },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        agent.run("Hello, world!!!")
        agent.close()
    }

    @Test
    fun `test llm streaming events success`() = runTest {
        val eventsCollector = TestEventsCollector()

        val model = OpenAIModels.Chat.GPT4o
        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0

        val strategyName = "event-handler-streaming-success"
        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreamingAndSendResults<String>("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollect)
            edge(streamAndCollect forwardTo nodeFinish transformed { messages -> messages.firstOrNull()?.content ?: "" })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val testLLMResponse = "Default test response"
        val executor = getMockExecutor {
            mockLLMAnswer(testLLMResponse).asDefaultResponse onUserRequestEquals "Test user message"
        }

        createAgent(
            agentId = "test-agent-id",
            strategy = strategy,
            executor = executor,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = toolRegistry,
        ) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }.use { agent ->
            agent.run("")
        }

        val runId = eventsCollector.runId

        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt" +
            "}]"

        val expectedEvents = listOf(
            "OnLLMStreamingStarting (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMStreamingFrameReceived (run id: $runId, frame: Append(text=$testLLMResponse))",
            "OnLLMStreamingCompleted (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test llm streaming events failure`() = runTest {
        val eventsCollector = TestEventsCollector()

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0

        val model = OpenAIModels.Chat.GPT4o

        val strategyName = "event-handler-streaming-failure"
        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreamingAndSendResults<String>("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollect)
            edge(streamAndCollect forwardTo nodeFinish transformed { messages -> messages.firstOrNull()?.content ?: "" })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val testStreamingErrorMessage = "Test streaming error"

        val testStreamingExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> = emptyList()

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                throw IllegalStateException(testStreamingErrorMessage)
            }

            override suspend fun moderate(
                prompt: Prompt,
                model: LLModel
            ): ModerationResult {
                throw UnsupportedOperationException("Not used in test")
            }

            override fun close() {}
        }

        createAgent(
            strategy = strategy,
            executor = testStreamingExecutor,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = toolRegistry,
        ) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }.use { agent ->
            val throwable = assertThrows<IllegalStateException> { agent.run("") }
            assertEquals(testStreamingErrorMessage, throwable.message)
        }

        val runId = eventsCollector.runId

        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt" +
            "}]"

        val expectedEvents = listOf(
            "OnLLMStreamingStarting (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name}}])",
            "OnLLMStreamingFailed (run id: $runId, error: $testStreamingErrorMessage)",
            "OnLLMStreamingCompleted (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name}}])",
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test subgraph execution events success`() = runTest {
        val eventsCollector = TestEventsCollector()

        val strategyName = "test-strategy"
        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphOutput = "test-subgraph-output"
        val inputRequest = "Test input"

        val strategy = strategy<String, String>(strategyName) {
            val subgraph by subgraph<String, String>(subgraphName) {
                val subgraphNode by node<String, String>(subgraphNodeName) { subgraphOutput }
                nodeStart then subgraphNode then nodeFinish
            }
            nodeStart then subgraph then nodeFinish
        }

        createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler) eventHandlerConfig@{
                    setEventFilter { context ->
                        context is SubgraphExecutionEventContext
                    }
                    eventsCollector.eventHandlerFeatureConfig.invoke(this@eventHandlerConfig)
                }
            }
        ).use { agent ->
            agent.run(inputRequest)
        }

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnSubgraphExecutionStarting (run id: $runId, subgraph: $subgraphName, input: $inputRequest)",
            "OnSubgraphExecutionCompleted (run id: $runId, subgraph: $subgraphName, input: $inputRequest, output: $subgraphOutput)",
        )

        assertEquals(expectedEvents.size, eventsCollector.collectedEvents.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test subgraph execution events failure`() = runTest {
        val eventsCollector = TestEventsCollector()

        val strategyName = "test-strategy"
        val subgraphName = "test-subgraph"
        val subgraphErrorNodeName = "test-subgraph-error-node"
        val subgraphNodeErrorMessage = "Test subgraph error"
        val inputRequest = "Test input"

        val strategy = strategy<String, String>(strategyName) {
            val subgraph by subgraph<String, String>(subgraphName) {
                val nodeWithError by node<String, String>(subgraphErrorNodeName) {
                    throw IllegalStateException(subgraphNodeErrorMessage)
                }
                nodeStart then nodeWithError then nodeFinish
            }
            nodeStart then subgraph then nodeFinish
        }

        val agentThrowable = createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler) eventHandlerConfig@{
                    setEventFilter { context ->
                        context is SubgraphExecutionEventContext
                    }
                    eventsCollector.eventHandlerFeatureConfig.invoke(this@eventHandlerConfig)
                }
            }
        ).use { agent ->
            assertFails { agent.run(inputRequest) }
        }

        assertEquals(subgraphNodeErrorMessage, agentThrowable.message)

        // Check captured events
        val runId = eventsCollector.runId
        val expectedEvents = listOf(
            "OnSubgraphExecutionStarting (run id: $runId, subgraph: $subgraphName, input: $inputRequest)",
            "OnSubgraphExecutionFailed (run id: $runId, subgraph: $subgraphName, input: $inputRequest, error: $subgraphNodeErrorMessage)",
        )

        assertEquals(expectedEvents.size, eventsCollector.collectedEvents.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test onLLMPromptTransforming modifies prompt`() = runTest {
        val eventsCollector = TestEventsCollector()
        val agentId = "test-agent-id"

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"
        val testLLMResponse = "Test LLM call prompt"
        val additionalContext = "Additional context from database"

        val strategyName = "tracing-test-strategy"
        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        // Track the prompts seen by the transformer and the LLM call handler
        var transformedPromptMessages: List<Message>? = null
        var llmCallStartingPromptMessages: List<Message>? = null

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler) {
                    // First transformer: add context from "database"
                    onLLMPromptTransforming { prompt ->
                        val augmentedMessages = listOf<Message>(
                            Message.System("Context: $additionalContext", RequestMetaInfo.Empty)
                        ) + prompt.messages
                        transformedPromptMessages = augmentedMessages
                        prompt.copy(messages = augmentedMessages)
                    }

                    // Track what prompt the LLM call handler sees
                    onLLMCallStarting { eventContext ->
                        llmCallStartingPromptMessages = eventContext.prompt.messages
                    }

                    eventsCollector.eventHandlerFeatureConfig.invoke(this)
                }
            }
        )

        agent.use { it.run("Hello") }

        // Verify the transformer was called and modified the prompt
        // The prompt has: 1 added context + system + user + assistant + user (from testLLMResponse) = 5 messages
        assertEquals(5, transformedPromptMessages?.size)
        assertEquals("Context: $additionalContext", (transformedPromptMessages?.first() as? Message.System)?.content)

        // Verify the LLM call handler received the transformed prompt
        assertEquals(5, llmCallStartingPromptMessages?.size)
        assertEquals("Context: $additionalContext", (llmCallStartingPromptMessages?.first() as? Message.System)?.content)
    }

    @Test
    fun `test onLLMPromptTransforming chains multiple transformers`() = runTest {
        val agentId = "test-agent-id"

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"
        val testLLMResponse = "Test LLM call prompt"

        val strategyName = "tracing-test-strategy"
        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        // Track the order of transformations
        val transformationOrder = mutableListOf<String>()
        var finalPromptMessages: List<Message>? = null

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler) {
                    // First transformer
                    onLLMPromptTransforming { prompt ->
                        transformationOrder.add("first")
                        val augmentedMessages = listOf<Message>(
                            Message.System("First transformer context", RequestMetaInfo.Empty)
                        ) + prompt.messages
                        prompt.copy(messages = augmentedMessages)
                    }

                    // Second transformer (should run after first)
                    onLLMPromptTransforming { prompt ->
                        transformationOrder.add("second")
                        val augmentedMessages = listOf<Message>(
                            Message.System("Second transformer context", RequestMetaInfo.Empty)
                        ) + prompt.messages
                        prompt.copy(messages = augmentedMessages)
                    }

                    // Track final prompt
                    onLLMCallStarting { eventContext ->
                        finalPromptMessages = eventContext.prompt.messages
                    }
                }
            }
        )

        agent.use { it.run("Hello") }

        // Verify transformers were called in order
        assertEquals(listOf("first", "second"), transformationOrder)

        // Verify the final prompt has both transformations applied
        // Order should be: Second (added last, so first in list), First, then original messages
        // The prompt has: 2 added context + system + user + assistant + user (from testLLMResponse) = 6 messages
        assertEquals(6, finalPromptMessages?.size)
        assertEquals("Second transformer context", (finalPromptMessages?.get(0) as? Message.System)?.content)
        assertEquals("First transformer context", (finalPromptMessages?.get(1) as? Message.System)?.content)
    }

    @Test
    fun `test onLLMCallCompleted receives transformed prompt`() = runTest {
        val agentId = "test-agent-id"

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"
        val testLLMResponse = "Test LLM call prompt"
        val additionalContext = "Additional context from database"

        val strategyName = "tracing-test-strategy"
        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        // Track the prompts seen by the transformer and the LLM call completed handler
        var originalPromptMessageCount: Int? = null
        var llmCallCompletedPromptMessages: List<Message>? = null

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler) {
                    // Transformer: add context from "database" and track original prompt size
                    onLLMPromptTransforming { prompt ->
                        originalPromptMessageCount = prompt.messages.size
                        val augmentedMessages = listOf<Message>(
                            Message.System("Context: $additionalContext", RequestMetaInfo.Empty)
                        ) + prompt.messages
                        prompt.copy(messages = augmentedMessages)
                    }

                    // Track what prompt the LLM call completed handler sees
                    onLLMCallCompleted { eventContext ->
                        llmCallCompletedPromptMessages = eventContext.prompt.messages
                    }
                }
            }
        )

        agent.use { it.run(userPrompt) }

        // Verify the original prompt had 4 messages (system + user + assistant + user from testLLMResponse)
        assertEquals(4, originalPromptMessageCount)

        // Verify the LLM call completed handler received the TRANSFORMED prompt (not the original)
        // The transformed prompt has: 1 added context + original 4 = 5 messages
        assertEquals(5, llmCallCompletedPromptMessages?.size)
        assertEquals("Context: $additionalContext", (llmCallCompletedPromptMessages?.first() as? Message.System)?.content)

        // Verify the transformed prompt contains the additional context as the first message
        // This proves that onLLMCallCompleted receives the transformed prompt, not the original
        val firstMessage = llmCallCompletedPromptMessages?.first()
        assertTrue(firstMessage is Message.System)
        assertTrue(firstMessage.content.startsWith("Context:"))
    }

    @Test
    fun `test handler chain - onLLMCallStarting saves user message, onLLMPromptTransforming augments prompt, onLLMCallCompleted saves assistant response`() = runTest {
        val agentId = "test-agent-id"

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"
        val testLLMResponse = "What is the weather today?"

        val strategyName = "database-handler-chain-test"
        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        // Chat memory to store user messages and assistant responses
        val chatMemory = mutableListOf<Message>()
        // RAG/search database with pre-populated context
        val db = mutableMapOf<String, String>()
        
        // Pre-populate database with search results for the expected user message
        db[testLLMResponse] = "Weather forecast: Sunny, 25°C"

        // Track execution order and data flow
        val executionOrder = mutableListOf<String>()
        var lastUserMessageAsSeenByTransformer: String? = null
        var relevantContextAsSeenByTransformer: String? = null

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler) {
                    // STEP 1: Transform runs FIRST - search DB and augment prompt
                    onLLMPromptTransforming { prompt ->
                        executionOrder.add("onLLMPromptTransforming")

                        // Get the last user message from the CURRENT prompt
                        val lastUserMessage: Message.User = prompt.messages
                            .filterIsInstance<Message.User>()
                            .last()

                        lastUserMessageAsSeenByTransformer = lastUserMessage.content

                        // Search database for relevant context
                        val relevantContext = db[lastUserMessage.content]

                        if (relevantContext.isNullOrEmpty()) {
                            throw IllegalStateException("relevant context is required for tests")
                        }

                        relevantContextAsSeenByTransformer = relevantContext

                        // Find the index of the last user message
                        val lastUserIndex = prompt.messages.indexOfLast { it is Message.User }

                        // Add context as a user message BEFORE the original last user message
                        val augmentedMessages = prompt.messages.toMutableList()
                        augmentedMessages.add(
                            lastUserIndex,
                            Message.User("Relevant context: $relevantContext", RequestMetaInfo.Empty)
                        )
                        prompt.copy(messages = augmentedMessages)
                    }
                    
                    // STEP 2: Starting runs SECOND - save user message to chat memory
                    onLLMCallStarting { ctx ->
                        executionOrder.add("onLLMCallStarting")
                        
                        // Get the last user message (the original one, not the prepended context)
                        val lastUserMessage = ctx.prompt.messages
                            .filterIsInstance<Message.User>()
                            .last()
                        
                        lastUserMessage.let { userMsg ->
                            chatMemory.add(userMsg)
                        }
                    }
                    
                    // STEP 3: Completed runs THIRD - save assistant response to chat memory
                    onLLMCallCompleted { ctx ->
                        executionOrder.add("onLLMCallCompleted")
                        
                        // Get assistant response from RESPONSES, not from prompt
                        val assistantResponse = ctx.responses
                            .filterIsInstance<Message.Assistant>()
                            .last()
                        
                        assistantResponse.let { response ->
                            chatMemory.add(response)
                        }
                    }
                }
            }
        )

        agent.use { it.run(userPrompt) }

        // Verify execution order: onLLMPromptTransforming runs FIRST, then onLLMCallStarting, then onLLMCallCompleted
        assertEquals(listOf("onLLMPromptTransforming", "onLLMCallStarting", "onLLMCallCompleted"), executionOrder)

        // Verify STEP 1: onLLMPromptTransforming found the user message and searched the database
        assertEquals(testLLMResponse, lastUserMessageAsSeenByTransformer)
        assertEquals("Weather forecast: Sunny, 25°C", relevantContextAsSeenByTransformer)

        // Verify STEP 2: onLLMCallStarting saved the ORIGINAL last user message (not the prepended context)
        val savedUserMessages = chatMemory.filterIsInstance<Message.User>()
        assertEquals(1, savedUserMessages.size)
        assertEquals(testLLMResponse, savedUserMessages.first().content)

        // Verify STEP 3: onLLMCallCompleted saved the assistant response
        val savedAssistantResponses = chatMemory.filterIsInstance<Message.Assistant>()
        assertEquals(1, savedAssistantResponses.size)
        assertEquals("Default test response", savedAssistantResponses.first().content)
        
        // Verify chat memory contains both user message and assistant response in order
        assertEquals(2, chatMemory.size)
        assertTrue(chatMemory[0] is Message.User)
        assertTrue(chatMemory[1] is Message.Assistant)
    }


    @Test
    fun `test onLLMCallCompleted persists prompt transformed by onLLMPromptTransforming`() = runTest {
        val agentId = "test-agent-id"

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"
        val testLLMResponse = "What is the weather today?"
        val additionalContext = "RAG context: Weather forecast is sunny"

        val strategyName = "persist-transformed-prompt-test"

        // Use a custom node that doesn't use writeSession internally
        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        // Track the prompts at different stages
        var originalPromptMessageCount: Int? = null
        var transformedPromptMessageCount: Int? = null
        var llmCallStartingPromptMessageCount: Int? = null
        var llmCallStartingPromptFirstMessage: Message? = null
        var llmCallCompletedPromptMessageCount: Int? = null
        var llmCallCompletedPromptFirstMessage: Message? = null
        var persistedPromptMessageCount: Int? = null
        var persistedPromptFirstMessage: Message? = null

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler) {
                    // STEP 1: Transform the prompt (add RAG context)
                    onLLMPromptTransforming { prompt ->
                        originalPromptMessageCount = prompt.messages.size

                        // Add RAG context as the first message
                        val augmentedMessages = listOf<Message>(
                            Message.System(additionalContext, RequestMetaInfo.Empty)
                        ) + prompt.messages

                        transformedPromptMessageCount = augmentedMessages.size
                        prompt.copy(messages = augmentedMessages)
                    }

                    // STEP 2: Verify onLLMCallStarting receives the transformed prompt
                    onLLMCallStarting { ctx ->
                        llmCallStartingPromptMessageCount = ctx.prompt.messages.size
                        llmCallStartingPromptFirstMessage = ctx.prompt.messages.firstOrNull()
                    }

                    // STEP 3: Verify onLLMCallCompleted receives the transformed prompt and persist it
                    onLLMCallCompleted { ctx ->
                        llmCallCompletedPromptMessageCount = ctx.prompt.messages.size
                        llmCallCompletedPromptFirstMessage = ctx.prompt.messages.firstOrNull()

                        // Persist the transformed prompt to the context
                        ctx.context.llm.prompt = ctx.prompt

                        // Verify the context's prompt was updated
                        persistedPromptMessageCount = ctx.context.llm.prompt.messages.size
                        persistedPromptFirstMessage = ctx.context.llm.prompt.messages.firstOrNull()
                    }
                }
            }
        )

        agent.use { it.run(userPrompt) }

        // Verify the original prompt had 4 messages (system + user + assistant from initial prompt + user from testLLMResponse)
        assertEquals(4, originalPromptMessageCount)

        // Verify the transformed prompt has 5 messages (added RAG context + original 4)
        assertEquals(5, transformedPromptMessageCount)

        // Verify onLLMCallStarting received the transformed prompt (5 messages)
        assertEquals(5, llmCallStartingPromptMessageCount)
        assertTrue(llmCallStartingPromptFirstMessage is Message.System)
        assertEquals(additionalContext, (llmCallStartingPromptFirstMessage as Message.System).content)

        // Verify onLLMCallCompleted received the transformed prompt (5 messages)
        assertEquals(5, llmCallCompletedPromptMessageCount)
        assertTrue(llmCallCompletedPromptFirstMessage is Message.System)
        assertEquals(additionalContext, (llmCallCompletedPromptFirstMessage as Message.System).content)

        // Verify the persisted prompt in context has the transformed prompt (5 messages)
        assertEquals(5, persistedPromptMessageCount)

        // Verify the first message in the persisted prompt is the RAG context
        assertTrue(persistedPromptFirstMessage is Message.System)
        assertEquals(additionalContext, (persistedPromptFirstMessage as Message.System).content)
    }

    //region Private Methods

    private fun AIAgentSubgraphBuilderBase<*, *>.nodeException(name: String? = null): AIAgentNodeDelegate<String, Message.Response> =
        node(name) { throw IllegalStateException("Test exception") }

    //endregion Private Methods
}
