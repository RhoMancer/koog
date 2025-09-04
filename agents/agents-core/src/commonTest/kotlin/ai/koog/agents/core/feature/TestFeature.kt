package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.prompt.message.Message

class TestFeature(val events: MutableList<String>) {

    class Config : FeatureConfig() {
        var events: MutableList<String>? = null
    }

    companion object Feature : AIAgentFeature<Config, TestFeature> {
        override val key: AIAgentStorageKey<TestFeature> = createStorageKey("test-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(
            config: Config,
            pipeline: AIAgentPipeline
        ) {
            val feature = TestFeature(events = config.events ?: mutableListOf())
            val context = InterceptContext(this, feature)

            pipeline.interceptAgentStarting(context) { eventContext ->
                feature.events += "Agent: before agent started (strategy name: ${eventContext.strategy.name})"
            }

            pipeline.interceptStrategyStarting(context) { eventContext ->
                feature.events += "Agent: strategy started (strategy name: ${eventContext.strategy.name})"
            }

            pipeline.interceptContextAgentFeature(this) { _: AIAgentContextBase ->
                feature.events += "Agent Context: request features from agent context"
                TestFeature(mutableListOf())
            }

            pipeline.interceptLLMCallStaring(context) { event ->
                feature.events +=
                    "LLM: start LLM call (prompt: ${event.prompt.messages.firstOrNull {
                        it.role == Message.Role.User
                    }?.content}, tools: [${event.tools.joinToString { it.name }}])"
            }

            pipeline.interceptLLMCallCompleted(context) { event ->
                feature.events +=
                    "LLM: finish LLM call (responses: [${event.responses.joinToString(", ") {
                        "${it.role.name}: ${it.content}"
                    }}])"
            }

            pipeline.interceptNodeExecutionStarting(context) { event ->
                feature.events += "Node: start node (name: ${event.node.name}, input: ${event.input})"
            }

            pipeline.interceptNodeExecutionCompleted(context) { event ->
                feature.events +=
                    "Node: finish node (name: ${event.node.name}, input: ${event.input}, output: ${event.output})"
            }

            pipeline.interceptNodeExecutionFailed(context) { event ->
                feature.events += "Node: execution error (name: ${event.node.name}, error: ${event.throwable.message})"
            }

            pipeline.interceptToolExecutionStarting(context) { event ->
                feature.events += "Tool: call tool (tool: ${event.tool.name}, args: ${event.toolArgs})"
            }

            pipeline.interceptToolExecutionCompleted(context) { event ->
                feature.events +=
                    "Tool: finish tool call with result (tool: ${event.tool.name}, result: ${event.result?.toStringDefault() ?: "null"})"
            }
        }
    }
}
