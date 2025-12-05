package ai.koog.agents.core.agent.concierge

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.text.TextContentBuilderBase

internal class KoogelisHistoryCompressionStrategy(
    private val contextReductionConfig: AgentConfiguration.ContextReductionConfig
) : HistoryCompressionStrategy() {

    // Shared computed view of the conversation to avoid duplication between methods
    private data class ComputedHistory(
        val trailingToolCalls: List<Message>,
        val historyWithoutTrailingTools: List<Message>,
        val preserveSystem: Boolean,
        val maxMessages: Int?,
        val maxTokens: Int?,
        val keepRecent: Int,
        val systemMessages: List<Message>,
        val firstUserMessage: Message?,
        val nonPreserved: List<Message>,
        val recentToKeep: List<Message>,
        val candidateForSummary: List<Message>,
        val preservedHeadForSummary: List<Message>,
        val exceedsMaxMessages: Boolean,
        val exceedsMaxTokens: Boolean,
        val compressedSizeWithoutTldr: Int,
    )

    private fun computeHistory(
        originalMessages: List<Message>,
        memoryMessages: List<Message>,
        config: AgentConfiguration.ContextReductionConfig,
        approxTokens: (Message) -> Int,
    ): ComputedHistory {
        val trailingToolCalls = originalMessages.takeLastWhile { it is Message.Tool.Call }
        val historyWithoutTrailingTools =
            if (trailingToolCalls.isNotEmpty()) originalMessages.dropLast(trailingToolCalls.size) else originalMessages

        val preserveSystem = config.preserveSystemMessages ?: true
        val maxMessages = config.maxMessages
        val maxTokens = config.maxTokens
        val keepRecent = (config.keepRecentMessagesCount ?: 0).coerceAtLeast(0)

        val systemMessages = if (preserveSystem) historyWithoutTrailingTools.filterIsInstance<Message.System>() else emptyList()
        val firstUserMessage = historyWithoutTrailingTools.firstOrNull { it is Message.User }

        val nonPreserved = historyWithoutTrailingTools.filter { msg ->
            (!preserveSystem || msg !is Message.System) && msg != firstUserMessage
        }
        val recentToKeep = if (keepRecent > 0) nonPreserved.takeLast(keepRecent) else emptyList()
        val candidateForSummary = nonPreserved.dropLast(recentToKeep.size)

        val exceedsMaxMessages = maxMessages?.let { historyWithoutTrailingTools.size > it } ?: false
        val approxTokenCount = candidateForSummary.sumOf { approxTokens(it) }
        val exceedsMaxTokens = maxTokens?.let { approxTokenCount > it } ?: false

        val preservedHeadForSummary = buildList<Message> {
            addAll(systemMessages)
            firstUserMessage?.let { add(it) }
        }

        val compressedSizeWithoutTldr = buildList<Message> {
            addAll(systemMessages)
            firstUserMessage?.let { add(it) }
            addAll(memoryMessages)
            addAll(recentToKeep)
            addAll(trailingToolCalls)
        }.size

        return ComputedHistory(
            trailingToolCalls = trailingToolCalls,
            historyWithoutTrailingTools = historyWithoutTrailingTools,
            preserveSystem = preserveSystem,
            maxMessages = maxMessages,
            maxTokens = maxTokens,
            keepRecent = keepRecent,
            systemMessages = systemMessages,
            firstUserMessage = firstUserMessage,
            nonPreserved = nonPreserved,
            recentToKeep = recentToKeep,
            candidateForSummary = candidateForSummary,
            preservedHeadForSummary = preservedHeadForSummary,
            exceedsMaxMessages = exceedsMaxMessages,
            exceedsMaxTokens = exceedsMaxTokens,
            compressedSizeWithoutTldr = compressedSizeWithoutTldr,
        )
    }

    private fun composeCompressed(
        computed: ComputedHistory,
        memoryMessages: List<Message>,
        tldrMessages: List<Message>,
    ): List<Message> {
        val compressedMessages = buildList {
            addAll(computed.systemMessages)
            computed.firstUserMessage?.let { add(it) }
            addAll(memoryMessages)
            addAll(computed.recentToKeep)
            addAll(tldrMessages)
            addAll(computed.trailingToolCalls)
        }

        // Respect trimming rule used previously when no TL;DR produced
        return if (computed.maxMessages != null && tldrMessages.isEmpty() && compressedMessages.size > computed.maxMessages) {
            val head = buildList {
                if (computed.preserveSystem) addAll(computed.systemMessages)
                computed.firstUserMessage?.let { add(it) }
                addAll(memoryMessages)
            }
            if (compressedMessages.size <= head.size) compressedMessages
            else {
                val tailQuota = (computed.maxMessages - head.size).coerceAtLeast(0)
                val tail = compressedMessages.drop(head.size).takeLast(tailQuota)
                head + tail
            }
        } else compressedMessages
    }

    /**
     * Returns true if history compression is required based on the current configuration
     * and the provided messages; false otherwise.
     */
    fun isCompressionNeeded(
        originalMessages: List<Message>,
        memoryMessages: List<Message>
    ): Boolean {
        val computed = computeHistory(
            originalMessages = originalMessages,
            memoryMessages = memoryMessages,
            config = contextReductionConfig,
            approxTokens = ::approxTokens,
        )

        val willSummarize = computed.candidateForSummary.isNotEmpty() &&
            (computed.exceedsMaxMessages || computed.exceedsMaxTokens)
        if (willSummarize) return true

        return computed.maxMessages != null && computed.compressedSizeWithoutTldr > computed.maxMessages
    }

    override suspend fun compress(
        llmSession: AIAgentLLMWriteSession,
        memoryMessages: List<Message>
    ) {
        val originalMessages = llmSession.prompt.messages

        // Skip if compression isn't needed
        if (!isCompressionNeeded(originalMessages, memoryMessages)) return

        val result = compressMessages(
            originalMessages = originalMessages,
            memoryMessages = memoryMessages,
            config = contextReductionConfig,
            approxTokens = ::approxTokens,
            summarize = { preservedHead, candidate ->
                // Compose a temporary prompt from preservedHead + candidate and ask LLM for TL;DR
                val tmpPrompt = buildList<Message> {
                    addAll(preservedHead)
                    addAll(candidate)
                }
                llmSession.prompt = llmSession.prompt.withMessages { tmpPrompt }
                llmSession.appendPrompt {
                    user {
                        summarizeInTLDR(contextReductionConfig.summaryPrefix)
                    }
                }
                listOf(llmSession.requestLLMWithoutTools())
            }
        )

        llmSession.prompt = llmSession.prompt.withMessages { result }
    }

    internal suspend fun compressMessages(
        originalMessages: List<Message>,
        memoryMessages: List<Message>,
        config: AgentConfiguration.ContextReductionConfig,
        approxTokens: (Message) -> Int,
        summarize: suspend (preservedHead: List<Message>, candidateForSummary: List<Message>) -> List<Message>,
    ): List<Message> {
        val computed = computeHistory(
            originalMessages = originalMessages,
            memoryMessages = memoryMessages,
            config = config,
            approxTokens = approxTokens,
        )

        val needTldr = computed.candidateForSummary.isNotEmpty() &&
            (computed.exceedsMaxMessages || computed.exceedsMaxTokens)

        val tldrMessages: List<Message> = if (needTldr) {
            summarize(computed.preservedHeadForSummary, computed.candidateForSummary)
        } else emptyList()

        return composeCompressed(computed, memoryMessages, tldrMessages)
    }

    // Very rough token approximation based on character count; keeps us within limits when maxTokens is provided
    private fun approxTokens(message: Message): Int {
        // Common heuristic: ~4 chars per token for English-like text
        val chars = message.content.length
        return (chars / 4).coerceAtLeast(1)
    }

    // Local helper mirroring the examples' summarizeInTLDR prompt, with optional prefix
    private fun TextContentBuilderBase<*>.summarizeInTLDR(prefix: String?) =
        markdown {
            if (!prefix.isNullOrBlank()) {
                +prefix
                br()
            }
            +"Create a comprehensive summary (TL;DR) of the conversation above."
            br()
            +"Include: key objectives/problems, tools used and outcomes, critical findings, current status, and any open questions."
        }
}
