package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf

/**
 * Implementation class for building and configuring AI agents with customizable components and settings.
 *
 * The `AIAgentBuilderImpl` class provides functionality to configure multiple aspects of an AI agent,
 * such as tool registry, large language model, execution strategies, and system-level prompts.
 * The class offers a fluent API enabling efficient chaining of configuration methods, ensuring flexibility
 * in constructing AI agents tailored for specific requirements.
 *
 * Fields:
 * - `promptExecutor`: Manages the execution of prompts and interactions with language models.
 * - `toolRegistry`: Stores tools available for use by the agent.
 * - `id`: Optional identifier for the agent being built.
 * - `prompt`: Defines the input prompt template to guide model behavior.
 * - `llmModel`: Represents the Large Language Model (LLM) utilized by the agent.
 * - `temperature`: Configures the randomness of language model outputs.
 * - `numberOfChoices`: Specifies the number of response options generated.
 * - `missingToolsConversionStrategy`: Strategy to handle cases where required tools are unavailable.
 * - `maxIterations`: Limits the number of iterations an agent can utilize.
 * - `clock`: Tracks and manages time-related operations within the agent.
 */
internal class AIAgentBuilderImpl internal constructor() : AIAgentBuilder() {
    @property:PublishedApi
    internal override var promptExecutor: PromptExecutor? = null

    @property:PublishedApi
    internal override var toolRegistry: ToolRegistry = ToolRegistry.EMPTY

    @property:PublishedApi
    internal override var id: String? = null

    @property:PublishedApi
    internal override var prompt: Prompt = Prompt.Empty

    @property:PublishedApi
    internal override var llmModel: LLModel? = null

    @property:PublishedApi
    internal override var temperature: Double = 1.0

    @property:PublishedApi
    internal override var numberOfChoices: Int = 1

    @property:PublishedApi
    internal override var missingToolsConversionStrategy: MissingToolsConversionStrategy =
        MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)

    @property:PublishedApi
    internal override var maxIterations: Int = 50

    @property:PublishedApi
    internal override var clock: Clock = Clock.System

    public override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentBuilder = apply {
        AIAgentConfig
        this.promptExecutor = promptExecutor
    }

    public override fun llmModel(model: LLModel): AIAgentBuilder = apply {
        this.llmModel = model
    }

    public override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentBuilder = apply {
        this.toolRegistry = toolRegistry
    }

    public override fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output> = GraphAgentBuilder(
        strategy = strategy,
        inputType = strategy.inputType,
        outputType = strategy.outputType,
        id = this.id,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        missingToolsConversionStrategy = this.missingToolsConversionStrategy,
        clock = this.clock
    )

    public override fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output> = FunctionalAgentBuilder(
        strategy = strategy,
        id = this.id,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        missingToolsConversionStrategy = this.missingToolsConversionStrategy,
        clock = this.clock
    )

    public override fun id(id: String?): AIAgentBuilder = apply {
        this.id = id
    }

    public override fun systemPrompt(systemPrompt: String): AIAgentBuilder = apply {
        this.prompt = ai.koog.prompt.dsl.prompt(id = "agent") { system(systemPrompt) }
    }

    public override fun prompt(prompt: Prompt): AIAgentBuilder = apply {
        this.prompt = prompt
    }

    public override fun temperature(temperature: Double): AIAgentBuilder = apply {
        this.temperature = temperature
    }

    public override fun numberOfChoices(numberOfChoices: Int): AIAgentBuilder = apply {
        this.numberOfChoices = numberOfChoices
    }

    public override fun maxIterations(maxIterations: Int): AIAgentBuilder = apply {
        this.maxIterations = maxIterations
    }

    public override fun agentConfig(config: AIAgentConfig): AIAgentBuilder = apply {
        this.prompt = config.prompt
        this.llmModel = config.model
        this.maxIterations = config.maxAgentIterations
        this.missingToolsConversionStrategy = config.missingToolsConversionStrategy
    }

    public override fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String> = GraphAgentBuilder(
        strategy = singleRunStrategy(),
        inputType = typeOf<String>(),
        outputType = typeOf<String>(),
        id = this.id,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        missingToolsConversionStrategy = this.missingToolsConversionStrategy,
        clock = this.clock,
        featureInstallers = mutableListOf({
            install(feature) {
                configure.configure(this)
            }
        })
    )

    public override fun build(): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = requireNotNull(promptExecutor) { "promptExecutor must be set" },
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            id = id,
            agentConfig = AIAgentConfig(
                prompt = prompt ?: Prompt.Empty,
                model = requireNotNull(llmModel) { "llmModel must be set" },
                maxAgentIterations = maxIterations,
            ),
            clock = clock
        )
    }
}
