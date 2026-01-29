package ai.koog.protocol.model

import ai.koog.protocol.agent.FlowAgentInput
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 *
 */
internal fun JsonElement.toFlowAgentInput(): FlowAgentInput? {
    return when (this) {
        is JsonPrimitive -> {
            this.toFlowAgentInput()
        }

        is JsonArray -> {
            if (isEmpty()) {
                error("Unsupported empty array input")
            }

            this.toFlowAgentInputArray()
        }

        is JsonObject -> {
            this.toFlowAgentInputObject()
        }

        JsonNull -> null
    }
}

internal fun JsonPrimitive.toFlowAgentInput(): FlowAgentInput {
    return booleanOrNull?.let { data -> FlowAgentInput.Boolean(data) }
        ?: intOrNull?.let { data -> FlowAgentInput.Int(data) }
        ?: doubleOrNull?.let { data -> FlowAgentInput.Double(data) }
        ?: contentOrNull?.let { data -> FlowAgentInput.String(data) }
        ?: error("Unsupported primitive type: $this")
}

internal fun JsonArray.toFlowAgentInputArray(): FlowAgentInput {
    return when {
        all { it.jsonPrimitive.isString } -> {
            FlowAgentInput.ArrayStrings(mapNotNull { it.jsonPrimitive.contentOrNull }.toTypedArray())
        }
        all { it.jsonPrimitive.booleanOrNull != null } -> {
            FlowAgentInput.ArrayBooleans(mapNotNull { it.jsonPrimitive.booleanOrNull }.toTypedArray())
        }
        all { it.jsonPrimitive.intOrNull != null } -> {
            FlowAgentInput.ArrayInt(mapNotNull { it.jsonPrimitive.intOrNull }.toTypedArray())
        }
        all { it.jsonPrimitive.doubleOrNull != null } -> {
            FlowAgentInput.ArrayDouble(mapNotNull { it.jsonPrimitive.doubleOrNull }.toTypedArray())
        }
        else -> {
            error("Expected an array of uniform primitive types, but got: <$this>")
        }
    }
}

internal fun JsonObject.toFlowAgentInputObject(): FlowAgentInput? {
    val knownInputObject = this.toFlowAgentCritiqueResult()
        ?: error("Unable to create Flow Agent Input type from Json input: $this")

    return knownInputObject
}

internal fun JsonObject.toFlowAgentCritiqueResult() : FlowAgentInput.CritiqueResult? {
    val success = this["success"]?.jsonPrimitive?.booleanOrNull ?: return null
    val feedback = this["feedback"]?.jsonPrimitive?.contentOrNull ?: return null

    return FlowAgentInput.CritiqueResult(success, feedback)
}
