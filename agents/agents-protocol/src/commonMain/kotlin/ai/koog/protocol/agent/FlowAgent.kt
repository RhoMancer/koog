package ai.koog.protocol.agent

/**
 *
 */
public interface FlowAgent {

    /**
     * Agent identifier (maps to "name" in JSON).
     */
    public val name: String

    /**
     * Agent type (maps to "type" in JSON).
     */
    public val type: FlowAgentKind

    /**
     * Agent configuration.
     */
    public val config: FlowAgentConfig

    /**
     * Agent prompt configuration.
     */
    public val prompt: FlowAgentPrompt?

    /**
     * Agent input type.
     */
    public val input: FlowAgentInput

    /**
     *
     */
    public val parameters: FlowAgentParameters
}
