package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgentInput
import kotlin.reflect.KClass

/**
 *
 */
public object FlowAgentInputCastUtil {

    /**
     * Converts a FlowAgentInput to the target type specified by KClass.
     */
    public fun convertToTargetType(
        input: FlowAgentInput,
        targetType: KClass<FlowAgentInput>,
    ): FlowAgentInput {

        return when (targetType) {
            FlowAgentInput.InputString::class -> convertToString(input)
            FlowAgentInput.InputInt::class -> convertToInt(input)
            FlowAgentInput.InputDouble::class -> convertToDouble(input)
            FlowAgentInput.InputBoolean::class -> convertToBoolean(input)
            FlowAgentInput.InputArrayStrings::class -> convertToArrayStrings(input)
            FlowAgentInput.InputArrayInt::class -> convertToArrayInt(input)
            FlowAgentInput.InputArrayDouble::class -> convertToArrayDouble(input)
            FlowAgentInput.InputArrayBooleans::class -> convertToArrayBooleans(input)
            else -> input
        }
    }

    /**
     * Converts any FlowAgentInput to InputString.
     */
    private fun convertToString(input: FlowAgentInput): FlowAgentInput.InputString {
        return FlowAgentInput.InputString(
            when (input) {
                is FlowAgentInput.InputInt -> input.data.toString()
                is FlowAgentInput.InputDouble -> input.data.toString()
                is FlowAgentInput.InputBoolean -> input.data.toString()
                is FlowAgentInput.InputString -> input.data
                is FlowAgentInput.InputArrayInt -> input.data.joinToString(",")
                is FlowAgentInput.InputArrayDouble -> input.data.joinToString(",")
                is FlowAgentInput.InputArrayBooleans -> input.data.joinToString(",")
                is FlowAgentInput.InputArrayStrings -> input.data.joinToString(",")
                is FlowAgentInput.InputCritiqueResult -> "${input.success}:${input.feedback}"
                else -> error("Unsupported input type for conversion to String: $input")
            }
        )
    }

    /**
     * Converts any FlowAgentInput to InputInt.
     */
    private fun convertToInt(input: FlowAgentInput): FlowAgentInput.InputInt {
        return FlowAgentInput.InputInt(
            when (input) {
                is FlowAgentInput.InputInt -> input.data
                is FlowAgentInput.InputDouble -> input.data.toInt()
                is FlowAgentInput.InputBoolean -> if (input.data) 1 else 0
                is FlowAgentInput.InputString -> input.data.toIntOrNull() ?: 0
                is FlowAgentInput.InputArrayInt -> input.data.firstOrNull() ?: 0
                is FlowAgentInput.InputArrayDouble -> input.data.firstOrNull()?.toInt() ?: 0
                is FlowAgentInput.InputArrayBooleans -> if (input.data.firstOrNull() == true) 1 else 0
                is FlowAgentInput.InputArrayStrings -> input.data.firstOrNull()?.toIntOrNull() ?: 0
                is FlowAgentInput.InputCritiqueResult -> if (input.success) 1 else 0
                else -> error("Unsupported input type for conversion to Int: $input")
            }
        )
    }

    /**
     * Converts any FlowAgentInput to InputDouble.
     */
    private fun convertToDouble(input: FlowAgentInput): FlowAgentInput.InputDouble {
        return FlowAgentInput.InputDouble(
            when (input) {
                is FlowAgentInput.InputInt -> input.data.toDouble()
                is FlowAgentInput.InputDouble -> input.data
                is FlowAgentInput.InputBoolean -> if (input.data) 1.0 else 0.0
                is FlowAgentInput.InputString -> input.data.toDoubleOrNull() ?: 0.0
                is FlowAgentInput.InputArrayInt -> input.data.firstOrNull()?.toDouble() ?: 0.0
                is FlowAgentInput.InputArrayDouble -> input.data.firstOrNull() ?: 0.0
                is FlowAgentInput.InputArrayBooleans -> if (input.data.firstOrNull() == true) 1.0 else 0.0
                is FlowAgentInput.InputArrayStrings -> input.data.firstOrNull()?.toDoubleOrNull() ?: 0.0
                is FlowAgentInput.InputCritiqueResult -> if (input.success) 1.0 else 0.0
                else -> error("Unsupported input type for conversion to Double: $input")
            }
        )
    }

    /**
     * Converts any FlowAgentInput to InputBoolean.
     */
    private fun convertToBoolean(input: FlowAgentInput): FlowAgentInput.InputBoolean {
        return FlowAgentInput.InputBoolean(
            when (input) {
                is FlowAgentInput.InputInt -> input.data != 0
                is FlowAgentInput.InputDouble -> input.data != 0.0
                is FlowAgentInput.InputBoolean -> input.data
                is FlowAgentInput.InputString -> input.data.toBooleanStrictOrNull() ?: false
                is FlowAgentInput.InputArrayInt -> input.data.firstOrNull() != 0
                is FlowAgentInput.InputArrayDouble -> input.data.firstOrNull() != 0.0
                is FlowAgentInput.InputArrayBooleans -> input.data.firstOrNull() ?: false
                is FlowAgentInput.InputArrayStrings -> input.data.firstOrNull()?.toBooleanStrictOrNull() ?: false
                is FlowAgentInput.InputCritiqueResult -> input.success
                else -> error("Unsupported input type for conversion to Boolean: $input")
            }
        )
    }

    /**
     * Converts any FlowAgentInput to InputArrayStrings.
     */
    private fun convertToArrayStrings(input: FlowAgentInput): FlowAgentInput.InputArrayStrings {
        return FlowAgentInput.InputArrayStrings(
            when (input) {
                is FlowAgentInput.InputInt -> arrayOf(input.data.toString())
                is FlowAgentInput.InputDouble -> arrayOf(input.data.toString())
                is FlowAgentInput.InputBoolean -> arrayOf(input.data.toString())
                is FlowAgentInput.InputString -> arrayOf(input.data)
                is FlowAgentInput.InputArrayInt -> input.data.map { it.toString() }.toTypedArray()
                is FlowAgentInput.InputArrayDouble -> input.data.map { it.toString() }.toTypedArray()
                is FlowAgentInput.InputArrayBooleans -> input.data.map { it.toString() }.toTypedArray()
                is FlowAgentInput.InputArrayStrings -> input.data
                is FlowAgentInput.InputCritiqueResult -> arrayOf("${input.success}:${input.feedback}")
                else -> error("Unsupported input type for conversion to Array String: $input")
            }
        )
    }

    /**
     * Converts any FlowAgentInput to InputArrayInt.
     */
    private fun convertToArrayInt(input: FlowAgentInput): FlowAgentInput.InputArrayInt {
        return FlowAgentInput.InputArrayInt(
            when (input) {
                is FlowAgentInput.InputInt -> arrayOf(input.data)
                is FlowAgentInput.InputDouble -> arrayOf(input.data.toInt())
                is FlowAgentInput.InputBoolean -> arrayOf(if (input.data) 1 else 0)
                is FlowAgentInput.InputString -> arrayOf(input.data.toIntOrNull() ?: 0)
                is FlowAgentInput.InputArrayInt -> input.data
                is FlowAgentInput.InputArrayDouble -> input.data.map { it.toInt() }.toTypedArray()
                is FlowAgentInput.InputArrayBooleans -> input.data.map { if (it) 1 else 0 }.toTypedArray()
                is FlowAgentInput.InputArrayStrings -> input.data.mapNotNull { it.toIntOrNull() }.toTypedArray()
                is FlowAgentInput.InputCritiqueResult -> arrayOf(if (input.success) 1 else 0)
                else -> error("Unsupported input type for conversion to Array Int: $input")
            }
        )
    }

    /**
     * Converts any FlowAgentInput to InputArrayDouble.
     */
    private fun convertToArrayDouble(input: FlowAgentInput): FlowAgentInput.InputArrayDouble {
        return FlowAgentInput.InputArrayDouble(
            when (input) {
                is FlowAgentInput.InputInt -> arrayOf(input.data.toDouble())
                is FlowAgentInput.InputDouble -> arrayOf(input.data)
                is FlowAgentInput.InputBoolean -> arrayOf(if (input.data) 1.0 else 0.0)
                is FlowAgentInput.InputString -> arrayOf(input.data.toDoubleOrNull() ?: 0.0)
                is FlowAgentInput.InputArrayInt -> input.data.map { it.toDouble() }.toTypedArray()
                is FlowAgentInput.InputArrayDouble -> input.data
                is FlowAgentInput.InputArrayBooleans -> input.data.map { if (it) 1.0 else 0.0 }.toTypedArray()
                is FlowAgentInput.InputArrayStrings -> input.data.mapNotNull { it.toDoubleOrNull() }.toTypedArray()
                is FlowAgentInput.InputCritiqueResult -> arrayOf(if (input.success) 1.0 else 0.0)
                else -> error("Unsupported input type for conversion to Array Double: $input")
            }
        )
    }

    /**
     * Converts any FlowAgentInput to InputArrayBooleans.
     */
    private fun convertToArrayBooleans(input: FlowAgentInput): FlowAgentInput.InputArrayBooleans {
        return FlowAgentInput.InputArrayBooleans(
            when (input) {
                is FlowAgentInput.InputInt -> arrayOf(input.data != 0)
                is FlowAgentInput.InputDouble -> arrayOf(input.data != 0.0)
                is FlowAgentInput.InputBoolean -> arrayOf(input.data)
                is FlowAgentInput.InputString -> arrayOf(input.data.toBooleanStrictOrNull() ?: false)
                is FlowAgentInput.InputArrayInt -> input.data.map { it != 0 }.toTypedArray()
                is FlowAgentInput.InputArrayDouble -> input.data.map { it != 0.0 }.toTypedArray()
                is FlowAgentInput.InputArrayBooleans -> input.data
                is FlowAgentInput.InputArrayStrings -> input.data.mapNotNull { it.toBooleanStrictOrNull() }
                    .toTypedArray()

                is FlowAgentInput.InputCritiqueResult -> arrayOf(input.success)
                else -> error("Unsupported input type for conversion to Array Boolean: $input")
            }
        )
    }

    private fun convertToCritiqueResult(input: FlowAgentInput): FlowAgentInput.InputCritiqueResult {
        return when(input) {
            is FlowAgentInput.InputInt -> FlowAgentInput.InputCritiqueResult(input.data != 0, "", input)
            is FlowAgentInput.InputDouble -> FlowAgentInput.InputCritiqueResult(input.data != 0.0, "", input)
            is FlowAgentInput.InputBoolean -> FlowAgentInput.InputCritiqueResult(input.data, "", input)
            is FlowAgentInput.InputString -> FlowAgentInput.InputCritiqueResult(input.data.toBooleanStrictOrNull() ?: false, "", input)
            is FlowAgentInput.InputArrayInt -> FlowAgentInput.InputCritiqueResult(input.data.firstOrNull() != 0, "", input)
            is FlowAgentInput.InputArrayDouble -> FlowAgentInput.InputCritiqueResult(input.data.firstOrNull() != 0.0, "", input)
            is FlowAgentInput.InputArrayBooleans -> FlowAgentInput.InputCritiqueResult(input.data.firstOrNull() ?: false, "", input)
            is FlowAgentInput.InputArrayStrings -> FlowAgentInput.InputCritiqueResult(input.data.firstOrNull()?.toBooleanStrictOrNull() ?: false, "", input)
            is FlowAgentInput.InputCritiqueResult -> input
            else -> error("Unsupported input type for conversion to Critique Result: $input")
        }
    }

    /**
     * Converts a FlowAgentInput to the target type specified by type name string.
     * Supports both class simple names and common aliases.
     */
    public fun convertToTargetType(
        input: FlowAgentInput,
        targetTypeName: String
    ): FlowAgentInput {
        return when (targetTypeName) {
            "${FlowAgentInput.InputString::class.simpleName}" -> convertToString(input)
            "${FlowAgentInput.InputInt::class.simpleName}" -> convertToInt(input)
            "${FlowAgentInput.InputDouble::class.simpleName}" -> convertToDouble(input)
            "${FlowAgentInput.InputBoolean::class.simpleName}" -> convertToBoolean(input)
            "${FlowAgentInput.InputArrayStrings::class.simpleName}" -> convertToArrayStrings(input)
            "${FlowAgentInput.InputArrayInt::class.simpleName}" -> convertToArrayInt(input)
            "${FlowAgentInput.InputArrayDouble::class.simpleName}" -> convertToArrayDouble(input)
            "${FlowAgentInput.InputArrayBooleans::class.simpleName}" -> convertToArrayBooleans(input)
            "${FlowAgentInput.InputCritiqueResult::class.simpleName}" -> convertToCritiqueResult(input)
            else -> input // Unsupported or same type
        }
    }
}
