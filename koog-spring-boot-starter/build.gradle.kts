import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm.publish")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.management)
}

dependencies {
    api(libs.reactor.kotlin.extensions)
    api(project(":koog-agents"))
    implementation(project.dependencies.platform(libs.spring.boot.bom))
    api(libs.spring.boot.starter)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.ktor.client.apache5)
}

publishToMaven()
