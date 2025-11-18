# 🚀 Spice Framework 1.0.0 - Quick Start Guide

Welcome to Spice Framework 1.0.0! This guide will get you up and running in minutes.

## What's New in 1.0.0?

Spice 1.0.0 introduces a revolutionary **single message type** architecture:

- ✅ **SpiceMessage** - One unified message type replaces Comm/NodeContext/NodeResult
- ✅ **Graph DSL** - Build complex workflows with nodes and edges
- ✅ **State Machine** - Built-in execution states (READY → RUNNING → WAITING → COMPLETED/FAILED)
- ✅ **HITL Support** - Human-in-the-Loop with checkpoint/resume
- ✅ **Tool Standardization** - OpenAI/Anthropic compatible tool calls

---

## Installation

### Prerequisites
- Kotlin 2.2.0+
- JDK 17+
- Gradle 8.0+ or Maven 3.8+

### Gradle (Kotlin DSL)

Add the Spice repository and dependencies:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("http://218.232.94.139:8081/repository/maven-releases/")
            credentials {
                username = project.findProperty("nexusUsername") as String? ?: System.getenv("NEXUS_USERNAME")
                password = project.findProperty("nexusPassword") as String? ?: System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}

// build.gradle.kts
dependencies {
    // Core framework
    implementation("io.github.noailabs:spice-core:1.0.0-beta-1")

    // Optional: Agents module
    implementation("io.github.noailabs:spice-agents:1.0.0-beta-1")

    // Optional: Spring Boot integration
    implementation("io.github.noailabs:spice-springboot:1.0.0-beta-1")

    // Optional: Spring AI integration
    implementation("io.github.noailabs:spice-springboot-ai:1.0.0-beta-1")

    // Optional: State machine integration
    implementation("io.github.noailabs:spice-springboot-statemachine:1.0.0-beta-1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

### Maven

```xml
<!-- Add repository -->
<repositories>
    <repository>
        <id>noai-nexus</id>
        <url>http://218.232.94.139:8081/repository/maven-releases/</url>
    </repository>
</repositories>

<!-- Add dependencies -->
<dependencies>
    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-core</artifactId>
        <version>1.0.0-beta-1</version>
    </dependency>

    <!-- Optional modules -->
    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-agents</artifactId>
        <version>1.0.0-beta-1</version>
    </dependency>
</dependencies>
```

---

## Your First Agent

### Example 1: Simple Echo Agent

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.agent.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create a simple echo agent
    val echoAgent = object : Agent {
        override val id = "echo"
        override val name = "Echo Agent"
        override val description = "Echoes user input"
        override val capabilities = listOf("chat")

        override suspend fun processMessage(
            message: SpiceMessage
        ): SpiceResult<SpiceMessage> {
            // Extract input
            val userInput = message.content

            // Process and reply
            return SpiceResult.success(
                message.reply("Echo: $userInput", id)
            )
        }
    }

    // Use the agent
    val message = SpiceMessage.create("Hello, Spice!", "user")
    val result = echoAgent.processMessage(message)

    when (result) {
        is SpiceResult.Success -> println(result.value.content)  // "Echo: Hello, Spice!"
        is SpiceResult.Failure -> println("Error: ${result.error.message}")
    }
}
```

### Example 2: Agent with Data Processing

```kotlin
class GreetingAgent : Agent {
    override val id = "greeting"
    override val name = "Greeting Agent"
    override val description = "Greets users by name"
    override val capabilities = listOf("greeting")

    override suspend fun processMessage(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        // Extract data from message
        val userName = message.getData<String>("userName") ?: "Guest"
        val userId = message.getMetadata<String>("userId")

        // Create greeting
        val greeting = "Hello, $userName! Welcome to Spice Framework!"

        // Return with additional data
        return SpiceResult.success(
            message.reply(greeting, id)
                .withData(mapOf(
                    "greeted" to true,
                    "greetingTime" to Clock.System.now().toString()
                ))
                .withMetadata(mapOf(
                    "processedBy" to id
                ))
        )
    }
}

fun main() = runBlocking {
    val agent = GreetingAgent()

    val message = SpiceMessage.create("Hi there", "user")
        .withData(mapOf("userName" to "Alice"))
        .withMetadata(mapOf("userId" to "user-123"))

    val result = agent.processMessage(message)
    println(result.getOrNull()?.content)  // "Hello, Alice! Welcome to Spice Framework!"
}
```

---

## Building Workflows with Graph DSL

### Example 3: Simple Linear Workflow

```kotlin
import io.github.noailabs.spice.graph.*
import io.github.noailabs.spice.node.*

fun main() = runBlocking {
    // Create agents
    val analyzerAgent = object : Agent {
        override val id = "analyzer"
        override val name = "Text Analyzer"
        override val description = "Analyzes text"
        override val capabilities = listOf("analysis")

        override suspend fun processMessage(message: SpiceMessage) =
            SpiceResult.success(
                message.reply("Analysis: ${message.content.length} characters", id)
                    .withData(mapOf("wordCount" to message.content.split(" ").size))
            )
    }

    val summarizerAgent = object : Agent {
        override val id = "summarizer"
        override val name = "Summarizer"
        override val description = "Summarizes text"
        override val capabilities = listOf("summarization")

        override suspend fun processMessage(message: SpiceMessage) = SpiceResult.success(
            message.reply("Summary: ${message.content.take(50)}...", id)
        )
    }

    // Build graph
    val graph = graph("text-processing") {
        // Define nodes
        agent("analyze", analyzerAgent)
        agent("summarize", summarizerAgent)
        output("result") { message -> message.content }

        // Define flow
        edge("analyze", "summarize")
        edge("summarize", "result")
    }

    // Execute
    val runner = DefaultGraphRunner()
    val initialMessage = SpiceMessage.create("This is a sample text for processing", "user")
    val result = runner.execute(graph, initialMessage)

    println(result.getOrNull()?.content)
}
```

### Example 4: Workflow with Tool Calls

```kotlin
import io.github.noailabs.spice.tool.*

// Create a simple tool
class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Performs arithmetic calculations"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "operation" to ParameterSchema("string", "add, subtract, multiply, divide", required = true),
            "a" to ParameterSchema("number", "First number", required = true),
            "b" to ParameterSchema("number", "Second number", required = true)
        )
    )

    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val operation = params["operation"] as String
        val a = (params["a"] as Number).toDouble()
        val b = (params["b"] as Number).toDouble()

        val result = when (operation) {
            "add" -> a + b
            "subtract" -> a - b
            "multiply" -> a * b
            "divide" -> if (b != 0.0) a / b else return SpiceResult.success(
                ToolResult(success = false, result = null, error = "Division by zero")
            )
            else -> return SpiceResult.success(
                ToolResult(success = false, result = null, error = "Unknown operation")
            )
        }

        return SpiceResult.success(
            ToolResult(success = true, result = result)
        )
    }
}

fun main() = runBlocking {
    val calcTool = CalculatorTool()

    val graph = graph("calculator-workflow") {
        // Add tool node
        tool("calculate", calcTool) { message ->
            // Extract parameters from message
            mapOf(
                "operation" to (message.getData<String>("operation") ?: "add"),
                "a" to (message.getData<Int>("a") ?: 0),
                "b" to (message.getData<Int>("b") ?: 0)
            )
        }

        output("result") { message ->
            message.getData<Double>("tool_result")?.toString() ?: "No result"
        }

        edge("calculate", "result")
    }

    val runner = DefaultGraphRunner()
    val message = SpiceMessage.create("Calculate", "user")
        .withData(mapOf(
            "operation" to "multiply",
            "a" to 6,
            "b" to 7
        ))

    val result = runner.execute(graph, message)
    println("Result: ${result.getOrNull()?.content}")  // "42.0"
}
```

---

## Human-in-the-Loop (HITL)

### Example 5: Workflow with User Input

```kotlin
import io.github.noailabs.spice.checkpoint.*

fun main() = runBlocking {
    // Create graph with human node
    val graph = graph("booking-workflow") {
        agent("greeter", GreetingAgent())

        // Pause for user input
        human("get-name",
            prompt = "What is your name?",
            options = emptyList()
        )

        output("confirmation") { message ->
            "Thank you, ${message.content}! Your booking is confirmed."
        }

        edge("greeter", "get-name")
        edge("get-name", "confirmation")
    }

    // Setup checkpoint storage
    val checkpointStore = InMemoryCheckpointStore()
    val runner = DefaultGraphRunner()

    // Initial execution (will pause at human node)
    val initialMessage = SpiceMessage.create("Start booking", "user")
    val result1 = runner.executeWithCheckpoint(graph, initialMessage, checkpointStore)

    when (result1) {
        is SpiceResult.Success -> {
            if (result1.value.state == ExecutionState.WAITING) {
                println("System: ${result1.value.content}")  // "What is your name?"

                // Simulate user input
                val checkpointId = result1.value.runId!!
                val userResponse = SpiceMessage.create("Alice", "user")

                // Resume execution
                val result2 = runner.resumeFromCheckpoint(
                    graph, checkpointId, userResponse, checkpointStore
                )

                println(result2.getOrNull()?.content)  // "Thank you, Alice! Your booking is confirmed."
            }
        }
        is SpiceResult.Failure -> println("Error: ${result1.error.message}")
    }
}
```

---

## Understanding SpiceMessage

### Message Structure

```kotlin
data class SpiceMessage(
    // Identity
    val id: String,                          // Unique message ID
    val correlationId: String,               // Groups related messages
    val causationId: String? = null,         // Parent message ID

    // Content
    val content: String,                     // Human-readable text
    val data: Map<String, Any>,              // Structured data
    val toolCalls: List<OAIToolCall>,        // Tool call specifications

    // Execution State
    val state: ExecutionState,               // READY, RUNNING, WAITING, COMPLETED, FAILED
    val stateHistory: List<StateTransition>, // State transition audit trail
    val metadata: Map<String, Any>,          // Execution metadata

    // Graph Context
    val graphId: String?,                    // Current graph ID
    val nodeId: String?,                     // Current node ID
    val runId: String?,                      // Execution run ID

    // Actors
    val from: String,                        // Sender
    val to: String?,                         // Recipient

    // Timing
    val timestamp: Instant,                  // Creation time
    val expiresAt: Instant?                  // Expiration time
)
```

### Helper Methods

```kotlin
// Creating messages
val message = SpiceMessage.create("Hello", "user")

// Replying
val reply = message.reply("Response", "agent-id")

// Adding data
val withData = message.withData(mapOf("key" to "value"))

// Adding metadata
val withMeta = message.withMetadata(mapOf("userId" to "123"))

// Adding tool calls
val withTool = message.withToolCall(OAIToolCall.selection(...))

// State transitions
val running = message.transitionTo(ExecutionState.RUNNING, "Starting processing")

// Accessing data
val userName = message.getData<String>("userName")
val userId = message.getMetadata<String>("userId")

// Checking tool calls
val hasTools = message.hasToolCalls()
val toolCall = message.findToolCall("request_user_selection")
```

---

## Next Steps

Now that you have the basics, explore:

1. **[Installation Guide](INSTALLATION_1.0.md)** - Detailed setup for all modules
2. **[Architecture Overview](ARCHITECTURE_1.0.md)** - Deep dive into 1.0.0 design
3. **[Migration Guide](MIGRATION_0.x_TO_1.0.md)** - Upgrading from 0.x
4. **[Agent Development Guide](wiki/agent-guide.md)** - Building advanced agents
5. **[Tool Development Guide](wiki/tool-development.md)** - Creating custom tools
6. **[Spring Boot Integration](wiki/spring-boot.md)** - Using Spice with Spring

---

## Common Patterns

### Pattern 1: Agent → Agent Chain

```kotlin
val graph = graph("chain") {
    agent("step1", agent1)
    agent("step2", agent2)
    agent("step3", agent3)
    output("result")

    edge("step1", "step2")
    edge("step2", "step3")
    edge("step3", "result")
}
```

### Pattern 2: Conditional Routing

```kotlin
val graph = graph("conditional") {
    agent("classifier", classifierAgent)
    agent("handler-a", handlerA)
    agent("handler-b", handlerB)
    output("result")

    edge("classifier", "handler-a") { message ->
        message.getData<String>("category") == "A"
    }
    edge("classifier", "handler-b") { message ->
        message.getData<String>("category") == "B"
    }
    edge("handler-a", "result")
    edge("handler-b", "result")
}
```

### Pattern 3: Tool Pipeline

```kotlin
val graph = graph("tool-pipeline") {
    tool("validate", validationTool)
    tool("process", processingTool)
    tool("save", saveTool)
    output("result")

    edge("validate", "process") { message ->
        message.getData<Boolean>("valid") == true
    }
    edge("process", "save")
    edge("save", "result")
}
```

---

## Getting Help

- **Documentation**: [GitHub Wiki](https://github.com/no-ai-labs/spice/wiki)
- **Issues**: [Report bugs](https://github.com/no-ai-labs/spice/issues)
- **Discussions**: [Ask questions](https://github.com/no-ai-labs/spice/discussions)
- **Examples**: [Sample projects](../spice-dsl-samples)

---

**Ready to build amazing AI applications? 🌶️**
