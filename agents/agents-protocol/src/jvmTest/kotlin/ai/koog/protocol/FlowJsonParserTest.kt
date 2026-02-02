package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.parser.FlowJsonConfigParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class FlowJsonParserTest : FlowTestBase() {

    @Test
    fun testJsonParsing_randomNumbersFlowJson() {
        val jsonContent = readFlow("random_koog_agent_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow config
        assertEquals("random-numbers-flow", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt-4o", flowConfig.defaultModel)
        assertEquals(2, flowConfig.agents.size)
        assertEquals(1, flowConfig.transitions.size)

        // Verify first agent (get_numbers) - uses defaultModel
        val getNumbersAgent = flowConfig.agents[0]
        assertIs<FlowTaskAgent>(getNumbersAgent)
        assertEquals("get_numbers", getNumbersAgent.name)
        assertEquals(FlowAgentKind.TASK, getNumbersAgent.type)
        assertEquals("openai/gpt-4o", getNumbersAgent.model) // inherited from defaultModel
        assertNotNull(getNumbersAgent.parameters)
        assertEquals(
            "Generate two random numbers between 1 and 100. Output them with a space between them.",
            getNumbersAgent.parameters.task
        )

        // Verify the second agent (calculator) - overrides with an own model
        val calculatorAgent = flowConfig.agents[1]
        assertIs<FlowTaskAgent>(calculatorAgent)
        assertEquals("calculator", calculatorAgent.name)
        assertEquals(FlowAgentKind.TASK, calculatorAgent.type)
        assertEquals("openai/gpt-4o-mini", calculatorAgent.model) // agent-specific model
        assertNotNull(calculatorAgent.parameters)
        assertEquals(
            "Your task is to sum all individual numbers in the input string. Numbers are separated by spaces.",
            calculatorAgent.parameters.task
        )

        // Verify transition
        val transition = flowConfig.transitions[0]
        assertEquals("get_numbers", transition.from)
        assertEquals("calculator", transition.to)
    }
}
