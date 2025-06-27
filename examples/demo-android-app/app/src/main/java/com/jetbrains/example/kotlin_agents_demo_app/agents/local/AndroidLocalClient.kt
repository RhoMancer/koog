package com.jetbrains.example.kotlin_agents_demo_app.agents.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import android.content.Context
import com.google.ai.edge.localagents.core.proto.Candidate
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.core.proto.Schema
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.fc.GemmaFormatter
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import com.google.ai.edge.localagents.core.proto.Type
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlin.collections.component1
import kotlin.collections.component2

class AndroidLocalClientSettings(
    val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
)

/**
 * https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android?hl=ru
 */
open class AndroidLLocalLLMClient(
    private val context: Context,
    private val modelsPath: String,
    private val settings: AndroidLocalClientSettings = AndroidLocalClientSettings(),
    private val clock: Clock = Clock.System,
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
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

        val localTools = tools.map { toolDescriptor ->
            Tool.newBuilder().addFunctionDeclarations(
                FunctionDeclaration.newBuilder()
                    .setName(toolDescriptor.name)
                    .setDescription(toolDescriptor.description)
                    .setParameters(convertToProto(toolDescriptor))
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

        val results =
            formatter.parseResponse(session.generateResponse())?.candidatesList?.map { candidate ->
                parseMessage(candidate)
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

    private fun convertToProto(toolDescriptor: ToolDescriptor): Schema {
        val schemaBuilder = Schema.newBuilder()
            .setType(Type.OBJECT)

        val allParameters = toolDescriptor.requiredParameters + toolDescriptor.optionalParameters
        val requiredNames = toolDescriptor.requiredParameters.map { it.name }

        if (allParameters.isNotEmpty()) {
            allParameters.forEach { param ->
                schemaBuilder.putProperties(param.name, convertParameterToProto(param))
            }

            if (requiredNames.isNotEmpty()) {
                schemaBuilder.addAllRequired(requiredNames)
            }
        }

        return schemaBuilder.build()
    }

    private fun convertParameterToProto(parameter: ToolParameterDescriptor): Schema {
        val schemaBuilder = Schema.newBuilder()

        when (val paramType = parameter.type) {
            is ToolParameterType.String -> {
                schemaBuilder.type = Type.STRING
            }

            is ToolParameterType.Integer -> {
                schemaBuilder.type = Type.INTEGER
            }

            is ToolParameterType.Float -> {
                schemaBuilder.type = Type.NUMBER
            }

            is ToolParameterType.Boolean -> {
                schemaBuilder.type = Type.BOOLEAN
            }

            is ToolParameterType.Enum -> {
                schemaBuilder.type = Type.STRING
                paramType.entries.forEach { enumValue ->
                    schemaBuilder.addEnum(enumValue)
                }
            }

            is ToolParameterType.List -> {
                schemaBuilder.type = Type.ARRAY
                val itemParameter = ToolParameterDescriptor("items", "", paramType.itemsType)
                schemaBuilder.items = convertParameterToProto(itemParameter)
            }

            is ToolParameterType.Object -> {
                schemaBuilder.type = Type.OBJECT
                paramType.properties.forEach { prop ->
                    schemaBuilder.putProperties(prop.name, convertParameterToProto(prop))
                }
                if (paramType.requiredProperties.isNotEmpty()) {
                    schemaBuilder.addAllRequired(paramType.requiredProperties)
                }
            }
        }

        if (parameter.description.isNotEmpty()) {
            schemaBuilder.description = parameter.description
        }

        return schemaBuilder.build()
    }

    private fun parseMessage(candidate: Candidate): Message.Response {
        val parts = candidate.content.partsList
        if (parts.any { it.hasFunctionCall() }) {
            val functionCallPart = parts.first { it.hasFunctionCall() }
            val functionCall = functionCallPart.functionCall

            val argsJson = Json.encodeToString(parseArgsToJsonObject(functionCall.args))

            return Message.Tool.Call(
                id = null,
                tool = functionCall.name,
                content = argsJson,
                metaInfo = ResponseMetaInfo(clock.now())
            )
        }

        return Message.Assistant(
            content = candidate.content.getParts(0).text,
            metaInfo = ResponseMetaInfo(clock.now())
        )
    }

    private fun parseArgsToJsonObject(argsStruct: Struct): JsonObject {
        return buildJsonObject {
            argsStruct.fieldsMap.forEach { (key, value) ->
                put(key, parseValueToJsonElement(value))
            }
        }
    }


    private fun parseValueToJsonElement(value: Value): JsonElement {
        return when (value.kindCase) {
            Value.KindCase.NULL_VALUE -> JsonNull
            Value.KindCase.NUMBER_VALUE -> JsonPrimitive(value.numberValue)
            Value.KindCase.STRING_VALUE -> JsonPrimitive(value.stringValue)
            Value.KindCase.BOOL_VALUE -> JsonPrimitive(value.boolValue)
            Value.KindCase.STRUCT_VALUE -> {
                buildJsonObject {
                    value.structValue.fieldsMap.forEach { (key, nestedValue) ->
                        put(key, parseValueToJsonElement(nestedValue))
                    }
                }
            }

            Value.KindCase.LIST_VALUE -> {
                buildJsonArray {
                    value.listValue.valuesList.forEach { listValue ->
                        add(parseValueToJsonElement(listValue))
                    }
                }
            }

            else -> JsonPrimitive(value.toString())
        }
    }
}
