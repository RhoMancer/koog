package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyEventGraph
import ai.koog.agents.core.feature.model.events.StrategyEventGraphEdge
import ai.koog.agents.core.feature.model.events.StrategyEventGraphNode
import ai.koog.agents.core.feature.model.events.StrategyStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.feature.writer.FeatureMessageRemoteWriter
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.mock.MockLLMProvider
import ai.koog.agents.features.tracing.mock.TestFeatureMessageWriter
import ai.koog.agents.features.tracing.mock.assistantMessage
import ai.koog.agents.features.tracing.mock.createAgent
import ai.koog.agents.features.tracing.mock.receivedToolResult
import ai.koog.agents.features.tracing.mock.systemMessage
import ai.koog.agents.features.tracing.mock.testClock
import ai.koog.agents.features.tracing.mock.toolCallMessage
import ai.koog.agents.features.tracing.mock.userMessage
import ai.koog.agents.testing.feature.message.findEvents
import ai.koog.agents.testing.feature.message.singleEvent
import ai.koog.agents.testing.feature.message.singleNodeEvent
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.Message
import ai.koog.utils.io.use
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.SAME_THREAD)
class TraceFeatureMessageRemoteWriterTest {

    companion object {
        private val logger = KotlinLogging.logger { }
        private val defaultClientServerTimeout = 30.seconds
        private const val HOST = "127.0.0.1"
    }

    private fun CoroutineScope.signalServerStarted(
        writer: FeatureMessageRemoteWriter,
        signal: CompletableDeferred<Boolean>,
    ): Job = launch {
        val isStarted = withTimeoutOrNull(defaultClientServerTimeout) {
            writer.isOpen.first { it }
        } ?: throw AssertionError("Server did not start in time")

        if (!signal.isCompleted) {
            signal.complete(isStarted)
        }
    }

    @Test
    fun `test health check on agent run`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isServerStarted = CompletableDeferred<Boolean>()
        val isClientFinished = CompletableDeferred<Boolean>()

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                isServerStarted.await()
                client.connect()
                client.healthCheck()

                isClientFinished.complete(true)
            }
        }

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val serverReadyJob = signalServerStarted(writer, isServerStarted)

                val strategy = strategy<String, String>("tracing-test-strategy") {
                    val llmCallNode by nodeLLMRequest("test LLM call")
                    val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                    edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                    edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                    edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                }

                try {
                    createAgent(strategy = strategy) {
                        install(Tracing) {
                            addMessageProcessor(writer)
                        }
                    }.use { agent ->
                        agent.run("")
                        isClientFinished.await()
                    }
                } finally {
                    serverReadyJob.join()
                }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test feature message remote writer collect events on agent run`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val nodeSendLLMCallName = "test-llm-call"
        val nodeExecuteToolName = "test-tool-call"
        val nodeSendToolResultName = "test-node-llm-send-tool-result"

        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

        val mockResponse = "Return test result"

        // Tools
        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Model
        val testModel = LLModel(
            provider = MockLLMProvider(),
            id = "test-llm-id",
            capabilities = emptyList(),
            contextLength = 1_000,
        )

        // Prompt
        val expectedPrompt = Prompt(
            messages = listOf(
                systemMessage(systemPrompt),
                userMessage(userPrompt),
                assistantMessage(assistantPrompt)
            ),
            id = promptId
        )

        val expectedLLMCallPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + userMessage(content = userPrompt)
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = userPrompt),
                toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                receivedToolResult("0", dummyTool.name, dummyTool.result, dummyTool.encodeResult(dummyTool.result)).toMessage(clock = testClock)
            )
        )

        // Test Data
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isServerStarted = CompletableDeferred<Boolean>()

        val expectedClientEvents = mutableListOf<FeatureMessage>()
        val actualClientEvents = mutableListOf<FeatureMessage>()

        // Server
        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val serverReadyJob = signalServerStarted(writer, isServerStarted)

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

                val mockExecutor = getMockExecutor(clock = testClock) {
                    mockLLMToolCall(tool = dummyTool, args = DummyTool.Args("test"), toolCallId = "0") onRequestEquals
                        userPrompt
                    mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
                }

                try {
                    createAgent(
                        agentId = agentId,
                        strategy = strategy,
                        promptId = promptId,
                        model = testModel,
                        userPrompt = userPrompt,
                        systemPrompt = systemPrompt,
                        assistantPrompt = assistantPrompt,
                        toolRegistry = toolRegistry,
                        promptExecutor = mockExecutor
                    ) {
                        install(Tracing) {
                            addMessageProcessor(writer)
                        }
                    }.use { agent ->
                        agent.run(userPrompt)
                    }
                } finally {
                    serverReadyJob.join()
                }
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                var runId = ""

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        if (event is AgentStartingEvent) {
                            runId = event.runId
                        }

                        actualClientEvents.add(event as DefinedFeatureEvent)
                        logger.info { "[${actualClientEvents.size}] Received event: $event" }

                        if (event is AgentClosingEvent) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()

                client.connect()
                collectEventsJob.join()

                val llmCallGraphNode = StrategyEventGraphNode(id = nodeSendLLMCallName, name = nodeSendLLMCallName)
                val executeToolGraphNode = StrategyEventGraphNode(id = nodeExecuteToolName, name = nodeExecuteToolName)
                val sendToolResultGraphNode =
                    StrategyEventGraphNode(id = nodeSendToolResultName, name = nodeSendToolResultName)

                val startGraphNode = StrategyEventGraphNode(id = "__start__", name = "__start__")
                val finishGraphNode = StrategyEventGraphNode(id = "__finish__", name = "__finish__")

                // Expected events
                val actualAgentClosingEvent = actualClientEvents.singleEvent<AgentClosingEvent>()
                val actualAgentStartingEvent = actualClientEvents.singleEvent<AgentStartingEvent>()
                val actualStrategyStartingEvent = actualClientEvents.singleEvent<StrategyStartingEvent>()

                val actualNodeStartEvent = actualClientEvents.singleNodeEvent("__start__")
                val actualNodeLLMCallEvent = actualClientEvents.singleNodeEvent("test-llm-call")
                val actualNodeToolCallEvent = actualClientEvents.singleNodeEvent("test-tool-call")
                val actualNodeSendToolResultEvent = actualClientEvents.singleNodeEvent("test-node-llm-send-tool-result")
                val actualNodeFinishEvent = actualClientEvents.singleNodeEvent("__finish__")

                val actualLLMCallStartingEvents = actualClientEvents.findEvents<LLMCallStartingEvent>()
                val actualLLMCallEvent = actualLLMCallStartingEvents[0]
                val actualLLMSendToolResultEvent = actualLLMCallStartingEvents[1]

                val actualToolCallStartingEvent = actualClientEvents.singleEvent<ToolCallStartingEvent>()

                // Correct run id will be set after the 'collect events job' is finished.
                expectedClientEvents.addAll(
                    listOf(
                        AgentStartingEvent(
                            id = actualAgentStartingEvent.id,
                            parentId = null,
                            agentId = agentId,
                            runId = runId,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        GraphStrategyStartingEvent(
                            id = actualStrategyStartingEvent.id,
                            parentId = actualAgentStartingEvent.id,
                            runId = runId,
                            strategyName = strategyName,
                            graph = StrategyEventGraph(
                                nodes = listOf(
                                    startGraphNode,
                                    llmCallGraphNode,
                                    executeToolGraphNode,
                                    sendToolResultGraphNode,
                                    finishGraphNode,
                                ),
                                edges = listOf(
                                    StrategyEventGraphEdge(sourceNode = startGraphNode, targetNode = llmCallGraphNode),
                                    StrategyEventGraphEdge(
                                        sourceNode = llmCallGraphNode,
                                        targetNode = executeToolGraphNode
                                    ),
                                    StrategyEventGraphEdge(sourceNode = llmCallGraphNode, targetNode = finishGraphNode),
                                    StrategyEventGraphEdge(
                                        sourceNode = executeToolGraphNode,
                                        targetNode = sendToolResultGraphNode
                                    ),
                                    StrategyEventGraphEdge(
                                        sourceNode = sendToolResultGraphNode,
                                        targetNode = finishGraphNode
                                    ),
                                    StrategyEventGraphEdge(
                                        sourceNode = sendToolResultGraphNode,
                                        targetNode = executeToolGraphNode
                                    )
                                )
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionStartingEvent(
                            id = actualNodeStartEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "__start__",
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            id = actualNodeStartEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "__start__",
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            output = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionStartingEvent(
                            id = actualNodeLLMCallEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "test-llm-call",
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallStartingEvent(
                            id = actualLLMCallEvent.id,
                            parentId = actualNodeLLMCallEvent.id,
                            runId = runId,
                            prompt = expectedLLMCallPrompt,
                            model = testModel.toModelInfo(),
                            tools = listOf(dummyTool.name),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallCompletedEvent(
                            id = actualLLMCallEvent.id,
                            parentId = actualNodeLLMCallEvent.id,
                            runId = runId,
                            prompt = expectedLLMCallPrompt,
                            model = testModel.toModelInfo(),
                            responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""")),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            id = actualNodeLLMCallEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "test-llm-call",
                            // TODO: KG-485. Update to include serialized [ReceivedToolResult] when it became a serializable type.
                            input = JsonPrimitive(userPrompt),
                            output = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                                dataType = typeOf<Message>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionStartingEvent(
                            id = actualNodeToolCallEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "test-tool-call",
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                                dataType = typeOf<Message.Tool.Call>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        ToolCallStartingEvent(
                            id = actualToolCallStartingEvent.id,
                            parentId = actualNodeToolCallEvent.id,
                            runId = runId,
                            toolCallId = "0",
                            toolName = dummyTool.name,
                            toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        ToolCallCompletedEvent(
                            id = actualToolCallStartingEvent.id,
                            parentId = actualNodeToolCallEvent.id,
                            runId = runId,
                            toolCallId = "0",
                            toolName = dummyTool.name,
                            toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                            result = dummyTool.result,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            id = actualNodeToolCallEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "test-tool-call",
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                                dataType = typeOf<Message.Tool.Call>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds(),
                            // Tool result is wrapped into an object with id, tool, content, and result fields
                            output = kotlinx.serialization.json.JsonObject(
                                mapOf(
                                    "id" to JsonPrimitive("0"),
                                    "tool" to JsonPrimitive(dummyTool.name),
                                    "content" to JsonPrimitive(dummyTool.result),
                                    "result" to dummyTool.encodeResult(dummyTool.result)
                                )
                            )
                        ),
                        NodeExecutionStartingEvent(
                            id = actualNodeSendToolResultEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "test-node-llm-send-tool-result",
                            input = kotlinx.serialization.json.JsonObject(
                                mapOf(
                                    "id" to JsonPrimitive("0"),
                                    "tool" to JsonPrimitive(dummyTool.name),
                                    "content" to JsonPrimitive(dummyTool.result),
                                    "result" to dummyTool.encodeResult(dummyTool.result)
                                )
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallStartingEvent(
                            id = actualLLMSendToolResultEvent.id,
                            parentId = actualNodeSendToolResultEvent.id,
                            runId = runId,
                            prompt = expectedLLMCallWithToolsPrompt,
                            model = testModel.toModelInfo(),
                            tools = listOf(dummyTool.name),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallCompletedEvent(
                            id = actualLLMSendToolResultEvent.id,
                            parentId = actualNodeSendToolResultEvent.id,
                            runId = runId,
                            prompt = expectedLLMCallWithToolsPrompt,
                            model = testModel.toModelInfo(),
                            responses = listOf(assistantMessage(mockResponse)),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            id = actualNodeSendToolResultEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "test-node-llm-send-tool-result",
                            input = kotlinx.serialization.json.JsonObject(
                                mapOf(
                                    "id" to JsonPrimitive("0"),
                                    "tool" to JsonPrimitive(dummyTool.name),
                                    "content" to JsonPrimitive(dummyTool.result),
                                    "result" to dummyTool.encodeResult(dummyTool.result)
                                )
                            ),
                            output = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = assistantMessage(mockResponse),
                                dataType = typeOf<Message>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionStartingEvent(
                            id = actualNodeFinishEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "__finish__",
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = mockResponse,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            id = actualNodeFinishEvent.id,
                            parentId = actualStrategyStartingEvent.id,
                            runId = runId,
                            nodeName = "__finish__",
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = mockResponse,
                                dataType = typeOf<String>()
                            ),
                            output = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = mockResponse,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        StrategyCompletedEvent(
                            id = actualStrategyStartingEvent.id,
                            parentId = actualAgentStartingEvent.id,
                            runId = runId,
                            strategyName = strategyName,
                            result = mockResponse,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        AgentCompletedEvent(
                            id = actualAgentStartingEvent.id,
                            parentId = null,
                            agentId = agentId,
                            runId = runId,
                            result = mockResponse,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        AgentClosingEvent(
                            id = actualAgentClosingEvent.id,
                            parentId = null,
                            agentId = agentId,
                            timestamp = testClock.now().toEpochMilliseconds()
                        )
                    )
                )
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        // The 'runId' is updated when the agent is finished.
        // We cannot simplify that and move the expected events list before the job is finished
        // and relay on the number of elements in the list.
        assertEquals(
            expectedClientEvents.size,
            actualClientEvents.size,
            "expectedEventsCount variable in the test need to be updated"
        )
        assertContentEquals(expectedClientEvents, actualClientEvents)
    }

    @Test
    fun `test feature message remote writer is not set`() = runBlocking {
        val strategyName = "tracing-test-strategy"

        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val actualEvents = mutableListOf<FeatureMessage>()

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use {
                TestFeatureMessageWriter().use { testWriter ->

                    val strategy = strategy<String, String>(strategyName) {
                        val llmCallNode by nodeLLMRequest("test LLM call")
                        val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                        edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                        edge(
                            llmCallNode forwardTo llmCallWithToolsNode transformed {
                                "Test LLM call with tools prompt"
                            }
                        )
                        edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
                    }

                    createAgent(strategy = strategy) {
                        install(Tracing) {
                            addMessageProcessor(testWriter)
                        }
                    }.use { agent ->
                        agent.run("")
                        isServerStarted.complete(true)
                        isClientFinished.await()
                    }
                }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { message: FeatureMessage ->
                        logger.debug { "Client received message: $message" }
                        actualEvents.add(message)
                    }
                }

                logger.debug { "Client waits for server to start" }
                isServerStarted.await()

                val throwable = assertFailsWith<SSEClientException> {
                    client.connect()
                }

                logger.debug { "Client sends finish event to a server" }
                isClientFinished.complete(true)

                collectEventsJob.cancelAndJoin()

                val actualErrorMessage = throwable.message
                assertNotNull(actualErrorMessage)
                assertTrue(actualErrorMessage.contains("Connection refused"))

                assertEquals(0, actualEvents.size)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test feature message remote writer filter`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"

        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"

        val mockResponse = "Return test result"

        // Tools
        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Model
        val testModel = LLModel(
            provider = MockLLMProvider(),
            id = "test-llm-id",
            capabilities = emptyList(),
            contextLength = 1_000,
        )

        // Prompt
        val expectedPrompt = Prompt(
            messages = listOf(
                systemMessage(systemPrompt),
                userMessage(userPrompt),
                assistantMessage(assistantPrompt)
            ),
            id = promptId
        )

        val expectedLLMCallPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + userMessage(content = userPrompt)
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = userPrompt),
                toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                receivedToolResult("0", dummyTool.name, dummyTool.result, dummyTool.encodeResult(dummyTool.result)).toMessage(clock = testClock)
            )
        )

        // Test Data
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(host = HOST, port = port)
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isServerStarted = CompletableDeferred<Boolean>()

        val actualClientEvents = mutableListOf<FeatureMessage>()
        val expectedClientEvents = mutableListOf<FeatureMessage>()

        // Server
        val serverJob = launch {
            TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                val serverReadyJob = signalServerStarted(writer, isServerStarted)

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

                val mockExecutor = getMockExecutor(clock = testClock) {
                    mockLLMToolCall(tool = dummyTool, args = DummyTool.Args("test"), toolCallId = "0") onRequestEquals
                        userPrompt
                    mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
                }

                try {
                    createAgent(
                        agentId = agentId,
                        strategy = strategy,
                        promptId = promptId,
                        model = testModel,
                        userPrompt = userPrompt,
                        systemPrompt = systemPrompt,
                        assistantPrompt = assistantPrompt,
                        toolRegistry = toolRegistry,
                        promptExecutor = mockExecutor
                    ) {
                        install(Tracing) {
                            writer.setMessageFilter { message ->
                                message is LLMCallStartingEvent || message is LLMCallCompletedEvent
                            }
                            addMessageProcessor(writer)
                        }
                    }.use { agent ->
                        agent.run(userPrompt)
                    }
                } finally {
                    serverReadyJob.join()
                }
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                var runId = ""

                val collectEventsJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { event ->
                        if (event is LLMCallStartingEvent) {
                            runId = event.runId
                        }

                        actualClientEvents.add(event as DefinedFeatureEvent)
                        logger.info { "[${actualClientEvents.size}] Received event: $event" }

                        if (actualClientEvents.size == 4) {
                            cancel()
                        }
                    }
                }

                isServerStarted.await()

                client.connect()
                collectEventsJob.join()

                // Expected events
                val actualLLMCallStartingEvents = actualClientEvents.findEvents<LLMCallStartingEvent>()
                val actualLLMCallEvent = actualLLMCallStartingEvents[0]
                val actualLLMSendToolResultEvent = actualLLMCallStartingEvents[1]

                // Correct run id will be set after the 'collect events job' is finished.
                expectedClientEvents.addAll(
                    listOf(
                        LLMCallStartingEvent(
                            id = actualLLMCallEvent.id,
                            // We test filter and not able to receive node events to verify the 'parentId' field
                            parentId = actualLLMCallEvent.parentId,
                            runId = runId,
                            prompt = expectedLLMCallPrompt,
                            model = testModel.toModelInfo(),
                            tools = listOf(dummyTool.name),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallCompletedEvent(
                            id = actualLLMCallEvent.id,
                            // We test filter and not able to receive node events to verify the 'parentId' field
                            parentId = actualLLMCallEvent.parentId,
                            runId = runId,
                            prompt = expectedLLMCallPrompt,
                            model = testModel.toModelInfo(),
                            responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""")),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallStartingEvent(
                            id = actualLLMSendToolResultEvent.id,
                            // We test filter and not able to receive node events to verify the 'parentId' field
                            parentId = actualLLMSendToolResultEvent.parentId,
                            runId = runId,
                            prompt = expectedLLMCallWithToolsPrompt,
                            model = testModel.toModelInfo(),
                            tools = listOf(dummyTool.name),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallCompletedEvent(
                            id = actualLLMSendToolResultEvent.id,
                            // We test filter and not able to receive node events to verify the 'parentId' field
                            parentId = actualLLMSendToolResultEvent.parentId,
                            runId = runId,
                            prompt = expectedLLMCallWithToolsPrompt,
                            model = testModel.toModelInfo(),
                            responses = listOf(assistantMessage(mockResponse)),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                    )
                )
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertEquals(
            expectedClientEvents.size,
            actualClientEvents.size,
            "expectedEventsCount variable in the test need to be updated"
        )
        assertContentEquals(expectedClientEvents, actualClientEvents)
    }
}
