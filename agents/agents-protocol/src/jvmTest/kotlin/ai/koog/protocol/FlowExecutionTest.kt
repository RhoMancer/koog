package ai.koog.protocol

import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.flow.KoogFlow
import ai.koog.protocol.parser.FlowJsonConfigParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FlowExecutionTest : FlowTestBase() {

    @Test
    fun testFlowRun_randomNumbersFlowJson() = runTest {
        val jsonContent = readFlow("random_koog_agent_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Create finalize tool for mocking with FlowAgentInput type
        val finalizeTool = SubgraphWithTaskUtils.finishTool<FlowAgentInput>()

        // Mock executor: first agent returns "42 58", second returns "100"
        val testExecutor = getMockExecutor {
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("42 58")) onCondition { request ->
                request.contains("Generate two random numbers")
            }
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("100")) onCondition { request ->
                // Only match the second agent's requests (not the first one)
                !request.contains("Generate two random numbers")
            }
        }

        val flow = KoogFlow(
            id = flowConfig.id ?: "test-flow",
            agents = flowConfig.agents,
            tools = emptyList(),
            transitions = flowConfig.transitions,
            defaultModel = flowConfig.defaultModel,
            promptExecutor = testExecutor
        )

        val result = flow.run()

        // Verify the result is the sum from the calculator agent
        assertIs<FlowAgentInput.InputString>(result)
        assertEquals("100", result.data)
    }
}
