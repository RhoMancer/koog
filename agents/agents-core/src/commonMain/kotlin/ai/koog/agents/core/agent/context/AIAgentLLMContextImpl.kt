@file:OptIn(DetachedPromptExecutorAPI::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.RWLock
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock

internal class AIAgentLLMContextImpl(
    tools: List<ToolDescriptor>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    prompt: Prompt,
    model: LLModel,
    promptExecutor: PromptExecutor,
    environment: AIAgentEnvironment,
    config: AIAgentConfig,
    clock: Clock
) : AIAgentLLMContext(
    tools,
    toolRegistry,
    prompt,
    model,
    promptExecutor,
    environment,
    config,
    clock
) {
    public override suspend fun withPrompt(block: Prompt.() -> Prompt): AIAgentLLMContext = rwLock.withReadLock {
        this.prompt = prompt.block()
        this
    }

    public override suspend fun copy(
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry,
        prompt: Prompt,
        model: LLModel,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext = rwLock.withReadLock {
        AIAgentLLMContext(
            tools = tools,
            toolRegistry = toolRegistry,
            prompt = prompt,
            model = model,
            promptExecutor = promptExecutor,
            environment = environment,
            config = config,
            clock = clock
        )
    }

    private val rwLock = RWLock()

    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T =
        rwLock.withWriteLock {
            val session =
                AIAgentLLMWriteSession(environment, promptExecutor, tools, toolRegistry, prompt, model, config, clock)

            session.use {
                val result = it.block()

                // update tools and prompt after session execution
                this.prompt = it.prompt
                this.tools = it.tools
                this.model = it.model

                result
            }
        }

    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T = rwLock.withReadLock {
        val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, config)

        session.use { block(it) }
    }

    public override fun copy(
        tools: List<ToolDescriptor>,
        prompt: Prompt,
        model: LLModel,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock,
    ): AIAgentLLMContext {
        return AIAgentLLMContext(tools, toolRegistry, prompt, model, promptExecutor, environment, config, clock)
    }
}
