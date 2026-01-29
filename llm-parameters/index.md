# LLM parameters

This page provides details about LLM parameters in the Koog agentic framework. LLM parameters let you control and customize the behavior of language models.

## Overview

LLM parameters are configuration options that let you fine-tune how language models generate responses. These parameters control aspects like response randomness, length, format, and tool usage. By adjusting the parameters, you optimize model behavior for different use cases, from creative content generation to deterministic structured outputs.

In Koog, the `LLMParams` class incorporates LLM parameters and provides a consistent interface for configuring language model behavior. You can use LLM parameters in the following ways:

- When creating a prompt:

```kotlin
val prompt = prompt(
    id = "dev-assistant",
    params = LLMParams(
        temperature = 0.7,
        maxTokens = 500
    )
) {
    // Add a system message to set the context
    system("You are a helpful assistant.")

    // Add a user message
    user("Tell me about Kotlin")
}
```

For more information about prompt creation, see [Prompts](../prompts/prompt-creation/).

- When creating a subgraph:

```kotlin
val processQuery by subgraphWithTask<String, String>(
    tools = listOf(searchTool, calculatorTool, weatherTool),
    llmModel = OpenAIModels.Chat.GPT4o,
    llmParams = LLMParams(
        temperature = 0.7,
        maxTokens = 500
    ),
    runMode = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax = 3,
) { userQuery ->
    """
    You are a helpful assistant that can answer questions about various topics.
    Please help with the following query:
    $userQuery
    """
}
```

For more information about existing subgraph types in Koog, see [Predefined subgraphs](../nodes-and-components/#predefined-subgraphs). To learn how to create and implement your own subgraphs, see [Custom subgraphs](../custom-subgraphs/).

- When updating a prompt in an LLM write session:

```kotlin
llm.writeSession {
    changeLLMParams(
        LLMParams(
            temperature = 0.7,
            maxTokens = 500
        )
    )
}
```

For more information about sessions, see [LLM sessions and manual history management](../sessions/).

## LLM parameter reference

The following table provides a reference of LLM parameters included in the `LLMParams` class and supported by all LLM providers that are available in Koog out of the box. For a list of parameters that are specific to some providers, see [Provider-specific parameters](#provider-specific-parameters).

| Parameter              | Type                      | Description                                                                                                                                                                                     |
| ---------------------- | ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `temperature`          | Double                    | Controls randomness in the output. Higher values, such as 0.7–1.0, produce more diverse and creative responses, while lower values produce more deterministic and focused responses.            |
| `maxTokens`            | Integer                   | Maximum number of tokens to generate in the response. Useful for controlling response length.                                                                                                   |
| `numberOfChoices`      | Integer                   | Number of alternative responses to generate. Must be greater than 0.                                                                                                                            |
| `speculation`          | String                    | A speculative configuration string that influences model behavior, designed to enhance result speed and accuracy. Supported only by certain models, but may greatly improve speed and accuracy. |
| `schema`               | Schema                    | Defines the structure for the model's response format, enabling structured outputs like JSON. For more information, see [Schema](#schema).                                                      |
| `toolChoice`           | ToolChoice                | Controls tool calling behavior of the language model. For more information, see [Tool choice](#tool-choice).                                                                                    |
| `user`                 | String                    | Identifier for the user making the request, which can be used for tracking purposes.                                                                                                            |
| `additionalProperties` | Map\<String, JsonElement> | Additional properties that can be used to store custom parameters specific to certain model providers.                                                                                          |

For a list of default values for each parameter, see the corresponding LLM provider documentation:

- [OpenAI Chat](https://platform.openai.com/docs/api-reference/chat/create)
- [OpenAI Responses](https://platform.openai.com/docs/api-reference/responses/create)
- [Google](https://ai.google.dev/api/generate-content#generationconfig)
- [Anthropic](https://platform.claude.com/docs/en/api/messages/create)
- [Mistral](https://docs.mistral.ai/api/#operation/chatCompletions)
- [DeepSeek](https://api-docs.deepseek.com/api/create-chat-completion#request)
- [OpenRouter](https://openrouter.ai/docs/api/reference/parameters)
- Alibaba ([DashScope](https://www.alibabacloud.com/help/en/model-studio/qwen-api-reference))

## Schema

The `Schema` interface defines the structure for the model's response format. Koog supports JSON schemas, as described in the sections below.

### JSON schemas

JSON schemas let you request structured JSON data from language models. Koog supports the following two types of JSON schemas:

1. **Basic JSON Schema** (`LLMParams.Schema.JSON.Basic`): Used for basic JSON processing capabilities. This format primarily focuses on nested data definitions without advanced JSON Schema functionalities.

```kotlin
// Create parameters with a basic JSON schema
val jsonParams = LLMParams(
    temperature = 0.2,
    schema = LLMParams.Schema.JSON.Basic(
        name = "PersonInfo",
        schema = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(
                mapOf(
                    "name" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                    "age" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                    "skills" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "items" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                        )
                    )
                )
            ),
            "additionalProperties" to JsonPrimitive(false),
            "required" to JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("age"), JsonPrimitive("skills")))
        ))
    )
)
```

2. **Standard JSON Schema** (`LLMParams.Schema.JSON.Standard`): Represents a standard JSON schema according to [json-schema.org](https://json-schema.org/). This format is a proper subset of the official JSON Schema specification. Note that the flavor across different LLM providers might vary, since not all of them support full JSON schemas.

```kotlin
// Create parameters with a standard JSON schema
val standardJsonParams = LLMParams(
    temperature = 0.2,
    schema = LLMParams.Schema.JSON.Standard(
        name = "ProductCatalog",
        schema = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "products" to JsonObject(mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "id" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                            "name" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                            "price" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                            "description" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                        )),
                        "additionalProperties" to JsonPrimitive(false),
                        "required" to JsonArray(listOf(JsonPrimitive("id"), JsonPrimitive("name"), JsonPrimitive("price"), JsonPrimitive("description")))
                    ))
                ))
            )),
            "additionalProperties" to JsonPrimitive(false),
            "required" to JsonArray(listOf(JsonPrimitive("products")))
        ))
    )
)
```

## Tool choice

The `ToolChoice` class controls how the language model uses tools. It provides the following options:

- `LLMParams.ToolChoice.Named`: the language model calls the specified tool. Takes the `name` string argument that represents the name of the tool to call.
- `LLMParams.ToolChoice.All`: the language model calls all tools.
- `LLMParams.ToolChoice.None`: the language model does not call tools and only generates text.
- `LLMParams.ToolChoice.Auto`: the language model automatically decides whether to call tools and which tool to call.
- `LLMParams.ToolChoice.Required`: the language model calls at least one tool.

Here is an example of using the `LLMParams.ToolChoice.Named` class to call a specific tool:

```kotlin
val specificToolParams = LLMParams(
    toolChoice = LLMParams.ToolChoice.Named(name = "calculator")
)
```

## Provider-specific parameters

Koog supports provider-specific parameters for some LLM providers. These parameters extend the base `LLMParams` class and add provider-specific functionality. The following classes include parameters that are specific per provider:

- `OpenAIChatParams`: Parameters specific to the OpenAI Chat Completions API.
- `OpenAIResponsesParams`: Parameters specific to the OpenAI Responses API.
- `GoogleParams`: Parameters specific to Google models.
- `AnthropicParams`: Parameters specific to Anthropic models.
- `MistralAIParams`: Parameters specific to Mistral models.
- `DeepSeekParams`: Parameters specific to DeepSeek models.
- `OpenRouterParams`: Parameters specific to OpenRouter models.
- `DashscopeParams`: Parameters specific to Alibaba models.

Here is the complete reference of provider-specific parameters in Koog:

The following example shows defined OpenRouter LLM parameters using the provider-specific `OpenRouterParams` class:

```kotlin
val openRouterParams = OpenRouterParams(
    temperature = 0.7,
    maxTokens = 500,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    topP = 0.9,
    topK = 40,
    repetitionPenalty = 1.1,
    models = listOf("anthropic/claude-3-opus", "anthropic/claude-3-sonnet"),
    transforms = listOf("middle-out")
)
```

## Usage examples

### Basic usage

```kotlin
// A basic set of parameters with limited length 
val basicParams = LLMParams(
    temperature = 0.7,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto
)
```

### Reasoning control

You implement reasoning control through provider-specific parameters that control model reasoning. When using the OpenAI Chat API and models that support reasoning, use the `reasoningEffort` parameter to control how many reasoning tokens the model generates before providing a response:

```kotlin
val openAIReasoningEffortParams = OpenAIChatParams(
    reasoningEffort = ReasoningEffort.MEDIUM
)
```

In addition, when using the OpenAI Responses API in a stateless mode, you keep an encrypted history of reasoning items and send it to the model in every conversation turn. The encryption is done on the OpenAI side, and you need to request encrypted reasoning tokens by setting the `include` parameter in your requests to `reasoning.encrypted_content`. You can then pass the encrypted reasoning tokens back to the model in the next conversation turns.

```kotlin
val openAIStatelessReasoningParams = OpenAIResponsesParams(
    include = listOf(OpenAIInclude.REASONING_ENCRYPTED_CONTENT)
)
```

### Custom parameters

To add custom parameters that may be provider specific and not supported in Koog out of the box, use the `additionalProperties` property as shown in the example below.

```kotlin
// Add custom parameters for specific model providers
val customParams = LLMParams(
    additionalProperties = additionalPropertiesOf(
        "top_p" to 0.95,
        "frequency_penalty" to 0.5,
        "presence_penalty" to 0.5
    )
)
```

### Setting and overriding parameters

The code sample below shows how you can define a set of LLM parameters that you may want to use primarily, then create another set by partially overriding values from the original set and adding new values to it. This lets you define parameters that are common to most requests but also add more specific parameter combinations without having to repeat the common parameters.

```kotlin
// Define default parameters
val defaultParams = LLMParams(
    temperature = 0.7,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto
)

// Create parameters with some overrides, using defaults for the rest
val overrideParams = LLMParams(
    temperature = 0.2,
    numberOfChoices = 3
).default(defaultParams)
```

The values in the resulting `overrideParams` set are equivalent to the following:

```kotlin
val overrideParams = LLMParams(
    temperature = 0.2,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto,
    numberOfChoices = 3
)
```
