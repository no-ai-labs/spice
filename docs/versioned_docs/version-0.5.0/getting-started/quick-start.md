# Quick Start

Build your first Spice application in 5 minutes.

## Prerequisites

- Kotlin 1.9.0+
- JDK 11+
- Gradle

## Step 1: Create Project

```bash
mkdir spice-demo
cd spice-demo
gradle init --type kotlin-application
```

## Step 2: Add Dependencies

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:latest")
}
```

## Step 3: Create Your Agent

```kotlin
// src/main/kotlin/Main.kt
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val chatbot = buildAgent {
        id = "chatbot"
        name = "Simple Chatbot"

        tool("greet") {
            description = "Greet the user"
            parameter("name", "string", "User's name")
            execute { params ->
                ToolResult.success("Hello, ${params["name"]}! Welcome to Spice!")
            }
        }

        handle { comm ->
            when {
                comm.content.startsWith("greet") -> {
                    val name = comm.content.removePrefix("greet ").trim()
                    val result = run("greet", mapOf("name" to name))
                    comm.reply(result.result, id)
                }
                else -> comm.reply("Try: greet [name]", id)
            }
        }
    }

    // Test the chatbot
    val response = chatbot.processComm(
        Comm(content = "greet Alice", from = "user")
    )
    println(response.content)
}
```

## Step 4: Run

```bash
./gradlew run
```

## What's Next?

- [Build Complex Agents](../dsl-guide/build-agent)
- [Add LLM Integration](../llm-integrations/overview)
- [Create Multi-Agent Systems](../orchestration/multi-agent)
