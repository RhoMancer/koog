package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.parser.FlowJsonConfigParser
import ai.koog.protocol.tool.FlowTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class FlowJsonParserTest : FlowTestBase() {

    //region Flow

    @Test
    fun testJsonParsing_randomNumbersFlowJson() {
        val jsonContent = readFlow("random_koog_agent_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow config
        assertEquals("random-numbers-flow", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt-4o", flowConfig.defaultModel)


        // Verify agents
        assertEquals(2, flowConfig.agents.size)

        // get_numbers
        val getNumbersAgent = flowConfig.agents[0]
        assertIs<FlowTaskAgent>(getNumbersAgent)
        assertEquals("get_numbers", getNumbersAgent.name)
        assertEquals(FlowAgentKind.TASK, getNumbersAgent.type)
        assertEquals("openai/gpt-4o", getNumbersAgent.model, "Expected to get a default model, but received ${getNumbersAgent.model}")
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
        assertEquals("openai/gpt-4o-mini", calculatorAgent.model, "Expected to get a custom model, but received ${calculatorAgent.model}")
        assertNotNull(calculatorAgent.parameters)
        assertEquals(
            "Your task is to sum all individual numbers in the input string. Numbers are separated by spaces.",
            calculatorAgent.parameters.task
        )

        // Verify transition
        assertEquals(1, flowConfig.transitions.size)

        val transition = flowConfig.transitions[0]
        assertEquals("get_numbers", transition.from)
        assertEquals("calculator", transition.to)
    }

    //endregion Flow

    //region Tools

    @Test
    fun testFlowParsing_withMcpTools() {
        val jsonContent = readFlow("random_koog_agent_flow_with_mcp_tools.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow metadata
        assertEquals("random-numbers-flow-with-mcp-tools", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt-4o", flowConfig.defaultModel)

        // Verify tools are parsed correctly
        assertEquals(2, flowConfig.tools.size)

        // First tool: MCP SSE
        val sseTool = flowConfig.tools[0]
        assertIs<FlowTool.Mcp.SSE>(sseTool)
        assertEquals("http://localhost:8931/sse", sseTool.url)
        assertEquals(emptyMap(), sseTool.headers)

        // Second tool: MCP Stdio
        val stdioTool = flowConfig.tools[1]
        assertIs<FlowTool.Mcp.Stdio>(stdioTool)
        assertEquals("npx", stdioTool.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-github"), stdioTool.args)

        // Verify agents are still parsed correctly
        assertEquals(2, flowConfig.agents.size)
        assertEquals("get_numbers", flowConfig.agents[0].name)
        assertEquals("calculator", flowConfig.agents[1].name)

        // Verify transitions are still parsed correctly
        assertEquals(1, flowConfig.transitions.size)
        assertEquals("get_numbers", flowConfig.transitions[0].from)
        assertEquals("calculator", flowConfig.transitions[0].to)
    }

    @Test
    fun testFlowParsing_withMcpSseToolWithHeaders() {
        val jsonContent = """
        {
            "id": "test-flow",
            "version": "1.0",
            "defaultModel": "openai/gpt-4o",
            "tools": [
                {
                    "name": "authenticated-mcp-server",
                    "type": "mcp",
                    "parameters": {
                        "transport": "sse",
                        "url": "http://localhost:9000/sse",
                        "headers": {
                            "Authorization": "Bearer token123",
                            "X-Custom-Header": "custom-value"
                        }
                    }
                }
            ],
            "agents": [],
            "transitions": []
        }
        """.trimIndent()

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        assertEquals(1, flowConfig.tools.size)

        val sseTool = flowConfig.tools[0]
        assertIs<FlowTool.Mcp.SSE>(sseTool)
        assertEquals("http://localhost:9000/sse", sseTool.url)
        assertEquals(
            mapOf(
                "Authorization" to "Bearer token123",
                "X-Custom-Header" to "custom-value"
            ),
            sseTool.headers
        )
    }

    @Test
    fun testFlowParsing_withMcpStdioToolMinimalArgs() {
        val jsonContent = """
        {
            "id": "test-flow",
            "version": "1.0",
            "defaultModel": "openai/gpt-4o",
            "tools": [
                {
                    "name": "simple-stdio-tool",
                    "type": "mcp",
                    "parameters": {
                        "transport": "stdio",
                        "command": "python3"
                    }
                }
            ],
            "agents": [],
            "transitions": []
        }
        """.trimIndent()

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        assertEquals(1, flowConfig.tools.size)

        val stdioTool = flowConfig.tools[0]
        assertIs<FlowTool.Mcp.Stdio>(stdioTool)
        assertEquals("python3", stdioTool.command)
        assertEquals(emptyList(), stdioTool.args)
    }

    //endregion Tools
}
