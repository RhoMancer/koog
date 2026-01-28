import ai.koog.gradle.publish.maven.configureJvmJarManifest
// import jetbrains.sign.GpgSignSignatoryProvider // Temporarily commented to allow build without blocked repositories

plugins {
    kotlin("jvm")
    `maven-publish`
    id("signing")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

configureJvmJarManifest("jar")

val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
signing {
    if (isUnderTeamCity) {
        // signatories = GpgSignSignatoryProvider() // Temporarily commented to allow build without blocked repositories
        sign(publishing.publications)
    }
}
