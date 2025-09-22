package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a basic interface for AI agent.
 */
public abstract class AIAgent<TInput, TOutput>(
    id: String? = null,
    public val clock: Clock = Clock.System,
    private val installFeatures: FeatureContext.() -> Unit = {},
) : AIAgentBase<TInput, TOutput> {

    @OptIn(ExperimentalUuidApi::class)
    override val id: String by lazy { id ?: Uuid.random().toString() }

    /**
     * The configuration for the AI agent.
     */
    abstract override val agentConfig: AIAgentConfig

    public abstract val promptExecutor: PromptExecutor

    public abstract val toolRegistry: ToolRegistry

    public abstract val strategy: AIAgentStrategy<TInput, TOutput, *>

    protected abstract val pipeline: AIAgentPipeline

    protected abstract val environment: AIAgentEnvironment

    protected fun formatLog(runId: String, message: String): String =
        "[agent id: $id, run id: $runId] $message"
}
