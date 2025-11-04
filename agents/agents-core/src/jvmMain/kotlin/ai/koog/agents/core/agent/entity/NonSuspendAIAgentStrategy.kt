@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentContext

@JavaAPI
public interface NonSuspendAIAgentStrategy<TInput, TOutput, TContext : AIAgentContext> :
    AIAgentStrategy<TInput, TOutput, TContext> {

    /**
     * Executes the AI agent's strategy asynchronously using the provided context and input.
     *
     * This method processes the given input data within the specified context, leveraging the AI agent's
     * internal logic and strategy to produce an output. The execution is performed asynchronously and returns
     * a `CompletableFuture` immediately, allowing the calling thread to continue without waiting for the result.
     *
     * @param context The execution context in which the AI agent operates. It provides access to the agent's
     * environment, configuration, lifecycle state, and other components required for processing.
     * @param input The input data to be processed by the AI agent's strategy. The type of the input is determined
     * by the implementation of the strategy and is used to derive an appropriate output.
     * @return A `CompletableFuture` representing the asynchronous computation of the strategy's output.
     * The output type is defined by the strategy's implementation and may be null if no output is generated.
     */
    public fun executeImpl(context: TContext, input: TInput, ): TOutput

    override suspend fun execute(context: TContext, input: TInput): TOutput? = executeImpl(context, input)
}
