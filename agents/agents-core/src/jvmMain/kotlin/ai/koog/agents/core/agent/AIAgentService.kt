@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.runOnMainDispatcher
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock
import java.util.concurrent.ExecutorService

public actual interface AIAgentService<Input, Output, TAgent : AIAgent<Input, Output>> {
    public actual val promptExecutor: PromptExecutor
    public actual val agentConfig: AIAgentConfig
    public actual val toolRegistry: ToolRegistry
    public actual suspend fun createAgent(id: String?, clock: Clock): TAgent
    public actual suspend fun createAgentAndRun(agentInput: Input, id: String?, clock: Clock): Output
    public actual suspend fun removeAgent(agent: TAgent): Boolean
    public actual suspend fun removeAgentWithId(id: String): Boolean
    public actual suspend fun agentById(id: String): TAgent?
    public actual suspend fun listAllAgents(): List<TAgent>
    public actual suspend fun listActiveAgents(): List<TAgent>
    public actual suspend fun listInactiveAgents(): List<TAgent>
    public actual suspend fun listFinishedAgents(): List<TAgent>
    public actual suspend fun closeAll()

    @JavaAPI
    public fun createAgent(id: String?, clock: Clock, executorService: ExecutorService? = null): TAgent =
        agentConfig.runOnMainDispatcher(executorService) { createAgent(id, clock) }

    @JavaAPI
    public fun createAgentAndRun(
        agentInput: Input,
        id: String?,
        clock: Clock,
        executorService: ExecutorService? = null
    ): Output = createAgent(id, clock, executorService).run(agentInput, executorService)

    @JavaAPI
    public fun removeAgent(
        agent: TAgent,
        executorService: ExecutorService? = null
    ): Boolean = agentConfig.runOnMainDispatcher(executorService) {
        removeAgent(agent)
    }

    @JavaAPI
    public fun removeAgentWithId(
        id: String,
        executorService: ExecutorService? = null
    ): Boolean = agentConfig.runOnMainDispatcher(executorService) {
        removeAgentWithId(id)
    }

    @JavaAPI
    public fun agentById(
        id: String,
        executorService: ExecutorService? = null
    ): TAgent? = agentConfig.runOnMainDispatcher(executorService) {
        agentById(id)
    }

    @JavaAPI
    public fun listAllAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnMainDispatcher(executorService) {
        listAllAgents()
    }

    @JavaAPI
    public fun listActiveAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnMainDispatcher(executorService) {
        listActiveAgents()
    }

    @JavaAPI
    public fun listInactiveAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnMainDispatcher(executorService) {
        listInactiveAgents()
    }

    @JavaAPI
    public fun listFinishedAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnMainDispatcher(executorService) {
        listFinishedAgents()
    }

    @JavaAPI
    public fun closeAll(
        executorService: ExecutorService? = null
    ) {
        agentConfig.runOnMainDispatcher(executorService) {
            closeAll()
        }
    }


    public actual companion object {
        @JvmStatic
        public actual fun builder(): AIAgentServiceBuilder = AIAgentServiceBuilder()

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual inline fun <reified Input, reified Output> fromAgent(
            agent: GraphAIAgent<Input, Output>
        ): AIAgentService<Input, Output, GraphAIAgent<Input, Output>> = AIAgentServiceHelper.fromAgent(agent)

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual fun <Input, Output> fromAgent(
            agent: FunctionalAIAgent<Input, Output>
        ): AIAgentService<Input, Output, FunctionalAIAgent<Input, Output>> = AIAgentServiceHelper.fromAgent(agent)

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            noinline installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): GraphAIAgentService<Input, Output> =
            AIAgentServiceHelper.invoke(promptExecutor, agentConfig, strategy, toolRegistry, installFeatures)

        public actual operator fun invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            strategy: AIAgentGraphStrategy<String, String>,
            toolRegistry: ToolRegistry,
            systemPrompt: String,
            temperature: Double,
            numberOfChoices: Int,
            maxIterations: Int,
            installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): GraphAIAgentService<String, String> = AIAgentServiceHelper.invoke(
            promptExecutor,
            llmModel,
            strategy,
            toolRegistry,
            systemPrompt,
            temperature,
            numberOfChoices,
            maxIterations,
            installFeatures
        )

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit
        ): FunctionalAIAgentService<Input, Output> =
            AIAgentServiceHelper.invoke(promptExecutor, agentConfig, strategy, toolRegistry, installFeatures)
    }
}
