package ai.koog.agents.core.feature.handler.agent

import ai.koog.agents.core.environment.AIAgentEnvironment

/**
 * Feature implementation for agent and strategy interception.
 */
public class AgentEventHandler {

    /**
     * Configurable transformer used to manipulate or modify an instance of AgentEnvironment.
     * Allows customization of the environment during agent creation or updates by applying
     * the provided transformation logic.
     */
    public var agentEnvironmentTransformingHandler: AgentEnvironmentTransformingHandler =
        AgentEnvironmentTransformingHandler { _, environment -> environment }

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    public suspend fun transformEnvironment(
        context: AgentEnvironmentTransformingContext,
        environment: AIAgentEnvironment
    ): AIAgentEnvironment =
        agentEnvironmentTransformingHandler.transform(context, environment)
}

/**
 * Handler for transforming an instance of AgentEnvironment.
 *
 * Ex: useful for mocks in tests
 */
public fun interface AgentEnvironmentTransformingHandler {

    /**
     * Transforms the provided agent environment based on the given context.
     *
     * @param context The context containing the agent, strategy, and feature information
     * @param environment The current agent environment to be transformed
     * @return The transformed agent environment
     */
    public suspend fun transform(
        context: AgentEnvironmentTransformingContext,
        environment: AIAgentEnvironment
    ): AIAgentEnvironment
}
