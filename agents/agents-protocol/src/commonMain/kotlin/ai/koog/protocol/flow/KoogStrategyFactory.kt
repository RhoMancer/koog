package ai.koog.protocol.flow

import ai.koog._initial.agent.FlowAgentKind
import ai.koog._initial.agent.koog.PromptExecutorFactory
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

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
    ): AIAgentGraphStrategy<String, String> {

        // No agents
        if (agents.isEmpty()) {
            return createEmptyStrategy(id)
        }

        // No transitions
        if (transitions.isEmpty()) {
            return createEmptyStrategy(id)
        }

        return strategy(id) {
            // Create nodes for each agent type
            val collectedNodes = agents.map { agent ->
                val node by createAgentNode(agent, defaultModel)
                node
            }

            val firstNodeName = transitions.firstOrNull()?.let {
                agents.find { agent -> it.from == agent.name }
            }?.name ?: agents.first().name

            val firstNode = collectedNodes.find { it.name == firstNodeName }
                ?: error("First agent not found: $firstNodeName")

            // Connect start node to first agent
            edge(nodeStart forwardTo firstNode)

            // Process transitions and create edges
            transitions.forEach { transition ->
                val fromNode = collectedNodes.find { it.name == transition.from }
                    ?: error("Agent not found: ${transition.from}")

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
     * Creates a node delegate for a given agent based on its type.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.createAgentNode(
        agent: ai.koog._initial.agent.FlowAgent,
        defaultModel: String?
    ): AIAgentNodeDelegate<String, String> {
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
        agent: ai.koog._initial.agent.FlowAgent,
        defaultModel: String?
    ): AIAgentNodeDelegate<String, String> {
        return node(agent.name) { input ->
            // Switch to an agent-specific model if specified
            val modelToUse = agent.model ?: defaultModel
            if (modelToUse != null) {
                @OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
                llm.model = PromptExecutorFactory.resolveModel(modelToUse)
            }

            // Build full system prompt: combine agent's system prompt with task instructions
            val fullSystemPrompt = buildString {
                agent.prompt?.system?.let { append(it) }
                agent.input.task?.let { taskInstructions ->
                    if (isNotEmpty()) append("\n\n")
                    append("Task: $taskInstructions")
                }
            }

            // Set system prompt if present
            if (fullSystemPrompt.isNotEmpty()) {
                llm.writeSession {
                    appendPrompt {
                        system(fullSystemPrompt)
                    }
                }
            }

            // Always use runtime input as the user message (data from previous agent)
            llm.writeSession {
                appendPrompt {
                    user(input)
                }
                requestLLM().content
            }
        }
    }

    /**
     * Creates a verify node that checks/validates using LLM.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeVerify(
        agent: ai.koog._initial.agent.FlowAgent,
        defaultModel: String?
    ): AIAgentNodeDelegate<String, String> {
        val defaultVerifyPrompt = "You are a verification agent. Verify the task was completed correctly. Return 'PASS' if correct, or describe the issues if not."

        return node(agent.name) { input ->
            // Switch to an agent-specific model if specified
            val modelToUse = agent.model ?: defaultModel
            if (modelToUse != null) {
                @OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
                llm.model = PromptExecutorFactory.resolveModel(modelToUse)
            }

            // Build full system prompt: combine agent's system prompt with task instructions
            val fullSystemPrompt = buildString {
                val basePrompt = agent.prompt?.system ?: defaultVerifyPrompt
                append(basePrompt)
                agent.input.task?.let { taskInstructions ->
                    append("\n\n")
                    append("Task: $taskInstructions")
                }
            }

            // Always use runtime input as the user message (data from previous agent)
            llm.writeSession {
                appendPrompt {
                    system(fullSystemPrompt)
                    user(input)
                }
                requestLLM().content
            }
        }
    }

    /**
     * Creates a transform node that applies transformations without LLM.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTransform(
        agent: ai.koog._initial.agent.FlowAgent
    ): AIAgentNodeDelegate<String, String> {
        return node(agent.name) { input ->
            val transformations = agent.input.transformations
            if (transformations.isNullOrEmpty()) {
                agent.input.task ?: input
            } else {
                // Apply transformations: for now, return a description
                transformations.joinToString("\n") { transformation ->
                    "${transformation.input} -> ${transformation.to}"
                }
            }
        }
    }

    /**
     * Creates an empty strategy that immediately finishes.
     */
    private fun createEmptyStrategy(id: String): AIAgentGraphStrategy<String, String> {
        return strategy(id) {
            edge(nodeStart forwardTo nodeFinish)
        }
    }

    /**
     * Evaluates a transition condition against the current output.
     *
     * @param condition The condition to evaluate
     * @param output The current agent output
     * @return true if the condition is satisfied
     */
    private fun evaluateCondition(condition: TransitionCondition, output: String): Boolean {
        // For now, we support simple string comparisons
        // TODO: Implement variable resolution (e.g., agent.verify.output.success)
        val actualValue = output
        val expectedValue = condition.value

        return when (condition.operation) {
            ConditionOperationKind.EQUALS -> compareEquals(actualValue, expectedValue)
            ConditionOperationKind.NOT_EQUALS -> !compareEquals(actualValue, expectedValue)
            ConditionOperationKind.MORE -> compareMore(actualValue, expectedValue)
            ConditionOperationKind.LESS -> compareLess(actualValue, expectedValue)
            ConditionOperationKind.MORE_OR_EQUAL -> compareMore(actualValue, expectedValue) || compareEquals(actualValue, expectedValue)
            ConditionOperationKind.LESS_OR_EQUAL -> compareLess(actualValue, expectedValue) || compareEquals(actualValue, expectedValue)
            ConditionOperationKind.NOT -> !actualValue.toBoolean()
            ConditionOperationKind.AND -> actualValue.toBoolean() && expectedValue.booleanOrNull == true
            ConditionOperationKind.OR -> actualValue.toBoolean() || expectedValue.booleanOrNull == true
        }
    }

    private fun compareEquals(actual: String, expected: JsonPrimitive): Boolean {
        return when {
            expected.isString -> actual == expected.content
            expected.booleanOrNull != null -> actual.toBooleanStrictOrNull() == expected.booleanOrNull
            expected.doubleOrNull != null -> actual.toDoubleOrNull() == expected.doubleOrNull
            else -> actual == expected.content
        }
    }

    private fun compareMore(actual: String, expected: JsonPrimitive): Boolean {
        val actualNum = actual.toDoubleOrNull() ?: return false
        val expectedNum = expected.doubleOrNull ?: return false
        return actualNum > expectedNum
    }

    private fun compareLess(actual: String, expected: JsonPrimitive): Boolean {
        val actualNum = actual.toDoubleOrNull() ?: return false
        val expectedNum = expected.doubleOrNull ?: return false
        return actualNum < expectedNum
    }

    //endregion Private Methods
}
