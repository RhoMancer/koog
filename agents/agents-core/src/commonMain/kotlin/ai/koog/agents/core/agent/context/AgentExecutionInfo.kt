package ai.koog.agents.core.agent.context

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A data class representing context information used for observability and tracing purposes.
 *
 * @property id A unique identifier for the current context.
 * @property parentId An optional identifier that links this context to its parent if applicable.
 */
public data class AgentExecutionInfo(
    var id: String,
    var parentId: String? = null,
    val path: AgentExecutionPath = AgentExecutionPath()
)

/**
 * TODO: SD --
 */
public suspend fun <T> withParent(
    context: AIAgentContext,
    partName: String,
    block: suspend () -> T
): T {
    val originalParentId = context.executionInfo.parentId
    val originalId = context.executionInfo.id

    @OptIn(ExperimentalUuidApi::class)
    val id = Uuid.random().toString()

    context.executionInfo.parentId = originalId
    context.executionInfo.id = id
    context.executionInfo.path.append(partName)

    try {
        return block()
    } finally {
        context.executionInfo.id = originalId
        context.executionInfo.parentId = originalParentId
        context.executionInfo.path.dropLast()
    }
}

