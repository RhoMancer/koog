package ai.koog.agents.core.system.feature

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.AIAgentFeatureTestAPI.testClock
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
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
import ai.koog.agents.core.feature.remote.client.config.ClientConnectionConfig
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.writer.FeatureMessageRemoteWriter
import ai.koog.agents.core.system.feature.DebuggerTestAPI.HOST
import ai.koog.agents.core.system.feature.DebuggerTestAPI.defaultClientServerTimeout
import ai.koog.agents.core.system.feature.DebuggerTestAPI.mockLLModel
import ai.koog.agents.core.system.feature.DebuggerTestAPI.testBaseClient
import ai.koog.agents.core.system.mock.ClientEventsCollector
import ai.koog.agents.core.system.mock.assistantMessage
import ai.koog.agents.core.system.mock.createAgent
import ai.koog.agents.core.system.mock.systemMessage
import ai.koog.agents.core.system.mock.toolCallMessage
import ai.koog.agents.core.system.mock.toolResultMessage
import ai.koog.agents.core.system.mock.userMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.testing.agent.agentExecutionInfo
import ai.koog.agents.testing.feature.message.findEvents
import ai.koog.agents.testing.feature.message.singleEvent
import ai.koog.agents.testing.feature.message.singleNodeEvent
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.io.use
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DebuggerTest {

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `test feature message remote writer collect events on agent run`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val nodeSendLLMCallName = "test-llm-call"
        val nodeExecuteToolName = "test-tool-call"
        val nodeSendToolResultName = "test-node-llm-send-tool-result"

        val dummyTool = DummyTool()
        val requestedDummyToolArgs = "test"

        val userPrompt = "Call the dummy tool with argument: $requestedDummyToolArgs"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"

        val mockResponse = "Return test result"

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        // Prompt
        val promptId = "Test prompt id"
        val expectedPrompt = Prompt(
            messages = listOf(
                systemMessage(systemPrompt),
                userMessage(userPrompt),
                assistantMessage(assistantPrompt)
            ),
            id = promptId
        )

        val expectedLLMCallPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + userMessage(
                content = userPrompt
            )
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = userPrompt),
                toolCallMessage(
                    toolName = dummyTool.name,
                    content = """{"dummy":"$requestedDummyToolArgs"}"""
                ),
                toolResultMessage(
                    toolCallId = "0",
                    toolName = dummyTool.name,
                    content = dummyTool.encodeResultToString(dummyTool.result),
                    metaInfo = RequestMetaInfo.create(testClock)
                )
            )
        )

        // Test Data
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val expectedFilteredEvents = mutableListOf<FeatureMessage>()
        val actualFilteredEvents = mutableListOf<FeatureMessage>()

        // Server
        val serverJob = launch {
            val strategy = strategy(strategyName) {
                val nodeSendInput by nodeLLMRequest(nodeSendLLMCallName)
                val nodeExecuteTool by nodeExecuteTool(nodeExecuteToolName)
                val nodeSendToolResult by nodeLLMSendToolResult(nodeSendToolResultName)

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(
                    tool = dummyTool,
                    args = DummyTool.Args(requestedDummyToolArgs),
                    toolCallId = "0"
                ) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor,
                model = mockLLModel,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)

                    launch {
                        val messageProcessor = messageProcessors.single() as FeatureMessageRemoteWriter
                        val isServerStartedCheck = withTimeoutOrNull(defaultClientServerTimeout) {
                            messageProcessor.isOpen.first { it }
                        } != null

                        assertTrue(isServerStartedCheck, "Server did not start in time")
                    }
                }
            }.use { agent ->
                agent.run(userPrompt)
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->
                val clientEventsCollector = ClientEventsCollector(client = client)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connect()
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val llmCallGraphNode = StrategyEventGraphNode(id = nodeSendLLMCallName, name = nodeSendLLMCallName)
                val executeToolGraphNode = StrategyEventGraphNode(id = nodeExecuteToolName, name = nodeExecuteToolName)
                val sendToolResultGraphNode =
                    StrategyEventGraphNode(id = nodeSendToolResultName, name = nodeSendToolResultName)

                val startGraphNode = StrategyEventGraphNode(id = START_NODE_PREFIX, name = START_NODE_PREFIX)
                val finishGraphNode = StrategyEventGraphNode(id = FINISH_NODE_PREFIX, name = FINISH_NODE_PREFIX)

                // Expected events
                val actualEvents = clientEventsCollector.collectedEvents

                val actualAgentClosingEvent = actualEvents.singleEvent<AgentClosingEvent>()
                val actualAgentStartingEvent = actualEvents.singleEvent<AgentStartingEvent>()
                val actualStrategyStartingEvent = actualEvents.singleEvent<StrategyStartingEvent>()

                val actualNodeStartEvent = actualEvents.singleNodeEvent(START_NODE_PREFIX)
                val actualNodeLLMCallEvent = actualEvents.singleNodeEvent(nodeSendLLMCallName)
                val actualNodeToolCallEvent = actualEvents.singleNodeEvent(nodeExecuteToolName)
                val actualNodeSendToolResultEvent = actualEvents.singleNodeEvent(nodeSendToolResultName)
                val actualNodeFinishEvent = actualEvents.singleNodeEvent(FINISH_NODE_PREFIX)

                val actualLLMCallStartingEvents = actualEvents.findEvents<LLMCallStartingEvent>()
                val actualLLMCallEvent = actualLLMCallStartingEvents[0]
                val actualLLMSendToolResultEvent = actualLLMCallStartingEvents[1]

                val actualToolCallStartingEvent = actualEvents.singleEvent<ToolCallStartingEvent>()

                actualFilteredEvents.addAll(clientEventsCollector.collectedEvents)

                expectedFilteredEvents.addAll(
                    listOf(
                        AgentStartingEvent(
                            eventId = actualAgentStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId),
                            agentId = agentId,
                            runId = clientEventsCollector.runId,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        GraphStrategyStartingEvent(
                            eventId = actualStrategyStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName),
                            runId = clientEventsCollector.runId,
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
                                        targetNode = executeToolGraphNode,
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
                            eventId = actualNodeStartEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, START_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = START_NODE_PREFIX,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            eventId = actualNodeStartEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, START_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = START_NODE_PREFIX,
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
                            eventId = actualNodeLLMCallEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendLLMCallName),
                            runId = clientEventsCollector.runId,
                            nodeName = nodeSendLLMCallName,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallStartingEvent(
                            eventId = actualLLMCallEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendLLMCallName),
                            runId = clientEventsCollector.runId,
                            prompt = expectedLLMCallPrompt,
                            model = mockLLModel.toModelInfo(),
                            tools = listOf(dummyTool.name),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallCompletedEvent(
                            eventId = actualLLMCallEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendLLMCallName),
                            runId = clientEventsCollector.runId,
                            prompt = expectedLLMCallPrompt,
                            model = mockLLModel.toModelInfo(),
                            responses = listOf(
                                toolCallMessage(
                                    dummyTool.name,
                                    content = """{"dummy":"$requestedDummyToolArgs"}"""
                                )
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            eventId = actualNodeLLMCallEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendLLMCallName),
                            runId = clientEventsCollector.runId,
                            nodeName = nodeSendLLMCallName,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = userPrompt,
                                dataType = typeOf<String>()
                            ),
                            output = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = toolCallMessage(
                                    dummyTool.name,
                                    content = """{"dummy":"$requestedDummyToolArgs"}"""
                                ),
                                dataType = typeOf<Message>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionStartingEvent(
                            eventId = actualNodeToolCallEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeExecuteToolName),
                            runId = clientEventsCollector.runId,
                            nodeName = nodeExecuteToolName,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = toolCallMessage(
                                    dummyTool.name,
                                    content = """{"dummy":"$requestedDummyToolArgs"}"""
                                ),
                                dataType = typeOf<Message.Tool.Call>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        ToolCallStartingEvent(
                            eventId = actualToolCallStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeExecuteToolName),
                            runId = clientEventsCollector.runId,
                            toolCallId = "0",
                            toolName = dummyTool.name,
                            toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        ToolCallCompletedEvent(
                            eventId = actualToolCallStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeExecuteToolName),
                            runId = clientEventsCollector.runId,
                            toolCallId = "0",
                            toolName = dummyTool.name,
                            toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                            toolDescription = dummyTool.descriptor.description,
                            result = dummyTool.encodeResult(dummyTool.result),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            eventId = actualNodeToolCallEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeExecuteToolName),
                            runId = clientEventsCollector.runId,
                            nodeName = nodeExecuteToolName,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = toolCallMessage(
                                    toolName = dummyTool.name,
                                    content = """{"dummy":"$requestedDummyToolArgs"}"""
                                ),
                                dataType = typeOf<Message.Tool.Call>()
                            ),
                            output = SerializationUtils.encodeDataToJsonElementOrNull(
                                data = ReceivedToolResult(
                                    id = "0",
                                    tool = dummyTool.name,
                                    toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                                    toolDescription = dummyTool.descriptor.description,
                                    content = dummyTool.result,
                                    resultKind = ToolResultKind.Success,
                                    result = dummyTool.encodeResult(dummyTool.result)
                                ),
                                dataType = typeOf<ReceivedToolResult>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionStartingEvent(
                            eventId = actualNodeSendToolResultEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendToolResultName),
                            runId = clientEventsCollector.runId,
                            nodeName = nodeSendToolResultName,
                            input = SerializationUtils.encodeDataToJsonElementOrNull(
                                data = ReceivedToolResult(
                                    id = "0",
                                    tool = dummyTool.name,
                                    toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                                    toolDescription = dummyTool.descriptor.description,
                                    content = dummyTool.result,
                                    resultKind = ToolResultKind.Success,
                                    result = dummyTool.encodeResult(dummyTool.result)
                                ),
                                dataType = typeOf<ReceivedToolResult>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallStartingEvent(
                            eventId = actualLLMSendToolResultEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendToolResultName),
                            runId = clientEventsCollector.runId,
                            prompt = expectedLLMCallWithToolsPrompt,
                            model = mockLLModel.toModelInfo(),
                            tools = listOf(dummyTool.name),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        LLMCallCompletedEvent(
                            eventId = actualLLMSendToolResultEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendToolResultName),
                            runId = clientEventsCollector.runId,
                            prompt = expectedLLMCallWithToolsPrompt,
                            model = mockLLModel.toModelInfo(),
                            responses = listOf(assistantMessage(mockResponse)),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            eventId = actualNodeSendToolResultEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, nodeSendToolResultName),
                            runId = clientEventsCollector.runId,
                            nodeName = nodeSendToolResultName,
                            input = SerializationUtils.encodeDataToJsonElementOrNull(
                                data = ReceivedToolResult(
                                    id = "0",
                                    tool = dummyTool.name,
                                    toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                                    toolDescription = dummyTool.descriptor.description,
                                    content = dummyTool.result,
                                    resultKind = ToolResultKind.Success,
                                    result = dummyTool.encodeResult(dummyTool.result)
                                ),
                                dataType = typeOf<ReceivedToolResult>()
                            ),
                            output = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = assistantMessage(mockResponse),
                                dataType = typeOf<Message>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionStartingEvent(
                            eventId = actualNodeFinishEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, FINISH_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = FINISH_NODE_PREFIX,
                            input = @OptIn(InternalAgentsApi::class)
                            SerializationUtils.encodeDataToJsonElementOrNull(
                                data = mockResponse,
                                dataType = typeOf<String>()
                            ),
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        NodeExecutionCompletedEvent(
                            eventId = actualNodeFinishEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName, FINISH_NODE_PREFIX),
                            runId = clientEventsCollector.runId,
                            nodeName = FINISH_NODE_PREFIX,
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
                            eventId = actualStrategyStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId, strategyName),
                            runId = clientEventsCollector.runId,
                            strategyName = strategyName,
                            result = mockResponse,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        AgentCompletedEvent(
                            eventId = actualAgentStartingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId),
                            agentId = agentId,
                            runId = clientEventsCollector.runId,
                            result = mockResponse,
                            timestamp = testClock.now().toEpochMilliseconds()
                        ),
                        AgentClosingEvent(
                            eventId = actualAgentClosingEvent.eventId,
                            executionInfo = agentExecutionInfo(agentId),
                            agentId = agentId,
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
            expectedFilteredEvents.size,
            actualFilteredEvents.size,
            "expectedEventsCount variable in the test need to be updated"
        )
        assertContentEquals(expectedFilteredEvents, actualFilteredEvents)
    }

    @Test
    fun `test two clients connected to one server`() = runBlocking {

        // Test Data
        val port1 = findAvailablePort()
        val port2 = findAvailablePort()

        val client1Config = DefaultClientConnectionConfig(host = HOST, port = port1, protocol = URLProtocol.HTTP)
        val client1Events = mutableListOf<FeatureMessage>()

        val client2Config = DefaultClientConnectionConfig(host = HOST, port = port2, protocol = URLProtocol.HTTP)
        val client2Events = mutableListOf<FeatureMessage>()

        val isClient1Connected = MutableStateFlow(false)
        val isClient2Connected = MutableStateFlow(false)

        // Server 1
        val serger1AgentId = "server-1-test-agent-multiple-clients"
        val server1UserPrompt = "server-1 Test prompt for multiple clients"
        val server1MockResponse = "server-1 Response for multiple clients"

        val server1Strategy = strategy("server-1-test-strategy") {
            val nodeLLMCall by nodeLLMRequest("server-1-node")
            val nodeDelay by node<String, String>("server-1-node-delay") {
                delay(10000)
                it
            }

            edge(nodeStart forwardTo nodeDelay)
            edge(nodeDelay forwardTo nodeLLMCall)
            edge(nodeLLMCall forwardTo nodeFinish onAssistantMessage { true })
        }

        val server1MockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(server1MockResponse) onRequestEquals server1UserPrompt
        }

        val server1StartTime = System.currentTimeMillis()
        val server1Job = launchServerOnPort(
            port = port1,
            agentId = serger1AgentId,
            strategy = server1Strategy,
            executor = server1MockExecutor,
            userPrompt = server1UserPrompt,
        )

        server1Job.invokeOnCompletion {
            println("SD -- [S] Server 1 finished after: ${(System.currentTimeMillis() - server1StartTime).toDuration(
                DurationUnit.MILLISECONDS).inWholeSeconds} s")
        }

        // Server 2
        val serger2AgentId = "server-2-test-agent-multiple-clients"
        val server2UserPrompt = "server-2 Test prompt for multiple clients"
        val server2MockResponse = "server-2 Response for multiple clients"

        val server2Strategy = strategy("server-2-test-strategy") {
            val node by nodeLLMRequest("server-2-node")

            edge(nodeStart forwardTo node)
            edge(node forwardTo nodeFinish onAssistantMessage { true })
        }

        val server2MockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(server2MockResponse) onRequestEquals server1UserPrompt
        }

        val server2StartTime = System.currentTimeMillis()
        val server2Job = launchServerOnPort(
            port = port2,
            agentId = serger2AgentId,
            strategy = server2Strategy,
            executor = server2MockExecutor,
            userPrompt = server2UserPrompt,
        )

        server2Job.invokeOnCompletion {
            println("SD -- [S] Server 2 finished after: ${(System.currentTimeMillis() - server2StartTime).toDuration(
                DurationUnit.MILLISECONDS).inWholeSeconds} s")
        }

        // Client 1
        val client1Job = connectClient(
            clientConfig = client1Config,
            isClientConnected = isClient1Connected,
            clientEvents = client1Events,
        )

        // Client 2
        val client2Job = connectClient(
            clientConfig = client2Config,
            isClientConnected = isClient2Connected,
            clientEvents = client2Events,
        )

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(client1Job, client2Job, server1Job, server2Job).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Clients or server did not finish in time")

        // Verify both clients received events
        assertTrue(client1Events.isNotEmpty(), "Client 1 should have received events")
        assertTrue(client2Events.isNotEmpty(), "Client 2 should have received events")

        // Verify key events are present in both clients
        val client1StartEvents = client1Events.filterIsInstance<AgentStartingEvent>()
        val client2StartEvents = client2Events.filterIsInstance<AgentStartingEvent>()
        assertEquals(1, client1StartEvents.size, "Client 1 should receive exactly one AgentStartingEvent")
        assertEquals(1, client2StartEvents.size, "Client 2 should receive exactly one AgentStartingEvent")

        val client1CompletedEvents = client1Events.filterIsInstance<AgentCompletedEvent>()
        val client2CompletedEvents = client2Events.filterIsInstance<AgentCompletedEvent>()
        assertEquals(1, client1CompletedEvents.size, "Client 1 should receive exactly one AgentCompletedEvent")
        assertEquals(1, client2CompletedEvents.size, "Client 2 should receive exactly one AgentCompletedEvent")
    }

    //region Private Methods

    private fun CoroutineScope.launchServerOnPort(
        port: Int,
        agentId: String,
        userPrompt: String,
        strategy: AIAgentGraphStrategy<String, String>? = null,
        executor: PromptExecutor? = null,
    ): Job {
        val job = launch {
            val mockStrategy = strategy("test-strategy") {
                val node by nodeLLMRequest("test-node")

                edge(nodeStart forwardTo node)
                edge(node forwardTo nodeFinish onAssistantMessage { true })
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMAnswer("Test response") onRequestEquals userPrompt
            }

            createAgent(
                agentId = agentId,
                strategy = strategy ?: mockStrategy,
                promptId = "test-prompt-id",
                userPrompt = userPrompt,
                toolRegistry = ToolRegistry {},
                promptExecutor = executor ?: mockExecutor,
                model = mockLLModel,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)
                }

                handleEvents {
                    onAgentStarting {

                    }
                }
            }.use { agent ->
                agent.run(userPrompt)
            }
        }

        println("SD -- [S] launched server on port $port")
        return job
    }

    private fun CoroutineScope.connectClient(
        clientConfig: ClientConnectionConfig,
        isClientConnected: MutableStateFlow<Boolean>,
        clientEvents: MutableList<FeatureMessage>
    ): Job {
        val job = launch {
            FeatureMessageRemoteClient(
                connectionConfig = clientConfig,
                baseClient = testBaseClient,
                scope = this
            ).use { client ->
                val clientEventsCollector = ClientEventsCollector(client = client, clientEvents)
                val collectEventsJob = clientEventsCollector.startCollectEvents(coroutineScope = this)

                client.connect()
                collectEventsJob.join()
                isClientConnected.value = true
            }
        }

        println("SD -- [C] launched client on port ${clientConfig.port}")
        return job
    }

    //endregion Private Methods
}
