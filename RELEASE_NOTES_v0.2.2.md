# 🌶️ Spice Framework v0.2.2 Release Notes

**Release Date**: 2025-10-22
**Type**: Minor Release (No Breaking Changes)

## 🎯 Overview

Spice v0.2.2 focuses on **developer experience improvements**, **code quality**, and **comprehensive documentation**. This release introduces the powerful Swarm Tools DSL, eliminates code duplication, and adds extensive documentation with real-world examples. Perfect for teams building production-grade multi-agent systems!

## ✨ New Features

### 1. 🛠️ Swarm Tools DSL - Shared Tools Across Agent Swarms

The new `swarmTools` DSL lets you define tools that all swarm members can use, enabling seamless inter-agent collaboration and coordination.

**Key Features**:
- ✅ **Inline tool definition** with automatic parameter validation
- ✅ **Tool sharing** across all swarm members with automatic deduplication
- ✅ **Built-in coordination tools** for consensus, conflict resolution, and quality assessment
- ✅ **Type-safe execution** with explicit function syntax
- ✅ **Error handling** - exceptions automatically caught and wrapped

**Example - Custom Calculator Tool**:
```kotlin
val swarm = buildSwarmAgent {
    name = "Math Analysis Swarm"

    swarmTools {
        // Define a calculator tool all agents can use
        tool("calculate", "Simple calculator") {
            parameter("a", "number", "First number", required = true)
            parameter("b", "number", "Second number", required = true)
            parameter("operation", "string", "Operation (+,-,*,/)", required = true)

            execute(fun(params: Map<String, Any>): String {
                val a = (params["a"] as Number).toDouble()
                val b = (params["b"] as Number).toDouble()
                val op = params["operation"] as String

                return when (op) {
                    "+" -> (a + b).toString()
                    "-" -> (a - b).toString()
                    "*" -> (a * b).toString()
                    "/" -> (a / b).toString()
                    else -> "Unknown operation"
                }
            })
        }
    }

    quickSwarm {
        specialist("agent1", "Mathematician", "calculations")
        specialist("agent2", "Analyst", "analysis")
    }
}

// All agents can now use the calculator tool!
```

**Example - Built-in Coordination Tools**:
```kotlin
swarmTools {
    // AI-powered consensus building
    aiConsensus(scoringAgent = myLLM)

    // Conflict resolution between agent responses
    conflictResolver()

    // Quality assessment with AI scoring
    qualityAssessor(scoringAgent = myLLM)

    // Intelligent result aggregation
    resultAggregator()

    // Strategy optimization
    strategyOptimizer()
}
```

**Benefits**:
- 🚀 **Reduce boilerplate** - Define tools once, use everywhere
- 🔒 **Type safety** - Automatic parameter validation prevents runtime errors
- 🤝 **Coordination** - Built-in tools for intelligent multi-agent collaboration
- 📦 **Reusability** - Share tools across multiple swarms

### 2. ✅ Automatic Parameter Validation

InlineToolBuilder now automatically validates required parameters before execution:

**Before v0.2.2** - Manual validation:
```kotlin
tool("greet") {
    parameter("name", "string", required = true)

    execute { params ->
        val name = params["name"] as? String
            ?: return@execute SpiceResult.success(
                ToolResult.error("Missing parameter: name")
            )

        SpiceResult.success(ToolResult.success("Hello, $name!"))
    }
}
```

**After v0.2.2** - Automatic validation:
```kotlin
tool("greet") {
    parameter("name", "string", required = true)

    // Missing parameters automatically caught!
    execute(fun(params: Map<String, Any>): String {
        val name = params["name"] as String
        return "Hello, $name!"
    })
}

// Calling without required parameter:
tool.execute(emptyMap())
// Automatically returns: ToolResult(
//   success = false,
//   error = "Parameter validation failed: Missing required parameter: name"
// )
```

**Features**:
- ✅ Validates all `required = true` parameters before execution
- ✅ Clear error messages: `"Missing required parameter: <name>"`
- ✅ No more boilerplate validation code
- ✅ Works with both simple and advanced execute methods

## 🧹 Code Quality Improvements

### 1. Eliminated Duplicate Code

Removed **31 lines of duplicate code** across the codebase:

#### Duplicate: OptimizerConfig (REMOVED)
```kotlin
// SwarmTools.kt - DELETED (unused)
data class OptimizerConfig(
    val enableLearning: Boolean = true,
    val historyLimit: Int = 100
)
```

The `StrategyOptimizerTool` never used its config parameter. Now simplified to:
```kotlin
class StrategyOptimizerTool : Tool {
    // Cleaner, no unused config!
}
```

#### Duplicate: toJsonObject() (REMOVED)
```kotlin
// MCPClient.kt - DELETED (27 lines of duplication)
fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
    // 27 lines of duplicate JSON conversion logic
}
```

Now uses the centralized `SpiceSerializer.toJsonObject()`:
```kotlin
// MCPClient.kt - Clean!
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonObject

// All JSON conversion now goes through one source of truth
parameters.toJsonObject()
```

**Impact**:
- ✅ **-31 lines** of duplicate code removed
- ✅ **Single source of truth** for JSON serialization
- ✅ **Consistent behavior** across entire framework
- ✅ **Easier maintenance** - fix once, fixes everywhere

## 📚 Documentation Overhaul

Added **566 lines** of comprehensive documentation with real-world examples:

### Updated Documentation Files

#### 1. orchestration/swarm.md (+79 lines)
**New Section: Swarm Tools**
- Inline tool definition examples
- Pre-built tool integration
- Built-in coordination tools reference
- Benefits and use cases

#### 2. dsl-guide/tools.md (+152 lines)
**Complete Rewrite** with:
- **Simple Execute (Recommended)** - New preferred pattern
- **Parameter Validation** - Automatic validation explained
- **Error Handling** - Exception catching and wrapping
- **Best Practices** - 6 key guidelines
- **Common Patterns** - Calculator and data processor examples

#### 3. tools-extensions/creating-tools.md (+335 lines)
**Expanded from 19 → 338 lines** with:
- **Quick Start: Inline Tools** - Fastest way to get started
- **Custom Tool Class** - Reusable tool patterns
- **Tool Patterns** - 4 real-world patterns:
  - Stateless tools
  - Tools with external APIs
  - Tools with database access
  - Complex tools with validation
- **Best Practices** - 5 detailed guidelines with code examples
- **Testing Tools** - Unit and integration test examples

### Documentation Statistics
```
docs/docs/orchestration/swarm.md             | +79 lines
docs/docs/dsl-guide/tools.md                 | +152 lines
docs/docs/tools-extensions/creating-tools.md | +335 lines
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Total: 3 files, +566 lines
```

## 🔧 Technical Improvements

### Enhanced InlineToolBuilder

**Added Parameter Validation**:
```kotlin
// InlineToolBuilder.execute() now validates required params
fun execute(executor: (Map<String, Any>) -> Any?) {
    executeFunction = { params ->
        // NEW: Automatic validation
        val missingParams = parametersMap
            .filter { (_, schema) -> schema.required }
            .filter { (name, _) -> !params.containsKey(name) }
            .keys

        if (missingParams.isNotEmpty()) {
            return SpiceResult.success(ToolResult(
                success = false,
                error = "Parameter validation failed: Missing required parameter: ${missingParams.first()}"
            ))
        }

        try {
            val result = executor(params)
            SpiceResult.success(ToolResult(success = true, result = result?.toString() ?: ""))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult(success = false, error = e.message ?: "Unknown error"))
        }
    }
}
```

### Refactored SwarmTools

**Before** - Duplicate SimpleToolBuilder:
```kotlin
// SwarmTools.kt had its own 75-line SimpleToolBuilder class
class SimpleToolBuilder { /* 75 lines of duplication */ }
```

**After** - Uses CoreDSL's InlineToolBuilder:
```kotlin
// SwarmTools.kt - Clean and unified!
fun tool(name: String, description: String = "", config: InlineToolBuilder.() -> Unit) {
    val builder = InlineToolBuilder(name)
    if (description.isNotEmpty()) {
        builder.description = description
    }
    builder.config()
    tools.add(builder.build())
}
```

## 📊 Test Coverage

All tests passing with comprehensive coverage:

```bash
$ ./gradlew :spice-core:test

> Task :spice-core:test
95 tests completed, 94 passed, 1 skipped

BUILD SUCCESSFUL
```

**New Tests**:
- ✅ SwarmToolsTest (7 tests) - All passing
  - Inline tool definitions
  - Tool execution with parameters
  - Parameter validation
  - Error handling
  - Tool deduplication
  - Multiple agent access
  - Coordination tools

**Updated Tests**:
- ✅ MCPClientTest - Now uses SpiceSerializer.toJsonObject()
- ✅ All existing tests remain passing

## 🚀 Upgrade Path

### 1. Update Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.no-ai-labs:spice-core:0.2.2")
}
```

### 2. No Breaking Changes

All existing v0.2.1 code continues to work without modification.

### 3. Opt-In to New Features

```kotlin
// Add swarm tools (optional)
buildSwarmAgent {
    swarmTools {
        tool("my_tool") { /* ... */ }
    }
}

// Benefit from automatic validation (automatic!)
tool("my_tool") {
    parameter("required_param", "string", required = true)
    execute(fun(params: Map<String, Any>): String {
        // No manual validation needed!
        val param = params["required_param"] as String
        return "Result: $param"
    })
}
```

### 4. Run Tests

```bash
./gradlew test
```

## 📈 What's Improved

### Developer Experience

**Before v0.2.2**:
```kotlin
// Lots of boilerplate for tool validation
tool("my_tool") {
    parameter("name", "string", required = true)

    execute { params ->
        val name = params["name"] as? String
        if (name == null) {
            return@execute SpiceResult.success(
                ToolResult.error("Missing parameter: name")
            )
        }

        SpiceResult.success(ToolResult.success("Hello, $name!"))
    }
}
```

**After v0.2.2**:
```kotlin
// Clean, simple, automatic validation
tool("my_tool") {
    parameter("name", "string", required = true)

    execute(fun(params: Map<String, Any>): String {
        val name = params["name"] as String
        return "Hello, $name!"
    })
}
```

**Improvement**: **-6 lines** per tool, **zero boilerplate** validation code!

### Swarm Coordination

**Before v0.2.2**:
- Each agent needs its own tools
- Manual tool registration for each agent
- No built-in coordination patterns

**After v0.2.2**:
- Tools defined once, shared across swarm
- Automatic tool availability for all members
- Built-in coordination tools ready to use

### Documentation

**Before v0.2.2**:
- Basic examples only
- Limited tool creation guidance
- Minimal swarm documentation

**After v0.2.2**:
- 566 lines of new documentation
- Real-world patterns and examples
- Complete tool creation guide
- Comprehensive swarm tools reference

## 🎨 Design Decisions

### Why Explicit Function Syntax?

The `execute(fun(params): String)` syntax prevents Kotlin overload ambiguity:

```kotlin
// InlineToolBuilder has two execute() overloads:
fun execute(executor: (Map<String, Any>) -> Any?)  // Simple
fun execute(executor: suspend (Map<String, Any>) -> SpiceResult<ToolResult>)  // Advanced

// Explicit syntax makes it clear which one to use
execute(fun(params: Map<String, Any>): String {  // ✅ Clear!
    return "Result"
})

execute { params ->  // ❌ Ambiguous!
    "Result"
}
```

### Why Swarm Tools?

Multi-agent systems need shared capabilities:
- **Coordination** - Agents need to work together
- **Consistency** - Same tool, same behavior
- **Efficiency** - Define once, use everywhere
- **Discoverability** - All tools available to all agents

### Why Centralized SpiceSerializer?

Having one JSON conversion implementation:
- Handles all types consistently (Enum, Instant, Agent, Tool, etc.)
- Easier to maintain and extend
- Prevents subtle serialization bugs
- Single source of truth

## 🔮 What's Next (v0.3.0)

### Planned Features

- **Tool Composition** - Chain tools together
- **Tool Marketplace** - Community-contributed tools
- **Advanced Coordination** - More built-in swarm patterns
- **Tool Versioning** - Track and manage tool versions
- **Performance Metrics** - Per-tool execution metrics
- **Tool Hot-Reload** - Update tools without restart

### Community Feedback Welcome

We'd love to hear your feedback on:
- Swarm Tools DSL ease of use
- Documentation clarity and completeness
- Feature requests for v0.3.0
- Real-world use cases

## 💡 Best Practices

### Tool Creation

1. **Use Simple Execute** for most tools
```kotlin
execute(fun(params: Map<String, Any>): String {
    return "Simple and clear!"
})
```

2. **Mark Required Parameters**
```kotlin
parameter("required_field", "string", required = true)
```

3. **Throw Exceptions for Errors**
```kotlin
if (invalid) {
    throw IllegalArgumentException("Clear error message")
}
```

### Swarm Tools

1. **Define Shared Tools** for coordination
```kotlin
swarmTools {
    tool("shared_calculator") { /* ... */ }
}
```

2. **Use Built-in Coordination** when possible
```kotlin
swarmTools {
    aiConsensus(scoringAgent = llm)
    conflictResolver()
}
```

3. **Document Tool Purpose** clearly
```kotlin
tool("analyze_sentiment", "Analyzes text sentiment and returns positive/negative/neutral") {
    // Clear description helps other agents understand what this tool does
}
```

## 🙏 Acknowledgments

This release improves developer experience through cleaner APIs, better documentation, and reduced boilerplate. Special thanks to the community for feedback on tool creation patterns and documentation gaps.

## 📞 Support

- **Documentation**: https://no-ai-labs.github.io/spice/
- **Issues**: https://github.com/no-ai-labs/spice/issues
- **Examples**: See `/examples` directory for updated samples

---

**Full Changelog**: v0.2.1...v0.2.2

**Downloads**: [GitHub Releases](https://github.com/no-ai-labs/spice/releases/tag/v0.2.2)
