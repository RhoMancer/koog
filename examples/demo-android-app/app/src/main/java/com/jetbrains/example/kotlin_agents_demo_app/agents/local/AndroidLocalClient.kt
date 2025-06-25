package com.jetbrains.example.kotlin_agents_demo_app.agents.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import android.content.Context
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.fc.GemmaFormatter
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy


class AndroidLocalClientSettings(
    val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
)


open class AndroidLLocalLLMClient(
    private val context: Context,
    private val modelsPath: String,
    private val settings: AndroidLocalClientSettings = AndroidLocalClientSettings(),
    private val clock: Clock = Clock.System,
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
        // OpenAI API is not polymorphic, it's "dynamic". Don't add polymorphic discriminators
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    override suspend fun execute(
        prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val modelPath = modelsPath + "/${model.id}.task"
        val taskOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .build()
        val llmInference = LlmInference.createFromOptions(context, taskOptions)

        val formatter = GemmaFormatter()
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder().build()
        val session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)

        // TODO: Add parameters to function
        val localTools = tools.map {
            Tool.newBuilder().addFunctionDeclarations(
                FunctionDeclaration.newBuilder()
                    .setName(it.name)
                    .setDescription(it.description)
            ).build()
        }

        val toolsMessage: String = formatter.formatSystemMessage(
            Content.getDefaultInstance(),
            localTools
        )

        session.addQueryChunk(toolsMessage)

        prompt.messages.forEach {
            val content = Content.newBuilder()
                .setRole(it.role.name)
                .addParts(Part.newBuilder().setText(it.content))
                .build()
            session.addQueryChunk(formatter.formatContent(content))
        }

        val results = formatter.parseResponse(session.generateResponse())?.candidatesList?.map {
            println(it)
            print(it.content.role)
            Message.Assistant(
                content = it.content.getParts(0).text,
                metaInfo = ResponseMetaInfo(clock.now())
            )
        } ?: emptyList()

        return results
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        TODO()
    }
}
