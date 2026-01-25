package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.entity.AIAgentStorageKey

internal class AgentLifecycleHandlersCollector {

    private class FeatureEventHandlers(
        val featureKey: AIAgentStorageKey<*>
    ) {
        private val handlersByEventType = mutableMapOf<AgentLifecycleEventType, MutableList<AgentLifecycleEventHandler<*>>>()

        fun <TContext : AgentLifecycleEventContext> addHandler(
            eventType: AgentLifecycleEventType,
            handler: AgentLifecycleEventHandler<TContext>
        ) {
            handlersByEventType
                .getOrPut(eventType) { mutableListOf() }
                    .add(handler)
        }

        fun <TContext : AgentLifecycleEventContext> getHandlers(
            eventType: AgentLifecycleEventType
        ): List<AgentLifecycleEventHandler<TContext>> {
            return handlersByEventType[eventType]?.mapNotNull { handler ->
                @Suppress("UNCHECKED_CAST")
                handler as? AgentLifecycleEventHandler<TContext>
            } ?: emptyList()
        }
    }

    private val featureToHandlersMap =
        mutableMapOf<AIAgentStorageKey<*>, FeatureEventHandlers>()

    internal fun <TContext : AgentLifecycleEventContext> addHandlerForFeature(
        featureKey: AIAgentStorageKey<*>,
        eventType: AgentLifecycleEventType,
        handler: AgentLifecycleEventHandler<TContext>
    ) {
        featureToHandlersMap.getOrPut(featureKey) { FeatureEventHandlers(featureKey) }
            .addHandler(eventType, handler)
    }

    internal fun <TContext : AgentLifecycleEventContext> getHandlersForEvent(
        eventType: AgentLifecycleEventType
    ): Map<AIAgentStorageKey<*>, List<AgentLifecycleEventHandler<TContext>>> {

        val handlers = featureToHandlersMap
            .mapValues { (_, featureHandlers) -> featureHandlers.getHandlers<TContext>(eventType) }
            .filterValues { it.isNotEmpty() }

        return handlers
    }
}
