@file:OptIn(ExperimentalWasmDsl::class)

import ai.koog.gradle.publish.maven.configureJvmJarManifest
import ai.koog.gradle.tests.configureTests
// import jetbrains.sign.GpgSignSignatoryProvider // Temporarily commented to allow build without blocked repositories
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
    // id("com.android.library") // Temporarily commented to allow build without blocked repositories
    id("signing")
}

kotlin {
    // Tiers are in accordance with <https://kotlinlang.org/docs/native-target-support.html>
    // Tier 1
    iosSimulatorArm64()
    iosX64()

    // Tier 2
    iosArm64()

    // Tier 3

    // Android - Temporarily commented to allow build without blocked repositories
    // androidTarget()

    // jvm & js
    jvm {
        configureTests()
    }

    js(IR) {
        browser {
            binaries.library()
        }

        configureTests()
    }

    wasmJs {
        browser()
        nodejs()
        binaries.library()
    }

    sourceSets {
        // androidUnitTest { // Temporarily commented to allow build without blocked repositories
        //     dependencies {
        //         implementation(kotlin("test-junit"))
        //     }
        // }
    }

    compilerOptions {
        coreLibrariesVersion = "2.1.21"
    }
}

// android { // Temporarily commented to allow build without blocked repositories
//     compileSdk = 36
//     namespace = "${project.group.toString().replace('-', '.')}.${project.name.replace('-', '.')}"
// 
//     compileOptions {
//         sourceCompatibility = JavaVersion.VERSION_17
//         targetCompatibility = JavaVersion.VERSION_17
//     }
// }

configureJvmJarManifest("jvmJar")

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType(MavenPublication::class).all {
        if (name.contains("jvm", ignoreCase = true)) {
            artifact(javadocJar)
        }
    }
}

val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
signing {
    if (isUnderTeamCity) {
        // signatories = GpgSignSignatoryProvider() // Temporarily commented to allow build without blocked repositories
        sign(publishing.publications)
    }
}
