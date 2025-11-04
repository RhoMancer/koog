package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentState.NotStarted
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.element.AgentRunInfoContextElement
import ai.koog.agents.core.feature.AIAgentFeature
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal abstract class StatefulSingleUseAIAgentImpl<Input, Output, TContext : AIAgentContext>(
    logger: KLogger,
    id: String? = null,
) : StatefulSingleUseAIAgent<Input, Output, TContext>(logger, id) {
    protected override val logger: KLogger = logger

    /**
     * A mutex used to synchronize access to the state of the agent. Ensures that only one coroutine
     * can modify or read the shared state of the agent at a time, preventing data races and ensuring
     * thread-safe operations.
     */
    private val agentStateMutex: Mutex = Mutex()

    private var state: AIAgentState<Output> = NotStarted()

    override suspend fun getState(): AIAgentState<Output> = agentStateMutex.withLock { state.copy() }

    override val id: String by lazy { id ?: Uuid.random().toString() }

    override suspend fun run(agentInput: Input): Output {
        agentStateMutex.withLock {
            if (state !is NotStarted) {
                throw IllegalStateException(
                    "Agent was already started. Please use AIAgentService.createAgentAndRun(agentInput) to run an agent multiple times."
                )
            }
            state = AIAgentState.Starting()
        }

        val runId = Uuid.random().toString()

        pipeline.prepareFeatures()

        return withContext(
            AgentRunInfoContextElement(
                agentId = this@StatefulSingleUseAIAgentImpl.id,
                runId = runId,
                agentConfig = agentConfig,
                strategyName = strategy.name
            )
        ) {
            val context = prepareContext(agentInput, runId)

            agentStateMutex.withLock {
                state = AIAgentState.Running(context)
            }

            logger.debug {
                formatLog(
                    agentId = this@StatefulSingleUseAIAgentImpl.id,
                    runId = runId,
                    message = "Starting agent execution"
                )
            }

            pipeline.onAgentStarting<Input, Output>(
                runId = runId,
                agent = this@StatefulSingleUseAIAgentImpl,
                context = context
            )

            val result = try {
                strategy.execute(context = context, input = agentInput)
            } catch (e: Throwable) {
                logger.error(e) { "Execution exception reported by server!" }
                pipeline.onAgentExecutionFailed(
                    agentId = this@StatefulSingleUseAIAgentImpl.id,
                    runId = runId,
                    throwable = e
                )
                agentStateMutex.withLock { state = AIAgentState.Failed(e) }
                throw e
            }

            logger.debug {
                formatLog(
                    agentId = this@StatefulSingleUseAIAgentImpl.id,
                    runId = runId,
                    message = "Finished agent execution"
                )
            }
            pipeline.onAgentCompleted(
                agentId = this@StatefulSingleUseAIAgentImpl.id,
                runId = runId,
                result = result
            )

            agentStateMutex.withLock {
                state = if (result != null) {
                    AIAgentState.Finished(result)
                } else {
                    AIAgentState.Failed(Exception("result is null"))
                }
            }

            return@withContext result ?: error("result is null")
        }
    }

    override suspend fun close() {
        pipeline.onAgentClosing(agentId = this@StatefulSingleUseAIAgentImpl.id)
        pipeline.closeFeaturesStreamProviders()
    }

    public override fun <TFeature : Any> feature(
        featureClass: KClass<TFeature>,
        feature: AIAgentFeature<*, TFeature>
    ): TFeature? = pipeline.feature(featureClass, feature)

    /**
     * Formats a log message with the specified agent ID, run ID, and message content.
     *
     * @param agentId The unique identifier of the agent generating the log.
     * @param runId The unique identifier of the specific run or task associated with the log.
     * @param message The content of the log message to be formatted.
     * @return A formatted log string containing the agent ID, run ID, and the provided message.
     */
    protected override fun formatLog(agentId: String, runId: String, message: String): String =
        "[agent id: $agentId, run id: $runId] $message"
}
