package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.utils.HiddenString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenTelemetrySubgraphTest : OpenTelemetryTestBase() {

    @Test
    fun `test node execution spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphNodeOutput = "$userInput (subgraph)"

        val strategy = strategy<String, String>("test-strategy") {
            val subgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphBlank by node<String, String>(subgraphNodeName) { subgraphNodeOutput }
                nodeStart then nodeSubgraphBlank then nodeFinish
            }
            nodeStart then subgraph then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(strategy = strategy, userPrompt = userInput)

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterSubgraphExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                FINISH_NODE_PREFIX to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to FINISH_NODE_PREFIX,
                        "koog.subgraph.output" to "\"$subgraphNodeOutput\"",
                        "koog.subgraph.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                subgraphName to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to subgraphName,
                        "koog.subgraph.output" to "\"$subgraphNodeOutput\"",
                        "koog.subgraph.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$FINISH_NODE_PREFIX.$subgraphName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to "$FINISH_NODE_PREFIX.$subgraphName",
                        "koog.subgraph.output" to "\"$subgraphNodeOutput\"",
                        "koog.subgraph.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                subgraphNodeName to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to subgraphNodeName,
                        "koog.subgraph.output" to "\"$subgraphNodeOutput\"",
                        "koog.subgraph.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "$START_NODE_PREFIX.$subgraphName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to "$START_NODE_PREFIX.$subgraphName",
                        "koog.subgraph.input" to "\"$userInput\"",
                        "koog.subgraph.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                START_NODE_PREFIX to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to START_NODE_PREFIX,
                        "koog.subgraph.input" to "\"$userInput\"",
                        "koog.subgraph.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execution spans with verbose logging disabled`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphNodeOutput = "$userInput (subgraph)"

        val strategy = strategy<String, String>("test-strategy") {
            val subgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphBlank by node<String, String>(subgraphNodeName) { subgraphNodeOutput }
                nodeStart then nodeSubgraphBlank then nodeFinish
            }
            nodeStart then subgraph then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            verbose = false,
        )

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterSubgraphExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                FINISH_NODE_PREFIX to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to FINISH_NODE_PREFIX,
                        "koog.subgraph.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.subgraph.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                subgraphName to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to subgraphName,
                        "koog.subgraph.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.subgraph.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$FINISH_NODE_PREFIX.$subgraphName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to "$FINISH_NODE_PREFIX.$subgraphName",
                        "koog.subgraph.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.subgraph.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                subgraphNodeName to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to subgraphNodeName,
                        "koog.subgraph.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.subgraph.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "$START_NODE_PREFIX.$subgraphName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to "$START_NODE_PREFIX.$subgraphName",
                        "koog.subgraph.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.subgraph.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                START_NODE_PREFIX to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to START_NODE_PREFIX,
                        "koog.subgraph.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.subgraph.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test inner and outer node execution spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val rootNodeName = "test-root-node"
        val rootNodeOutput = "$userInput (root)"

        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphNodeOutput = "$userInput (subgraph)"

        val strategy = strategy<String, String>("test-strategy") {
            val subgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphBlank by node<String, String>(subgraphNodeName) { subgraphNodeOutput }
                nodeStart then nodeSubgraphBlank then nodeFinish
            }

            val nodeBlank by node<String, String>(rootNodeName) { rootNodeOutput }
            nodeStart then subgraph then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(strategy = strategy, userPrompt = userInput)

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterSubgraphExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                FINISH_NODE_PREFIX to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to FINISH_NODE_PREFIX,
                        "koog.subgraph.output" to "\"$rootNodeOutput\"",
                        "koog.subgraph.input" to "\"$rootNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                rootNodeName to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to rootNodeName,
                        "koog.subgraph.output" to "\"$rootNodeOutput\"",
                        "koog.subgraph.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                subgraphName to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to subgraphName,
                        "koog.subgraph.output" to "\"$subgraphNodeOutput\"",
                        "koog.subgraph.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "$FINISH_NODE_PREFIX.$subgraphName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to "$FINISH_NODE_PREFIX.$subgraphName",
                        "koog.subgraph.output" to "\"$subgraphNodeOutput\"",
                        "koog.subgraph.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                subgraphNodeName to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to subgraphNodeName,
                        "koog.subgraph.output" to "\"$subgraphNodeOutput\"",
                        "koog.subgraph.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "$START_NODE_PREFIX.$subgraphName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to "$START_NODE_PREFIX.$subgraphName",
                        "koog.subgraph.input" to "\"$userInput\"",
                        "koog.subgraph.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                START_NODE_PREFIX to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.subgraph.id" to START_NODE_PREFIX,
                        "koog.subgraph.input" to "\"$userInput\"",
                        "koog.subgraph.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
