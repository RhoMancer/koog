package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
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
import kotlinx.coroutines.BuildersKt;
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
 * Comprehensive integration tests for Koog Java API.
 *
 * Coverage:
 * - LLM clients (OpenAI, Anthropic)
 * - Prompt executors (SingleLLM, MultiLLM)
 * - AIAgent.builder() API
 * - AIAgent.run() with different functional strategies
 * - Custom functional strategies
 * - EventHandler feature
 * - subtask() functionality
 */
public class JavaAPIIntegrationTest {

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
    // LLM CLIENT TESTS
    // ============================================================================

    @Test
    public void testOpenAILLMClient() throws Exception {
        String apiKey = TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv();
        OpenAILLMClient client = new OpenAILLMClient(apiKey);
        resourcesToClose.add(client);

        assertEquals(LLMProvider.OpenAI.INSTANCE, client.llmProvider());

        Prompt prompt = Prompt.builder("test-openai")
                .system("You are a helpful assistant.")
                .user("Say 'Hello from OpenAI'")
                .build();

        @SuppressWarnings("unchecked")
        List<Message.Response> responses = (List<Message.Response>) BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> (Object) client.execute(prompt, OpenAIModels.Chat.GPT4o, List.of(), continuation)
        );

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0) instanceof Message.Assistant);
        assertFalse(((Message.Assistant) responses.get(0)).getContent().isEmpty());
    }

    @Test
    public void testAnthropicLLMClient() throws Exception {
        String apiKey = TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv();
        AnthropicLLMClient client = new AnthropicLLMClient(apiKey);
        resourcesToClose.add(client);

        assertEquals(LLMProvider.Anthropic.INSTANCE, client.llmProvider());

        Prompt prompt = Prompt.builder("test-anthropic")
                .system("You are a helpful assistant.")
                .user("Say 'Hello from Anthropic'")
                .build();

        @SuppressWarnings("unchecked")
        List<Message.Response> responses = (List<Message.Response>) BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> (Object) client.execute(prompt, AnthropicModels.Haiku_4_5, List.of(), continuation)
        );

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0) instanceof Message.Assistant);
        assertFalse(((Message.Assistant) responses.get(0)).getContent().isEmpty());
    }

    // ============================================================================
    // PROMPT EXECUTOR TESTS
    // ============================================================================

    @Test
    public void testSingleLLMPromptExecutor() throws Exception {
        String apiKey = TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv();
        LLMClient client = new OpenAILLMClient(apiKey);
        resourcesToClose.add(client);

        SingleLLMPromptExecutor executor = new SingleLLMPromptExecutor(client);

        Prompt prompt = Prompt.builder("test-single-executor")
                .system("You are a helpful assistant.")
                .user("What is 2+2?")
                .build();

        @SuppressWarnings("unchecked")
        List<Message.Response> responses = (List<Message.Response>) BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> (Object) executor.execute(
                        prompt,
                        OpenAIModels.Chat.GPT4o,
                        List.of(),
                        continuation
                )
        );

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0) instanceof Message.Assistant);
        String content = ((Message.Assistant) responses.get(0)).getContent();
        assertTrue(content.contains("4"));
    }

    @Test
    public void testMultiLLMPromptExecutor() throws Exception {
        String openAIKey = TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv();
        String anthropicKey = TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv();

        OpenAILLMClient openAIClient = new OpenAILLMClient(openAIKey);
        AnthropicLLMClient anthropicClient = new AnthropicLLMClient(anthropicKey);

        resourcesToClose.add(openAIClient);
        resourcesToClose.add(anthropicClient);

        MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(
                new kotlin.Pair<>(LLMProvider.OpenAI.INSTANCE, openAIClient),
                new kotlin.Pair<>(LLMProvider.Anthropic.INSTANCE, anthropicClient)
        );

        // Test with OpenAI model
        Prompt openAIPrompt = Prompt.builder("test-multi-openai")
                .system("You are a helpful assistant.")
                .user("Say 'OpenAI response'")
                .build();

        @SuppressWarnings("unchecked")
        List<Message.Response> openAIResponses = (List<Message.Response>) BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> (Object) executor.execute(
                        openAIPrompt,
                        OpenAIModels.Chat.GPT4o,
                        List.of(),
                        continuation
                )
        );

        assertNotNull(openAIResponses);
        assertFalse(openAIResponses.isEmpty());

        // Test with Anthropic model
        Prompt anthropicPrompt = Prompt.builder("test-multi-anthropic")
                .system("You are a helpful assistant.")
                .user("Say 'Anthropic response'")
                .build();

        @SuppressWarnings("unchecked")
        List<Message.Response> anthropicResponses = (List<Message.Response>) BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> (Object) executor.execute(
                        anthropicPrompt,
                        AnthropicModels.Haiku_4_5,
                        List.of(),
                        continuation
                )
        );

        assertNotNull(anthropicResponses);
        assertFalse(anthropicResponses.isEmpty());
    }

    // ============================================================================
    // AIAGENT.BUILDER() TESTS
    // ============================================================================

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testBuilderBasicUsage(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("What is the capital of France?", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("paris"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testBuilderWithToolRegistry(LLModel model) throws Exception {
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

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("What is 15 + 27?", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isBlank());
        // The result should contain 42 or acknowledge the calculation
        assertTrue(result.contains("42") || result.contains("fifteen") || result.contains("twenty"));
    }

    // ============================================================================
    // FUNCTIONAL STRATEGY TESTS
    // ============================================================================

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testSimpleFunctionalStrategy(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    // Simple strategy: just request LLM once
                    Message.Response response = BuildersKt.runBlocking(
                            kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                            (scope, continuation) -> context.requestLLM(input, true, continuation)
                    );

                    if (response instanceof Message.Assistant) {
                        return ((Message.Assistant) response).getContent();
                    }
                    return "Unexpected response type";
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("Say hello", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("hello"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testMultiStepFunctionalStrategy(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    // Multi-step strategy: request LLM multiple times
                    Message.Response response1 = BuildersKt.runBlocking(
                            kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                            (scope, continuation) -> context.requestLLM("First step: " + input, true, continuation)
                    );

                    String step1Result = "";
                    if (response1 instanceof Message.Assistant) {
                        step1Result = ((Message.Assistant) response1).getContent();
                    }

                    Message.Response response2 = BuildersKt.runBlocking(
                            kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                            (scope, continuation) -> context.requestLLM(
                                    "Second step, previous result was: " + step1Result,
                                    true,
                                    continuation
                            )
                    );

                    if (response2 instanceof Message.Assistant) {
                        return ((Message.Assistant) response2).getContent();
                    }
                    return "Unexpected response type";
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("Count to 3", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testFunctionalStrategyWithManualToolHandling(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        CalculatorTools calculator = new CalculatorTools();

        ToolRegistry toolRegistry = ToolRegistry.builder()
                .tool(calculator.getAddTool())
                .tool(calculator.getMultiplyTool())
                .build();

        // DESIGN LIMITATION: functionalStrategy requires manual tool call handling
        // The strategy must implement the tool execution loop manually
        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a calculator. Use the add tool to perform calculations.")
                .toolRegistry(toolRegistry)
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    Message.Response currentResponse = BuildersKt.runBlocking(
                            kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                            (scope, continuation) -> context.requestLLM(
                                    "Calculate: " + input + ". You MUST use the add tool.",
                                    true,
                                    continuation
                            )
                    );

                    int iterations = 0;
                    int maxIterations = 5;

                    // Manual tool call loop
                    while (currentResponse instanceof Message.Tool.Call && iterations < maxIterations) {
                        Message.Tool.Call toolCall = (Message.Tool.Call) currentResponse;

                        // Execute the tool
                        ReceivedToolResult toolResult = BuildersKt.runBlocking(
                                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                                (scope, continuation) -> context.executeTool(toolCall, continuation)
                        );

                        // Send result back to LLM
                        currentResponse = BuildersKt.runBlocking(
                                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                                (scope, continuation) -> context.sendToolResult(toolResult, continuation)
                        );

                        iterations++;
                    }

                    if (currentResponse instanceof Message.Assistant) {
                        return ((Message.Assistant) currentResponse).getContent();
                    } else if (currentResponse instanceof Message.Tool.Call) {
                        return "Max iterations reached, last tool: " + ((Message.Tool.Call) currentResponse).getTool();
                    }
                    return "Unexpected response type";
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("10 + 5", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isBlank());
        // Result should contain calculation or mention of tool usage
    }

    // ============================================================================
    // EVENT HANDLER TESTS
    // ============================================================================

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testEventHandler(LLModel model) throws Exception {
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

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("What is 8 + 12?", continuation)
        );

        assertNotNull(result);
        assertTrue(agentStarted.get(), "Agent should have started");
        assertTrue(agentCompleted.get(), "Agent should have completed");
        assertTrue(llmCallsCount.get() > 0, "LLM should have been called at least once");
    }

    // ============================================================================
    // SUBTASK TESTS
    // ============================================================================

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testSubtask(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        CalculatorTools calculator = new CalculatorTools();

        List<Tool<?, ?>> calculatorTools = List.of(
                calculator.getAddTool(),
                calculator.getMultiplyTool()
        );

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that coordinates calculations.")
                .toolRegistry(ToolRegistry.builder()
                        .tool(calculator.getAddTool())
                        .tool(calculator.getMultiplyTool())
                        .build())
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    // Use subtask to delegate calculation
                    String subtaskResult = BuildersKt.runBlocking(
                            kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                            (scope, continuation) -> context
                                    .subtask("Calculate: " + input)
                                    .withInput(input)
                                    .withOutput(String.class)
                                    .withTools(calculatorTools)
                                    .useLLM(model)
                                    .run()
                    );

                    return "Calculation result: " + subtaskResult;
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("What is 5 + 3?", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ============================================================================
    // CUSTOM STRATEGY TESTS
    // ============================================================================

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testCustomStrategyWithRetry(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    // Custom strategy with retry logic
                    int maxRetries = 3;
                    String result = null;

                    for (int i = 0; i < maxRetries; i++) {
                        Message.Response response = BuildersKt.runBlocking(
                                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                                (scope, continuation) -> context.requestLLM(input, true, continuation)
                        );

                        if (response instanceof Message.Assistant) {
                            result = ((Message.Assistant) response).getContent();
                            if (!result.isEmpty()) {
                                break;
                            }
                        }
                    }

                    return result != null ? result : "Failed after retries";
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("Hello", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testCustomStrategyWithValidation(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that generates JSON.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    // Custom strategy with validation
                    Message.Response response = BuildersKt.runBlocking(
                            kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                            (scope, continuation) -> context.requestLLM(
                                    "Generate a JSON object with 'status' field set to 'success'",
                                    true,
                                    continuation
                            )
                    );

                    if (response instanceof Message.Assistant) {
                        String content = ((Message.Assistant) response).getContent();

                        // Validate response contains expected content
                        if (content.contains("status") && content.contains("success")) {
                            return content;
                        } else {
                            return "Validation failed: response doesn't contain expected fields";
                        }
                    }
                    return "Unexpected response type";
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("Generate status JSON", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
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
