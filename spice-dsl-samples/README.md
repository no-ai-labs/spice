# 🎮 Spice DSL Playground

Interactive playground and sample collection for the Spice Framework DSL. Learn, experiment, and prototype with hands-on examples covering everything from basic agent creation to complete workflow orchestration.

## 🚀 Quick Start

```bash
# Run basic examples
./gradlew :spice-dsl-samples:run --args="basic"

# Explore all features
./gradlew :spice-dsl-samples:run --args="all"

# List available scenarios
./gradlew :spice-dsl-samples:run --args="list"
```

## 📊 What's Included

### 🔧 Core DSL Examples
- **SimpleAgent.kt** — Handler separation patterns and reusable functions
- **SimpleTool.kt** — Tool creation with parameters and execution
- **SimpleFlow.kt** — Flow orchestration and agent coordination

### 🧪 Advanced Features
- **ExperimentalFeatures.kt** — Cutting-edge DSL capabilities
- **DebugModeExample.kt** — Development debugging and logging

### 📋 Template System
- **TemplateUsageExamples.kt** — Pre-built templates for rapid prototyping
- **DSLTemplates** — Agent, tool, and flow scaffolding functions
- **Sample Loading** — `loadSample()` DSL for instant prototypes

### 🎭 Scenario Runner
- **ScenarioRunner** — Automated demo and testing framework
- **BatchRunner** — Category-based execution and benchmarking
- **Performance Metrics** — Timing and success rate measurement

## 🎯 Usage Commands

### 📋 Information Commands
```bash
# Show help and usage information
./gradlew :spice-dsl-samples:run --args="help"

# List all available scenarios
./gradlew :spice-dsl-samples:run --args="list"
```

### 🚀 Category Execution
```bash
# Basic DSL features (agents, tools)
./gradlew :spice-dsl-samples:run --args="basic"

# Advanced features (flows, experimental)
./gradlew :spice-dsl-samples:run --args="advanced"

# Templates and scaffolding
./gradlew :spice-dsl-samples:run --args="templates"

# Complete workflows and debug features
./gradlew :spice-dsl-samples:run --args="complete"
```

### 🎯 Specific Scenarios
```bash
# Run a specific scenario by name
./gradlew :spice-dsl-samples:run --args="scenario basic-agents"
./gradlew :spice-dsl-samples:run --args="scenario debug-mode"
./gradlew :spice-dsl-samples:run --args="scenario complete-workflow"
```

### ⏱️ Performance Testing
```bash
# Run comprehensive benchmark (3 iterations)
./gradlew :spice-dsl-samples:run --args="benchmark"
```

### 🔧 Legacy Support
```bash
# Backward compatibility
./gradlew :spice-dsl-samples:run --args="experimental"
./gradlew :spice-dsl-samples:run --args="flow"
```

## 📁 Project Structure

```
spice-dsl-samples/
├── src/main/kotlin/io/github/spice/samples/
│   ├── PlaygroundMain.kt          # Command-line interface
│   └── ScenarioRunner.kt          # Scenario execution framework
├── samples/
│   ├── basic/                     # Core DSL examples
│   │   ├── SimpleAgent.kt         # Agent creation patterns
│   │   ├── SimpleTool.kt          # Tool development
│   │   └── DebugModeExample.kt    # Debug features
│   ├── flow/
│   │   └── SimpleFlow.kt          # Flow orchestration
│   ├── experimental/
│   │   └── ExperimentalFeatures.kt # Advanced DSL
│   └── templates/
│       ├── TemplateUsageExamples.kt # Template examples
│       └── README.md              # Template documentation
└── src/test/kotlin/               # Test examples
    └── DSLSummaryTest.kt          # Documentation tests
```

## 🔍 Key Features

### 🎮 Interactive Learning
- **Progressive Examples** — From simple to complex use cases
- **Hands-on Execution** — Run and modify code immediately
- **Comprehensive Coverage** — All DSL features with real examples

### 📋 Template System
```kotlin
// Load pre-built samples instantly
val chatbot = loadSample("chatbot") {
    agentId = "my-customer-bot"
    registerComponents = true
}

// Use template functions with aliases
val echoAgent = echoAgent("Customer Bot", alias = "prod-echo")
val calculator = calculatorTool(alias = "math-service")
```

### 🐛 Debug Mode
```kotlin
// Enable automatic logging for development
val debugAgent = buildAgent {
    id = "debug-agent" 
    debugMode(enabled = true, prefix = "[🔍 DEV]")
    handle { message ->
        // Your logic here - automatic logging included
        Message(content = "Response", sender = id, receiver = message.sender)
    }
}
```

### 📊 Documentation Generation
```kotlin
// Auto-generate documentation
val markdown = describeAllToMarkdown(
    filePath = "my-system-docs.md",
    includeExamples = true
)

// Export to file
writeMarkdownToFile(markdown, "documentation.md")

// Check system health
val issues = checkDSLHealth()
println("System health: ${if (issues.isEmpty()) "✅ Healthy" else "⚠️ Issues detected"}")
```

### 🎭 Scenario Framework
```kotlin
// Custom scenario registration
val scenarioRunner = ScenarioRunner()
scenarioRunner.registerScenario(Scenario(
    name = "my-custom-scenario",
    description = "Custom business logic testing",
    estimatedTimeMs = 2000
) {
    // Your scenario implementation
})

// Batch execution
val batchRunner = BatchRunner(scenarioRunner)
val results = batchRunner.runCategory("basic")
```

## 🌟 Sample Templates

Available through `loadSample()`:

- **echo** — Simple echo agent
- **calculator** — Math operations with natural language
- **logger** — Message logging agent  
- **chatbot** — FAQ chatbot with common responses
- **transformer** — Text transformation operations
- **customer-service** — Complete customer service workflow
- **data-processing** — Data validation and processing pipeline

## 🎯 Learning Path

### 🥇 Beginner (Start Here)
1. Run `./gradlew :spice-dsl-samples:run --args="basic"`
2. Explore `samples/basic/SimpleAgent.kt`
3. Try `loadSample("echo")` in your code

### 🥈 Intermediate
1. Run `./gradlew :spice-dsl-samples:run --args="templates"`
2. Study `samples/flow/SimpleFlow.kt`
3. Enable debug mode: `debugMode(true)`

### 🥉 Advanced
1. Run `./gradlew :spice-dsl-samples:run --args="complete"`
2. Explore `samples/experimental/ExperimentalFeatures.kt`
3. Create custom scenarios with `ScenarioRunner`

## 🤝 Contributing

1. Add new samples to appropriate directories
2. Register scenarios in `ScenarioRunner`
3. Update documentation with examples
4. Ensure all samples are runnable and well-documented

## 📚 Related Documentation

- [Spice Framework Main README](../README.md)
- [Core DSL Documentation](../spice-core/README.md)
- [Template Documentation](samples/templates/README.md)

## 🎉 Getting Help

- Run `--args="help"` for command documentation
- Explore `samples/` directories for code examples
- Check test files for advanced usage patterns
- Visit the main Spice Framework repository for core documentation 