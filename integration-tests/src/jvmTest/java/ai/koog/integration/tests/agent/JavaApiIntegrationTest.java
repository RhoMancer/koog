package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
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
 */
public class JavaApiIntegrationTest extends KoogJavaTestBase {

    private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

    @AfterEach
    public void cleanup() throws Exception {
        for (AutoCloseable resource : resourcesToClose) {
            resource.close();
        }
        resourcesToClose.clear();
    }

    @Test
    public void integration_testOpenAILLMClient() {
        String apiKey = TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv();
        OpenAILLMClient client = JavaInteropUtils.createOpenAIClient(apiKey);
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.OpenAI.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt("test-openai", "You are a helpful assistant.", "Say 'Hello from OpenAI'");
        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertInstanceOf(Message.Assistant.class, responses.get(0));
        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) responses.get(0));
        assertFalse(content.isEmpty());
    }

    @Test
    public void integration_testAnthropicLLMClient() {
        String apiKey = TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv();
        AnthropicLLMClient client = JavaInteropUtils.createAnthropicClient(apiKey);
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.Anthropic.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt("test-anthropic", "You are a helpful assistant.", "Say 'Hello from Anthropic'");
        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, AnthropicModels.Haiku_4_5, Collections.emptyList());

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertInstanceOf(Message.Assistant.class, responses.get(0));
        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) responses.get(0));
        assertFalse(content.isEmpty());
    }

    @Test
    public void integration_testMultiLLMPromptExecutor() {
        String openAIKey = TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv();
        String anthropicKey = TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv();

        OpenAILLMClient openAIClient = JavaInteropUtils.createOpenAIClient(openAIKey);
        AnthropicLLMClient anthropicClient = JavaInteropUtils.createAnthropicClient(anthropicKey);

        resourcesToClose.add((AutoCloseable) openAIClient);
        resourcesToClose.add((AutoCloseable) anthropicClient);

        MultiLLMPromptExecutor executor = JavaInteropUtils.createMultiLLMPromptExecutor(openAIClient, anthropicClient);

        Prompt openAIPrompt = JavaInteropUtils.buildSimplePrompt("test-multi-openai", "You are a helpful assistant.", "Say 'OpenAI response'");
        List<Message.Response> openAIResponses = JavaInteropUtils.executeExecutorBlocking(executor, openAIPrompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());
        assertNotNull(openAIResponses);
        assertFalse(openAIResponses.isEmpty());

        Prompt anthropicPrompt = JavaInteropUtils.buildSimplePrompt("test-multi-anthropic", "You are a helpful assistant.", "Say 'Anthropic response'");
        List<Message.Response> anthropicResponses = JavaInteropUtils.executeExecutorBlocking(executor, anthropicPrompt, AnthropicModels.Haiku_4_5, Collections.emptyList());
        assertNotNull(anthropicResponses);
        assertFalse(anthropicResponses.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_testBuilderBasicUsage(LLModel model) {
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
    public void integration_testBuilderWithToolRegistry(LLModel model) {
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
    public void integration_testEventHandler(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AtomicBoolean agentStarted = new AtomicBoolean(false);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);
        AtomicInteger llmCallsCount = new AtomicInteger(0);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a calculator. Use the add tool when needed.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> agentStarted.set(true));
                config.onAgentCompleted(ctx -> agentCompleted.set(true));
                config.onLLMCallStarting(ctx -> llmCallsCount.incrementAndGet());
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

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_testSimpleFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
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
    public void integration_testMultiStepFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    Message.Response response1 = JavaInteropUtils.requestLLMBlocking(context, "First step: " + input, true);

                    String step1Result = "";
                    if (response1 instanceof Message.Assistant) {
                        step1Result = JavaInteropUtils.getAssistantContent((Message.Assistant) response1);
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
    public void integration_testFunctionalStrategyWithManualToolHandling(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

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

                    while (currentResponse instanceof Message.Tool.Call && iterations < maxIterations) {
                        Message.Tool.Call toolCall = (Message.Tool.Call) currentResponse;
                        ReceivedToolResult toolResult = JavaInteropUtils.executeToolBlocking(context, toolCall);
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
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("BUG #3: Nested runBlocking in Java lambda causes InterruptedException")
    public void integration_testSubtask(LLModel model) {
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
    public void integration_testCustomStrategyWithRetry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    int maxRetries = 3;
                    for (int i = 0; i < maxRetries; i++) {
                        Message.Response response = JavaInteropUtils.requestLLMBlocking(context, input, true);
                        if (response instanceof Message.Assistant) {
                            String result = JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                            if (!result.isEmpty()) {
                                return result;
                            }
                        }
                    }
                    return "Failed after retries";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_testCustomStrategyWithValidation(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that generates JSON.")
                .functionalStrategy((context, input) -> {
                    Message.Response response = JavaInteropUtils.requestLLMBlocking(
                        context,
                        "Generate a JSON object with 'status' field set to 'success'",
                        true
                    );

                    if (response instanceof Message.Assistant) {
                        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                        if (content.contains("status") && content.contains("success")) {
                            return content;
                        }
                        return "Validation failed: response doesn't contain expected fields";
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
