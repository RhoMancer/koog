package org.example.agents.calculator.strategy

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*

object CalculatorStrategy {
    private const val MAX_TOKENS_THRESHOLD = 1000

    val strategy = strategy<String, String>("test") {
        val nodeCallLLM by nodeLLMRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        edge(nodeStart forwardTo nodeCallLLM)

        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })

        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
        edge(nodeSendToolResult forwardTo nodeFinish onToolCall { tc -> tc.tool == "__exit__" } transformed { "Chat finished" })
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
    }

//    val strategy = strategy<String, String>("test") {
//        val nodeCallLLM by nodeLLMRequest()
//        val nodeExecuteTool by nodeExecuteTool()
//        val nodeSendToolResult by nodeLLMSendToolResult()
//
//        edge(nodeStart forwardTo nodeCallLLM)
//
//        edge(nodeCallLLM forwardTo nodeFinish
//                transformed { it.content } onAssistantMessage { true }
//        )
//
//        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
//
//        edge(nodeExecuteTool forwardTo nodeSendToolResult)
//
//        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
//
//        edge(nodeSendToolResult forwardTo nodeFinish
//                transformed { it.content } onAssistantMessage { true }
//        )
//    }
}