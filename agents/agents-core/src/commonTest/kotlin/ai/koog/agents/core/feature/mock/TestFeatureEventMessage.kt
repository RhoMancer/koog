package ai.koog.agents.core.feature.mock

import ai.koog.agents.core.agent.context.AgentExecutionInfo
import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import kotlinx.datetime.Clock

internal class TestFeatureEventMessage(
    override val executionInfo: AgentExecutionInfo = AgentExecutionInfo("test-id"),
) : FeatureEvent {
    override val timestamp: Long get() = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
