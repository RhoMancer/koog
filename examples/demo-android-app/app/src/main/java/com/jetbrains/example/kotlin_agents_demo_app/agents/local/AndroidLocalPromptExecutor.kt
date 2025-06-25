package com.jetbrains.example.kotlin_agents_demo_app.agents.local

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import android.content.Context

public fun simpleAndroidLocalExecutor(
    context: Context,
    modelsPath: String
): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(
        AndroidLLocalLLMClient(context, modelsPath)
    )
