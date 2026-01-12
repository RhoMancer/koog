import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":rag:rag-base"))
                api(project(":embeddings:embeddings-base"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.io.core)
            }
        }

        jvmMain {
            dependencies {
                api(libs.exposed.core)
                api(libs.exposed.dao)
                api(libs.exposed.jdbc)
                api(libs.exposed.json)
                api(libs.exposed.kotlin.datetime)
                compileOnly(libs.postgresql)
                implementation(libs.hikaricp)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-test"))
                implementation(project(":test-utils"))
                implementation(libs.mockk)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.postgresql)

                runtimeOnly(libs.postgresql)
            }
        }
    }

    explicitApi()
}

publishToMaven()
