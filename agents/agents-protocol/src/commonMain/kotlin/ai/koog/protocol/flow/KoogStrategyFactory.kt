package ai.koog.protocol.flow

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformation
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.transition.FlowTransition
import ai.koog.protocol.transition.FlowTransitionCondition
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
        toolRegistry: ToolRegistry,
        defaultModel: String?
    ): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {
        // No agents - create an empty strategy
        if (agents.isEmpty()) {
            return createEmptyStrategy(id)
        }

        // No transitions - chain agents sequentially
        if (transitions.isEmpty()) {
            return createSequentialStrategy(id, agents, toolRegistry, defaultModel)
        }

        return strategy(id) {
            // Nodes
            val collectedNodes = agents.map { agent ->
                val node by convertFlowAgentToKoogNode(agent, toolRegistry, defaultModel)
                node
            }

            val firstAgentName = FlowUtil.getFirstAgent(agents, transitions).name
            val firstNode = collectedNodes.find { it.name == firstAgentName }
                ?: error("First agent not found: $firstAgentName")

            // Edges
            // Connect the koog system start node to the first flow node
            edge(nodeStart forwardTo firstNode)

            // Process the rest of transitions and create edges
            transitions.forEach { transition -> transitionToEdge(collectedNodes, transition) }

            // Connect all agents without outgoing transitions to finish
            val agentsWithOutgoingTransitions = transitions.map { it.from }.toSet()
            val nodesWithoutFinish = collectedNodes.filter { node ->
                node.name !in agentsWithOutgoingTransitions
            }

            nodesWithoutFinish.forEach { nodeWithoutFinish ->
                createEdgeToFinish(nodeWithoutFinish, null)
            }
        }
    }

    //region Private Methods

    //region Edges

    private fun AIAgentSubgraphBuilderBase<FlowAgentInput, FlowAgentInput>.transitionToEdge(
        collectedNodes: List<AIAgentNodeBase<FlowAgentInput, FlowAgentInput>>,
        transition: FlowTransition,
    ) {
        val fromNode = collectedNodes.find { it.name == transition.from }
            ?: error("Unable to find 'from' node for transition '${transition.transitionString}': ${transition.from}")

        if (transition.to == FINISH_NODE_PREFIX) {
            createEdgeToFinish(fromNode, transition.condition)
        } else {
            val toNode = collectedNodes.find { it.name == transition.to }
                ?: error("Unable to find 'to' node for transition '${transition.transitionString}': ${transition.to}")

            createEdge(fromNode, toNode, transition.condition)
        }
    }

    private fun AIAgentSubgraphBuilderBase<FlowAgentInput, FlowAgentInput>.createEdge(
        fromNode: AIAgentNodeBase<FlowAgentInput, FlowAgentInput>,
        toNode: AIAgentNodeBase<FlowAgentInput, FlowAgentInput>,
        condition: FlowTransitionCondition?
    ) {
        if (condition == null) {
            edge(fromNode forwardTo toNode)
            return
        }

        edge(
            fromNode forwardTo toNode onCondition { output -> evaluateCondition(condition, output) }
        )
    }

    /**
     * Creates an edge from a node to the finish node, optionally with a condition.
     */
    private fun AIAgentSubgraphBuilderBase<FlowAgentInput, FlowAgentInput>.createEdgeToFinish(
        fromNode: AIAgentNodeBase<FlowAgentInput, FlowAgentInput>,
        condition: FlowTransitionCondition?
    ) = createEdge(fromNode, nodeFinish, condition)

    //endregion Edges

    //region Nodes

    /**
     * Converts a flow agent into Koog node delegate for a given flow agent type.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.convertFlowAgentToKoogNode(
        agent: FlowAgent,
        toolRegistry: ToolRegistry,
        defaultModel: String?
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return when (agent) {
            is FlowTaskAgent -> nodeTask(agent, toolRegistry, defaultModel)
            is FlowVerifyAgent -> nodeVerify(agent, toolRegistry, defaultModel)
            is FlowInputTransformAgent -> nodeTransform(agent)
            else -> error("Parallel agent type is not yet supported")
        }
    }

    private fun ToolRegistry.defineToolSelectionStrategy(toolNames: List<String>): ToolSelectionStrategy {
        if (toolNames.isEmpty()) {
            return ToolSelectionStrategy.ALL
        }

        val selectedTools = this.tools.filter { tool ->
            tool.name in toolNames
        }

        return ToolSelectionStrategy.Tools(selectedTools.map { it.descriptor })
    }

    //region Task

    /**
     * Creates a task node that performs LLM request with the agent's configuration.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTask(
        agent: FlowTaskAgent,
        toolRegistry: ToolRegistry,
        defaultModel: String?,
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> =
        subgraphWithTask<FlowAgentInput, FlowAgentInput>(
            name = agent.name,
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = KoogPromptExecutorFactory.resolveModel(agent.model, defaultModel),
        ) { input ->
            agent.parameters.task
        }

    //endregion Task

    //region Verify

    /**
     * Creates a node that checks/validates using LLM with structured verification output.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeVerify(
        agent: FlowVerifyAgent,
        toolRegistry: ToolRegistry,
        defaultModel: String?,
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        val verifySubgraph by subgraphWithVerification<FlowAgentInput>(
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = KoogPromptExecutorFactory.resolveModel(agent.model, defaultModel),
        ) { input ->
            agent.parameters.task
        }

        return subgraph(name = agent.name) {
            val transformResult by node<CriticResult<FlowAgentInput>, FlowAgentInput> { result ->
                FlowAgentInput.InputCritiqueResult(
                    success = result.successful,
                    feedback = result.feedback,
                    input = result.input
                )
            }

            nodeStart then verifySubgraph then transformResult then nodeFinish
        }
    }

    //endregion Verify

    //region Transformation

    /**
     * Creates a transform node that applies transformations without LLM.
     * The transformation converts input from one FlowAgentInput type to another based on defined rules.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTransform(
        agent: FlowInputTransformAgent
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return subgraph(name = agent.name) {
            val transform by node<FlowAgentInput, FlowAgentInput> { runtimeInput ->
                transformFlowAgentInput(runtimeInput, agent.parameters.transformations)
            }

            nodeStart then transform then nodeFinish
        }
    }

    /**
     * Transforms a FlowAgentInput based on the provided transformation configuration.
     *
     * @param input The input to transform
     * @param transformations The list of transformations to apply.
     *
     * @return Transformed input, or original input if no matching transformation found
     */
    private fun transformFlowAgentInput(
        input: FlowAgentInput,
        transformations: List<FlowInputTransformation>
    ): FlowAgentInput {
        if (transformations.isEmpty()) {
            return input
        }

        // Find matching transformation based on input type
        val matchingTransformation = transformations.find { transformation ->
            transformation.value::class == input::class
        } ?: return input

        // Convert input to a target type
        return FlowAgentInputCastUtil.convertToTargetType(input, matchingTransformation.to)
    }

    //endregion Transformation

    //endregion Nodes

    //region Strategy

    /**
     * Creates an empty strategy that immediately finishes.
     */
    private fun createEmptyStrategy(id: String): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {
        return strategy(id) {
            edge(nodeStart forwardTo nodeFinish)
        }
    }

    /**
     * Creates a sequential strategy that chains all agents one after another.
     * Agents are connected in order: start → agent1 → agent2 → ... → finish
     */
    private fun createSequentialStrategy(
        id: String,
        agents: List<FlowAgent>,
        toolRegistry: ToolRegistry,
        defaultModel: String?
    ): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {
        return strategy(id) {
            val collectedNodes = agents.map { agent ->
                val node by convertFlowAgentToKoogNode(agent, toolRegistry, defaultModel)
                node
            }

            // Chain: start → first agent
            edge(nodeStart forwardTo collectedNodes.first())

            // Chain agents sequentially: agent[i] → agent[i+1]
            collectedNodes.zipWithNext { current, next ->
                edge(current forwardTo next)
            }

            // Chain: last agent → finish
            edge(collectedNodes.last() forwardTo nodeFinish)
        }
    }

    //endregion Strategy

    /**
     * Evaluates a transition condition against the current output.
     *
     * @param condition The condition to evaluate
     * @param output The current agent output
     * @return true if the condition is satisfied
     */
    private fun evaluateCondition(condition: FlowTransitionCondition, output: FlowAgentInput): Boolean {
        // Extract value from output based on the variable name
        val outputValue: Any = when (output) {
            is FlowAgentInput.InputBoolean -> output.data
            is FlowAgentInput.InputInt -> output.data
            is FlowAgentInput.InputDouble -> output.data
            is FlowAgentInput.InputString -> output.data
            is FlowAgentInput.InputCritiqueResult -> {
                when (condition.variable) {
                    "success" -> output.success
                    "feedback" -> output.feedback
                    else -> output
                }
            }
            is FlowAgentInput.InputArrayBooleans,
            is FlowAgentInput.InputArrayDouble,
            is FlowAgentInput.InputArrayInt,
            is FlowAgentInput.InputArrayStrings -> output
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
