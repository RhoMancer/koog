@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.annotation.InternalAgentsApi

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
public actual open class RollbackToolRegistryBuilder actual constructor(
    internal actual val delegate: RollbackToolRegistryBuilderImpl
) : RollbackToolRegistryBuilderAPI by delegate
