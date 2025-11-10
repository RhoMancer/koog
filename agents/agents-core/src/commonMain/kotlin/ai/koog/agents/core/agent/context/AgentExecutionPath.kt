package ai.koog.agents.core.agent.context

import kotlinx.serialization.Serializable

/**
 * Represents an execution path for agents, providing a hierarchical structure of path components
 * separated by a configurable separator.
 */
@Serializable
public data class AgentExecutionPath(public val separator: CharSequence = defaultSeparator) {

    /**
     * Companion object containing default values and constants for AgentExecutionPath.
     */
    public companion object {
        /**
         * The default separator used to join path parts, which is a dot (".").
         */
        private val defaultSeparator: CharSequence = "."

        /**
         * An empty agent execution path with no parts.
         */
        public val EMPTY: AgentExecutionPath = AgentExecutionPath()
    }

    /**
     * Creates an AgentExecutionPath from the provided parts.
     *
     * @param parts Variable number of string parts to initialize the path with.
     * @param separator The separator to use for joining path parts (defaults to ".").
     */
    public constructor(vararg parts: String, separator: CharSequence = defaultSeparator) : this(separator) {
        parts.forEach { append(it) }
    }

    /**
     * Returns the full path as a string with parts joined by the separator.
     */
    public val path: String
        get() = parts.joinToString(separator)

    private val _parts = mutableListOf<String>()

    /**
     * Returns an immutable copy of the path parts list.
     */
    public val parts: List<String>
        get() = _parts.toList()

    /**
     * Appends a part to the execution path using the division operator.
     *
     * @param part The path part to append.
     */
    public operator fun div(part: String) {
        _parts.add(part)
    }

    /**
     * Appends a part to the execution path.
     *
     * @param part The path part to append.
     * @return Always returns true as the underlying list is always modified.
     */
    public fun append(part: String): Boolean {
        return _parts.add(part)
    }

    /**
     * Removes the last part from the path and returns it.
     *
     * @return The removed part, or null if the path is empty.
     */
    public fun dropLast(): String? {
        return _parts.removeLastOrNull()
    }
}
