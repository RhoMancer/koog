# Overview

Koog is an open-source JetBrains framework for building AI agents with an idiomatic, type-safe Kotlin DSL designed specifically for JVM and Kotlin developers. It lets you create agents that interact with tools, handle complex workflows, and communicate with users.

You can customize agent capabilities with a modular feature system and deploy your agents across JVM, JS, WasmJS, Android, and iOS targets using Kotlin Multiplatform.

- [**Getting started**](getting-started/)

  ______________________________________________________________________

  Build and run your first AI agent

- [**Glossary**](glossary/)

  ______________________________________________________________________

  Learn the essential terms

## Agent types

- [**Basic agents**](basic-agents/)

  ______________________________________________________________________

  Create and run agents that process a single input and provide a response

- [**Functional agents**](functional-agents/)

  ______________________________________________________________________

  Create and run lightweight agents with custom logic in plain Kotlin

- [**Complex workflow agents**](complex-workflow-agents/)

  ______________________________________________________________________

  Create and run agents that handle complex workflows with custom strategies

- [**Planner agents**](planner-agents/)

  ______________________________________________________________________

  Create and run agents that iteratively build and execute plans

## Core functionality

- [**Prompts**](prompts/)

  ______________________________________________________________________

  Create prompts, run them using LLM clients or prompt executors, switch between LLMs and providers, and handle failures with built-in retries

- [**Tools**](tools-overview/)

  ______________________________________________________________________

  Enhance your agents with built‑in, annotation‑based, or class‑based tools that can access external systems and APIs

- [**Strategies**](predefined-agent-strategies/)

  ______________________________________________________________________

  Design complex agent behaviors using intuitive graph-based workflows

- [**Events**](agent-events/)

  ______________________________________________________________________

  Monitor and process agent lifecycle, strategy, node, LLM call, and tool call events with predefined handlers

## Advanced usage

- [**History compression**](history-compression/)

  ______________________________________________________________________

  Optimize token usage while maintaining context in long-running conversations using advanced techniques

- [**Agent persistence**](agent-persistence/)

  ______________________________________________________________________

  Restore the agent state at specific points during execution

- [**Structured output**](structured-output/)

  ______________________________________________________________________

  Generate responses in structured formats

- [**Streaming API**](streaming-api/)

  ______________________________________________________________________

  Process responses in real-time with streaming support and parallel tool calls

- [**Knowledge retrieval**](embeddings/)

  ______________________________________________________________________

  Retain and retrieve knowledge across conversations using [vector embeddings](embeddings/), [ranked document storage](ranked-document-storage/), and [shared agent memory](agent-memory/)

- [**Tracing**](tracing/)

  ______________________________________________________________________

  Debug and monitor agent execution with detailed, configurable tracing

## Integrations

- [**Model Context Protocol (MCP)**](model-context-protocol/)

  ______________________________________________________________________

  Use MCP tools directly in AI agents

- [**Spring Boot**](spring-boot/)

  ______________________________________________________________________

  Add Koog to your Spring applications

- [**Ktor**](ktor-plugin/)

  ______________________________________________________________________

  Integrate Koog with Ktor servers

- [**OpenTelemetry**](opentelemetry-support/)

  ______________________________________________________________________

  Trace, log, and measure your agent with popular observability tools

- [**A2A Protocol**](a2a-protocol-overview/)

  ______________________________________________________________________

  Connect agents and services over a shared protocol
