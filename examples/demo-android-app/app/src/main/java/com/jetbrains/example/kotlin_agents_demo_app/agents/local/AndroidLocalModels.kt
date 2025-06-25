package com.jetbrains.example.kotlin_agents_demo_app.agents.local

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

object AndroidLocalModels : LLModelDefinitions {
    object Chat {
        /**
         * GGPT-4o (“o” for “omni”) is a versatile, high-intelligence flagship model.
         * It accepts both text and image inputs, and produces text outputs (including Structured Outputs).
         * It is the best model for most tasks, and is currently the most capable model
         * outside of the o-series models.
         *
         * 128,000 context window
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o">
         */
        val Gemma: LLModel = LLModel(
            provider = AndroidLocalLLMProvider, id = "gemma3-1b-it-int4", capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.Completion
            )
        )
    }
}
