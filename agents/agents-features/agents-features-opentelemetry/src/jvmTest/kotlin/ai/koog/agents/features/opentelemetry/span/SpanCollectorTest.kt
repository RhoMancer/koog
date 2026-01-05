package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.features.opentelemetry.assertMapsEqual
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import ai.koog.agents.utils.HiddenString
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpanCollectorTest {

    @Test
    fun `spanProcessor should have zero spans initially`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `startSpan should add span to processor`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.startSpan(span, executionInfo)

        assertEquals(1, spanCollector.activeSpansCount)
    }

    @Test
    fun `getSpan should return span by id when it exists`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        val actualSpan = spanCollector.getSpan(executionInfo)
        assertEquals(spanId, actualSpan?.id)
    }

    @Test
    fun `getSpan should return null when no spans are added`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val executionInfo = AgentExecutionInfo(null, "test")
        assertEquals(0, spanCollector.activeSpansCount)

        val retrievedSpan = spanCollector.getSpan(executionInfo)

        assertNull(retrievedSpan)
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `getSpan should return null when span with given id not found`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        val nonExistentSpanExecutionInfo = AgentExecutionInfo(null, "non-existent-span")
        val retrievedSpan = spanCollector.getSpan(nonExistentSpanExecutionInfo)

        assertNull(retrievedSpan)
        assertEquals(1, spanCollector.activeSpansCount)
    }

    @Test
    fun `endSpan should decrease active span count`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, spanId)
        assertEquals(0, spanCollector.activeSpansCount)

        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        spanCollector.endSpan(span)
        assertEquals(0, spanCollector.activeSpansCount)

        val retrievedSpan = spanCollector.getSpan(executionInfo)
        assertNull(retrievedSpan)
    }

    @Test
    fun `endUnfinishedSpans should end spans that match the filter`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        // Create spans with different IDs
        val span1Id = "span1"
        val span1Name = "test1-span-name"
        val span2Id = "span2"
        val span2Name = "test2-span-name"
        val span3Id = "span3"
        val span3Name = "test3-span-name"

        // Create and start spans
        val span1 = MockGenAIAgentSpan(span1Id, span1Name)
        val path1 = AgentExecutionInfo(null, span1Id)
        spanCollector.startSpan(span1, path1)

        val span2 = MockGenAIAgentSpan(span2Id, span2Name)
        val path2 = AgentExecutionInfo(null, span2Id)
        spanCollector.startSpan(span2, path2)

        val span3 = MockGenAIAgentSpan(span3Id, span3Name)
        val path3 = AgentExecutionInfo(null, span3Id)
        spanCollector.startSpan(span3, path3)

        assertEquals(3, spanCollector.activeSpansCount)

        // End one of the spans
        spanCollector.endSpan(span2)
        assertEquals(2, spanCollector.activeSpansCount)

        // Verify initial state
        assertTrue(span1.isStarted)
        assertFalse(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)

        // End spans that match the filter (only span1)
        spanCollector.endUnfinishedSpans { span -> span.id == span1Id }

        // Verify span1 is ended, span2 was already ended, span3 is still not ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)

        // End all remaining unfinished spans
        spanCollector.endUnfinishedSpans()

        // Verify all spans are ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertTrue(span3.isEnded)
    }

    @Test
    fun `endUnfinishedSpans should end all spans`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        val agentId = "test-agent"
        val runId = "test-run"

        val agentSpanId = agentId
        val agentSpanName = "agent-span-name"

        val agentRunSpanId = runId
        val agentRunSpanName = "agent-run-span-name"

        val nodeSpanId = "testNode"
        val nodeSpanName = "node-span-name"

        val toolSpanId = "testTool"
        val toolSpanName = "tool-span-name"

        // Create paths
        val agentPath = AgentExecutionInfo(null, agentSpanId)
        val agentRunPath = AgentExecutionInfo(agentPath, agentRunSpanId)
        val nodePath = AgentExecutionInfo(agentRunPath, nodeSpanId)
        val toolPath = AgentExecutionInfo(nodePath, toolSpanId)

        // Create and start spans
        val agentSpan = MockGenAIAgentSpan(agentSpanId, agentSpanName)
        val agentRunSpan = MockGenAIAgentSpan(agentRunSpanId, agentRunSpanName)
        val nodeSpan = MockGenAIAgentSpan(nodeSpanId, nodeSpanName)
        val toolSpan = MockGenAIAgentSpan(toolSpanId, toolSpanName)

        // Add spans to storage
        spanCollector.startSpan(agentSpan, agentPath)
        spanCollector.startSpan(agentRunSpan, agentRunPath)
        spanCollector.startSpan(nodeSpan, nodePath)
        spanCollector.startSpan(toolSpan, toolPath)
        assertEquals(4, spanCollector.activeSpansCount)

        // Verify initial state - all spans are started but not ended
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)

        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)

        assertTrue(nodeSpan.isStarted)
        assertFalse(nodeSpan.isEnded)

        assertTrue(toolSpan.isStarted)
        assertFalse(toolSpan.isEnded)

        // End unfinished spans
        spanCollector.endUnfinishedSpans()

        // Verify that all spans are ended
        assertTrue(agentSpan.isStarted)
        assertTrue(agentSpan.isEnded)

        assertTrue(agentRunSpan.isStarted)
        assertTrue(agentRunSpan.isEnded)

        assertTrue(nodeSpan.isStarted)
        assertTrue(nodeSpan.isEnded)

        assertTrue(toolSpan.isStarted)
        assertTrue(toolSpan.isEnded)
    }

    @Test
    fun `test mask HiddenString values in attributes when verbose set to false`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = false)

        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val mockSpan = MockSpan()
        val executionInfo = AgentExecutionInfo(null, spanId)

        // Start the span to replace the span instance later with a mocked value
        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        // Replace started span with the mock instance
        span.span = mockSpan
        val attributes = listOf(
            MockAttribute(key = "secretKey", value = HiddenString("super-secret")),
            MockAttribute(key = "arraySecretKey", value = listOf(HiddenString("one"), HiddenString("two"))),
            MockAttribute("regularKey", "visible")
        )
        span.addAttributes(attributes)

        spanCollector.endSpan(span)
        assertEquals(0, spanCollector.activeSpansCount)

        // Verify exact converted values when verbose is set to 'false'
        val expectedAttributes = mapOf(
            AttributeKey.stringKey("secretKey") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
            AttributeKey.stringArrayKey("arraySecretKey") to listOf(HiddenString.HIDDEN_STRING_PLACEHOLDER, HiddenString.HIDDEN_STRING_PLACEHOLDER),
            AttributeKey.stringKey("regularKey") to "visible"
        )

        assertEquals(expectedAttributes.size, mockSpan.collectedAttributes.size)
        assertMapsEqual(expectedAttributes, mockSpan.collectedAttributes)
    }

    @Test
    fun `test mask HiddenString values in event attributes and body fields with verbose set to false`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = false)

        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val mockSpan = MockSpan()
        val executionInfo = AgentExecutionInfo(null, spanId)

        // Start the span to replace the span instance later with a mocked value
        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        // Event with attribute HiddenString and a body field that contains HiddenString
        // Replace started span with the mock instance
        span.span = mockSpan
        val event = MockGenAIAgentEvent(name = "event").apply {
            addAttribute(MockAttribute("secretKey", HiddenString("secretValue")))
            addBodyField(EventBodyFields.Content("some sensitive content"))
        }
        span.addEvent(event)

        spanCollector.endSpan(span)
        assertEquals(0, spanCollector.activeSpansCount)

        // Assert collected event
        assertEquals(1, mockSpan.collectedEvents.size)
        val actualEventAttributes = mockSpan.collectedEvents.getValue("event").asMap()

        // Assert attributes for the collected event when the verbose flag is set to 'false'
        val expectedEventAttributes = mapOf(
            AttributeKey.stringKey("secretKey") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
            AttributeKey.stringKey("content") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
        )

        assertEquals(expectedEventAttributes.size, actualEventAttributes.size)
        assertMapsEqual(expectedEventAttributes, actualEventAttributes)
    }
}
