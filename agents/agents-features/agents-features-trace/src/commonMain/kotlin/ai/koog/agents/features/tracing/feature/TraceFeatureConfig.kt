package ai.koog.agents.features.tracing.feature

import ai.koog.agents.core.feature.config.FeatureConfig

/**
 * Configuration for the tracing feature.
 *
 * This class allows you to configure how the tracing feature behaves, including:
 * - Which message processors receive trace events
 * - Which events are traced (via message filtering)
 *
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     install(Tracing) {
 *         // Add message processors to handle trace events
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
 *         addMessageProcessor(TraceFeatureMessageFileWriter(outputFile, fileSystem::sink))
 *
 *         // Configure message filtering
 *         messageFilter = { message ->
 *             // Only trace LLM calls and tool calls
 *             message is LLMCallStartEvent ||
 *             message is LLMCallEndEvent ||
 *             message is ToolCallEvent ||
 *             message is ToolCallResultEvent
 *         }
 *     }
 * }
 * ```
 */
public class TraceFeatureConfig() : FeatureConfig()
