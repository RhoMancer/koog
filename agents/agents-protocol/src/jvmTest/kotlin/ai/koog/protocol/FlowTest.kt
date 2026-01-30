package ai.koog.protocol

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.protocol.flow.KoogFlow
import ai.koog.protocol.parser.FlowJsonConfigParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlowTest {

    @Test
    fun test_random_numbers_flow() = runTest {
        val jsonContent = readFlow("random_koog_agent_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        println("Parsed FlowConfig:")
        println("  ID: ${flowConfig.id}")
        println("  Version: ${flowConfig.version}")
        println("  Agents: ${flowConfig.agents.size}")
        flowConfig.agents.forEach { agent ->
            println("    - ${agent.name} (${agent.type})")
        }
        println("  Transitions: ${flowConfig.transitions.size}")
        flowConfig.transitions.forEach { transition ->
            println("    - ${transition.transitionString}")
        }

        // Create a test executor that returns mock responses
        val testExecutor = getMockExecutor {
            mockLLMAnswer("42 58").asDefaultResponse
        }

        // Create a KoogFlow from the config and run it
        println("\nCreating KoogFlow and running...")
        val firstAgentModel = flowConfig.transitions.firstOrNull()?.let { firstTransition ->
            flowConfig.agents.find { agent -> agent.name == firstTransition.from }?.model
        }

        val defaultModelString = "openai/gpt-4"

        val flow = KoogFlow(
            id = flowConfig.id ?: "koog-test-flow",
            agents = flowConfig.agents,
            tools = emptyList(),
            transitions = flowConfig.transitions,
            defaultModel = firstAgentModel ?: defaultModelString,
            promptExecutor = testExecutor
        )

        val result = flow.run()
        println("Flow execution result: $result")

        // Verify that we got a result
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "Flow should return non-empty result")
    }

    private fun readFlow(name: String): String {
        val jsonContent = object {}.javaClass
            .getResourceAsStream(name)
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not read JSON file")

        return jsonContent
    }
}
