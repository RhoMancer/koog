package com.jetbrains.example.kotlin_agents_demo_app.agents.local

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

object AndroidLocalModels : LLModelDefinitions {
    object Chat {
        /**
         * https://huggingface.co/google/gemma-3-1b-it
         */
        val Gemma: LLModel = LLModel(
            provider = AndroidLocalLLMProvider, id = "gemma3-1b-it-int4", capabilities = listOf(
                LLMCapability.Completion
            )
        )

        /**
         * https://huggingface.co/litert-community/Hammer2.1-1.5b
         */
        val Hammer: LLModel = LLModel(
            provider = AndroidLocalLLMProvider, id = "hammer2.1_1.5b_q8_ekv4096", capabilities = listOf(
                LLMCapability.Tools,
                LLMCapability.Completion
            )
        )
    }
}
