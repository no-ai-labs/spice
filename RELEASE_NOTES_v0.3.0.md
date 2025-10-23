# 🌶️ Spice Framework v0.3.0 Release Notes

**Release Date**: 2025-10-23
**Type**: Major Release (**BREAKING CHANGES**)

## 🎯 Overview

Spice v0.3.0 is a **major release** that unifies flow orchestration under `MultiAgentFlow`, removing the incomplete `CoreFlow` abstraction. This release brings **conditional step execution**, **four execution strategies**, **dynamic strategy selection**, and a significantly improved developer experience for building multi-agent workflows.

**Key Highlights**:
- ✅ Unified flow system with executable workflows
- ✅ Conditional step execution based on runtime conditions
- ✅ Four execution strategies: SEQUENTIAL, PARALLEL, COMPETITION, PIPELINE
- ✅ Dynamic strategy selection at runtime
- ✅ Enhanced integrations (Neo4j, PSI, Mnemo)
- ✅ 1,629 lines of new/updated documentation

⚠️ **Breaking Changes**: `CoreFlow` has been removed. See [Migration Guide](#-migration-guide) below.

---

## ✨ New Features

### 1. 🔀 Conditional Step Execution

Execute agents based on runtime conditions, allowing dynamic flow control:

```kotlin
val flow = buildFlow {
    id = "validation-pipeline"
    name = "Validation Pipeline"
    strategy = FlowStrategy.SEQUENTIAL

    step("validate", "validator")

    // Only execute if validation passed
    step("process", "processor") { comm ->
        comm.data["valid"] == "true"
    }

    // Only execute if processing succeeded
    step("store", "storage") { comm ->
        comm.data["processed"] == "true"
    }
}

// Conditions evaluated at runtime
runBlocking {
    val result = flow.process(
        Comm(content = "Process this data", from = "user")
    )
}
```

**Key Features**:
- ✅ Lambda-based condition evaluation
- ✅ Access to current `Comm` state
- ✅ Skip agents that don't meet conditions
- ✅ Reduce unnecessary agent invocations
- ✅ Clean, readable syntax

**Use Cases**:
- Validation pipelines (only process if valid)
- Error handling (skip steps on failure)
- Feature flags (conditional execution based on data)
- Multi-stage workflows with gating logic

---

### 2. ⚡ Four Execution Strategies

Choose how your agents execute with built-in strategies:

#### SEQUENTIAL (Default)
Agents execute one by one: A → B → C

```kotlin
val flow = buildFlow {
    strategy = FlowStrategy.SEQUENTIAL
    step("step1", "agent1")
    step("step2", "agent2")
    step("step3", "agent3")
}
```

**Best for**: Workflows where order matters, each step depends on previous results

#### PARALLEL
All agents execute simultaneously

```kotlin
val flow = buildFlow {
    strategy = FlowStrategy.PARALLEL
    step("task1", "agent1")
    step("task2", "agent2")
    step("task3", "agent3")
}
```

**Best for**: Independent tasks that can run concurrently

#### COMPETITION
First successful response wins, others cancelled

```kotlin
val flow = buildFlow {
    strategy = FlowStrategy.COMPETITION
    step("gpt", "gpt-4")
    step("claude", "claude-3-5")
    step("gemini", "gemini-pro")
}
```

**Best for**: Multiple LLM providers, fastest response needed

#### PIPELINE
Output flows through agents like a data pipeline: A output → B input → C input

```kotlin
val flow = buildFlow {
    strategy = FlowStrategy.PIPELINE
    step("extract", "extractor")
    step("transform", "transformer")
    step("load", "loader")
}
```

**Best for**: ETL workflows, data transformation chains

---

### 3. 🎛️ Dynamic Strategy Selection

Select execution strategy at runtime based on message content:

```kotlin
val flow = buildFlow {
    id = "adaptive-flow"
    name = "Adaptive Flow"

    step("agent1", "agent1")
    step("agent2", "agent2")
    step("agent3", "agent3")
}

// Strategy chosen at runtime
flow.setStrategyResolver { comm, agents ->
    when {
        comm.data["urgent"] == "true" -> FlowStrategy.COMPETITION
        comm.data["parallel"] == "true" -> FlowStrategy.PARALLEL
        agents.size > 5 -> FlowStrategy.PIPELINE
        else -> FlowStrategy.SEQUENTIAL
    }
}

// Execute with different strategies based on message
runBlocking {
    val result1 = flow.process(
        Comm(
            content = "Process urgently",
            from = "user",
            data = mapOf("urgent" to "true")  // Uses COMPETITION
        )
    )

    val result2 = flow.process(
        Comm(
            content = "Normal processing",
            from = "user"  // Uses SEQUENTIAL
        )
    )
}
```

**Key Features**:
- ✅ Lambda-based strategy selection
- ✅ Access to `Comm` and agent list
- ✅ Different strategies per message
- ✅ Adaptive workflows

**Use Cases**:
- Priority-based routing (urgent = competition)
- Load balancing (many agents = parallel)
- Context-aware execution
- A/B testing different strategies

---

### 4. 🛠️ Convenience Functions & Operators

Quick flow creation with helper functions and operators:

**Convenience Functions**:
```kotlin
import io.github.noailabs.spice.*

// Create flows quickly
val seq = sequentialFlow(agent1, agent2, agent3)
val par = parallelFlow(agent1, agent2, agent3)
val comp = competitionFlow(agent1, agent2, agent3)
val pipe = pipelineFlow(agent1, agent2, agent3)

// Execute immediately
runBlocking {
    val result = seq.process(comm)
}
```

**Operators**:
```kotlin
// Sequential chaining: A then B
val flow1 = agent1 + agent2 + agent3

// Parallel execution: A and B
val flow2 = agent1 parallelWith agent2

// Competition: A vs B
val flow3 = agent1 competesWith agent2

// Execute
runBlocking {
    val result = flow1.process(comm)
}
```

**Benefits**:
- ✅ Concise, readable code
- ✅ Type-safe
- ✅ Chainable
- ✅ Familiar syntax

---

### 5. 📊 Flow Execution Metadata

Flows automatically include detailed execution metadata:

```kotlin
val flow = buildFlow {
    strategy = FlowStrategy.SEQUENTIAL
    step("step1", "agent1")
    step("step2", "agent2")
}

runBlocking {
    val result = flow.process(comm)

    // Access execution metadata
    println("Strategy: ${result.data["flow_strategy"]}")        // "SEQUENTIAL"
    println("Time: ${result.data["execution_time_ms"]}ms")      // "145"
    println("Agents: ${result.data["agent_count"]}")            // "2"
    println("Steps: ${result.data["completed_steps"]}")         // "2"
    println("Details: ${result.data["sequential_steps"]}")      // "Step 1: OK; Step 2: OK"
}
```

**Available Metadata**:
- `flow_strategy` - Strategy used (SEQUENTIAL, PARALLEL, etc.)
- `execution_time_ms` - Total execution time
- `agent_count` - Number of agents executed
- `completed_steps` - Number of completed steps
- `sequential_steps` / `parallel_results` / etc. - Strategy-specific details

**Use Cases**:
- Performance monitoring
- Debugging workflows
- Analytics and metrics
- Audit trails

---

### 6. 🔗 Enhanced Integration Support

Neo4j, PSI, and Mnemo integrations now support `MultiAgentFlow` with enhanced metadata:

**Neo4j Integration**:
```kotlin
val graph = flow.toNeo4jGraph()

// Graph now includes:
// - Flow node with stepCount
// - CONTAINS_STEP relationships to agents
// - Step metadata (index, hasCondition)
```

**PSI Integration**:
```kotlin
val psi = flow.toPsi()

// PSI now includes:
// - Flow type (MultiAgentFlow)
// - Step information (index, agentId, hasCondition)
// - Complete step structure
```

**Mnemo Integration**:
```kotlin
MnemoIntegration.saveFlowExecution(flow, input, output, mnemo)

// Captures:
// - Complete flow structure
// - Step relationships
// - Execution patterns
```

---

## 🔄 Changed

### buildFlow Returns MultiAgentFlow

`buildFlow` DSL now returns `MultiAgentFlow` instead of `CoreFlow`:

**Before (v0.2.x)**:
```kotlin
val flow = buildFlow {
    step("step1", "agent1")
}
// Type: CoreFlow
// No execute() method! 💀
```

**After (v0.3.0)**:
```kotlin
val flow = buildFlow {
    strategy = FlowStrategy.SEQUENTIAL  // Optional
    step("step1", "agent1")
}
// Type: MultiAgentFlow
runBlocking {
    val result = flow.process(comm)  // ✅ Works!
}
```

### FlowRegistry Type Changed

`FlowRegistry` now stores `MultiAgentFlow` instead of `CoreFlow`:

```kotlin
// Before (v0.2.x)
val flow: CoreFlow = FlowRegistry.get("my-flow")!!

// After (v0.3.0)
val flow: MultiAgentFlow = FlowRegistry.get("my-flow")!!
```

### MultiAgentFlow API Enhancements

New methods added to `MultiAgentFlow`:

- `addStep(agent, condition)` - Add agent with execution condition
- `getStepCount()` - Get total number of steps
- `getSteps()` - Get all steps with conditions
- `clearSteps()` - Remove all steps
- Implements `Identifiable` interface for registry support

---

## ❌ Removed (Breaking Changes)

### CoreFlow Removed

The following classes have been **completely removed**:

- ❌ `CoreFlow` class
- ❌ `CoreFlowBuilder` class
- ❌ `FlowStep` data class

**Why?**

`CoreFlow` was an incomplete abstraction that couldn't execute flows. It caused confusion:
- No `execute()` method existed
- Users couldn't run flows created with `buildFlow`
- `MultiAgentFlow` already provided full functionality
- Having two flow types was confusing

**Solution**: Everything is now unified under `MultiAgentFlow` with full execution support.

---

## 📋 Migration Guide

### Step 1: Update Flow Creation

**Before**:
```kotlin
val flow = buildFlow {
    name = "My Flow"
    step("step1", "agent1")
    step("step2", "agent2")
}
// Type: CoreFlow
```

**After**:
```kotlin
val flow = buildFlow {
    name = "My Flow"
    strategy = FlowStrategy.SEQUENTIAL  // Optional, defaults to SEQUENTIAL
    step("step1", "agent1")
    step("step2", "agent2")
}
// Type: MultiAgentFlow
```

### Step 2: Update Flow Execution

**Before**:
```kotlin
// This never worked in v0.2.x!
// flow.execute() didn't exist
```

**After**:
```kotlin
runBlocking {
    val result = flow.process(comm)
}
```

### Step 3: Update Type Annotations

**Before**:
```kotlin
val flow: CoreFlow = buildFlow { /* ... */ }
FlowRegistry.register(flow)
val retrieved: CoreFlow = FlowRegistry.get("flow")!!
```

**After**:
```kotlin
val flow: MultiAgentFlow = buildFlow { /* ... */ }
FlowRegistry.register(flow)
val retrieved: MultiAgentFlow = FlowRegistry.get("flow")!!
```

### Step 4: Update Integrations (if used)

**Before**:
```kotlin
val graph = flow.toNeo4jGraph()  // CoreFlow
```

**After**:
```kotlin
val graph = flow.toNeo4jGraph()  // MultiAgentFlow (enhanced!)
```

**That's it!** Conditions and step definitions work unchanged.

For complete migration instructions, see [MIGRATION_GUIDE_v0.3.md](docs/MIGRATION_GUIDE_v0.3.md).

---

## 📚 Documentation

### Complete Documentation Rewrite (1,629 lines)

We've completely rewritten the flow documentation with comprehensive guides and examples:

1. **[buildFlow Guide](docs/dsl-guide/build-flow.md)** (+230 lines)
   - Complete buildFlow DSL documentation
   - 4 execution strategies explained
   - Conditional execution guide
   - Registry vs direct reference
   - Dynamic strategy selection
   - Complete runnable examples

2. **[Flow Orchestration](docs/orchestration/flows.md)** (+324 lines)
   - Multi-agent flow patterns
   - Strategy selection guide
   - Convenience functions
   - Flow operators
   - Real-world examples
   - Complete validation pipeline example

3. **[DSL API Reference](docs/api/dsl.md)** (+275 lines)
   - Flow DSL complete API reference
   - All parameters documented
   - Best practices
   - Good vs Bad examples

4. **[Migration Guide](docs/MIGRATION_GUIDE_v0.3.md)** (NEW, 350 lines)
   - Step-by-step migration instructions
   - Before/After comparisons
   - Common scenarios
   - FAQ section

5. **[Release Blog Post](docs/blog/2025-10-23-spice-v0.3.0-released.md)** (NEW, 450 lines)
   - Release announcement
   - Feature highlights
   - Complete examples
   - Migration summary

All documentation includes:
- ✅ Runnable code examples
- ✅ Import statements
- ✅ Complete context
- ✅ Best practices
- ✅ Real-world use cases

---

## 💡 Real-World Example

Here's a complete validation pipeline showcasing v0.3.0 features:

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create validation agents
    val validator = buildAgent {
        id = "validator"
        name = "Input Validator"
        handle { comm ->
            val isValid = comm.content.length >= 10
            SpiceResult.success(
                comm.reply(
                    content = if (isValid) "Valid input" else "Invalid input",
                    from = id,
                    data = mapOf("valid" to isValid.toString())
                )
            )
        }
    }

    val processor = buildAgent {
        id = "processor"
        name = "Data Processor"
        handle { comm ->
            SpiceResult.success(
                comm.reply(
                    content = "Processed: ${comm.content.uppercase()}",
                    from = id,
                    data = mapOf("processed" to "true")
                )
            )
        }
    }

    // Register agents
    AgentRegistry.register(validator)
    AgentRegistry.register(processor)

    // Create conditional flow
    val flow = buildFlow {
        id = "validation-pipeline"
        name = "Validation Pipeline"
        strategy = FlowStrategy.SEQUENTIAL

        step("validate", "validator")

        // Only process if valid
        step("process", "processor") { comm ->
            comm.data["valid"] == "true"
        }
    }

    // Test with valid input
    println("=== Valid Input ===")
    val result1 = flow.process(
        Comm(content = "This is a valid message", from = "user")
    )
    println(result1.content)
    println("Time: ${result1.data["execution_time_ms"]}ms")

    // Test with invalid input (processor skipped!)
    println("\n=== Invalid Input ===")
    val result2 = flow.process(
        Comm(content = "Short", from = "user")
    )
    println(result2.content)
    println("Time: ${result2.data["execution_time_ms"]}ms")
}
```

Output:
```
=== Valid Input ===
Processed: THIS IS A VALID MESSAGE
Time: 145ms

=== Invalid Input ===
Invalid input
Time: 52ms
```

---

## 🔗 Resources

- **[Documentation](https://spice.noailabs.io)**
- **[GitHub Repository](https://github.com/noailabs/spice)**
- **[Migration Guide](docs/MIGRATION_GUIDE_v0.3.md)**
- **[Examples](docs/examples/)**
- **[Blog Post](docs/blog/2025-10-23-spice-v0.3.0-released.md)**

---

## 📦 Installation

### Gradle (Kotlin)
```kotlin
dependencies {
    implementation("io.github.noailabs:spice-core:0.3.0")
}
```

### Gradle (Groovy)
```groovy
dependencies {
    implementation 'io.github.noailabs:spice-core:0.3.0'
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.noailabs</groupId>
    <artifactId>spice-core</artifactId>
    <version>0.3.0</version>
</dependency>
```

---

## 🆘 Support

If you encounter issues during migration:

1. Review the [Migration Guide](docs/MIGRATION_GUIDE_v0.3.md)
2. Check the [FAQ section](docs/MIGRATION_GUIDE_v0.3.md#-faq)
3. Review [examples](docs/examples/)
4. Open an issue on [GitHub](https://github.com/noailabs/spice/issues)

---

## 🎉 Thank You!

Thank you for using Spice Framework. We believe v0.3.0 provides a cleaner, more powerful flow orchestration system that will make building multi-agent applications easier and more intuitive.

**Happy building!** 🌶️🚀
