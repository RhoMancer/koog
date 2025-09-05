package ai.koog.agents.core.feature.handler

internal abstract class AgentEventHandler {

    internal abstract fun handle(eventContext: AgentEventHandlerContext)
}
