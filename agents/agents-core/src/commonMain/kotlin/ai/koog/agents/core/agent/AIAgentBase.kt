package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.utils.Closeable

/**
 * Base interface for implementing an AI agent. This interface provides the foundational
 * structure to define and execute AI-driven operations. It includes mechanisms for configuration,
 * execution, and resource management.
 *
 * @param Input The type of input data that the agent will process.
 * @param Output The type of output data that the agent will produce.
 */
public interface AIAgentBase<Input, Output> : Closeable {

    /**
     * Represents the unique identifier for the AI agent.
     */
    public val id: String

    /**
     * The configuration for the AI agent.
     */
    public val agentConfig: AIAgentConfigBase

    /**
     * Executes the AI agent with the given input and retrieves the resulting output.
     *
     * @param agentInput The input for the agent.
     * @return The output produced by the agent.
     */
    public suspend fun run(agentInput: Input): Output
}
