import ai.koog.gradle.tests.configureCommonTestSettings

plugins {
    kotlin("jvm")
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
}

tasks.test {
    configureCommonTestSettings()
}
