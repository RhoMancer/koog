package ai.koog.flow.koog

import ai.koog.agent.FlowAgent
import ai.koog.agent.koog.PromptExecutorFactory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.flow.Flow
import ai.koog.model.Transition
import ai.koog.tools.FlowTool

/**
 *
 */
public class KoogFlow(
    override val id: String,
    override val agents: List<FlowAgent>,
    override val tools: List<FlowTool>,
    override val transitions: List<Transition>,
    private val defaultModel: String? = null
) : Flow {

    /**
     *
     */
    private fun buildStrategy(): AIAgentGraphStrategy<String, String> {
        return KoogFlowStrategyBuilder().build(
            id = id,
            agents = agents,
            transitions = transitions,
            tools = tools,
            defaultModel = defaultModel
        )
    }

    /**
     * Runs the flow with the given input.
     *
     * Creates a PromptExecutor internally, builds the strategy, and executes
     * the entire flow as a single AIAgent.
     *
     * @param input Initial input for the flow (optional, defaults to empty string)
     * @return The output from the final agent in the flow
     */
    override suspend fun run(input: String): String {
        if (agents.isEmpty()) return ""

        return PromptExecutorFactory.createExecutor(defaultModel).use { executor ->
            val llmModel = PromptExecutorFactory.resolveModel(defaultModel)

            val agent = AIAgent(
                promptExecutor = executor,
                llmModel = llmModel,
                strategy = buildStrategy(),
                toolRegistry = ToolRegistry.EMPTY // TODO: Build tool registry from flow tools
            ) {
                handleEvents {
                    onNodeExecutionStarting {
                        if (it.node.name != START_NODE_PREFIX && it.node.name != FINISH_NODE_PREFIX) {
                            println("Starting execution of agent: ${it.node.name}")
                        }
                    }

                    onNodeExecutionCompleted {
                        if (it.node.name != START_NODE_PREFIX && it.node.name != FINISH_NODE_PREFIX) {
                            println("Completed execution of agent: ${it.node.name}. Result: ${it.output}")
                        }
                    }

                    onAgentCompleted {
                        println("Completed execution of flow: ${it.agentId}")
                    }
                }
            }

            agent.run(input)
        }
    }
}
