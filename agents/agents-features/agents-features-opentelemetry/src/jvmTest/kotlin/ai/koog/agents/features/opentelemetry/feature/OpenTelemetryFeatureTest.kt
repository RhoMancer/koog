package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenTelemetryFeatureTest {

    @Test
    fun testPatchExecutionInfoForTopLevelPathWithoutRunId() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.patchExecutionInfo(executionInfo, null)
        assertEquals("parent", patchedInfo.path())
    }

    @Test
    fun testPatchExecutionInfoForTopLevelPathWithRunId() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.patchExecutionInfo(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id", patchedInfo.path())
    }

    @Test
    fun testPatchExecutionInfoForNonTopLevelPath() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.patchExecutionInfo(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id/child", patchedInfo.path())
    }

    @Test
    fun testPatchExecutionInfoForMultipleChildren() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child1"), "child2")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.patchExecutionInfo(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id/child1/child2", patchedInfo.path())
    }
}
