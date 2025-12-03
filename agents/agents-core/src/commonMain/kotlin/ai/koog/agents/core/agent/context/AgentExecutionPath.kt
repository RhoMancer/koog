package ai.koog.agents.core.agent.context

import kotlinx.serialization.Serializable

/**
 * TODO: SD --
 */
@Serializable
public data class AgentExecutionPath(public val separator: CharSequence = defaultSeparator) {

    /**
     * TODO: SD --
     */
    public companion object {
        private val defaultSeparator: CharSequence = "."

        /**
         * TODO: SD --
         */
        public val EMPTY: AgentExecutionPath = AgentExecutionPath()
    }

    /**
     * TODO: SD --
     */
    public constructor(vararg parts: String, separator: CharSequence = defaultSeparator) : this(separator) {
        parts.forEach { append(it) }
    }

    /**
     * TODO: SD --
     */
    public val path: String
        get() = parts.joinToString(separator)

    private val _parts = mutableListOf<String>()

    /**
     * TODO: SD --
     */
    public val parts: List<String>
        get() = _parts.toList()

    /**
     * TODO: SD --
     */
    public operator fun div(part: String) {
        _parts.add(part)
    }

    /**
     * TODO: SD --
     */
    public fun append(part: String): Boolean {
        return _parts.add(part)
    }

    /**
     * TODO: SD --
     */
    public fun dropLast(): String? {
        return parts.dropLast(1).singleOrNull()
    }
}
