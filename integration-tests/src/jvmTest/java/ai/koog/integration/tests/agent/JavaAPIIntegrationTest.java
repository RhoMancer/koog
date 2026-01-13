package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.TestCredentials;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Koog Java API.
 *
 * Coverage:
 * - AIAgent.builder() API
 * - EventHandler feature
 *
 * Note: The following tests are excluded due to Java/Kotlin interop issues:
 * - LLM client tests: Continuation type issues with client.execute()
 * - Prompt executor tests: Same Continuation type issues
 * - Functional strategy tests: BUG #3 - Nested runBlocking causes InterruptedException
 *
 * These Java interop issues make direct client/executor testing difficult from pure Java.
 * However, AIAgent tests work because agent.run() has a simpler signature.
 */
public class JavaAPIIntegrationTest extends KoogJavaTestBase {

    private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

    @AfterEach
    public void cleanup() {
        for (AutoCloseable resource : resourcesToClose) {
            try {
                resource.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        resourcesToClose.clear();
    }

    // ============================================================================
    // AIAGENT.BUILDER() TESTS
    // ============================================================================

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testBuilderBasicUsage(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .build();

        String result = runBlocking(continuation ->
                agent.run("What is the capital of France?", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("paris"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testBuilderWithToolRegistry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        CalculatorTools calculator = new CalculatorTools();

        ToolRegistry toolRegistry = ToolRegistry.builder()
                .tool(calculator.getAddTool())
                .tool(calculator.getMultiplyTool())
                .build();

        // BUG WORKAROUND: Builder API doesn't support setting LLMParams.toolChoice
        // The model may not call tools even when available. Using explicit prompt.
        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a calculator assistant. You MUST use the add tool to perform additions. ALWAYS call the tool.")
                .toolRegistry(toolRegistry)
                .build();

        String result = runBlocking(continuation ->
                agent.run("What is 15 + 27?", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isBlank());
        // The result should contain 42 or acknowledge the calculation
        assertTrue(result.contains("42") || result.contains("fifteen") || result.contains("twenty"));
    }

    // ============================================================================
    // EVENT HANDLER TESTS
    // ============================================================================

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testEventHandler(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        AtomicBoolean agentStarted = new AtomicBoolean(false);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);
        AtomicInteger llmCallsCount = new AtomicInteger(0);
        List<String> toolsCalled = new ArrayList<>();

        CalculatorTools calculator = new CalculatorTools();

        ToolRegistry toolRegistry = ToolRegistry.builder()
                .tool(calculator.getAddTool())
                .tool(calculator.getMultiplyTool())
                .build();

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a calculator. Use the add tool when needed.")
                .toolRegistry(toolRegistry)
                .install(EventHandler.Feature, config -> {
                    config.onAgentStarting(ctx -> {
                        agentStarted.set(true);
                    });

                    config.onAgentCompleted(ctx -> {
                        agentCompleted.set(true);
                    });

                    config.onLLMCallStarting(ctx -> {
                        llmCallsCount.incrementAndGet();
                    });

                    config.onToolCallStarting(ctx -> {
                        toolsCalled.add(ctx.getToolName());
                    });
                })
                .build();

        String result = runBlocking(continuation ->
                agent.run("What is 8 + 12?", continuation)
        );

        assertNotNull(result);
        assertTrue(agentStarted.get(), "Agent should have started");
        assertTrue(agentCompleted.get(), "Agent should have completed");
        assertTrue(llmCallsCount.get() > 0, "LLM should have been called at least once");
    }

    // ============================================================================
    // HELPER METHODS AND CLASSES
    // ============================================================================

    private SingleLLMPromptExecutor createExecutor(LLModel model) {
        LLMClient client;
        if (model.getProvider() == LLMProvider.OpenAI.INSTANCE) {
            client = new OpenAILLMClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        } else if (model.getProvider() == LLMProvider.Anthropic.INSTANCE) {
            client = new AnthropicLLMClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());
        } else {
            throw new IllegalArgumentException("Unsupported provider: " + model.getProvider());
        }
        resourcesToClose.add(client);
        return new SingleLLMPromptExecutor(client);
    }

    /**
     * Simple calculator tools for testing
     */
    public static class CalculatorTools implements ToolSet {

        @ai.koog.agents.core.tools.annotations.Tool(customName = "add")
        @LLMDescription(description = "Adds two numbers together")
        public int add(
                @LLMDescription(description = "First number") int a,
                @LLMDescription(description = "Second number") int b
        ) {
            return a + b;
        }

        @ai.koog.agents.core.tools.annotations.Tool(customName = "multiply")
        @LLMDescription(description = "Multiplies two numbers")
        public int multiply(
                @LLMDescription(description = "First number") int a,
                @LLMDescription(description = "Second number") int b
        ) {
            return a * b;
        }

        public Tool<?, ?> getAddTool() {
            ToolRegistry registry = ToolRegistry.builder().tools(this).build();
            return registry.getTools().stream()
                    .filter(t -> t.getName().equals("add"))
                    .findFirst()
                    .orElseThrow();
        }

        public Tool<?, ?> getMultiplyTool() {
            ToolRegistry registry = ToolRegistry.builder().tools(this).build();
            return registry.getTools().stream()
                    .filter(t -> t.getName().equals("multiply"))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
