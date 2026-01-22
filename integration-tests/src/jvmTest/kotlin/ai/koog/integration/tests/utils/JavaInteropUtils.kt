package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentBuilder
import ai.koog.agents.core.agent.FunctionalAgentBuilder
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 *
 * The object is necessary to bridge the gap between Kotlin coroutines/DSL and Java.
 * It's necessary because Java doesn't natively support some used Kotlin concepts like runBlocking.
 */

object JavaInteropUtils {
    @JvmStatic
    fun createOpenAIClient(apiKey: String): OpenAILLMClient = OpenAILLMClient(apiKey)

    @JvmStatic
    fun createAnthropicClient(apiKey: String): AnthropicLLMClient = AnthropicLLMClient(apiKey)

    @JvmStatic
    fun buildSimplePrompt(id: String, system: String, user: String): Prompt {
        return Prompt.builder(id)
            .system(system)
            .user(user)
            .build()
    }

    @JvmStatic
    fun executeClientBlocking(
        client: LLMClient,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response> = runBlocking {
        client.execute(prompt, model, tools)
    }

    @JvmStatic
    fun executeExecutorBlocking(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response> = runBlocking {
        executor.execute(prompt, model, tools)
    }

    @JvmStatic
    fun createMultiLLMPromptExecutor(
        openAIClient: OpenAILLMClient,
        anthropicClient: AnthropicLLMClient
    ): MultiLLMPromptExecutor {
        return MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )
    }

    @JvmStatic
    fun getAssistantContent(message: Message.Assistant): String = message.content

    @JvmStatic
    fun getToolName(call: Message.Tool.Call): String = call.tool

    @JvmStatic
    fun runAgentBlocking(agent: AIAgent<String, String>, input: String): String = runBlocking {
        agent.run(input)
    }

    @JvmStatic
    fun requestLLMBlocking(
        context: AIAgentFunctionalContext,
        input: String,
        includeHistory: Boolean = true
    ): Message.Response = runBlocking {
        context.requestLLM(input, includeHistory)
    }

    @JvmStatic
    fun sendToolResultBlocking(
        context: AIAgentFunctionalContext,
        toolResult: ReceivedToolResult
    ): Message.Response = runBlocking {
        context.sendToolResult(toolResult)
    }

    @JvmStatic
    fun executeToolBlocking(
        context: AIAgentFunctionalContext,
        toolCall: Message.Tool.Call
    ): ReceivedToolResult = runBlocking {
        context.executeTool(toolCall)
    }

    @JvmStatic
    fun createAgentBuilder(): AIAgentBuilder = AIAgent.builder()

    @JvmStatic
    fun buildFunctionalAgent(builder: FunctionalAgentBuilder<String, String>): AIAgent<String, String> = builder.build()

    @JvmStatic
    fun <I, O : Any> runSubtaskBlocking(
        context: AIAgentFunctionalContext,
        description: String,
        input: I,
        outputType: Class<O>,
        tools: List<Tool<*, *>>,
        model: LLModel
    ): O = runBlocking {
        context.subtask(description)
            .withInput(input)
            .withOutput(outputType)
            .withTools(tools)
            .useLLM(model)
            .run()
    }

    @JvmStatic
    fun createToolRegistry(toolSet: ToolSet): ToolRegistry {
        return ToolRegistry.builder()
            .tools(toolSet, Json.Default)
            .build()
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
