package ai.koog.agents.memory.feature

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for PromptAugmenter
 */
class PromptAugmenterTest {

    @Test
    fun testAugmentPromptWithSystemMessageMode() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val relevantContext = listOf(
            "Kotlin was developed by JetBrains",
            "Kotlin is 100% interoperable with Java"
        )

        val augmentedPrompt = PromptAugmenter.augmentPrompt(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext,
            contextInsertionMode = ContextInsertionMode.SYSTEM_MESSAGE
        )

        // Verify the system message was augmented
        val systemMessage = augmentedPrompt.messages.first { it is Message.System }
        assertTrue(systemMessage.content.contains("You are a helpful assistant"))
        assertTrue(systemMessage.content.contains("Kotlin was developed by JetBrains"))
        assertTrue(systemMessage.content.contains("Kotlin is 100% interoperable with Java"))
        assertTrue(systemMessage.content.contains("Relevant information"))
    }

    @Test
    fun testAugmentPromptWithUserMessageBeforeLastMode() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val relevantContext = listOf("Kotlin was developed by JetBrains")

        val augmentedPrompt = PromptAugmenter.augmentPrompt(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext,
            contextInsertionMode = ContextInsertionMode.USER_MESSAGE_BEFORE_LAST
        )

        // Verify a new user message was inserted before the last user message
        val userMessages = augmentedPrompt.messages.filter { it is Message.User }
        assertEquals(2, userMessages.size)

        // First user message should contain the context
        assertTrue(userMessages[0].content.contains("Kotlin was developed by JetBrains"))
        // Second user message should be the original
        assertEquals("What is Kotlin?", userMessages[1].content)
    }

    @Test
    fun testAugmentPromptWithAugmentLastUserMessageMode() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val relevantContext = listOf("Kotlin was developed by JetBrains")

        val augmentedPrompt = PromptAugmenter.augmentPrompt(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext,
            contextInsertionMode = ContextInsertionMode.AUGMENT_LAST_USER_MESSAGE
        )

        // Verify the last user message was augmented
        val userMessages = augmentedPrompt.messages.filter { it is Message.User }
        assertEquals(1, userMessages.size)

        // User message should contain both context and original question
        assertTrue(userMessages[0].content.contains("Kotlin was developed by JetBrains"))
        assertTrue(userMessages[0].content.contains("What is Kotlin?"))
    }

    @Test
    fun testAugmentPromptWithEmptyContext() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val augmentedPrompt = PromptAugmenter.augmentPrompt(
            originalPrompt = originalPrompt,
            relevantContext = emptyList(),
            contextInsertionMode = ContextInsertionMode.SYSTEM_MESSAGE
        )

        // With empty context, the prompt should remain unchanged
        assertEquals(originalPrompt.messages.size, augmentedPrompt.messages.size)
    }

    @Test
    fun testAugmentPromptWithCustomTemplates() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val relevantContext = listOf("Kotlin was developed by JetBrains")
        val customSystemTemplate = "CUSTOM CONTEXT: {relevant_context}"

        val augmentedPrompt = PromptAugmenter.augmentPrompt(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext,
            contextInsertionMode = ContextInsertionMode.SYSTEM_MESSAGE,
            systemPromptTemplate = customSystemTemplate
        )

        // Verify the custom template was used
        val systemMessage = augmentedPrompt.messages.first { it is Message.System }
        assertTrue(systemMessage.content.contains("CUSTOM CONTEXT:"))
        assertTrue(systemMessage.content.contains("Kotlin was developed by JetBrains"))
    }

    @Test
    fun testAugmentPromptWithNoSystemMessage() {
        val originalPrompt = prompt("test") {
            user("What is Kotlin?")
        }

        val relevantContext = listOf("Kotlin was developed by JetBrains")

        val augmentedPrompt = PromptAugmenter.augmentPrompt(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext,
            contextInsertionMode = ContextInsertionMode.SYSTEM_MESSAGE
        )

        // Verify a new system message was added at the beginning
        val systemMessages = augmentedPrompt.messages.filter { it is Message.System }
        assertEquals(1, systemMessages.size)
        assertTrue(systemMessages[0].content.contains("Kotlin was developed by JetBrains"))

        // System message should be first
        assertTrue(augmentedPrompt.messages.first() is Message.System)
    }

    @Test
    fun testContextNumbering() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("Tell me about programming languages")
        }

        val relevantContext = listOf(
            "First context item",
            "Second context item",
            "Third context item"
        )

        val augmentedPrompt = PromptAugmenter.augmentPrompt(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext,
            contextInsertionMode = ContextInsertionMode.SYSTEM_MESSAGE
        )

        val systemMessage = augmentedPrompt.messages.first { it is Message.System }
        assertTrue(systemMessage.content.contains("[1] First context item"))
        assertTrue(systemMessage.content.contains("[2] Second context item"))
        assertTrue(systemMessage.content.contains("[3] Third context item"))
    }
}
