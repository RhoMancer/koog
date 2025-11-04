@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

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

public actual open class AIAgentBuilder internal actual constructor() {
    private val delegate = AIAgentBuilderImpl()

    @property:PublishedApi
    internal actual open var promptExecutor: PromptExecutor? = delegate.promptExecutor

    @property:PublishedApi
    internal actual open var toolRegistry: ToolRegistry = delegate.toolRegistry

    @property:PublishedApi
    internal actual open var id: String? = delegate.id

    @property:PublishedApi
    internal actual open var prompt: Prompt = delegate.prompt

    @property:PublishedApi
    internal actual open var llmModel: LLModel? = delegate.llmModel

    @property:PublishedApi
    internal actual open var temperature: Double = delegate.temperature

    @property:PublishedApi
    internal actual open var numberOfChoices: Int = delegate.numberOfChoices

    @property:PublishedApi
    internal actual open var missingToolsConversionStrategy: MissingToolsConversionStrategy =
        delegate.missingToolsConversionStrategy

    @property:PublishedApi
    internal actual open var maxIterations: Int = delegate.maxIterations

    @property:PublishedApi
    internal actual open var clock: Clock = delegate.clock

    public actual open fun promptExecutor(promptExecutor: PromptExecutor): AIAgentBuilder =
        delegate.promptExecutor(promptExecutor)

    public actual open fun llmModel(model: LLModel): AIAgentBuilder = delegate.llmModel(model)

    public actual open fun toolRegistry(toolRegistry: ToolRegistry): AIAgentBuilder =
        delegate.toolRegistry(toolRegistry)

    public actual open fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output> = delegate.graphStrategy(strategy)

    public actual open fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output> = delegate.functionalStrategy(strategy)

    public actual open fun id(id: String?): AIAgentBuilder = delegate.id(id)

    public actual open fun systemPrompt(systemPrompt: String): AIAgentBuilder = delegate.systemPrompt(systemPrompt)

    public actual open fun prompt(prompt: Prompt): AIAgentBuilder = delegate.prompt(prompt)

    public actual open fun temperature(temperature: Double): AIAgentBuilder = delegate.temperature(temperature)

    public actual open fun numberOfChoices(numberOfChoices: Int): AIAgentBuilder =
        delegate.numberOfChoices(numberOfChoices)

    public actual open fun maxIterations(maxIterations: Int): AIAgentBuilder = delegate.maxIterations(maxIterations)

    public actual open fun agentConfig(config: AIAgentConfig): AIAgentBuilder = delegate.agentConfig(config)

    public actual open fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String> = delegate.install(feature, configure)

    public actual open fun build(): AIAgent<String, String> = delegate.build()
}
