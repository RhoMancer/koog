@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.context

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.runOnMainDispatcher
import ai.koog.agents.core.utils.submitToMainDispatcher
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock
import java.util.function.Function

public actual open class AIAgentLLMContext @JvmOverloads actual constructor(
    public actual var tools: List<ToolDescriptor>,
    public actual val toolRegistry: ToolRegistry,
    public actual var prompt: Prompt,
    public actual var model: LLModel,
    @property:DetachedPromptExecutorAPI
    public actual val promptExecutor: PromptExecutor,
    protected actual val environment: AIAgentEnvironment,
    protected actual val config: AIAgentConfig,
    protected actual val clock: Clock
) {
    @OptIn(DetachedPromptExecutorAPI::class)
    private val delegate = AIAgentLLMContextImpl(
        tools,
        toolRegistry,
        prompt,
        model,
        promptExecutor,
        environment,
        config,
        clock
    )

    public actual open suspend fun withPrompt(block: Prompt.() -> Prompt): AIAgentLLMContext =
        delegate.withPrompt(block)

    @JvmOverloads
    public actual open suspend fun copy(
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry,
        prompt: Prompt,
        model: LLModel,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext = delegate.copy(tools, toolRegistry, prompt, model, promptExecutor, environment, config, clock)

    public actual open suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T =
        delegate.writeSession(block)

    @JavaAPI
    public fun <T> writeSession(block: Function<AIAgentLLMWriteSession, T>): T = config.runOnMainDispatcher {
        writeSession {
            config.submitToMainDispatcher {
                block.apply(this)
            }
        }
    }

    @JavaAPI
    public fun <T> readSession(block: Function<AIAgentLLMReadSession, T>): T = config.runOnMainDispatcher {
        readSession {
            config.submitToMainDispatcher {
                block.apply(this)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    public actual open suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T =
        delegate.readSession(block)

    @JvmOverloads
    public actual open fun copy(
        tools: List<ToolDescriptor>,
        prompt: Prompt,
        model: LLModel,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext = delegate.copy(tools, prompt, model, promptExecutor, environment, config, clock)
}
