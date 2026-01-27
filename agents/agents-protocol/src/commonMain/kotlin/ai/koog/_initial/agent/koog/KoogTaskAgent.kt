package ai.koog._initial.agent.koog

import ai.koog._initial.agent.FlowAgentConfig
import ai.koog._initial.agent.FlowAgentKind
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog._initial.model.FlowAgentInput
import ai.koog._initial.model.FlowAgentPrompt

private const val DEFAULT_MAX_ITERATIONS = 10

/**
 * Koog task agent that executes tasks using AIAgent with singleRunStrategy.
 * This agent is created when type is "task" in the flow configuration.
 */
public class KoogTaskAgent(
    name: String,
    model: String? = null,
    prompt: FlowAgentPrompt? = null,
    input: FlowAgentInput,
    config: FlowAgentConfig
) : KoogFlowAgent(
    name = name,
    type = FlowAgentKind.TASK,
    model = model,
    prompt = prompt,
    input = input,
    config = config
) {
    override suspend fun execute(): String {
        return PromptExecutorFactory.createExecutor(model).use { executor ->
            val llmModel = PromptExecutorFactory.resolveModel(model)

            val agent = AIAgent(
                promptExecutor = executor,
                llmModel = llmModel,
                systemPrompt = prompt?.system ?: "",
                maxIterations = config.maxIterations ?: DEFAULT_MAX_ITERATIONS,
                strategy = singleRunStrategy(),
                toolRegistry = ToolRegistry.EMPTY
            )

            println("Running Koog 'task' agent with input: $input")

            agent.run(input.task ?: "")
        }
    }
}
