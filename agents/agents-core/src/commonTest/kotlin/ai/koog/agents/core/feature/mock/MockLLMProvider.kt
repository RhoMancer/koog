package ai.koog.agents.core.feature.mock

import ai.koog.prompt.llm.LLMProvider

class MockLLMProvider(
    id: String = "test-llm-provider",
    display: String = "test-llm-display"
) : LLMProvider(id, display)
