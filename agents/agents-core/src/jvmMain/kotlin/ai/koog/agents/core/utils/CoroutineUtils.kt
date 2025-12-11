package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking

internal fun ExecutorService?.asCoroutineContext(
    defaultExecutorService: ExecutorService? = null,
    fallbackDispatcher: CoroutineDispatcher = Dispatchers.Default
): CoroutineContext =
    (this ?: defaultExecutorService)?.asCoroutineDispatcher() ?: fallbackDispatcher

internal fun <T> AIAgentConfig.runOnLLMDispatcher(executorService: ExecutorService?, block: suspend () -> T): T =
    runBlocking(
        executorService.asCoroutineContext(
            defaultExecutorService = llmRequestExecutorService,
            fallbackDispatcher = Dispatchers.IO
        )
    ) {
        block()
    }

internal fun <T> AIAgentConfig.runOnMainDispatcher(
    executorService: ExecutorService? = null,
    block: suspend () -> T
): T =
    runBlocking(
        executorService.asCoroutineContext(
            defaultExecutorService = strategyExecutorService,
            fallbackDispatcher = Dispatchers.Default
        )
    ) {
        block()
    }

internal suspend fun <T> AIAgentConfig.submitToMainDispatcher(block: () -> T): T {
    val result = CompletableDeferred<T>()

    (strategyExecutorService ?: Dispatchers.Default.asExecutor()).execute {
        result.complete(block())
    }

    return result.await()
}
