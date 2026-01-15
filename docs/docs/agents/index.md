# Agents

Agents are AI entities that can interact with tools, execute complex workflows, and communicate with users to achieve specific goals.

A Koog agent consists of the following core components:

- **Prompt executor**: Manages agent interaction with LLMs.
- **Configuration**: Defines the agent's behavior, personality, and execution parameters.
- **Strategy**: Controls the agent's workflow.
- **Tools**: Enable the agent to perform specific tasks and interact with external systems.
- **Features**: Add capabilities like event handling, memory, tracing, and agent persistence.

When you run the agent, it executes its strategy with the provided input, uses the prompt executor to communicate 
with the LLM, invokes tools as needed, and returns the final output.

[`AIAgent`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent/-a-i-agent/index.html) is the basic
interface for creating agents in Koog. It can be instantiated using a constructor-like syntax with the core components.

!!! note
    For iterative planning tasks that require breaking down into smaller steps, Koog provides
    the [`PlannerAIAgent`](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner/-planner-a-i-agent/index.html) class.
    It still adheres to the basic `AIAgent` interface and shares the same configuration patterns, but provides additional
    planning functionality.

## Prompt executor

[Prompt executors](../prompts/prompt-executors.md) manage and run prompts provided to the agent.
They wrap [LLM clients](../prompts/llm-clients.md), enabling features like dynamic provider switching and fallbacks.

When you create a Koog agent, you must provide a prompt executor.
You can choose a [predefined prompt executor](../prompts/prompt-executors.md#pre-defined-prompt-executors) based
on your LLM provider or create a [custom prompt executor](../prompts/prompt-executors.md#creating-a-single-provider-executor).

For example, you can use the predefined `simpleOpenAIExecutor` if your LLM provider is OpenAI:

```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor("OPENAI_API_KEY")
    // Other agent parameters
)
```

To learn more, see [Prompt executors](../prompts/prompt-executors.md) and [LLM clients](../prompts/llm-clients.md).

## Agent configuration

Agent configuration specifies the agent's behavior using the initial prompt, LLM, and execution parameters.

### Basic configuration

With this approach, you pass parameters directly to the `AIAgent()` constructor. The agent configuration is constructed internally based on the provided parameters.
It is suitable for simple agents with a single system prompt and basic LLM parameters such as temperature.

```kotlin
val agent = AIAgent(
    llmModel = OpenAIModels.Chat.GPT4o,
    responseProcessor = customProcessor,
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    temperature = 0.7,
    numberOfChoices = 2,
    maxIterations = 10
)
```

### AIAgentConfig class

With this approach, you create an [`AIAgentConfig`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.config/-a-i-agent-config/index.html) instance to define all parameters related to the agent's behavior, 
and pass it to the `AIAgent()` constructor.

This enables you to define complex prompts with multiple messages, conversation history, LLM parameters,
and specify strategies for handling missing tools.
    
```kotlin
val agentConfig = AIAgentConfig(
    prompt = prompt(
        id = "classifier",
        params = LLMParams(
            temperature = 0.7,
            numberOfChoices = 2,
            toolChoice = LLMParams.ToolChoice.Auto
        )
    ) {
        system("You are a sentiment classifier.")
        user("I love this product!")
        assistant("positive") 
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 3,
    missingToolsConversionStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON),
    responseProcessor = customProcessor
)

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    agentConfig = agentConfig
)
```
    
??? tip

    You can use the [`withSystemPrompt()`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.config/-a-i-agent-config/-companion/with-system-prompt.html)
    helper function to create an `AIAgentConfig` instance with the minimum required parameters:

    ```kotlin
    val agentConfig = AIAgentConfig.withSystemPrompt(
        prompt = "You are a helpful sentiment classifier.", 
        llm = OpenAIModels.Chat.GPT4oMini,
        id = "classifier",
        maxAgentIterations = 5
    )
    
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
        agentConfig = agentConfig
    )
    ```

#### Prompt

The `prompt` parameter in [`AIAgentConfig`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.config/-a-i-agent-config/index.html) defines the agent's initial prompt.
You can provide a single system prompt as a string or a [Prompt](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.dsl/-prompt/index.html) object created with the [Kotlin DSL](../prompts/prompt-creation.md), which includes multiple prompt messages 
and LLM parameters that define the agent's personality, role, and initial context.
You can also provide conversation history and examples of desired behavior.

To create a prompt with multiple messages, use the Kotlin DSL:

```kotlin
val agentConfig = AIAgentConfig(
    prompt = prompt("classifier") {
        system("You are a sentiment classifier.")
        user("I love this product!")
        assistant("positive")
        user("It's okay, nothing special.")
        assistant("neutral")
    },
    // Other agent configuration parameters
)
```

The Prompt object can be customized by configuring parameters that control the LLM's behavior. For example:

```kotlin
val agentConfig = AIAgentConfig(
    prompt = prompt(
        id = "classifier",
        params = LLMParams(
            temperature = 0.7,
            numberOfChoices = 2,
            toolChoice = LLMParams.ToolChoice.Auto
        )
    ) {
        system("You are a sentiment classifier.")
        user("I love this product!")
        assistant("positive")
        user("It's okay, nothing special.")
        assistant("neutral")
    },
    // other agent configuration parameters
)
```

Additional input provided at runtime via `agent.run()` is appended to this initial prompt.

For the full list of available parameters, see [Prompt parameters](../prompts/prompt-creation.md#prompt-parameters).
For the LLM parameter reference, see [LLM parameters](../llm-parameters.md#llm-parameter-reference).

??? tip 
    
    If you only need a single system prompt, use the
    [`withSystemPrompt()`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.config/-a-i-agent-config/-companion/with-system-prompt.html)
    helper function:

    ```kotlin
    val agentConfig = AIAgentConfig.withSystemPrompt(
        prompt = "You are a helpful customer support agent.",
        // other agent configuration parameters
    ) 
    ```

#### Model

The `model` parameter specifies the LLM that the agent uses to process prompts and generate responses.
You can use one of the predefined models or create a custom model configuration.    
For more information, refer to [Model capabilities](../model-capabilities.md).

#### Maximum agent iterations

The `maxAgentIterations` parameter defines the maximum number of iterations the agent can perform during its execution.
Once the agent reaches this limit, it stops processing the request.
This prevents infinite loops and controls the number of API calls.

* For simple tasks, set 3-5 iterations.
* For complex workflows, use 10-30 iterations.

#### Missing tools

The `missingToolsConversionStrategy` parameter defines a strategy for handling missing tools during agent execution.
The missing tools can occur when:

* You have a multi-stage agent with different tool calls in each stage.
* You have a complex workflow with multiple subgraphs that share a common history but use different tools.
* You need to implement communication between agents with incompatible tools.

The following strategies are available:

| Strategy                                                            | Description                                                                         | When to use                                                                                                                                                                                         |
|---------------------------------------------------------------------|-------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MissingToolsConversionStrategy.Missing`                            | Converts only the missing tool calls and response messages to the specified format. | When you need backward compatibility with previous conversation rounds, when you have mostly overlapping tools between stages,  or if you simply want to keep structured tool calls where possible. |
| `MissingToolsConversionStrategy.All`                                | Converts all the tool calls to the specified format.                                | When you need to remove the tool-calling structure or pass the conversation history to a model that doesn't support tools.                                                                                                                 |

#### Response processing

The `responseProcessor` parameter defines a custom response processor for the agent.
It post-processes LLM responses before returning them to the agent. By default, there is no response processing.
You can enable it to moderate and validate the response content, change the response format, or log the response.

!!! note
    The response processor is applied to every LLM response during agent execution.

## Strategy

A strategy defines the agent's workflow and determines its type. The strategy controls how the agent processes input, decides when to call tools,
and when to return a final response.

Koog provides several agent types based on their strategy implementation.

## Tools

Agents can use tools to complete specific tasks. Koog provides some built-in tools, or you can implement your own custom tools.
To configure tools, use the `toolRegistry` parameter that defines the tools available to the agent:

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    llmModel = model,
    toolRegistry = ToolRegistry {
        tool(MyCustomTool)
    }
)
```

Koog supports [built-in tools](../built-in-tools.md), [annotation-based tools](../annotation-based-tools.md), and [class-based tools](../class-based-tools.md).
For more information, refer to the [Tools overview](../tools-overview.md).

## Features

Features let you add new capabilities to the agent, modify its behavior, provide access to external systems and resources,
and log and monitor events while the agent is running.

To install the feature, call the install function and provide the feature as an argument.
For example, to install the event handler feature, you can use the following:

```kotlin
// install the EventHandler feature
installFeatures = {
    install(EventHandler) {
        onAgentStarting { eventContext: AgentStartingContext ->
            println("Starting agent: ${eventContext.agent.id}")
        }
        onAgentCompleted { eventContext: AgentCompletedContext ->
            println("Result: ${eventContext.result}")
        }
    }
}
```

To learn more about feature configuration, see [Features overview](../features-overview.md).

## Agent types


## Input and output

* Basic agents use `String` for input and output.
* Functional and graph-based agents can use structured data classes for typed communication.
* Planner agents use a `State` object to track progress toward a goal.

For details on processing structured data, see [Structured output](../structured-output.md).

## Running an agent

To execute an agent, you call the `run()` method with the initial input.

```kotlin
val result = agent.run("Calculate the total revenue for Q4.")
```

When `agent.run()` is called:

1. The agent appends the input (usually a string) to the initial prompt defined in the agent's configuration. The system prompt and conversation history are preserved.
2. The agent executes its strategy, which calls the LLM through the prompt executor and invoking tools as needed.
3. The agent returns the final output produced by the strategy.

For more details on how prompts are managed during execution, see [Prompts in AI agents](../prompts/index.md#prompts-in-ai-agents).
