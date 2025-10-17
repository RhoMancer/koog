package org.example

import ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriter
import ai.koog.utils.io.use
import kotlinx.coroutines.runBlocking
import org.example.agents.calculator.CalculatorAgent

fun main() {
    runBlocking {
        val openAiKey = System.getenv("OPEN_AI_KEY")

        // Calculator agent
        CalculatorAgent.createAgent(openAiKey).use { agent ->
            println("Single-run agent started. Enter your request:")
            val input = "2+2+4"
            val result = agent.run(input)
            println("Agent completed. Result: $result")
        }

        // Chess
//        val chessGame = ChessGame()
//        Chess.createAgent(chessGame, openAiKey).use { agent ->
//            println("Chess Game started!")
//            val input = "Starting position is ${chessGame.getBoard()}. White to move!"
//            val result = agent.run(input)
//            println("Agent completed. Result: $result")
//        }

        // Banking agent
//        BankingAgent.createAgent(openAiKey).use { agent ->
//            println("Single-run agent started. Enter your request:")
//            val input = "Send 25 euros to Daniel for dinner at the restaurant."
//            val result = agent.run(input)
//            println("Agent completed. Result: $result")
//        }
    }
}
