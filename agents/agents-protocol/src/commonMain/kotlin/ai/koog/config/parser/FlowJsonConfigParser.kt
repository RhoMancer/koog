package ai.koog.config.parser

import ai.koog.flow.FlowConfig
import kotlinx.serialization.json.Json

/**
 * Parser for JSON flow configuration files.
 */
public class FlowJsonConfigParser : FlowConfigParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun parse(input: String): FlowConfig {
        return json.decodeFromString<FlowConfig>(input)
    }
}
