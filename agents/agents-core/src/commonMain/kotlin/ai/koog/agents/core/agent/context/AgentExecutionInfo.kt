package ai.koog.agents.core.agent.context

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A data class representing context information used for observability and tracing purposes.
 *
 * @property id A unique identifier for the current context.
 * @property parent An optional identifier that links this context to its parent if applicable.
 */
@Serializable
public data class AgentExecutionInfo(
    var id: String,
    var parent: AgentExecutionInfo? = null,
    val path: AgentExecutionPath = AgentExecutionPath()
)

/**
 * Executes a block of code with a modified execution context, creating a parent-child relationship
 * between execution contexts for tracing purposes.
 *
 * @param T The return type of the block being executed.
 * @param context The AI agent context whose execution info will be temporarily modified.
 * @param partName The name of the execution part to append to the execution path.
 * @param block The suspend function to execute with the modified execution context.
 * @return The result of executing the provided block.
 */
public inline fun <T> withParent(
    context: AIAgentContext,
    partName: String,
    block: (executionInfo: AgentExecutionInfo) -> T
): T {

    // Original
    val originalParent = context.executionInfo.parent
    val originalId = context.executionInfo.id

    // New
    val newParent = AgentExecutionInfo(originalId, originalParent, context.executionInfo.path)
    @OptIn(ExperimentalUuidApi::class)
    val newId = Uuid.random().toString()

    context.executionInfo.parent = newParent
    context.executionInfo.id = newId
    context.executionInfo.path.append(partName)

    try {
        return block(context.executionInfo)
    } finally {
        context.executionInfo.parent = originalParent
        context.executionInfo.id = originalId
        context.executionInfo.path.dropLast()
    }
}

