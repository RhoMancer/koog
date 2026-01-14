package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.AIAgentBuilder;
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
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
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
    public void testOpenAILLMClient() {
        String apiKey = TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv();
        OpenAILLMClient client = JavaInteropUtils.createOpenAIClient(apiKey);
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.OpenAI.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt(
            "test-openai",
            "You are a helpful assistant.",
            "Say 'Hello from OpenAI'"
        );

        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertInstanceOf(Message.Assistant.class, responses.get(0));
        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) responses.get(0));
        assertFalse(content.isEmpty());
    }

    @Test
    @Disabled("Java/Kotlin interop issue: Continuation type incompatibility with client.execute()")
    public void testAnthropicLLMClient() {
        String apiKey = TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv();
        AnthropicLLMClient client = JavaInteropUtils.createAnthropicClient(apiKey);
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.Anthropic.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt(
            "test-anthropic",
            "You are a helpful assistant.",
            "Say 'Hello from Anthropic'"
        );

        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, AnthropicModels.Haiku_4_5, Collections.emptyList());

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertInstanceOf(Message.Assistant.class, responses.get(0));
        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) responses.get(0));
        assertFalse(content.isEmpty());
    }

    @Test
    @Disabled("Java/Kotlin interop issue: Continuation type incompatibility with executor.execute()")
    public void testMultiLLMPromptExecutor() {
        String openAIKey = TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv();
        String anthropicKey = TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv();

        OpenAILLMClient openAIClient = JavaInteropUtils.createOpenAIClient(openAIKey);
        AnthropicLLMClient anthropicClient = JavaInteropUtils.createAnthropicClient(anthropicKey);

        resourcesToClose.add((AutoCloseable) openAIClient);
        resourcesToClose.add((AutoCloseable) anthropicClient);

        MultiLLMPromptExecutor executor = JavaInteropUtils.createMultiLLMPromptExecutor(openAIClient, anthropicClient);

        // Test with OpenAI model
        Prompt openAIPrompt = JavaInteropUtils.buildSimplePrompt(
            "test-multi-openai",
            "You are a helpful assistant.",
            "Say 'OpenAI response'"
        );

        List<Message.Response> openAIResponses = JavaInteropUtils.executeExecutorBlocking(executor, openAIPrompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());

        assertNotNull(openAIResponses);
        assertFalse(openAIResponses.isEmpty());

        Prompt anthropicPrompt = JavaInteropUtils.buildSimplePrompt(
            "test-multi-anthropic",
            "You are a helpful assistant.",
            "Say 'Anthropic response'"
        );

        List<Message.Response> anthropicResponses = JavaInteropUtils.executeExecutorBlocking(executor, anthropicPrompt, AnthropicModels.Haiku_4_5, Collections.emptyList());

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
    public void testSimpleFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    // Simple strategy: just request LLM once
                    Message.Response response = JavaInteropUtils.requestLLMBlocking(context, input, true);

                    if (response instanceof Message.Assistant) {
                        return JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                    }
                    return "Unexpected response type";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Say hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("hello"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testMultiStepFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    // Multi-step strategy: request LLM multiple times
                    Message.Response response1 = JavaInteropUtils.requestLLMBlocking(context, "First step: " + input, true);

                    final String step1Result;
                    if (response1 instanceof Message.Assistant) {
                        step1Result = JavaInteropUtils.getAssistantContent((Message.Assistant) response1);
                    } else {
                        step1Result = "";
                    }

                    Message.Response response2 = JavaInteropUtils.requestLLMBlocking(
                        context,
                        "Second step, previous result was: " + step1Result,
                        true
                    );

                    if (response2 instanceof Message.Assistant) {
                        return JavaInteropUtils.getAssistantContent((Message.Assistant) response2);
                    }
                    return "Unexpected response type";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Count to 3");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testFunctionalStrategyWithManualToolHandling(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        // DESIGN LIMITATION: functionalStrategy requires manual tool call handling
        // The strategy must implement the tool execution loop manually
        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a calculator. Use the add tool to perform calculations.")
                .toolRegistry(toolRegistry)
                .functionalStrategy((context, input) -> {
                    Message.Response currentResponse = JavaInteropUtils.requestLLMBlocking(
                        context,
                        "Calculate: " + input + ". You MUST use the add tool.",
                        true
                    );

                    int iterations = 0;
                    int maxIterations = 5;

                    // Manual tool call loop
                    while (currentResponse instanceof Message.Tool.Call && iterations < maxIterations) {
                        Message.Tool.Call toolCall = (Message.Tool.Call) currentResponse;

                        // Execute the tool
                        ReceivedToolResult toolResult = JavaInteropUtils.executeToolBlocking(context, toolCall);

                        // Send result back to LLM
                        currentResponse = JavaInteropUtils.sendToolResultBlocking(context, toolResult);

                        iterations++;
                    }

                    if (currentResponse instanceof Message.Assistant) {
                        return JavaInteropUtils.getAssistantContent((Message.Assistant) currentResponse);
                    } else if (currentResponse instanceof Message.Tool.Call) {
                        return "Max iterations reached, last tool: " + JavaInteropUtils.getToolName((Message.Tool.Call) currentResponse);
                    }
                    return "Unexpected response type";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "10 + 5");

        assertNotNull(result);
        assertFalse(result.isBlank());
        // Result should contain calculation or mention of tool usage
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testSubtask(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        List<Tool<?, ?>> calculatorTools = List.of(
            calculator.getAddTool(),
            calculator.getMultiplyTool()
        );

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that coordinates calculations.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    // Use subtask to delegate calculation
                    String subtaskResult = JavaInteropUtils.runSubtaskBlocking(
                        context,
                        "Calculate: " + input,
                        input,
                        String.class,
                        calculatorTools,
                        model
                    );

                    return "Calculation result: " + subtaskResult;
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "What is 5 + 3?");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testCustomStrategyWithRetry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    // Custom strategy with retry logic
                    int maxRetries = 3;
                    String result = null;

                    for (int i = 0; i < maxRetries; i++) {
                        Message.Response response = JavaInteropUtils.requestLLMBlocking(context, input, true);

                        if (response instanceof Message.Assistant) {
                            result = JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                            if (!result.isEmpty()) {
                                break;
                            }
                        }
                    }

                    return result != null ? result : "Failed after retries";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void testCustomStrategyWithValidation(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that generates JSON.")
                .functionalStrategy((context, input) -> {
                    // Custom strategy with validation
                    Message.Response response = JavaInteropUtils.requestLLMBlocking(
                        context,
                        "Generate a JSON object with 'status' field set to 'success'",
                        true
                    );

                    if (response instanceof Message.Assistant) {
                        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) response);

                        // Validate response contains expected content
                        if (content.contains("status") && content.contains("success")) {
                            return content;
                        } else {
                            return "Validation failed: response doesn't contain expected fields";
                        }
                    }
                    return "Unexpected response type";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Generate status JSON");

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
}
