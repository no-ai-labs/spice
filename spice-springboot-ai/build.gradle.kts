plugins {
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot") version "3.5.7"
    id("maven-publish")
}

repositories {
    mavenLocal()  // Check local Maven repository first
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
    maven {
        url = uri("https://repo.spring.io/release")
    }
    maven {
        url = uri("https://repo.spring.io/snapshot")
    }
}

dependencies {
    api(project(":spice-core"))
    api(project(":spice-springboot"))

    implementation("org.springframework.boot:spring-boot-starter:3.5.7")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.7")

    // Spring AI dependencies (1.1.0 uses new module structure)
    implementation("org.springframework.ai:spring-ai-model:1.1.0")
    implementation("org.springframework.ai:spring-ai-client-chat:1.1.0")
    implementation("org.springframework.ai:spring-ai-commons:1.1.0")
    implementation("org.springframework.ai:spring-ai-openai:1.1.0")
    implementation("org.springframework.ai:spring-ai-anthropic:1.1.0")
    implementation("org.springframework.ai:spring-ai-ollama:1.1.0")

    // Optional: Additional Spring AI providers (Azure, Vertex, Bedrock, etc.)
    compileOnly("org.springframework.ai:spring-ai-azure-openai:1.1.0")
    compileOnly("org.springframework.ai:spring-ai-vertex-ai-gemini:1.1.0")
    compileOnly("org.springframework.ai:spring-ai-bedrock:1.1.0")

    // Kotlin coroutines support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Spring Boot configuration processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.5.7")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.7")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    enabled = true
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

publishing {
    publications {
        create<MavenPublication>("springboot-ai") {
            from(components["java"])
            groupId = "io.github.noailabs"
            artifactId = "spice-springboot-ai"
            version = "1.0.0-beta"

            pom {
                name.set("Spice Spring Boot AI Extension")
                description.set("Spring AI integration for Spice Framework with multi-provider support (OpenAI, Anthropic, Ollama, etc.)")
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
