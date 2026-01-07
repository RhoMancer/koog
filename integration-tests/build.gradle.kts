group = "${rootProject.group}.integration-tests"
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    id("ai.koog.gradle.plugins.credentialsresolver")
    id("netty-convention")
}

kotlin {
    dependencies {

        implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

        implementation(libs.testcontainers)
        implementation(libs.ktor.server.netty)
        implementation(kotlin("test-junit5"))
        runtimeOnly(libs.ktor.client.cio)
        runtimeOnly(libs.slf4j.simple)

        testImplementation(project(":agents:agents-ext"))
        testImplementation(project(":agents:agents-features:agents-features-event-handler"))
        testImplementation(project(":agents:agents-features:agents-features-trace"))
        testImplementation(project(":agents:agents-features:agents-features-snapshot"))
        testImplementation(project(":agents:agents-mcp"))
        testImplementation(project(":agents:agents-mcp-server"))
        testImplementation(project(":agents:agents-test"))
        testImplementation(
            project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client")
        )
        testImplementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
        testImplementation(
            project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client")
        )
        testImplementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
        testImplementation(
            project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-mistralai-client")
        )
        testImplementation(libs.junit.jupiter.params)
        testImplementation(libs.kotlinx.coroutines.test)
        testImplementation(libs.kotlinx.serialization.json)
        testImplementation(libs.kotest.assertions.core)
        testImplementation(libs.aws.sdk.kotlin.sts)
        testImplementation(libs.aws.sdk.kotlin.bedrock)
        testImplementation(libs.aws.sdk.kotlin.bedrockruntime)
        testImplementation(libs.ktor.client.content.negotiation)
    }
}

configurations.all {
// make sure we have Netty as a server, not CIO
    exclude(group = "io.ktor", module = "ktor-server-cio")
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)

tasks.withType<Test> {
// Forward system properties to the test JVM
    System.getProperties().forEach { key, value ->
        systemProperty(key.toString(), value)
    }
}

// Try loading envs from file for integration tests only.
tasks.withType<Test>()
    .matching { it.name in listOf("jvmIntegrationTest", "jvmOllamaTest") }
    .configureEach {
        doFirst {
            logger.info("Loading envs from local file")
            environment(envs.get())
        }
    }
