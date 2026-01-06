# Agent configuration

`AIAgent` is the basic interface for creating Koog agents that can be instantiated using a constructor-like syntax.

When you create an agent, you provide a certain set of parameters for its configuration.
Koog allows you to configure agents in one of the following ways:

* By providing parameters directly to `AIAgent()`, which constructs the configuration internally.
* By creating an `AIAgentConfig` instance and providing it to `AIAgent()`.

The `AIAgentConfig` class is the recommended way to configure Koog agents, as it provides more control over agent behavior.

## AIAgentConfig class

`AIAgentConfig` is a configuration class for Koog agents that defines an initial prompt, LLM, and execution parameters.

There are two ways to create an `AIAgentConfig` instance:

* Using the `withSystemPrompt()` helper function for creating basic configuration with minimum parameters. For example:

```kotlin
val agentConfig = AIAgentConfig.withSystemPrompt(
    prompt = "You are a helpful sentiment classifier.", 
    llm = OpenAIModels.Chat.GPT4oMini,
    id = "classifier",
    maxAgentIterations = 5
)
```

* Using the `AIAgentConfig()` constructor for full control over agent configuration. For example:

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
```

### Prompt

The `prompt` parameter defines the initial prompt for the agent.
You can provide a single system prompt as a string, or a Prompt object created with the Kotlin DSL that includes multiple prompt messages 
and LLM parameters defining the agent's personality, role, and initial context.
Also, you can provide the conversation history with examples of desired behavior.

* If you only need a single system prompt, use the
[`withSystemPrompt()`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.config/-a-i-agent-config/-companion/with-system-prompt.html)
helper function:

```kotlin
val agentConfig = AIAgentConfig.withSystemPrompt(
    prompt = "You are a helpful customer support agent.",
    // other agent configuration parameters
) 
```

* If you need to create a prompt with multiple messages, use the Kotlin DSL:

```kotlin
val agentConfig = AIAgentConfig(
    prompt = prompt("classifier") {
        system("You are a sentiment classifier.")
        user("I love this product!")
        assistant("positive")
        user("It's okay, nothing special.")
        assistant("neutral")
    },
    // other agent configuration parameters
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

For the full list of available parameters, see [Prompt parameters](prompts/prompt-creation.md#prompt-parameters).
For the LLM parameter reference, see [LLM parameters](llm-parameters.md#llm-parameter-reference).

!!! tip
    * Provide instructions that never change. Avoid any request-specific data in the initial prompt.
    * Avoid using the same message in both the agent configuration and runtime input.

The prompt in `AIAgentConfig` sets the agent's initial prompt which remains unchainged.
Additional input provided at runtine via agent.run() is appended to this initial prompt.

### Model

The `model` parameter specifies an LLM to use for the agent. This defines which model will process 
the prompts and generate responses.
You can use predefined models or create a custom model configuration.
To learn more, see [Model capabilities](model-capabilities.md).

### Maximum agent iterations

The `maxAgentIterations` parameter defines the maximum number of iterations the agent can perform during its execution.
Once the agent reaches this limit, it stops processing the request.
This allows preventing infinite loops and controlling the number of API calls.

* For simple tasks, you can set 3-5 iterations.
* For complex workflows, you can use 10-30 iterations.

### Missing tools

The `missingToolsConversionStrategy` parameter defines a strategy for handling missing tools during agent execution.
The missing tools can occur when:

* You have a multi-stage agent with different tool calls in each stage.
* You have a complex workflow with multiple subgraphs that share history but use different tools.
* You need to implement communication between agents with incompatible tools.

The following strategies are available:

* `MissingToolsConversionStrategy.Missing`: Converts only the missing tool calls and response messages to the specified format.
    Use this strategy when you need backward compatibility with previous conversation rounds, when you have mostly overlapping tools between stages,
    or if you simply want to keep the structured tool calls where possible.
* `MissingToolsConversionStrategy.All`: Converts all the tool calls to the specified format.
    Use this strategy when you need to remove the tool-calling structure, or you need to pass the conversation history
    to a model that doesn't support tools.

### Response processing

The `responseProcessor` parameter defines a custom response processor for the agent.
It post-processes LLM responses before they are returned to the agent. By default, there is no response processing.
You can enable it to moderate and validate the response content, change the response format, or log the response.

!!! note
    The response processor is applied to every LLM response during agent execution.

