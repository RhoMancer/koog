package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.AIAgentState;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.TestCredentials;
import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for advanced AIAgent features (state, result, custom pipelines).
 */
public class JavaAIAgentAdvancedFeaturesIntegrationTest extends KoogJavaTestBase {

    private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

    @AfterEach
    public void cleanup() throws Exception {
        for (AutoCloseable resource : resourcesToClose) {
            resource.close();
        }
        resourcesToClose.clear();
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
}
