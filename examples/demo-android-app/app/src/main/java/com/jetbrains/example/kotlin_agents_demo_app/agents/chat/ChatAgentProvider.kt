package com.jetbrains.example.kotlin_agents_demo_app.agents.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMProvider
import androidx.compose.runtime.Composable
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.AgentProvider
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.ExitTool
import com.jetbrains.example.kotlin_agents_demo_app.agents.local.AndroidLLocalLLMClient
import com.jetbrains.example.kotlin_agents_demo_app.agents.local.AndroidLocalLLMProvider
import com.jetbrains.example.kotlin_agents_demo_app.agents.local.AndroidLocalModels
import com.jetbrains.example.kotlin_agents_demo_app.agents.local.simpleAndroidLocalExecutor
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Factory for creating chat agents
 */
object ChatAgentProvider : AgentProvider {
    override val title: String = "Chat"
    override val description: String = "Hi, I'm a chat agent, I can have a conversation with you"

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun provideAgent(
        appSettings: AppSettings,
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to OpenAILLMClient(appSettings.getCurrentSettings().openAiToken),
            LLMProvider.Anthropic to AnthropicLLMClient(appSettings.getCurrentSettings().anthropicToken),
            AndroidLocalLLMProvider to AndroidLLocalLLMClient(
                appSettings.getContext(),
                "data/local/tmp/llm"
            )
        )

        // Create tool registry with just the exit tool
        val toolRegistry = ToolRegistry {
            tool(ExitTool)
        }

        val strategy = strategy(title) {
            val nodeRequestLLM by nodeLLMRequest()
            val nodeAssistantMessage by node<String, String> { message -> onAssistantMessage(message) }

            edge(nodeStart forwardTo nodeRequestLLM)

            edge(
                nodeRequestLLM forwardTo nodeAssistantMessage
                        onAssistantMessage { true }
            )

            edge(
                nodeRequestLLM forwardTo nodeFinish
                        onToolCall { true }
                        transformed { it.content }
            )

            edge(nodeAssistantMessage forwardTo nodeRequestLLM)

        }

        // Create agent config with proper prompt
        val agentConfig = AIAgentConfig(
            prompt = prompt("chat") {
                system(
                    """
                    You are a helpful and friendly chat assistant.
                    Engage in conversation with the user, answering questions and providing information.
                    Be concise, accurate, and friendly in your responses.
                    If you don't know something, admit it rather than making up information.
                    """.trimIndent()
                )
            },
            model = AndroidLocalModels.Chat.Hammer,
            maxAgentIterations = 50
        )

        // Create the runner
        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onToolCall { tool: Tool<*, *>, toolArgs: Tool.Args ->
                    onToolCallEvent("Tool ${tool.name}, args $toolArgs")
                }

                onAgentRunError { strategyName: String, sessionUuid: Uuid?, throwable: Throwable ->
                    onErrorEvent("${throwable.message}")
                }

                onAgentFinished { strategyName: String, result: String? ->
                    // Skip finish event handling
                }
            }
        }
    }
}