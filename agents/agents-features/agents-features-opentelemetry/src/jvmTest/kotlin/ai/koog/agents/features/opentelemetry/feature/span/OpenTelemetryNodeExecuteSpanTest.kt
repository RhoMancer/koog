package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestData
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.utils.HiddenString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OpenTelemetryNodeExecuteSpanTest : OpenTelemetryTestBase() {

    @Test
    fun `test node execute spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val nodeName = "test-node"
        val nodeOutput = "$userInput (node)"

        val strategy = strategy<String, String>("test-strategy") {
            val nodeBlank by node<String, String>(nodeName) { nodeOutput }
            nodeStart then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
        )

        val runId = collectedTestData.lastRunId
        val result = collectedTestData.result

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Node spans should be created during agent execution")

        val actualNodeEventIds = collectedTestData.collectedNodeIds
        assertTrue(actualNodeEventIds.isNotEmpty(), "Node event ids should be collected during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "$START_NODE_PREFIX.${collectedTestData.singleNodeInfoById(START_NODE_PREFIX).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$nodeName.${collectedTestData.singleNodeInfoById(nodeName).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeName,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$nodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$FINISH_NODE_PREFIX.${collectedTestData.singleNodeInfoById(FINISH_NODE_PREFIX).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to FINISH_NODE_PREFIX,
                        "koog.node.output" to "\"$result\"",
                        "koog.node.input" to "\"$result\"",
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execute spans with verbose logging disabled`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val nodeName = "test-node"
        val nodeOutput = "$userInput (node)"

        val strategy = strategy<String, String>("test-strategy") {
            val nodeBlank by node<String, String>(nodeName) { nodeOutput }
            nodeStart then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            verbose = false,
        )

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val actualNodeEventIds = collectedTestData.collectedNodeIds
        assertTrue(actualNodeEventIds.isNotEmpty(), "Node event ids should be collected during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "$START_NODE_PREFIX.${collectedTestData.singleNodeInfoById(START_NODE_PREFIX).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$nodeName.${collectedTestData.singleNodeInfoById(nodeName).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeName,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$FINISH_NODE_PREFIX.${collectedTestData.singleNodeInfoById(FINISH_NODE_PREFIX).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to FINISH_NODE_PREFIX,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execute spans with parallel nodes execution`() = runTest {
        val userInput = "Generate a joke"

        val nodeGenerateJokesName = "node-generate-jokes"
        val nodeFirstJokeName = "node-1-joke"
        val nodeSecondJokeName = "node-2-joke"
        val nodeThirdJokeName = "node-3-joke"

        val nodeFirstJokeOutput = "First joke: Why do programmers prefer dark mode? Because light attracts bugs!"
        val nodeSecondJokeOutput = "Second joke: Why do Java developers wear glasses? Because they don't C#!"
        val nodeThirdJokeOutput = "Third joke: A SQL query walks into a bar, walks up to two tables and asks, 'Can I join you?'"

        val strategy = strategy("test-parallel-strategy") {
            val nodeFirstJoke by node<String, String>(nodeFirstJokeName) { nodeFirstJokeOutput }
            val nodeSecondJoke by node<String, String>(nodeSecondJokeName) { nodeSecondJokeOutput }
            val nodeThirdJoke by node<String, String>(nodeThirdJokeName) { nodeThirdJokeOutput }

            // Define a node to run joke generation in parallel
            val nodeGenerateJokes by parallel(nodeFirstJoke, nodeSecondJoke, nodeThirdJoke, name = nodeGenerateJokesName) {
                selectByIndex {
                    // Always select the first joke for testing purposes
                    0
                }
            }

            edge(nodeStart forwardTo nodeGenerateJokes)
            edge(nodeGenerateJokes forwardTo nodeFinish)
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
        )

        val runId = collectedTestData.lastRunId
        val result = collectedTestData.result

        val isJokeNodeSpansCollected = withContext(Dispatchers.Default) {
            withTimeoutOrNull(5.seconds) {
                collectedTestData.filterNodeExecutionSpans().count { it.name.contains("-joke") } == 3
            } != null
        }
        assertTrue(isJokeNodeSpansCollected, "Spans should be created during agent execution")

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
            .sortedWith { one, other ->
                if (one.name.contains("-joke") && other.name.contains("-joke")) {
                    one.name.compareTo(other.name)
                } else {
                    0
                }
            }

        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "$START_NODE_PREFIX.${collectedTestData.singleNodeInfoById(START_NODE_PREFIX).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$nodeFirstJokeName.${collectedTestData.singleNodeInfoById(nodeFirstJokeName).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeFirstJokeName,
                        "koog.node.output" to "\"$nodeFirstJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$nodeSecondJokeName.${collectedTestData.singleNodeInfoById(nodeSecondJokeName).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeSecondJokeName,
                        "koog.node.output" to "\"$nodeSecondJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$nodeThirdJokeName.${collectedTestData.singleNodeInfoById(nodeThirdJokeName).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeThirdJokeName,
                        "koog.node.output" to "\"$nodeThirdJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$nodeGenerateJokesName.${collectedTestData.singleNodeInfoById(nodeGenerateJokesName).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeGenerateJokesName,
                        "koog.node.output" to "\"$nodeFirstJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$FINISH_NODE_PREFIX.${collectedTestData.singleNodeInfoById(FINISH_NODE_PREFIX).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to FINISH_NODE_PREFIX,
                        "koog.node.output" to "\"$result\"",
                        "koog.node.input" to "\"$result\"",
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execution spans for node with error`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val nodeWithErrorName = "node-with-error"
        val testErrorMessage = "Test error"

        val strategy = strategy("test-strategy") {
            val nodeWithError by node<String, String>(nodeWithErrorName) {
                throw IllegalStateException(testErrorMessage)
            }

            nodeStart then nodeWithError then nodeFinish
        }

        val collectedTestData = OpenTelemetryTestData()

        val throwable = assertFails {
            runAgentWithStrategy(
                userPrompt = userInput,
                strategy = strategy,
                collectedTestData = collectedTestData
            )
        }

        val runId = collectedTestData.lastRunId
        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        assertEquals(testErrorMessage, throwable.message)

        val expectedSpans = listOf(
            mapOf(
                "$START_NODE_PREFIX.${collectedTestData.singleNodeInfoById(START_NODE_PREFIX).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$nodeWithErrorName.${collectedTestData.singleNodeInfoById(nodeWithErrorName).eventId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeWithErrorName,
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
