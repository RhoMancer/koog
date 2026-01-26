package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.tools.Tool;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import kotlinx.serialization.Serializable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static ai.koog.integration.tests.utils.LLMClientsKt.getLLMClientForProvider;
import static org.junit.jupiter.api.Assertions.*;

public class ContextAPIIntegrationTest extends KoogJavaTestBase {

    private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

    @AfterEach
    public void cleanup() throws Exception {
        for (AutoCloseable resource : resourcesToClose) {
            resource.close();
        }
        resourcesToClose.clear();
    }

    private MultiLLMPromptExecutor createExecutor(LLModel model) {
        var client = getLLMClientForProvider(model.getProvider());
        resourcesToClose.add((AutoCloseable) client);
        return new MultiLLMPromptExecutor(client);
    }

    @Serializable
    public static class CalculationResult {
        private final int result;
        private final String operation;

        public CalculationResult(int result, String operation) {
            this.result = result;
            this.operation = operation;
        }

        public int getResult() {
            return result;
        }

        public String getOperation() {
            return operation;
        }
    }

    @Serializable
    public static class PersonInfo {
        private final String name;
        private final int age;
        private final List<String> hobbies;

        public PersonInfo(String name, int age, List<String> hobbies) {
            this.name = name;
            this.age = age;
            this.hobbies = hobbies;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public List<String> getHobbies() {
            return hobbies;
        }
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_testRequestLLMStructuredSimple(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that provides structured responses.")
                .functionalStrategy((context, input) -> {
                    CalculationResult calc = JavaInteropUtils.requestLLMStructuredBlocking(
                        context,
                        "Calculate 15 + 27 and return the result in the specified format",
                        CalculationResult.class
                    );
                    return "Result: " + calc.getResult() + ", Operation: " + calc.getOperation();
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Calculate 15 + 27");

        assertNotNull(result);
        assertTrue(result.contains("42"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_testRequestLLMStructuredComplex(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that provides structured responses about people.")
                .functionalStrategy((context, input) -> {
                    PersonInfo person = JavaInteropUtils.requestLLMStructuredBlocking(
                        context,
                        "Create a person profile with name 'Alice', age 30, and hobbies: reading, coding, hiking",
                        PersonInfo.class
                    );
                    return "Name: " + person.getName() + ", Age: " + person.getAge() +
                        ", Hobbies: " + person.getHobbies().size();
                })
        );

        String result = runBlocking(continuation -> agent.run("Create person profile", continuation));

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("30"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_testLLMWriteSession(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) ->
                    JavaInteropUtils.llmWriteSession(context, writeSession -> {
                        writeSession.appendPrompt(prompt -> {
                            prompt.user("First question: What is 2+2?");
                            return null;
                        });

                        writeSession.appendPrompt(prompt -> {
                            prompt.user("Second question: What is 3+3?");
                            return null;
                        });

                        Message.Response response2 = writeSession.requestLLM();

                        if (response2 instanceof Message.Assistant) {
                            return JavaInteropUtils.getAssistantContent((Message.Assistant) response2);
                        }
                        return "Unexpected response type";
                    })
                )
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Test");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("6") || result.contains("six"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_testLLMReadSession(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    Message.Response response = JavaInteropUtils.requestLLM(context, "What is 5+5?", true);

                    return JavaInteropUtils.llmReadSession(context, readSession -> {
                        var messages = readSession.getPrompt().getMessages();
                        int historySize = messages.size();

                        if (response instanceof Message.Assistant) {
                            return "History size: " + historySize + ", Answer: " +
                                JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                        }
                        return "Unexpected response type";
                    });
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Test");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("History size:"));
        assertTrue(result.contains("10") || result.contains("ten"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_testSubtaskSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getAddTool());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a coordinator that delegates calculations.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    String subtaskResult = context.subtask("Calculate the sum of 10 and 20 using the add tool")
                        .withInput(input)
                        .withOutput(String.class)
                        .withTools(tools)
                        .useLLM(model)
                        .runMode(ToolCalls.SEQUENTIAL)
                        .run();

                    return "Subtask completed: " + subtaskResult;
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Perform calculation");

        assertNotNull(result);
        assertTrue(result.contains("Subtask completed"));
        assertTrue(result.contains("30") || result.contains("thirty"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_testSubtaskParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getAddTool(), calculator.getMultiplyTool());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a coordinator that delegates calculations.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    String subtaskResult = context.subtask("Calculate 5 + 3 and 4 * 6 using available tools")
                        .withInput(input)
                        .withOutput(String.class)
                        .withTools(tools)
                        .useLLM(model)
                        .runMode(ToolCalls.PARALLEL)
                        .run();

                    return "Parallel subtask result: " + subtaskResult;
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Perform parallel calculations");

        assertNotNull(result);
        assertTrue(result.contains("Parallel subtask result"));
        assertFalse(result.isBlank());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_testSubtaskSingleRunSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getAddTool());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a coordinator.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    String subtaskResult = context.subtask("Add 7 and 8")
                        .withInput(input)
                        .withOutput(String.class)
                        .withTools(tools)
                        .useLLM(model)
                        .runMode(ToolCalls.SINGLE_RUN_SEQUENTIAL)
                        .run();

                    return "Single-run result: " + subtaskResult;
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Calculate");

        assertNotNull(result);
        assertTrue(result.contains("Single-run result"));
        assertTrue(result.contains("15") || result.contains("fifteen"));
    }
}
