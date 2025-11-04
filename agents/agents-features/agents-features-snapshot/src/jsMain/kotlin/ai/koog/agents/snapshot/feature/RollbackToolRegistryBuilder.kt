package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.tools.Tool
import kotlin.jvm.JvmStatic

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
public actual open class RollbackToolRegistryBuilder actual constructor() {
    private val delegate = RollbackToolRegistryBuilderImpl()

    public actual open fun <TArgs> registerRollback(
        tool: Tool<TArgs, *>,
        rollbackTool: Tool<TArgs, *>
    ): RollbackToolRegistryBuilder = delegate.registerRollback(tool, rollbackTool)

    public actual open fun build(): RollbackToolRegistry = delegate.build()
}
