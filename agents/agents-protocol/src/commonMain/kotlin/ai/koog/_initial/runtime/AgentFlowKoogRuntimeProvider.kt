package ai.koog._initial.runtime

/**
 *
 */
public class AgentFlowKoogRuntimeProvider : AgentFlowRuntimeProvider {

    /**
     *
     */
    override fun provide(): AgentFlowRuntime {
        return AgentFlowKoogRuntime()
    }
}
