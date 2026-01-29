package ai.koog.protocol.flow

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition
import kotlin.reflect.typeOf

/**
 *
 */
public class KoogFlow(
    override val id: String,
    override val agents: List<FlowAgent>,
    override val tools: List<FlowTool>,
    override val transitions: List<FlowTransition>,
    public val defaultModel: String? = null,
    private val promptExecutor: PromptExecutor? = null
) : Flow {

    /**
     *
     */
    override suspend fun run(): FlowAgentInput {
        val agent = buildAgent()
        val firstFlowAgent = FlowUtil.getFirstAgent(agents, transitions)
        val input = firstFlowAgent?.input ?: FlowAgentInput.String("")

        return agent.run(input)
    }

    //region Private Methods

    private fun buildPromptExecutor(agents: List<FlowAgent>): PromptExecutor {
        // TODO: Read from all agents and collect a list of prompt executor clients
        val promptExecutor = MultiLLMPromptExecutor(

        )

        return promptExecutor
    }

    private fun buildToolRegistry(): ToolRegistry {
        // TODO: Update later parsing tools from config
        return ToolRegistry.EMPTY
    }

    private fun buildStrategy(
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>
    ): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> =
        KoogStrategyFactory.buildStrategy(
            id = "koog-flow-strategy-$id",
            agents = agents,
            transitions = transitions,
            tools = tools,
            defaultModel = defaultModel
        )

    private fun buildModel(): LLModel {
        val modelString = defaultModel ?: "openai/gpt-4o"
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
            else -> LLMProvider.OpenAI // default to OpenAI
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

    private fun buildAgent(): GraphAIAgent<FlowAgentInput, FlowAgentInput> {
        val promptExecutor = promptExecutor ?: buildPromptExecutor(agents)
        val toolRegistry = buildToolRegistry()
        val strategy = buildStrategy(agents, transitions)
        val model = buildModel()

        val firstAgent = FlowUtil.getFirstAgent(agents, transitions)
        val agentPrompt = prompt(id = "koog-flow-${id}") {
            firstAgent?.prompt?.system?.let { systemPrompt ->
                system(systemPrompt)
            }
        }

        val agentConfig = AIAgentConfig(
            prompt = agentPrompt,
            model = model,
            maxAgentIterations = firstAgent?.config?.maxIterations ?: 10
        )

        return GraphAIAgent(
            id = "koog-flow-agent-${id}",
            inputType = typeOf<FlowAgentInput>(),
            outputType = typeOf<FlowAgentInput>(),
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry
        )
    }

    //endregion Private Methods
}
