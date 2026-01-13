package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.AIAgentBuilder;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
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
import org.junit.jupiter.api.Disabled;
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
 * <p>
 * Coverage:
 * - AIAgent.builder() API (enabled)
 * - EventHandler feature (enabled)
 * - LLM client tests (@Disabled - Continuation type issues)
 * - Prompt executor tests (@Disabled - Continuation type issues)
 * - Functional strategy tests (@Disabled - BUG #3: nested runBlocking causes InterruptedException)
 * <p>
 * Some tests are disabled due to Java/Kotlin interop issues but kept for documentation
 * and potential future fixes when the interop issues are resolved.
 */
public class JavaAPIIntegrationTest extends KoogJavaTestBase {

    private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

    @AfterEach
    public void cleanup() {
        for (AutoCloseable resource : resourcesToClose) {
            try {
                resource.close();
            } catch (Exception e) {
            }
        }
        resourcesToClose.clear();
    }

    @Test
    @Disabled("Java/Kotlin interop issue: Continuation type incompatibility with client.execute()")
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
                (scope, continuation) -> client.execute(prompt, OpenAIModels.Chat.GPT4o, List.of(), continuation)
        );

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0) instanceof Message.Assistant);
        assertFalse(((Message.Assistant) responses.get(0)).getContent().isEmpty());
    }

    @Test
    @Disabled("Java/Kotlin interop issue: Continuation type incompatibility with client.execute()")
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
                (scope, continuation) -> client.execute(prompt, AnthropicModels.Haiku_4_5, List.of(), continuation)
        );

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0) instanceof Message.Assistant);
        assertFalse(((Message.Assistant) responses.get(0)).getContent().isEmpty());
    }

    @Test
    @Disabled("Java/Kotlin interop issue: Continuation type incompatibility with executor.execute()")
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
                (scope, continuation) -> executor.execute(
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
    @Disabled("Java/Kotlin interop issue: Continuation type incompatibility with executor.execute()")
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
                (scope, continuation) -> executor.execute(
                        openAIPrompt,
                        OpenAIModels.Chat.GPT4o,
                        List.of(),
                        continuation
                )
        );

        assertNotNull(openAIResponses);
        assertFalse(openAIResponses.isEmpty());

        Prompt anthropicPrompt = Prompt.builder("test-multi-anthropic")
                .system("You are a helpful assistant.")
                .user("Say 'Anthropic response'")
                .build();

        @SuppressWarnings("unchecked")
        List<Message.Response> anthropicResponses = (List<Message.Response>) BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> executor.execute(
                        anthropicPrompt,
                        AnthropicModels.Haiku_4_5,
                        List.of(),
                        continuation
                )
        );

        assertNotNull(anthropicResponses);
        assertFalse(anthropicResponses.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testBuilderBasicUsage(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

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
        assertTrue(result.contains("Paris"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testBuilderWithToolRegistry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

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
        assertTrue(result.contains("42") || result.contains("fifteen") || result.contains("twenty"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void testEventHandler(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AtomicBoolean agentStarted = new AtomicBoolean(false);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);
        AtomicInteger llmCallsCount = new AtomicInteger(0);
        List<String> toolsCalled = new ArrayList<>();

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgentBuilder builder = AIAgent.builder();
        builder.promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a calculator. Use the add tool when needed.")
            .toolRegistry(toolRegistry);

        JavaInteropUtils.installEventHandler(builder, agentStarted, agentCompleted, llmCallsCount, toolsCalled);

        AIAgent<String, String> agent = builder.build();

        String result = runBlocking(continuation ->
            agent.run("What is 8 + 12?", continuation)
        );

        assertNotNull(result);
        assertTrue(agentStarted.get(), "Agent should have started");
        assertTrue(agentCompleted.get(), "Agent should have completed");
        assertTrue(llmCallsCount.get() > 0, "LLM should have been called at least once");
        assertFalse(toolsCalled.isEmpty(), "Tools should have been called");
        assertTrue(toolsCalled.contains("add"), "Add tool should have been called");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testSimpleFunctionalStrategy(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createSingleExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    try {
                        // Simple strategy: just request LLM once
                        Message.Response response = BuildersKt.runBlocking(
                                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                                (scope, continuation) -> context.requestLLM(input, true, continuation)
                        );

                        if (response instanceof Message.Assistant) {
                            return ((Message.Assistant) response).getContent();
                        }
                        return "Unexpected response type";
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
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
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testMultiStepFunctionalStrategy(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createSingleExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    try {
                        // Multi-step strategy: request LLM multiple times
                        Message.Response response1 = BuildersKt.runBlocking(
                                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                                (scope, continuation) -> context.requestLLM("First step: " + input, true, continuation)
                        );

                        final String step1Result;
                        if (response1 instanceof Message.Assistant) {
                            step1Result = ((Message.Assistant) response1).getContent();
                        } else {
                            step1Result = "";
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
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
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
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testFunctionalStrategyWithManualToolHandling(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createSingleExecutor(model);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        // DESIGN LIMITATION: functionalStrategy requires manual tool call handling
        // The strategy must implement the tool execution loop manually
        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a calculator. Use the add tool to perform calculations.")
                .toolRegistry(toolRegistry)
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    try {
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
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

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testSubtask(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createSingleExecutor(model);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        List<Tool<?, ?>> calculatorTools = List.of(
                calculator.getAddTool(),
                calculator.getMultiplyTool()
        );

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that coordinates calculations.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    try {
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("What is 5 + 3?", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testCustomStrategyWithRetry(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createSingleExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    try {
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
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
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testCustomStrategyWithValidation(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());

        SingleLLMPromptExecutor executor = createSingleExecutor(model);

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that generates JSON.")
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    try {
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                })
                .build();

        String result = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> agent.run("Generate status JSON", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    private MultiLLMPromptExecutor createExecutor(LLModel model) {
        LLMClient client;
        if (model.getProvider() == LLMProvider.OpenAI.INSTANCE) {
            client = JavaInteropUtils.createOpenAIClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        } else if (model.getProvider() == LLMProvider.Anthropic.INSTANCE) {
            client = JavaInteropUtils.createAnthropicClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());
        } else {
            throw new IllegalArgumentException("Unsupported provider: " + model.getProvider());
        }
        resourcesToClose.add((AutoCloseable) client);
        return new MultiLLMPromptExecutor(client);
    }

    private SingleLLMPromptExecutor createSingleExecutor(LLModel model) {
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
}
