package ai.koog.agents.core.feature.handler

public fun interface AgentLifecycleEventHandler<TContext : AgentLifecycleEventContext, TReturn : Any> {

    public suspend fun handle(eventContext: TContext): TReturn
}
