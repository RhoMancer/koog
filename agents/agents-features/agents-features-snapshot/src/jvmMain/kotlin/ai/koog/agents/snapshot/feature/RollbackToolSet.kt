@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.java.ToolFromJavaMethod
import ai.koog.agents.core.tools.reflect.java.asTool

@Suppress("UNCHECKED_CAST")
@JavaAPI
public interface RollbackToolSet {
    @OptIn(InternalAgentToolsApi::class)
    @JavaAPI
    public fun revertToolFor(toolName: String, toolSet: ToolSet): Tool<ToolFromJavaMethod.VarArgs, *>? {
        return this::class.java.methods
            .find {
                it.isAnnotationPresent(Reverts::class.java)
                    && it.getAnnotation(Reverts::class.java).toolName == toolName
                    && it.getAnnotation(Reverts::class.java).toolSet.isInstance(toolSet)
            }
            ?.asTool() as? Tool<ToolFromJavaMethod.VarArgs, *>
    }
}
