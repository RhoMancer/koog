package ai.koog.gradle.tests

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * Common test configuration that can be applied to any Test task
 */
internal fun Test.configureCommonTestSettings(
    testType: TestType = TestType.DEFAULT,
    maxHeap: String? = null
) {
    useJUnitPlatform()
    filter { configureFilter(testType) }
    group = "verification"

    val heapSize = maxHeap ?: testType.maxHeapForJvm
    heapSize?.let { maxHeapSize = it }
}

/**
 * For KMP projects
 */
fun KotlinJvmTarget.configureTests(maxHeap: String? = null) {
    for (testType in TestType.values()) {
        testRuns.maybeCreate(testType.shortName).executionTask {
            configureCommonTestSettings(testType, maxHeap)
        }
    }
}

/**
 * For pure JVM projects
 */
fun Project.configureJvmTests(maxHeap: String? = null) {
    for (testType in TestType.values()) {
        val shortName = if (testType != TestType.DEFAULT) testType.shortName.uppercaseFirstChar() else ""

        tasks.register<Test>("jvm" + shortName + "Test") {
            configureCommonTestSettings(testType, maxHeap)
        }
    }
}
