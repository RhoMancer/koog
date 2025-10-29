import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":prompt:prompt-model"))
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-markdown"))
                api(libs.kotlinx.serialization.json)
                api(libs.oshai.kotlin.logging)
                api(libs.kotlinx.coroutines.core)
            }
        }
        androidMain {
            dependencies {
                runtimeOnly(libs.slf4j.simple)
            }
        }
        jvmMain {
            dependencies {
                api(kotlin("reflect"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
