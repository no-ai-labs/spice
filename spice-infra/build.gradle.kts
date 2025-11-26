plugins {
    kotlin("plugin.serialization")
    id("maven-publish")
}

dependencies {
    api(project(":spice-core"))

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
        create<MavenPublication>("infra") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "spice-infra"
            version = project.version.toString()

            pom {
                name.set("Spice Infra")
                description.set("Infrastructure implementations for Spice Framework (EventBus, HITL adapters, etc.)")
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
