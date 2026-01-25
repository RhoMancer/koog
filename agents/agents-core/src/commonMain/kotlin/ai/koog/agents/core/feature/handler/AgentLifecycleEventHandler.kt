package ai.koog.agents.core.feature.handler

public fun interface AgentLifecycleEventHandler<TContext : AgentLifecycleEventContext> {

    public suspend fun handle(eventContext: TContext)
}
