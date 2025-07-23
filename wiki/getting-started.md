# Getting Started with Spice Framework

## Installation

Add Spice Framework to your project using JitPack:

[![](https://jitpack.io/v/no-ai-labs/spice-framework.svg)](https://jitpack.io/#no-ai-labs/spice-framework)

### Gradle (Kotlin DSL)

First, add JitPack repository:
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependencies:
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:Tag")
    
    // Optional: Spring Boot support
    implementation("com.github.no-ai-labs.spice-framework:spice-springboot:Tag")
}
```

### Maven

Add JitPack repository:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then add the dependency:
```xml
<dependency>
    <groupId>com.github.no-ai-labs.spice-framework</groupId>
    <artifactId>spice-core</artifactId>
    <version>Tag</version>
</dependency>
```

> Replace `Tag` with the latest version: [![](https://jitpack.io/v/no-ai-labs/spice-framework.svg)](https://jitpack.io/#no-ai-labs/spice-framework)

## Your First Agent

### 1. Simple Agent
```kotlin
import io.github.spice.dsl.*

fun main() {
    // Create an agent
    val greeter = buildAgent {
        id = "greeter"
        name = "Greeting Agent"
        
        handle { comm ->
            when {
                comm.content.contains("hello", ignoreCase = true) -> 
                    comm.reply("Hello there! 👋", id)
                comm.content.contains("bye", ignoreCase = true) -> 
                    comm.reply("Goodbye! See you later!", id)
                else -> 
                    comm.reply("I can say hello or goodbye!", id)
            }
        }
    }
    
    // Use the agent
    val response = greeter.process(comm("Hello agent!"))
    println(response.content) // "Hello there! 👋"
}
```

### 2. Agent with Tools
```kotlin
val calculator = buildAgent {
    id = "calc"
    name = "Calculator Agent"
    
    // Add built-in calculator tool
    tools {
        useGlobal("calculator")
    }
    
    handle { comm ->
        if (comm.content.contains("calculate")) {
            val result = useTool("calculator", mapOf(
                "expression" to comm.content.substringAfter("calculate").trim()
            ))
            comm.reply("Result: ${result.result}", id)
        } else {
            comm.reply("Send me something to calculate!", id)
        }
    }
}
```

## Built-in Tools

Spice Framework comes with several built-in tools:

1. **Calculator** - Mathematical expressions
2. **Text Processor** - Text manipulation
3. **DateTime** - Date/time operations  
4. **Random** - Random value generation

### Using Built-in Tools
```kotlin
import io.github.spice.*

// Register built-in tools globally
ToolRegistry.register(ToolWrapper("calculator", calculatorTool()))
ToolRegistry.register(ToolWrapper("textProcessor", textProcessorTool()))
ToolRegistry.register(ToolWrapper("datetime", dateTimeTool()))
ToolRegistry.register(ToolWrapper("random", randomTool()))

// Now agents can use them
val agent = buildAgent {
    tools {
        useGlobal("calculator", "datetime")
    }
    // ...
}
```

## Communication System

The framework uses the `Comm` (Communication) type for all messaging:

```kotlin
// Create a message
val message = comm("Hello") {
    from("user")
    to("agent")
    type(CommType.TEXT)
    data("key", "value")
}

// Quick creation functions
val system = systemComm("System notification", "user")
val error = errorComm("Something went wrong", "user")
```

## Next Steps

- Learn about [Core Concepts](core-concepts.md)
- Explore [Agent Development](agent-guide.md)
- Try the [Sample Projects](../spice-dsl-samples)