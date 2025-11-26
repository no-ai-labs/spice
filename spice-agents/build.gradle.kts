plugins {
    kotlin("plugin.serialization")
    id("maven-publish")
}

dependencies {
    api(project(":spice-core"))

    // HTTP client (standalone mode - no Spring dependency)
    implementation("io.ktor:ktor-client-core:3.3.2")
    implementation("io.ktor:ktor-client-cio:3.3.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.2")
    implementation("io.ktor:ktor-client-logging:3.3.2")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    enabled = true
}

publishing {
    publications {
        create<MavenPublication>("agents") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "spice-agents"
            version = project.version.toString()

            pom {
                name.set("Spice Agents")
                description.set("Standalone LLM agent implementations for Spice Framework (OpenAI, Anthropic, etc.)")
                url.set("https://github.com/no-ai-labs/spice")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("no-ai-labs")
                        name.set("Spice Framework Team")
                        email.set("human@noailabs.ai")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/no-ai-labs/spice.git")
                    developerConnection.set("scm:git:ssh://github.com/no-ai-labs/spice.git")
                    url.set("https://github.com/no-ai-labs/spice")
                }
            }
        }
    }
}
