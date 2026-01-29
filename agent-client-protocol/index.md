# Agent Client Protocol

Agent Client Protocol (ACP) is a standardized protocol that enables client applications to communicate with AI agents through a consistent, bidirectional interface.

ACP provides a structured way for agents to interact with clients, supporting real-time event streaming, tool call notifications, and session lifecycle management.

The Koog framework provides integration with ACP, enabling you to build ACP-compliant agents that can communicate with standardized client applications.

To learn more about the protocol, see the [Agent Client Protocol](https://agentclientprotocol.com) documentation.

## Integration with Koog

The Koog framework integrates with ACP using the [ACP Kotlin SDK](https://github.com/agentclientprotocol/kotlin-sdk) with additional API extensions in the `agents-features-acp` module.

This integration lets Koog agents perform the following:

- Communicate with ACP-compliant client applications
- Send real-time updates about agent execution (tool calls, thoughts, completions)
- Handle standard ACP events and notifications automatically
- Convert between Koog message formats and ACP content blocks

### Key components

Here are the main components of the ACP integration in Koog:

| Component                                                                                                                        | Description                                                                      |
| -------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| [`AcpAgent`](https://api.koog.ai/agents/agents-features-acp/ai.koog.agents.features.acp/-acp-agent/index.html)                   | The main feature that enables communication between Koog agents and ACP clients. |
| [`MessageConverters`](https://api.koog.ai/agents/agents-features-acp/ai.koog.agents.features.acp/-message-converters/index.html) | Utilities for converting messages between Koog and ACP formats.                  |
| [`AcpConfig`](https://api.koog.ai/agents/agents-features-acp/ai.koog.agents.features.acp/-acp-agent/-acp-config/index.html)      | Configuration class for the AcpAgent feature.                                    |

## Getting started

ACP dependencies are **not** included by default in the `koog-agents` meta-dependency. You must explicitly add the ACP module to your project.

### Dependencies

To use ACP in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog:agents-features-acp:$koogVersion")
}
```

### 1. Implement ACP agent support

Koog ACP integration is based on [Kotlin ACP SDK](https://github.com/agentclientprotocol/kotlin-sdk). The SDK provides an `AgentSupport` and `AgentSession` interface that you need to implement to implement in order to connect your agent to ACP clients. The `AgentSupport` manages the agent sessions creation and loading. The interface implementation is almost the same for all agents, we'll provide an example implementation further. The `AgentSession` manages the agent instantiation, invocation and controls runtime. Inside the `prompt` method you will define and run the Koog agent.

To use ACP with Koog, you need to implement the `AgentSupport` and `AgentSession` interfaces from the ACP SDK:

```kotlin
// Implement AgentSession to manage the lifecycle of a Koog agent
class KoogAgentSession(
    override val sessionId: SessionId,
    private val promptExecutor: PromptExecutor,
    private val protocol: Protocol,
    private val clock: Clock,
) : AgentSession {

    private var agentJob: Deferred<Unit>? = null
    private val agentMutex = Mutex()

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?
    ): Flow<Event> = channelFlow {
        val agentConfig = AIAgentConfig(
            prompt = prompt("acp") {
                system("You are a helpful assistant.")
            }.appendPrompt(content),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 1000
        )

        agentMutex.withLock {
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                strategy = myStrategy()
            ) {
                install(AcpAgent) {
                    this.sessionId = this@KoogAgentSession.sessionId.value
                    this.protocol = this@KoogAgentSession.protocol
                    this.eventsProducer = this@channelFlow
                    this.setDefaultNotifications = true
                }
            }

            agentJob = async { agent.run(Unit) }
            agentJob?.await()
        }
    }

    private fun Prompt.appendPrompt(content: List<ContentBlock>): Prompt {
        return withMessages { messages ->
            messages + listOf(content.toKoogMessage(clock))
        }
    }

    private fun myStrategy() = strategy<Unit, Unit>("") {
        // Define your strategy here
    }    
    override suspend fun cancel() {
        agentJob?.cancel()
    }
}
```

### 2. Configure the AcpAgent feature

The `AcpAgent` feature can be configured through `AcpConfig`:

```kotlin
val agent = AIAgent(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = myStrategy()
) {
    install(AcpAgent) {
        // Required: The unique session identifier for the ACP connection
        this.sessionId = sessionIdValue

        // Required: The protocol instance used for sending requests and notifications
        this.protocol = protocol

        // Required: A coroutine-based producer scope for sending events
        this.eventsProducer = this@channelFlow

        // Optional: Whether to register default notification handlers (default: true)
        this.setDefaultNotifications = true
    }
}
```

Key configuration options:

- `sessionId`: The unique session identifier for the ACP connection
- `protocol`: The protocol instance used for sending requests and notifications to ACP clients
- `eventsProducer`: A coroutine-based producer scope for sending events
- `setDefaultNotifications`: Whether to register default notification handlers (default: `true`)

### 3. Handle incoming prompts

Convert ACP content blocks to Koog messages using the provided extension functions:

```kotlin
// Convert ACP content blocks to Koog message
val koogMessage = acpContent.toKoogMessage(clock)

// Append to existing prompt
fun Prompt.appendPrompt(content: List<ContentBlock>): Prompt {
    return withMessages { messages ->
        messages + listOf(content.toKoogMessage(clock))
    }
}
```

## Default notification handlers

When `setDefaultNotifications` is enabled, the AcpAgent feature automatically handles:

1. **Agent Completion**: Sends `PromptResponseEvent` with `StopReason.END_TURN` when the agent completes successfully
1. **Agent Execution Failures**: Sends `PromptResponseEvent` with appropriate stop reasons:
   - `StopReason.MAX_TURN_REQUESTS` for max iterations exceeded
   - `StopReason.REFUSAL` for other execution failures
1. **LLM Responses**: Converts and sends LLM responses as ACP events (text, tool calls, reasoning)
1. **Tool Call Lifecycle**: Reports tool call status changes:
   - `ToolCallStatus.IN_PROGRESS` when a tool call starts
   - `ToolCallStatus.COMPLETED` when a tool call succeeds
   - `ToolCallStatus.FAILED` when a tool call fails

## Sending custom events

You can send custom events to the ACP client using the `sendEvent` method:

```kotlin
// Access the ACP feature and send custom events
withAcpAgent {
    sendEvent(
        Event.SessionUpdateEvent(
            SessionUpdate.PlanUpdate(plan.entries)
        )
    )
}
```

Moreover, you can use `protocol` inside `withAcpAgent` and send custom notifications or requests:

```kotlin
// Access the ACP feature and send custom events
withAcpAgent {
    protocol.sendRequest<AuthenticateRequest, AuthenticateResponse>(
        AcpMethod.AgentMethods.Authenticate,
        AuthenticateRequest(methodId = AuthMethodId("Google"))
    )
}
```

## Message conversion

The module provides utilities for converting between Koog and ACP message formats:

### ACP to Koog

```kotlin
// Convert ACP content blocks to Koog message
val koogMessage = acpContentBlocks.toKoogMessage(clock)

// Convert single ACP content block to Koog content part
val contentPart = acpContentBlock.toKoogContentPart()
```

### Koog to ACP

```kotlin
// Convert Koog response message to ACP events
val acpEvents = koogResponseMessage.toAcpEvents()

// Convert Koog content part to ACP content block
val acpContentBlock = koogContentPart.toAcpContentBlock()
```

## Important notes

### Use channelFlow for event streaming

Use `channelFlow` to allow sending events from different coroutines:

```kotlin
override suspend fun prompt(
    content: List<ContentBlock>,
    _meta: JsonElement?
): Flow<Event> = channelFlow {
    // Install AcpAgent with this@channelFlow as eventsProducer
}
```

### Synchronize agent execution

Use a mutex to synchronize access to the agent instance, as the protocol should not trigger new execution until the previous one is finished:

```kotlin
private val agentMutex = Mutex()

agentMutex.withLock {
    // Create and run agent
}
```

### Manual notification handling

If you need custom notification handling, set `setDefaultNotifications = false` and process all agent events according to the specification:

```kotlin
install(AcpAgent) {
    this.setDefaultNotifications = false
    // Implement custom event handling
}
```

## Platform support

The ACP feature is currently available only on the JVM platform, as it depends on the ACP Kotlin SDK which is JVM-specific.

## Usage examples

Complete working examples can be found in the [Koog repository](https://github.com/JetBrains/koog/tree/develop/examples/simple-examples/src/main/kotlin/ai/koog/agents/example/acp).

### Running the example

1. Run the ACP example application:

   ```shell
   ./gradlew :examples:simple-examples:run
   ```

1. Enter a request for the ACP agent:

   ```shell
   Move file `my-file.md` to folder `my-folder` and append title '## My File' to the file content
   ```

1. Observe the event traces in the console showing the agent's execution, tool calls, and completion status.
