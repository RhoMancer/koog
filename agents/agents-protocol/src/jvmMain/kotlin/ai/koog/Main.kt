package ai.koog

import ai.koog.config.parser.FlowJsonConfigParser
import ai.koog.flow.FlowConfig

/**
 *
 */
public fun main() {
    val jsonContent = object {}.javaClass
        .getResourceAsStream("/flow/simple_koog_agent_flow.json")
        ?.bufferedReader()
        ?.readText()
        ?: error("Could not read JSON file")

    val parser = FlowJsonConfigParser()
    val config: FlowConfig = parser.parse(jsonContent)

    println("Parsed FlowConfig:")
    println("  ID: ${config.id}")
    println("  Version: ${config.version}")
    println("  Agents: ${config.agents.size}")
    config.agents.forEach { agent ->
        println("    - ${agent.name} (${agent.type}")
    }
    println("  Transitions: ${config.transitions.size}")
    config.transitions.forEach { transition ->
        println("    - ${transition.from} -> ${transition.to}")
    }
}
