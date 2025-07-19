# Getting Started with Spice Framework

## Installation

Add Spice Framework to your project:

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("io.github.spice:spice-core:0.1.0")
    
    // Optional: Spring Boot support
    implementation("io.github.spice:spice-springboot:0.1.0")
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.spice</groupId>
    <artifactId>spice-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

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