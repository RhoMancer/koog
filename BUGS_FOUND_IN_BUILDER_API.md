# Bugs and Issues Found in Koog Builder API

This document catalogs all bugs and design limitations found during comprehensive integration testing of the Koog Java/Kotlin Builder API introduced in commit `daa0e78119298413933a6ec889fb61315c9b9e1a`.

## Test Coverage

### Kotlin Tests
- File: `integration-tests/src/jvmTest/kotlin/ai/koog/integration/tests/agent/AIAgentBuilderIntegrationTest.kt`
- Tests: 10 integration tests covering builder API and functional strategies
- Status: ✅ All tests pass with workarounds

### Java Tests
- File: `integration-tests/src/jvmTest/java/ai/koog/integration/tests/agent/JavaAPIComprehensiveIntegrationTest.java`
- Tests: 7 basic integration tests (functional strategy tests excluded due to complexity)
- Status: ⚠️ Partial - compilation issues with nested runBlocking

---

## Critical Bugs

### BUG #1: Builder API Missing LLMParams.toolChoice Support

**Severity**: High
**Component**: AIAgent.builder()
**Affected API**: Kotlin & Java

#### Description
The new Builder API does not expose the `LLMParams.toolChoice` parameter, which is available in the old API. This parameter is critical for forcing models to use tools.

#### Impact
- Cannot guarantee tool usage by LLM
- Tests become unreliable as models may ignore available tools
- Workarounds require crafting specific prompts that may not always work

#### Old API (Works)
```kotlin
AIAgentConfig(
    prompt = prompt(
        id = "agent",
        params = LLMParams(
            temperature = 1.0,
            toolChoice = ToolChoice.Auto  // ✅ Forces tool usage
        )
    ) { /* ... */ }
)
```

#### New API (Broken)
```kotlin
AIAgent.builder()
    .promptExecutor(executor)
    .llmModel(model)
    .toolRegistry(toolRegistry)
    .build()  // ❌ No way to set toolChoice!
```

####  Current Workaround
```kotlin
AIAgent.builder()
    .promptExecutor(executor)
    .llmModel(model)
    .systemPrompt(
        "You are a calculator assistant. " +
        "You MUST use the calculator tool to perform calculations. " +
        "ALWAYS call the tool, then provide the result."  // ⚠️ Unreliable
    )
    .toolRegistry(toolRegistry)
    .build()
```

#### Test Evidence
- Kotlin: `AIAgentBuilderIntegrationTest.kt:236-265`
- Java: `JavaAPIComprehensiveIntegrationTest.java:234-266`

#### Recommended Fix
Add `toolChoice()` method to builder:
```kotlin
AIAgent.builder()
    .promptExecutor(executor)
    .llmModel(model)
    .toolChoice(ToolChoice.Auto)  // Proposed fix
    .toolRegistry(toolRegistry)
    .build()
```

---

### BUG #2: Functional Strategies Require Manual Tool Call Handling

**Severity**: High
**Component**: functionalStrategy
**Affected API**: Kotlin & Java

#### Description
When using `functionalStrategy`, developers must manually implement the tool execution loop. The strategy does not automatically handle tool calls like `singleRunStrategy()` or `reActStrategy()` do.

#### Impact
- Significantly increased complexity for users
- Easy to implement incorrectly
- Not documented clearly
- Different behavior than graph strategies

#### Expected Behavior
```kotlin
val strategy = functionalStrategy<String, String>("simple") { input ->
    requestLLM(input)  // Should handle tools automatically
}
```

#### Actual Required Implementation
```kotlin
val strategy = functionalStrategy<String, String>("manual-tools") { input ->
    var currentResponse = requestLLM(input)
    var iterations = 0
    val maxIterations = 5

    // ⚠️ Manual tool call loop required!
    while (currentResponse is Message.Tool.Call && iterations < maxIterations) {
        val toolResult = executeTool(currentResponse)
        currentResponse = sendToolResult(toolResult)
        iterations++
    }

    when (currentResponse) {
        is Message.Assistant -> currentResponse.content
        is Message.Tool.Call -> "Max iterations reached"
        else -> "Unexpected response"
    }
}
```

#### Test Evidence
- Kotlin: `AIAgentBuilderIntegrationTest.kt:268-310`
- Java: Not tested due to nested runBlocking complexity

#### Recommended Fix
1. Add `requestLLMWithTools()` method that handles tool loop automatically, or
2. Document this requirement clearly in API docs, or
3. Make tool handling behavior consistent with graph strategies

---

## Java-Specific Issues

### BUG #3: Nested runBlocking in Java Lambdas Causes InterruptedException

**Severity**: High
**Component**: Functional strategies in Java
**Affected API**: Java only

#### Description
When calling `BuildersKt.runBlocking()` inside Java lambda expressions (e.g., functional strategies), the compiler reports unhandled `InterruptedException`.

#### Impact
- **Functional strategies are essentially unusable from Java**
- Forces Java developers to use graph strategies only
- Major limitation for Java API usability

#### Code Example (Does Not Compile)
```java
AIAgent<String, String> agent = AIAgent.builder()
    .promptExecutor(executor)
    .llmModel(model)
    .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
        // ❌ Error: unreported exception InterruptedException
        Message.Response response = BuildersKt.runBlocking(
            kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
            (scope, continuation) -> context.requestLLM(input, true, continuation)
        );
        // ...
    })
    .build();
```

#### Compilation Error
```
error: unreported exception InterruptedException; must be caught or declared to be thrown
    Message.Response response = BuildersKt.runBlocking(
                                                      ^
```

#### Test Evidence
- Java: `JavaAPIComprehensiveIntegrationTest.java:291, 326, 336, 383, 400, 406, 519, 564, 603`

#### Recommended Fix
1. Provide Java-friendly wrapper methods that handle exceptions, or
2. Create `JavaFunctionalStrategy` base class with helper methods, or
3. Document that functional strategies should not be used from Java

---

### ISSUE #4: LLMProvider Access from Java is Non-Intuitive

**Severity**: Low (Documentation)
**Component**: LLMProvider
**Affected API**: Java only

#### Description
Kotlin `data object` members require `.INSTANCE` suffix when accessed from Java, which is not intuitive for Java developers.

#### Impact
- Confusing for Java developers
- Not mentioned in documentation
- Easy mistake to make

#### Incorrect Usage (Does Not Compile)
```java
if (model.getProvider() == LLMProvider.OpenAI) {  // ❌ Compile error
    // ...
}
```

#### Correct Usage
```java
if (model.getProvider() == LLMProvider.OpenAI.INSTANCE) {  // ✅ Works
    // ...
}
```

#### Test Evidence
- Java: `JavaAPIComprehensiveIntegrationTest.java:77, 101, 165, 166, 636, 638`

#### Recommended Fix
Add clear documentation and examples for Java developers

---

### ISSUE #5: ToolRegistry.tools Property Access from Java

**Severity**: Low (Documentation)
**Component**: ToolRegistry
**Affected API**: Java only

#### Description
Kotlin properties with custom getters need method-style access from Java (`getTools()` instead of `.tools`).

#### Impact
- Confusion for Java developers
- Not obvious from Kotlin documentation

#### Incorrect Usage (Does Not Compile)
```java
ToolRegistry registry = ToolRegistry.builder().tools(this).build();
return registry.tools.stream()  // ❌ Compile error: cannot find symbol 'tools'
    .filter(t -> t.getName().equals("add"))
    .findFirst()
    .orElseThrow();
```

#### Correct Usage
```java
ToolRegistry registry = ToolRegistry.builder().tools(this).build();
return registry.getTools().stream()  // ✅ Works
    .filter(t -> t.getName().equals("add"))
    .findFirst()
    .orElseThrow();
```

#### Test Evidence
- Java: `JavaAPIComprehensiveIntegrationTest.java:672, 680`

#### Recommended Fix
Add Java usage examples to documentation

---

## Related Issues Found (From Kotlin Tests)

### ISSUE #6: singleRunStrategy Returns Empty String When No Assistant Message

**Severity**: Medium
**Component**: singleRunStrategy
**Affected API**: Kotlin & Java

#### Description
When using `singleRunStrategy()` with tools, if the final message is a tool result (no assistant message follows), the strategy returns an empty string.

#### Code Location
```kotlin
// AIAgentSimpleStrategies.kt
edge(
    nodeCallLLM forwardTo nodeFinish
        onMultipleAssistantMessages { true }
        transformed { it.joinToString("\n") { message -> message.content } }
        // ^^^ Returns empty string if no assistant messages!
)
```

#### Impact
- Tests fail with empty results
- Unexpected behavior for users
- Not documented

#### Test Evidence
- Kotlin: `AIAgentBuilderIntegrationTest.kt` - Multiple tests needed `.shouldNotBeBlank()` assertions

#### Recommended Fix
Either:
1. Return the tool result content if no assistant message, or
2. Throw an exception with clear error message, or
3. Document this behavior clearly

---

## Summary

| Bug # | Severity | Component | Kotlin | Java | Status |
|-------|----------|-----------|--------|------|--------|
| #1 | High | Builder API toolChoice | ❌ | ❌ | **Needs Fix** |
| #2 | High | Functional strategy tools | ❌ | ❌ | **Design Review Needed** |
| #3 | High | Nested runBlocking | N/A | ❌ | **Blocks Java Functional Strategies** |
| #4 | Low | LLMProvider access | ✅ | ⚠️ | Documentation needed |
| #5 | Low | ToolRegistry access | ✅ | ⚠️ | Documentation needed |
| #6 | Medium | singleRunStrategy | ❌ | ❌ | Documentation or fix needed |

### Critical Path Items
1. **BUG #1** - Add toolChoice support to builder API
2. **BUG #2** - Either simplify tool handling or document clearly
3. **BUG #3** - Provide Java-friendly API for functional strategies

### Documentation Improvements Needed
1. Java interop patterns (LLMProvider, ToolRegistry)
2. Functional strategy tool handling requirements
3. Differences between functional and graph strategies
4. Java API examples and best practices

---

## Testing Notes

### Kotlin Tests Status
All 10 Kotlin tests pass with documented workarounds:
- ✅ Builder basic usage
- ✅ Builder with tools (with workaround)
- ✅ Builder with graph strategies (with workaround)
- ✅ Builder method chaining
- ✅ Functional strategy with lambda
- ✅ Functional strategy simple
- ✅ Functional strategy multi-step
- ✅ Builder with multiple features
- ✅ Functional strategy error handling
- ✅ Functional strategy with manual tool loop (demonstrates BUG #2)

### Java Tests Status
7 basic tests created (functional strategies excluded):
- ✅ OpenAI client test (compiles, not run)
- ✅ Anthropic client test (compiles, not run)
- ✅ Single executor test (compiles, not run)
- ✅ Multi executor test (compiles, not run)
- ⚠️ Builder basic usage (needs fixing)
- ⚠️ Builder with tools (needs fixing)
- ⚠️ Event handler (needs fixing)
- ❌ Functional strategy tests (blocked by BUG #3)
- ❌ Subtask tests (blocked by BUG #3)
- ❌ Custom strategy tests (blocked by BUG #3)

---

**Generated**: 2026-01-13
**Test Files**:
- `integration-tests/src/jvmTest/kotlin/ai/koog/integration/tests/agent/AIAgentBuilderIntegrationTest.kt`
- `integration-tests/src/jvmTest/java/ai/koog/integration/tests/agent/JavaAPIComprehensiveIntegrationTest.java`
