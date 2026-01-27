package ai.koog._initial.parser

import ai.koog._initial.flow.FlowConfig

/**
 *
 */
public interface FlowConfigParser {

    /**
     *
     */
    public fun parse(input: String): FlowConfig
}
