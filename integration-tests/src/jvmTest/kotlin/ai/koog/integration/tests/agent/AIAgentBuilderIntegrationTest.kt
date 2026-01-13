package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.tools.SimpleCalculatorTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for AIAgent.builder() API and functionalStrategy usage patterns.
 * These tests verify that the builder API works correctly with real LLM providers.
 */
class AIAgentBuilderIntegrationTest : AIAgentTestBase() {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            AIAgentTestBase.setup()
        }

        @JvmStatic
        fun allModels(): Stream<LLModel> = AIAgentTestBase.allModels()

        @JvmStatic
        fun latestModels(): Stream<LLModel> = getLatestModels()
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderBasicUsage(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant. Be brief.")
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say hello in one sentence.")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result.shouldNotBeBlank()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderWithToolRegistry(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(SimpleCalculatorTool)
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                // BUG WORKAROUND: Builder API doesn't support setting LLMParams.toolChoice
                // This means we can't force the model to use tools like in the old API.
                // The model may choose not to call tools even when they're available.
                // Workaround: Use a more explicit prompt and rely on model behavior.
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt(
                        "You are a helpful calculator assistant. " +
                            "You MUST use the calculator tool to perform calculations. " +
                            "ALWAYS call the tool, then provide the result."
                    )
                    .toolRegistry(toolRegistry)
                    .graphStrategy(singleRunStrategy(ToolCalls.SEQUENTIAL))
                    .temperature(1.0)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Calculate 15 times 3")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                    withClue("The ${SimpleCalculatorTool.name} tool should be called") {
                        actualToolCalls shouldContain SimpleCalculatorTool.name
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderWithGraphStrategy(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(SimpleCalculatorTool)
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                // BUG WORKAROUND: Builder API doesn't support setting LLMParams.toolChoice
                // See note in integration_BuilderWithToolRegistry
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt(
                        "You are a helpful calculator assistant. " +
                            "You MUST use the calculator tool to perform calculations. " +
                            "ALWAYS call the tool, then provide the result."
                    )
                    .toolRegistry(toolRegistry)
                    .graphStrategy(singleRunStrategy(ToolCalls.SEQUENTIAL))
                    .temperature(1.0)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("What is 7 multiplied by 8?")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result.shouldNotBeBlank()
                    withClue("Calculator tool should have been called") {
                        actualToolCalls shouldContain SimpleCalculatorTool.name
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_FunctionalStrategyWithLambda(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val strategy = functionalStrategy<String, String>("echo-strategy") { input ->
                    // Simple strategy that requests LLM and returns the response
                    val response = requestLLM(
                        "User says: $input. Respond with: 'Acknowledged: ' and repeat their message."
                    )
                    when (response) {
                        is Message.Assistant -> response.content
                        else -> "No response"
                    }
                }

                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Hello from test")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        lowercase() shouldContain "hello"
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_FunctionalStrategySimple(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val strategy = functionalStrategy<String, String>("summarize") { input ->
            when (val response = requestLLM("Summarize in one sentence: $input")) {
                is Message.Assistant -> response.content
                else -> "Unable to summarize"
            }
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant that provides concise summaries.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run(
                    "Kotlin is a modern programming language that combines " +
                        "object-oriented and functional programming features."
                )

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        length shouldBeGreaterThan 10
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_FunctionalStrategyWithMultipleSteps(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val strategy = functionalStrategy<String, String>("multi-step") { input ->
            // Step 1: Ask for ideas
            val ideas = when (val ideasResponse = requestLLM("Give me 2 brief ideas about: $input")) {
                is Message.Assistant -> ideasResponse.content
                else -> "No ideas"
            }

            // Step 2: Pick the best idea
            val refinedIdea = when (
                val response = requestLLM(
                    "Pick the best idea from: $ideas. Explain in one sentence why."
                )
            ) {
                is Message.Assistant -> response.content
                else -> "No refinement"
            }

            "Ideas: $ideas\n\nBest choice: $refinedIdea"
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a creative assistant.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("sustainable energy")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        shouldContain("Ideas:")
                        shouldContain("Best choice:")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderMethodChaining(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                // Test that all builder methods can be chained fluently
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .temperature(0.8)
                    .numberOfChoices(1)
                    .maxIterations(15)
                    .toolRegistry(ToolRegistry.EMPTY)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say 'configuration test passed' in different words.")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                }

                // Verify configuration was applied
                agent.agentConfig shouldNotBeNull {
                    maxAgentIterations shouldBe 15
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_BuilderWithMultipleFeatures(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            val eventCallbacks = mutableListOf<String>()

            val agent = AIAgent.builder()
                .promptExecutor(getExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant. Be very brief.")
                .temperature(0.7)
                .install(EventHandler.Feature) { config ->
                    config.onAgentStarting {
                        eventCallbacks.add("agent_started")
                    }
                    config.onAgentCompleted {
                        eventCallbacks.add("agent_completed")
                    }
                    config.onLLMCallStarting {
                        eventCallbacks.add("llm_call_started")
                    }
                    config.onLLMCallCompleted {
                        eventCallbacks.add("llm_call_completed")
                    }
                }
                .build()

            val result = agent.run("Reply with just 'OK'")

            result.shouldNotBeBlank()
            eventCallbacks shouldNotBeNull {
                shouldContain("agent_started")
                shouldContain("agent_completed")
                shouldContain("llm_call_started")
                shouldContain("llm_call_completed")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_FunctionalStrategyErrorHandling(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val strategy = functionalStrategy<String, String>("error-handling") { input ->
            try {
                when (val response = requestLLM("Process: $input")) {
                    is Message.Assistant -> "Success: ${response.content}"
                    else -> "Fallback: Unexpected response type"
                }
            } catch (e: Exception) {
                "Error handled: ${e.message}"
            }
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("test input")

                with(state) {
                    // Should not have errors because strategy handles them
                    errors.shouldBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        shouldContain("Success:")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_FunctionalStrategyWithToolCallLoop(model: LLModel) = runTest(timeout = 240.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(SimpleCalculatorTool)
        }

        // FRAMEWORK LIMITATION: functionalStrategy requires manual tool call handling
        // Unlike graphStrategy which handles tool calls automatically in the graph,
        // functional strategies must implement their own tool call loop.
        // This is by design - functional strategies provide low-level control.
        val strategy = functionalStrategy<String, String>("manual-tool-handling") { input ->
            var currentResponse = requestLLM(input)
            var iterations = 0
            val maxIterations = 5

            // Manual tool call loop
            while (currentResponse is Message.Tool.Call && iterations < maxIterations) {
                // Execute the tool
                val toolResult = executeTool(currentResponse)
                // Send result back to LLM
                currentResponse = sendToolResult(toolResult)
                iterations++
            }

            when (currentResponse) {
                is Message.Assistant -> currentResponse.content
                is Message.Tool.Call -> "Max iterations reached, last tool: ${currentResponse.tool}"
                else -> "Unexpected response type"
            }
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt(
                        "You are a helpful calculator assistant. " +
                            "Use the calculator tool to perform calculations, then provide the result."
                    )
                    .toolRegistry(toolRegistry)
                    .functionalStrategy(strategy)
                    .temperature(0.8)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("What is 25 times 4?")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        (contains("100") || contains("hundred")).shouldBe(true)
                    }

                    withClue("SimpleCalculatorTool should have been called in the manual loop") {
                        actualToolCalls shouldContain SimpleCalculatorTool.name
                    }
                }
            }
        }
    }

    // ============================================================================
    // ADDITIONAL TEST SCENARIOS FROM COMPREHENSIVE PLAN
    // ============================================================================

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_BuilderWithTemperatureControl(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                // Test low temperature (deterministic)
                val deterministicAgent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant. Answer with exactly '42'.")
                    .temperature(0.0) // Deterministic
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = deterministicAgent.run("What is the answer to life, the universe, and everything?")

                with(state) {
                    errors.shouldBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        shouldContain("42")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_BuilderWithMaxIterations(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .maxIterations(3) // Very low to test limit
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("List 5 numbers from 1 to 5.")

                with(state) {
                    // Should complete even with low iteration limit for simple task
                    result.shouldNotBeBlank()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_FunctionalStrategyWithExceptionHandling(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val strategyWithErrorHandling = functionalStrategy<String, String>("error-handling") { input ->
                    try {
                        val response = requestLLM(input)
                        when (response) {
                            is Message.Assistant -> response.content
                            else -> "Unexpected response type: ${response::class.simpleName}"
                        }
                    } catch (e: Exception) {
                        "Error occurred: ${e.message}"
                    }
                }

                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategyWithErrorHandling)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say hello")

                with(state) {
                    result.shouldNotBeBlank()
                    // Should not contain error message for simple request
                    (result.contains("Error occurred")).shouldBe(false)
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_BuilderWithNumberOfChoices(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a creative assistant.")
                    .numberOfChoices(1) // Single choice for deterministic testing
                    .temperature(0.7)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say 'test passed' in a creative way.")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun integration_FunctionalStrategyWithContextAccess(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val strategyWithContext = functionalStrategy<String, String>("context-aware") { input ->
                    // Access context information
                    val agentId = agentId
                    val response = requestLLM("Agent $agentId processing: $input")

                    when (response) {
                        is Message.Assistant -> "Processed by $agentId: ${response.content}"
                        else -> "Unexpected response"
                    }
                }

                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategyWithContext)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say hello")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                    result shouldContain "Processed by"
                }
            }
        }
    }
}
