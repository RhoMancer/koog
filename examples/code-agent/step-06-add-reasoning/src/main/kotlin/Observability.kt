package ai.koog.agents.examples.codeagent.step06

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.prompt.streaming.StreamFrame

/**
 * Extracted observability setup used by agents in this module.
 * Logic is kept identical to the original blocks; only the agent name is parameterized.
 */
fun GraphAIAgent.FeatureContext.setupObservability(agentName: String) {
    install(OpenTelemetry) {
        setVerbose(true) // Send full strings instead of HIDDEN placeholders
        addLangfuseExporter(
            traceAttributes = listOf(
                CustomAttribute("langfuse.session.id", System.getenv("LANGFUSE_SESSION_ID") ?: ""),
            )
        )
    }
    handleEvents {
        onToolCallStarting { ctx ->
            println("[$agentName] Tool '${ctx.toolName}' called with args: ${ctx.toolArgs.toString().take(100)}")
        }
        // Print streaming frames; with O-series + Responses API include flags this will contain reasoning text
        onLLMStreamingFrameReceived { ctx ->
            when (val f = ctx.streamFrame) {
                is StreamFrame.Append -> println("[$agentName][stream] ${f.text}")
                is StreamFrame.ToolCall -> println("[$agentName][stream-tool] ${f.name} args=${f.content.take(120)}")
                is StreamFrame.End -> println("[$agentName][stream-end] reason=${f.finishReason}")
            }
        }
    }
}
