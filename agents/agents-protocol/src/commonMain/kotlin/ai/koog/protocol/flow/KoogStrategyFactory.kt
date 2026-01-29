package ai.koog.protocol.flow

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 *
 */
public object KoogStrategyFactory {

    /**
     *
     */
    public fun buildStrategy(
        id: String,
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>,
        tools: List<FlowTool>,
        defaultModel: String?
    ): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {

        // No agents
        if (agents.isEmpty()) {
            return createEmptyStrategy(id)
        }

        // No transitions
        if (transitions.isEmpty()) {
            return createEmptyStrategy(id)
        }

        return strategy(id) {
            // Nodes
            val collectedNodes = agents.map { agent ->
                val node by convertFlowAgentToKoogNode(agent, defaultModel)
                node
            }

            val firstAgentName = FlowUtil.getFirstAgent(agents, transitions)?.name
                ?: error("Unexpected case. Fist agent name is not found." +
                    "\nCollected agent names: ${agents.joinToString { " - ${it.name}" } }")

            val firstNode = collectedNodes.find { node -> node.id == firstAgentName }
                ?: error("First agent node not found in collected nodes." +
                    "\nExpected agent name: $firstAgentName," +
                    "\nActual node names: ${collectedNodes.joinToString("\n") { " - ${it.id}" } }")

            // Edges
            edge(nodeStart forwardTo firstNode)

            // Process transitions and create edges
            transitions.forEach { transition ->
                val fromNode = collectedNodes.find { it.name == transition.from }
                    ?: error("Failed to build edge. Node id not found: ${transition.from}")

                if (transition.to == FINISH_NODE_PREFIX) {
                    // Edge to finish node
                    val edgeBuilder = fromNode forwardTo nodeFinish
                    if (transition.condition != null) {
                        edge(edgeBuilder onCondition { output ->
                            evaluateCondition(transition.condition, output)
                        })
                    } else {
                        edge(edgeBuilder)
                    }
                } else {
                    // Edge to another agent
                    val toNode = collectedNodes.find { it.name == transition.to }
                        ?: error("Agent not found: ${transition.to}")

                    val edgeBuilder = fromNode forwardTo toNode
                    if (transition.condition != null) {
                        edge(edgeBuilder onCondition { output ->
                            evaluateCondition(transition.condition, output)
                        })
                    } else {
                        edge(edgeBuilder)
                    }
                }
            }

            // For agents without outgoing transitions, connect them to finish
            val agentsWithOutgoingTransitions = transitions.map { it.from }.toSet()
            val nodesWithoutFinish = collectedNodes.filter { node ->
                node.name !in agentsWithOutgoingTransitions
            }

            nodesWithoutFinish.forEach { nodeWithoutFinish ->
                edge(nodeWithoutFinish forwardTo nodeFinish)
            }
        }
    }

    //region Private Methods

    /**
     * Converts a flow agent into Koog node delegate for a given flow agent type.
     */
    private fun AIAgentGraphStrategyBuilder<*, *>.convertFlowAgentToKoogNode(
        agent: FlowAgent,
        defaultModel: String?
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return when (agent.type) {
            FlowAgentKind.TASK -> nodeTask(agent, defaultModel)
            FlowAgentKind.VERIFY -> nodeVerify(agent, defaultModel)
            FlowAgentKind.TRANSFORM -> nodeTransform(agent)
            FlowAgentKind.PARALLEL -> error("Parallel agent type is not yet supported")
        }
    }

    /**
     * Creates a task node that performs LLM request with the agent's configuration.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTask(
        agent: FlowAgent,
        defaultModel: String?
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return subgraphWithTask<FlowAgentInput, FlowAgentInput>(
            name = agent.name,
            toolSelectionStrategy = ToolSelectionStrategy.ALL,
            llmModel = (agent.model ?: defaultModel)?.let { parseModel(it) }
        ) { input ->
            buildTaskPrompt(agent, input)
        }
    }

    /**
     * Creates a node that checks/validates using LLM.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeVerify(
        agent: FlowAgent,
        defaultModel: String?
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return subgraphWithTask<FlowAgentInput, FlowAgentInput>(
            name = agent.name,
            toolSelectionStrategy = ToolSelectionStrategy.ALL,
            llmModel = (agent.model ?: defaultModel)?.let { parseModel(it) }
        ) { input ->
            buildTaskPrompt(agent, input) +
                "\n\nProvide verification result indicating success/failure."
        }
    }

    /**
     * Creates a transform node that applies transformations without LLM.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTransform(
        agent: FlowAgent
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return subgraphWithTask<FlowAgentInput, FlowAgentInput>(
            name = agent.name,
            toolSelectionStrategy = ToolSelectionStrategy.ALL,
            llmModel = null
        ) { input ->
            buildTaskPrompt(agent, input)
        }
    }

    /**
     * Creates an empty strategy that immediately finishes.
     */
    private fun createEmptyStrategy(id: String): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {
        return strategy(id) {
            edge(nodeStart forwardTo nodeFinish)
        }
    }

    /**
     * Builds a task prompt combining agent's system and user prompts with input.
     */
    private fun buildTaskPrompt(agent: FlowAgent, input: FlowAgentInput): String = buildString {
        agent.prompt?.system?.let { system ->
            appendLine(system)
            appendLine()
        }

        agent.prompt?.user?.let { user ->
            appendLine(user)
            appendLine()
        }

        appendLine("Input:")
        appendLine(input.toStringValue())
    }

    /**
     * Parses a model string into an LLModel instance.
     */
    private fun parseModel(modelString: String): LLModel {
        val parts = modelString.split("/")
        val providerName = if (parts.size > 1) parts[0].lowercase() else "openai"
        val modelId = if (parts.size > 1) parts[1] else modelString

        val provider = when (providerName) {
            "openai" -> LLMProvider.OpenAI
            "anthropic" -> LLMProvider.Anthropic
            "google" -> LLMProvider.Google
            "meta" -> LLMProvider.Meta
            "ollama" -> LLMProvider.Ollama
            "openrouter" -> LLMProvider.OpenRouter
            "deepseek" -> LLMProvider.DeepSeek
            "mistralai" -> LLMProvider.MistralAI
            else -> LLMProvider.OpenAI
        }

        return LLModel(
            provider = provider,
            id = modelId,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.Completion
            ),
            contextLength = 128_000
        )
    }

    /**
     * Evaluates a transition condition against the current output.
     *
     * @param condition The condition to evaluate
     * @param output The current agent output
     * @return true if the condition is satisfied
     */
    private fun evaluateCondition(condition: FlowTransitionCondition, output: FlowAgentInput): Boolean {
        // Extract value from output based on variable name
        val outputValue: Any = when (output) {
            is FlowAgentInput.Boolean -> output.data
            is FlowAgentInput.Int -> output.data
            is FlowAgentInput.Double -> output.data
            is FlowAgentInput.String -> output.data
            is FlowAgentInput.CritiqueResult -> {
                // For CritiqueResult, check the variable name
                when (condition.variable) {
                    "success" -> output.success
                    "feedback" -> output.feedback
                    else -> output.toStringValue()
                }
            }
            is FlowAgentInput.ArrayBooleans,
            is FlowAgentInput.ArrayDouble,
            is FlowAgentInput.ArrayInt,
            is FlowAgentInput.ArrayStrings -> output.toStringValue()
        }

        val conditionValue = when {
            condition.value.booleanOrNull != null -> condition.value.booleanOrNull
            condition.value.intOrNull != null -> condition.value.intOrNull
            condition.value.doubleOrNull != null -> condition.value.doubleOrNull
            else -> condition.value.content
        }

        return when (condition.operation) {
            ConditionOperationKind.EQUALS -> outputValue == conditionValue
            ConditionOperationKind.NOT_EQUALS -> outputValue != conditionValue
            ConditionOperationKind.MORE -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() > conditionValue.toDouble()
                    else -> false
                }
            }
            ConditionOperationKind.LESS -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() < conditionValue.toDouble()
                    else -> false
                }
            }
            ConditionOperationKind.MORE_OR_EQUAL -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() >= conditionValue.toDouble()
                    else -> false
                }
            }
            ConditionOperationKind.LESS_OR_EQUAL -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() <= conditionValue.toDouble()
                    else -> false
                }
            }
            ConditionOperationKind.NOT -> {
                when (outputValue) {
                    is Boolean -> !outputValue
                    else -> false
                }
            }
            ConditionOperationKind.AND, ConditionOperationKind.OR -> {
                // Complex conditions not implemented yet
                false
            }
        }
    }

    //endregion Private Methods
}
