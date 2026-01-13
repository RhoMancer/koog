package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.AIAgentBuilder
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object JavaInteropUtils {
    @JvmStatic
    fun createOpenAIClient(apiKey: String): OpenAILLMClient = OpenAILLMClient(apiKey)

    @JvmStatic
    fun createAnthropicClient(apiKey: String): AnthropicLLMClient = AnthropicLLMClient(apiKey)

    @JvmStatic
    fun createToolRegistry(toolSet: ToolSet): ToolRegistry {
        return ToolRegistry.builder()
            .tools(toolSet, Json.Default)
            .build()
    }

    @JvmStatic
    fun installEventHandler(
        builder: AIAgentBuilder,
        agentStarted: AtomicBoolean,
        agentCompleted: AtomicBoolean,
        llmCallsCount: AtomicInteger,
        toolsCalled: MutableList<String>
    ) {
        builder.install(EventHandler.Feature) {
            val config = this as EventHandlerConfig
            config.onAgentStarting { agentStarted.set(true) }
            config.onAgentCompleted { agentCompleted.set(true) }
            config.onLLMCallStarting { llmCallsCount.incrementAndGet() }
            config.onToolCallStarting { toolsCalled.add(it.toolName) }
        }
    }

    class CalculatorTools : ToolSet {
        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription(description = "Adds two numbers together")
        fun add(
            @LLMDescription(description = "First number") a: Int,
            @LLMDescription(description = "Second number") b: Int
        ): Int = a + b

        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription(description = "Multiplies two numbers")
        fun multiply(
            @LLMDescription(description = "First number") a: Int,
            @LLMDescription(description = "Second number") b: Int
        ): Int = a * b

        fun getAddTool(): Tool<*, *> {
            return createToolRegistry(this).tools.first { it.name == "add" }
        }

        fun getMultiplyTool(): Tool<*, *> {
            return createToolRegistry(this).tools.first { it.name == "multiply" }
        }
    }
}
