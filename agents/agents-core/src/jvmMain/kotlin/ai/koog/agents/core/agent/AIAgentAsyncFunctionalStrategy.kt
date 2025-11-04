@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.NonSuspendAIAgentStrategy
import ai.koog.agents.core.utils.asCoroutineContext
import ai.koog.prompt.message.Message
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

@JavaAPI
public abstract class NonSuspendAIAgentFunctionalStrategy<TInput, TOutput> public constructor(
    override val name: String
) : NonSuspendAIAgentStrategy<TInput, TOutput, AIAgentFunctionalContext>, AIAgentFunctionalStrategy<TInput, TOutput> {

    abstract override fun executeImpl(context: AIAgentFunctionalContext, input: TInput): TOutput

    override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: TInput
    ): TOutput = execute(context, input)
}

@JavaAPI
public fun interface AIAgentFunctionalStrategyAction<TInput, TOutput> {
    public fun execute(context: AIAgentFunctionalContext, input: TInput): TOutput
}
//
//private fun f() {
//    val str = AIAgentFunctionalStrategyAction<String, String> { ctx, input ->
//        ctx.requestLLM(input, executorService = null)
//            .thenApplyAsync { message ->
//                if (message is Message.Tool.Call) {
//                    ctx.executeTool(message, executorService = null)
//                    ctx.sendToolResult(toolResul, executorService = null)
//                } else {
//
//                }
//            }
//        ctx.executeTool()
//
//        ""
//    }
//
//    val str2 = object: AIAgentFunctionalStrategy<String, String> {
//        override suspend fun execute(
//            context: AIAgentFunctionalContext,
//            input: String
//        ): String {
//            context.requestLLM(input)
//
//            return ""
//        }
//
//        override val name: String = "aaa"
//
//    }
//}
