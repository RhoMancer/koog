package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.context.element.StrategyInfoContextElement
import ai.koog.agents.core.agent.context.element.getAgentRunInfoElement
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A strategy for implementing AI agent behavior that operates in a loop-based manner.
 *
 * The [AIAgentFunctionalStrategy] class allows for the definition of a custom looping logic
 * that processes input and produces output by using an [ai.koog.agents.core.agent.context.AIAgentFunctionalContext]. This strategy
 * can be used to define iterative decision-making or execution processes for AI agents.
 *
 * @param TInput The type of input data processed by the strategy.
 * @param TOutput The type of output data produced by the strategy.
 * @property name The name of the strategy, providing a way to identify and describe the strategy.
 * @property func A suspending function representing the loop logic for the strategy. It accepts
 * input data of type [TInput] and an [ai.koog.agents.core.agent.context.AIAgentFunctionalContext] to execute the loop and produce the output.
 */
public class AIAgentFunctionalStrategy<TInput, TOutput>(
    override val name: String,
    public val func: suspend AIAgentFunctionalContext.(TInput) -> TOutput
) : AIAgentStrategy<TInput, TOutput, AIAgentFunctionalContext> {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: TInput
    ): TOutput {
        val id = Uuid.random().toString()
        val parentId = getAgentRunInfoElement()?.id

        return withContext(StrategyInfoContextElement(id, parentId, this.name)) {
            context.pipeline.onStrategyStarting(id, parentId, this@AIAgentFunctionalStrategy, context)
            val result = context.func(input)
            context.pipeline.onStrategyCompleted(id, parentId, this@AIAgentFunctionalStrategy, context, result, typeOf<Any?>())

            logger.debug { "Finished executing strategy (name: $name) with result: $result" }
            result
        }
    }
}

/**
 * Creates an [AIAgentFunctionalStrategy] with the specified loop logic and name.
 *
 * This function allows the definition of custom looping strategies for AI agents, where
 * the provided logic defines how the agent processes input and produces output within
 * its execution context.
 *
 * @param name The name of the strategy, used to identify and describe the strategy. Defaults to "funStrategy".
 * @param func A suspending function representing the loop logic of the strategy. It accepts an input of type [Input]
 * and is executed within an [AIAgentFunctionalContext], producing an output of type [Output].
 * @return An instance of [AIAgentFunctionalStrategy] configured with the given loop logic and name.
 */
public fun <Input, Output> functionalStrategy(
    name: String = "funStrategy",
    func: suspend AIAgentFunctionalContext.(input: Input) -> Output
): AIAgentFunctionalStrategy<Input, Output> =
    AIAgentFunctionalStrategy(name, func)
