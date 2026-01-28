package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.*;
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

    private void assertValidResponse(List<Message.Response> responses) {
        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertInstanceOf(Message.Assistant.class, responses.get(0));
        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) responses.get(0));
        assertFalse(content.isEmpty());
    }

    private String getAssistantContentOrDefault(Message.Response response, String defaultValue) {
        if (response instanceof Message.Assistant) {
            return JavaInteropUtils.getAssistantContent((Message.Assistant) response);
        }
        return defaultValue;
    }

    private AIAgent<String, String> buildAgentWithCalculator(MultiLLMPromptExecutor executor, LLModel model) {
        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        return AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. You MUST use the add tool to perform additions. ALWAYS call the tool.")
            .toolRegistry(toolRegistry)
            .build();
    }

    @Test
    public void integration_OpenAILLMClient() {
        OpenAILLMClient client = JavaInteropUtils.createOpenAIClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.OpenAI.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt("test-openai", "You are a helpful assistant.", "Say 'Hello from OpenAI'");
        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());

        assertValidResponse(responses);
    }

    @Test
    public void integration_AnthropicLLMClient() {
        AnthropicLLMClient client = JavaInteropUtils.createAnthropicClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.Anthropic.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt("test-anthropic", "You are a helpful assistant.", "Say 'Hello from Anthropic'");
        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, AnthropicModels.Haiku_4_5, Collections.emptyList());

        assertValidResponse(responses);
    }

    @Test
    public void integration_MultiLLMPromptExecutor() {
        OpenAILLMClient openAIClient = JavaInteropUtils.createOpenAIClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        AnthropicLLMClient anthropicClient = JavaInteropUtils.createAnthropicClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());

        resourcesToClose.add((AutoCloseable) openAIClient);
        resourcesToClose.add((AutoCloseable) anthropicClient);

        MultiLLMPromptExecutor executor = JavaInteropUtils.createMultiLLMPromptExecutor(openAIClient, anthropicClient);

        Prompt openAIPrompt = JavaInteropUtils.buildSimplePrompt("test-multi-openai", "You are a helpful assistant.", "Say 'OpenAI response'");
        List<Message.Response> openAIResponses = JavaInteropUtils.executeExecutorBlocking(executor, openAIPrompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());
        assertValidResponse(openAIResponses);

        Prompt anthropicPrompt = JavaInteropUtils.buildSimplePrompt("test-multi-anthropic", "You are a helpful assistant.", "Say 'Anthropic response'");
        List<Message.Response> anthropicResponses = JavaInteropUtils.executeExecutorBlocking(executor, anthropicPrompt, AnthropicModels.Haiku_4_5, Collections.emptyList());
        assertValidResponse(anthropicResponses);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderBasicUsageAndTemperature(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        // Test basic builder usage
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

        // Test builder with temperature setting
        AIAgent<String, String> agentWithTemp = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .temperature(0.5)
            .build();

        String tempResult = runBlocking(continuation -> agentWithTemp.run("Say hello", continuation));

        assertNotNull(tempResult);
        assertFalse(tempResult.isEmpty());
        assertTrue(tempResult.toLowerCase().contains("hello") || tempResult.toLowerCase().contains("hi"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderWithToolRegistry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. Use the add and multiply tools as needed.")
            .toolRegistry(toolRegistry)
            .build();

        // Test with complex expression using both tools
        String result = runBlocking(continuation -> agent.run("Calculate (5 + 3) * 2", continuation));

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_EventHandler(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicBoolean agentStarted = new AtomicBoolean(false);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);
        AtomicInteger llmCallsCount = new AtomicInteger(0);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
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
    public void integration_SimpleFunctionalStrategyWithRetry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        // Test simple functional strategy with retry logic
        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    for (int i = 0; i < 3; i++) {
                        String result = getAssistantContentOrDefault(
                            JavaInteropUtils.requestLLM(context, input, true),
                            ""
                        );
                        if (!result.isEmpty()) {
                            return result;
                        }
                    }
                    return "Failed after retries";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Say hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("hello"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_MultiStepFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    Message.Response response1 = JavaInteropUtils.requestLLM(context, "First step: " + input, true);
                    String step1Result = getAssistantContentOrDefault(response1, "");

                    Message.Response response2 = JavaInteropUtils.requestLLM(
                        context,
                        "Second step, previous result was: " + step1Result,
                        true
                    );

                    return getAssistantContentOrDefault(response2, "Unexpected response type");
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Count to 3");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_FunctionalStrategyWithManualToolHandling(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a calculator. Use the add tool to perform calculations.")
                .toolRegistry(toolRegistry)
                .functionalStrategy((context, input) -> {
                    Message.Response currentResponse = JavaInteropUtils.requestLLM(
                        context,
                        "Calculate: " + input + ". You MUST use the add tool.",
                        true
                    );

                    int maxIterations = 5;
                    for (int i = 0; i < maxIterations && currentResponse instanceof Message.Tool.Call; i++) {
                        Message.Tool.Call toolCall = (Message.Tool.Call) currentResponse;
                        ReceivedToolResult toolResult = JavaInteropUtils.executeTool(context, toolCall);
                        currentResponse = JavaInteropUtils.sendToolResult(context, toolResult);
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
    @Disabled("KG-669")
    public void integration_Subtask(LLModel model) {
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
                    String subtaskResult = JavaInteropUtils.runSubtask(
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
    public void integration_CustomStrategyWithValidation(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that generates JSON.")
                .functionalStrategy((context, input) -> {
                    Message.Response response = JavaInteropUtils.requestLLM(
                        context,
                        "Generate a JSON object with 'status' field set to 'success'",
                        true
                    );

                    String content = getAssistantContentOrDefault(response, "Unexpected response type");
                    if (content.contains("status") && content.contains("success")) {
                        return content;
                    }
                    return "Validation failed: response doesn't contain expected fields";
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

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceCreateAndListAgents(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        GraphAIAgent<String, String> agent1 = service.createAgent("agent-1");
        GraphAIAgent<String, String> agent2 = service.createAgent("agent-2");

        assertNotNull(agent1);
        assertNotNull(agent2);
        assertEquals("agent-1", agent1.getId());
        assertEquals("agent-2", agent2.getId());

        List<GraphAIAgent<String, String>> allAgents = service.listAllAgents();
        assertEquals(2, allAgents.size());
        assertTrue(allAgents.contains(agent1));
        assertTrue(allAgents.contains(agent2));

        service.closeAll();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceAgentById(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        GraphAIAgent<String, String> createdAgent = service.createAgent("test-agent");

        GraphAIAgent<String, String> retrievedAgent = service.agentById("test-agent");
        assertNotNull(retrievedAgent);
        assertEquals("test-agent", retrievedAgent.getId());
        assertEquals(createdAgent, retrievedAgent);

        GraphAIAgent<String, String> nonExistent = service.agentById("non-existent");
        assertNull(nonExistent);

        service.closeAll();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceRemoveAgent(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        // Test removing by agent instance
        GraphAIAgent<String, String> agent = service.createAgent("removable-agent");
        assertEquals(1, service.listAllAgents().size());

        boolean removed = service.removeAgent(agent);
        assertTrue(removed);
        assertEquals(0, service.listAllAgents().size());

        boolean removedAgain = service.removeAgent(agent);
        assertFalse(removedAgain);

        // Test removing by agent ID
        service.createAgent("agent-to-remove");
        assertEquals(1, service.listAllAgents().size());

        boolean removedById = service.removeAgentWithId("agent-to-remove");
        assertTrue(removedById);
        assertEquals(0, service.listAllAgents().size());

        boolean removedByIdAgain = service.removeAgentWithId("agent-to-remove");
        assertFalse(removedByIdAgain);

        service.closeAll();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceStateTracking(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        GraphAIAgent<String, String> agent = service.createAgent("state-test-agent");

        List<GraphAIAgent<String, String>> inactiveAgents = service.listInactiveAgents();
        assertTrue(inactiveAgents.contains(agent));

        List<GraphAIAgent<String, String>> activeAgents = service.listActiveAgents();
        assertFalse(activeAgents.contains(agent));

        String result = runBlocking(continuation -> agent.run("Say hello", continuation));
        assertNotNull(result);

        List<GraphAIAgent<String, String>> finishedAgents = service.listFinishedAgents();
        assertTrue(finishedAgents.contains(agent));

        service.closeAll();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceCreateAgentAndRun(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        ToolRegistry emptyRegistry = ToolRegistry.builder().build();
        String result = service.createAgentAndRun("What is 2+2?", "one-shot-agent",
            emptyRegistry, service.getAgentConfig(), null, kotlinx.datetime.Clock.System.INSTANCE);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        List<GraphAIAgent<String, String>> allAgents = service.listAllAgents();
        assertEquals(1, allAgents.size());
        assertEquals("one-shot-agent", allAgents.get(0).getId());

        service.closeAll();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceWithCustomToolRegistry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry serviceToolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. Use tools when needed.")
            .toolRegistry(serviceToolRegistry)
            .build();

        GraphAIAgent<String, String> agent = service.createAgent("calculator-agent");

        String result = runBlocking(continuation -> agent.run("Calculate 10 + 15", continuation));
        assertNotNull(result);
        assertFalse(result.isBlank());

        service.closeAll();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceBuilderConfiguration(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .temperature(0.7)
            .maxIterations(5)
            .build();

        GraphAIAgent<String, String> agent = service.createAgent();

        assertNotNull(agent);
        assertEquals(0.7, service.getAgentConfig().getPrompt().getParams().getTemperature());
        assertEquals(5, service.getAgentConfig().getMaxAgentIterations());

        service.closeAll();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderWithCustomId(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        GraphAIAgent<String, String> agent = service.createAgent("custom-test-id");

        assertNotNull(agent);
        assertEquals("custom-test-id", agent.getId());

        GraphAIAgent<String, String> retrievedAgent = service.agentById("custom-test-id");
        assertNotNull(retrievedAgent);
        assertEquals(agent, retrievedAgent);

        service.closeAll();
    }


    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderWithMaxIterations(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant with calculator tools.")
            .toolRegistry(toolRegistry)
            .maxIterations(5)
            .build();

        String result = runBlocking(continuation -> agent.run("What is 5 + 3?", continuation));

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_CustomPipelineFeature(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger llmInterceptCount = new AtomicInteger(0);
        AtomicInteger toolInterceptCount = new AtomicInteger(0);
        AtomicBoolean agentStarted = new AtomicBoolean(false);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);

        JavaInteropUtils.TransactionTools transactionTools = new JavaInteropUtils.TransactionTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(transactionTools);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant. When asked for transaction IDs, you MUST ALWAYS call the getTransactionId tool. " +
                "You do NOT know transaction IDs - you MUST call the tool to get them. NEVER make up transaction IDs. " +
                "ALWAYS use the tool. NO EXCEPTIONS.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(context -> agentStarted.set(true));
                config.onAgentCompleted(context -> agentCompleted.set(true));
                config.onLLMCallStarting(context -> llmInterceptCount.incrementAndGet());
                config.onToolCallStarting(context -> toolInterceptCount.incrementAndGet());
            })
            .build();

        String result = runBlocking(continuation -> agent.run("What is the transaction ID for order number 12345? You must use the getTransactionId tool.", continuation));

        assertNotNull(result);
        assertTrue(agentStarted.get(), "Agent should have started");
        assertTrue(agentCompleted.get(), "Agent should have completed");
        assertTrue(llmInterceptCount.get() > 0, "LLM interceptor should have been called");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GetAgentState(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        String result = runBlocking(continuation -> agent.run("Say hello", continuation));

        assertNotNull(result);

        var state = agent.getState();
        assertNotNull(state);
        assertInstanceOf(AIAgentState.Finished.class, state);
        AIAgentState.Finished<String> finishedState = (AIAgentState.Finished<String>) state;
        assertEquals(result, finishedState.getResult());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GetAgentResult(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        String result = runBlocking(continuation -> agent.run("Say hello", continuation));

        assertNotNull(result);

        String agentResult = runBlocking(continuation -> agent.result(continuation));
        assertNotNull(agentResult);
        assertEquals(result, agentResult);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceBuilderFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        var service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((context, input) -> {
                String inputStr = (input instanceof String) ? (String) input : String.valueOf(input);
                Message.Response response = JavaInteropUtils.requestLLM(context, inputStr, true);
                if (response instanceof Message.Assistant) {
                    return JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                }
                return "Unexpected response type";
            })
            .build();

        var agent = service.createAgent("functional-agent");

        String result = runBlocking(continuation -> agent.run("Say hello", continuation));

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("hello"));

        service.closeAll();
    }

}
