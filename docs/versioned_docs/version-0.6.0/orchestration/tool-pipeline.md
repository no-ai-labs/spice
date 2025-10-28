# Tool Pipeline DSL

Fluent, type-safe API for building multi-step tool pipelines with automatic data flow between steps.

## Overview

The Tool Pipeline DSL provides a clean, intuitive way to chain multiple tools together, automatically managing data flow between steps. It combines the power of ModernToolChain with a fluent API that makes complex pipelines easy to read and maintain.

**Key Features:**
- **Fluent API**: Method chaining with `.output()`, `.input()`, `.named()`
- **Type-Safe**: Use Tool objects directly instead of string names
- **Auto Data Flow**: Outputs automatically flow to next steps
- **Context-Aware**: Access previous step outputs via `requireOutputOf()`
- **Backward Compatible**: Works alongside traditional chain syntax

## Quick Start

### Basic Pipeline

```kotlin
val pipeline = toolChain("product-lookup") {
    name = "Product Information Pipeline"
    description = "Resolve SKU and fetch product specs"

    // Step 1: Resolve product name to SKU
    +step(resolveTool).output("sku").input {
        mapOf("text" to "iPhone 15 Pro")
    }

    // Step 2: Get specs using resolved SKU
    +step(getSpecsTool).input { context ->
        val sku = context.requireOutputOf("sku")
        mapOf("sku" to sku)
    }

    // Step 3: Format results
    +step(formatTool).input { context ->
        val specs = context.requireOutputOf("step-2")  // Can use step ID or output name
        mapOf("data" to specs)
    }
}

// Execute
val result = pipeline.execute(emptyMap())
```

### Simple Sequential Pipeline

```kotlin
val pipeline = toolChain("simple-math") {
    name = "Math Pipeline"

    // Steps use initial parameters automatically
    +step(multiplyTool).output("product")
    +step(addTool).output("sum")
    +step(formatTool)
}

// Provide parameters at execution
val result = pipeline.execute(mapOf(
    "a" to 5,
    "b" to 3
))
```

## Core Concepts

### Step Builder

The `StepBuilder` provides a fluent interface for configuring individual steps:

```kotlin
+step(tool)                    // Create step
    .named("custom-id")        // Set step ID (optional)
    .output("result-name")     // Name the output (optional)
    .input { context ->        // Provide input parameters (optional)
        mapOf("param" to value)
    }
```

**Order matters:**
1. `named()` - Set step ID
2. `output()` - Name the output
3. `input()` - Provide parameters

But all are optional!

### Unary Plus Operator

The `+` operator adds the configured step to the chain:

```kotlin
+step(myTool)  // Add step to chain
```

Without `+`, the step won't be added:

```kotlin
step(myTool)  // ❌ This does nothing!
```

### Output Naming

Named outputs are stored in `ChainContext` and can be accessed by subsequent steps:

```kotlin
// Step 1: Store output as "sku"
+step(resolveTool).output("sku").input {
    mapOf("text" to "MacBook Pro")
}

// Step 2: Access "sku" from context
+step(lookupTool).input { context ->
    val sku = context.requireOutputOf("sku")
    mapOf("product_id" to sku)
}
```

If you don't specify an output name, the step's result is stored with its step ID (e.g., "step-1", "step-2").

### Input Parameters

The `input()` block provides dynamic, context-aware parameters:

```kotlin
+step(tool).input { context ->
    // Access previous outputs
    val prev = context.requireOutputOf("previous-step")

    // Access shared data
    val shared = context.sharedData["key"]

    // Access last result
    val last = context.getLastResult()?.result

    // Return parameters map
    mapOf(
        "param1" to prev,
        "param2" to "static value"
    )
}
```

### Step IDs

By default, steps get auto-generated IDs: "step-1", "step-2", etc.

Set custom IDs with `named()`:

```kotlin
+step(tool).named("resolve").output("sku")
+step(tool).named("lookup").input { context ->
    val sku = context.requireOutputOf("sku")
    // OR use step ID
    val sku2 = context.getOutputOf("resolve")
    // ...
}
```

## Usage Patterns

### Pattern 1: Simple Sequential Chain

Steps execute in order using shared data:

```kotlin
val chain = toolChain("sequential") {
    name = "Sequential Processing"

    +step(step1Tool).output("value1")
    +step(step2Tool).output("value2")
    +step(step3Tool)
}

val result = chain.execute(initialParams)
```

### Pattern 2: Named Step References

Use named steps for clarity:

```kotlin
val chain = toolChain("named-steps") {
    name = "Named Step Pipeline"

    +step(resolveTool)
        .named("resolve")
        .output("sku")
        .input { mapOf("text" to "iPhone") }

    +step(lookupTool)
        .named("lookup")
        .output("product")
        .input { context ->
            mapOf("sku" to context.requireOutputOf("sku"))
        }

    +step(formatTool)
        .named("format")
        .input { context ->
            mapOf("data" to context.requireOutputOf("product"))
        }
}
```

### Pattern 3: Conditional Data Flow

Use context to make conditional decisions:

```kotlin
val chain = toolChain("conditional") {
    name = "Conditional Pipeline"

    +step(checkTool).output("status")

    +step(processTool).input { context ->
        val status = context.requireOutputOf("status")

        // Different parameters based on status
        if (status == "premium") {
            mapOf("level" to "high", "priority" to 1)
        } else {
            mapOf("level" to "standard", "priority" to 5)
        }
    }
}
```

### Pattern 4: Complex Data Transformation

Transform and combine outputs from multiple steps:

```kotlin
val chain = toolChain("complex") {
    name = "Complex Data Pipeline"

    +step(fetchUserTool)
        .named("user")
        .output("userData")

    +step(fetchOrdersTool)
        .named("orders")
        .output("orderData")

    +step(mergeTool)
        .named("merge")
        .input { context ->
            val user = context.requireOutputOf("userData")
            val orders = context.requireOutputOf("orderData")

            mapOf(
                "user" to user,
                "orders" to orders,
                "timestamp" to System.currentTimeMillis()
            )
        }
}
```

### Pattern 5: Mixed Traditional and Fluent

Combine fluent and traditional syntax:

```kotlin
val chain = toolChain("mixed") {
    name = "Mixed Syntax Pipeline"

    // Traditional syntax
    stepWithOutput("calc1", multiplyTool, "value", mapOf("a" to 5, "b" to 4))

    // Fluent syntax
    +step(formatTool).input { context ->
        val value = context.requireOutputOf("value")
        mapOf("data" to value)
    }

    // Traditional syntax again
    step("final", finalizeTool)
}
```

### Pattern 6: Parallel-Style Branching

While steps execute sequentially, you can simulate branching with conditional logic:

```kotlin
val chain = toolChain("branching") {
    name = "Branching Pipeline"

    +step(classifyTool).output("type")

    // Branch A processing
    +step(processATool).input { context ->
        val type = context.requireOutputOf("type")
        if (type == "A") {
            mapOf("data" to context.sharedData["input"])
        } else {
            mapOf("skip" to true)  // Skip this step's logic
        }
    }

    // Branch B processing
    +step(processBTool).input { context ->
        val type = context.requireOutputOf("type")
        if (type == "B") {
            mapOf("data" to context.sharedData["input"])
        } else {
            mapOf("skip" to true)
        }
    }
}
```

## API Reference

### ToolChainBuilder

```kotlin
class ToolChainBuilder(val id: String) {
    var name: String
    var description: String
    var debugEnabled: Boolean

    // Create fluent step builder
    fun step(tool: Tool): StepBuilder

    // Add configured step to chain
    operator fun StepBuilder.unaryPlus()

    // Traditional methods still available
    fun step(stepId: String, toolName: String, parameters: Map<String, Any>)
    fun step(stepId: String, tool: Tool, parameters: Map<String, Any>)

    // Convenience methods for named outputs
    fun stepWithOutput(stepId: String, tool: Tool, outputName: String, parameters: Map<String, Any>)
    fun stepWithOutput(stepId: String, toolName: String, outputName: String, parameters: Map<String, Any>)

    // Transform methods
    fun stepWithTransform(stepId: String, toolName: String, parameters: Map<String, Any>, transformer: (ToolResult, ChainContext) -> Map<String, Any>)
    fun stepWithTransform(stepId: String, tool: Tool, parameters: Map<String, Any>, transformer: (ToolResult, ChainContext) -> Map<String, Any>)
}
```

### StepBuilder

```kotlin
class StepBuilder(
    val tool: Tool,
    var stepId: String? = null
) {
    // Set step ID
    fun named(id: String): StepBuilder

    // Name the output
    fun output(name: String): StepBuilder

    // Provide input parameters (provider is a suspend lambda)
    fun input(provider: suspend (ChainContext) -> Map<String, Any>): StepBuilder
}
```

**Note:** The `input()` provider is a `suspend` lambda, which means it can call other suspend functions. However, when using it in code, you don't need to explicitly write the `suspend` keyword:

```kotlin
// ✅ Correct usage (implicit suspend)
+step(tool).input { context ->
    val value = context.requireOutputOf("previous")
    mapOf("param" to value)
}

// ❌ Don't do this (explicit suspend not needed in lambda body)
+step(tool).input { context: ChainContext ->  // Type annotation optional
    suspend {  // ❌ Not needed!
        val value = context.requireOutputOf("previous")
        mapOf("param" to value)
    }
}
```

### ChainContext

```kotlin
data class ChainContext(
    val chainId: String,
    var currentStep: Int,
    val results: MutableList<ToolResult>,
    val sharedData: MutableMap<String, Any>,
    val stepOutputs: MutableMap<String, Any>
) {
    fun addResult(result: ToolResult)
    fun getLastResult(): ToolResult?
    fun setStepOutput(stepId: String, output: Any)
}

// Extension functions
fun ChainContext.getOutputOf(stepId: String): Any?
fun ChainContext.requireOutputOf(stepId: String): Any  // Throws if not found
```

### Building and Executing

```kotlin
// Build chain
fun toolChain(id: String, init: ToolChainBuilder.() -> Unit): ModernToolChain

// Execute chain
suspend fun ModernToolChain.execute(initialParameters: Map<String, Any>): ChainResult

// Result
data class ChainResult(
    val success: Boolean,
    val result: String,
    val error: String,
    val executionTime: Long,
    val stepResults: List<ToolResult>
)
```

## Best Practices

### 1. Use Named Outputs for Clarity

**Do:**
```kotlin
+step(resolveTool).output("sku").input { ... }
+step(lookupTool).input { context ->
    mapOf("sku" to context.requireOutputOf("sku"))  // Clear reference
}
```

**Don't:**
```kotlin
+step(resolveTool)
+step(lookupTool).input { context ->
    mapOf("sku" to context.requireOutputOf("step-1"))  // Fragile!
}
```

### 2. Use Named Steps for Complex Pipelines

**Do:**
```kotlin
+step(tool1).named("fetch-user")
+step(tool2).named("fetch-orders")
+step(tool3).named("merge").input { context ->
    // Clear what we're accessing
    val user = context.requireOutputOf("fetch-user")
    val orders = context.requireOutputOf("fetch-orders")
    // ...
}
```

### 3. Prefer `requireOutputOf` Over `getOutputOf`

**Do:**
```kotlin
+step(tool).input { context ->
    val sku = context.requireOutputOf("sku")  // Fails fast with clear error
    // ...
}
```

**Don't:**
```kotlin
+step(tool).input { context ->
    val sku = context.getOutputOf("sku")  // Might be null, silent failures
    // ...
}
```

### 4. Keep Input Blocks Simple

**Do:**
```kotlin
+step(tool).input { context ->
    val sku = context.requireOutputOf("sku")
    mapOf("sku" to sku)
}
```

**Don't:**
```kotlin
+step(tool).input { context ->
    // Complex business logic in input block
    val sku = context.requireOutputOf("sku")
    val processed = complexTransformation(sku)
    val validated = validate(processed)
    // ... more logic ...
    mapOf("sku" to validated)
}
```

Extract complex logic to separate functions or tools.

### 5. Use Debug Mode During Development

```kotlin
val chain = toolChain("my-pipeline") {
    name = "My Pipeline"
    debugEnabled = true  // Enable debug logging

    +step(tool1).output("out1")
    +step(tool2).output("out2")
}
```

Debug mode logs:
- Step execution start/completion
- Skipped steps (conditions not met)
- Output values

### 6. Handle Errors Gracefully

```kotlin
+step(tool).input { context ->
    val sku = context.getOutputOf("sku")

    if (sku == null) {
        // Provide default or skip
        mapOf("skip" to true)
    } else {
        mapOf("sku" to sku)
    }
}
```

### 7. Don't Forget the Unary Plus!

**Do:**
```kotlin
+step(tool).output("result")  // ✅ Added to chain
```

**Don't:**
```kotlin
step(tool).output("result")  // ❌ Not added! No-op!
```

## Examples

### Example 1: Product Lookup Pipeline

```kotlin
val productPipeline = toolChain("product-pipeline") {
    name = "Product Information Pipeline"
    description = "Resolve product and fetch specifications"
    debugEnabled = false

    // Step 1: Resolve product name to SKU
    +step(resolveTool)
        .named("resolve")
        .output("sku")
        .input { mapOf("text" to "MacBook Pro 16-inch") }

    // Step 2: Fetch product specifications
    +step(getSpecsTool)
        .named("specs")
        .output("specifications")
        .input { context ->
            val sku = context.requireOutputOf("sku")
            mapOf("sku" to sku, "include_pricing" to true)
        }

    // Step 3: Format for display
    +step(formatTool)
        .named("format")
        .input { context ->
            val specs = context.requireOutputOf("specifications")
            mapOf(
                "data" to specs,
                "format" to "markdown"
            )
        }
}

// Execute
val result = productPipeline.execute(emptyMap())
println(result.result)
```

### Example 2: Data Processing Pipeline

```kotlin
val dataPipeline = toolChain("data-processing") {
    name = "Data Processing Pipeline"

    // Extract
    +step(extractTool)
        .named("extract")
        .output("rawData")
        .input { mapOf("source" to "database") }

    // Transform
    +step(transformTool)
        .named("transform")
        .output("cleanData")
        .input { context ->
            val raw = context.requireOutputOf("rawData")
            mapOf(
                "data" to raw,
                "operations" to listOf("normalize", "deduplicate", "validate")
            )
        }

    // Load
    +step(loadTool)
        .named("load")
        .input { context ->
            val clean = context.requireOutputOf("cleanData")
            mapOf(
                "data" to clean,
                "destination" to "warehouse"
            )
        }
}
```

### Example 3: Multi-Source Aggregation

```kotlin
val aggregationPipeline = toolChain("multi-source-agg") {
    name = "Multi-Source Aggregation"

    // Fetch from multiple sources
    +step(fetchSourceATool)
        .named("source-a")
        .output("dataA")
        .input { mapOf("source" to "api-a") }

    +step(fetchSourceBTool)
        .named("source-b")
        .output("dataB")
        .input { mapOf("source" to "api-b") }

    +step(fetchSourceCTool)
        .named("source-c")
        .output("dataC")
        .input { mapOf("source" to "api-c") }

    // Merge all sources
    +step(mergeTool)
        .named("merge")
        .output("merged")
        .input { context ->
            mapOf(
                "sources" to listOf(
                    context.requireOutputOf("dataA"),
                    context.requireOutputOf("dataB"),
                    context.requireOutputOf("dataC")
                )
            )
        }

    // Analyze merged data
    +step(analyzeTool)
        .named("analyze")
        .input { context ->
            val merged = context.requireOutputOf("merged")
            mapOf("data" to merged, "type" to "comprehensive")
        }
}
```

## Testing

### Unit Testing Steps

```kotlin
@Test
fun `test product pipeline`() = runBlocking {
    // Create test tools
    val resolveTool = SimpleTool("resolve", ...) { params ->
        ToolResult.success("SKU-12345")
    }

    val getSpecsTool = SimpleTool("get_specs", ...) { params ->
        val sku = params["sku"]
        ToolResult.success("Specs for $sku")
    }

    // Register tools
    ToolRegistry.register(resolveTool)
    ToolRegistry.register(getSpecsTool)

    // Build pipeline
    val pipeline = toolChain("test-pipeline") {
        name = "Test Pipeline"

        +step(resolveTool).output("sku").input {
            mapOf("text" to "iPhone")
        }

        +step(getSpecsTool).input { context ->
            mapOf("sku" to context.requireOutputOf("sku"))
        }
    }

    // Execute
    val result = pipeline.execute(emptyMap())

    // Assert
    assertTrue(result.success)
    assertEquals(2, result.stepResults.size)
    assertEquals("SKU-12345", result.stepResults[0].result)
    assertEquals("Specs for SKU-12345", result.stepResults[1].result)
}
```

### Integration Testing

```kotlin
@Test
fun `test full pipeline integration`() = runBlocking {
    // Setup real tools
    setupToolRegistry()

    // Build pipeline
    val pipeline = createProductPipeline()

    // Execute with real data
    val result = pipeline.execute(mapOf(
        "product_query" to "MacBook Pro 16-inch"
    ))

    // Verify end-to-end behavior
    assertTrue(result.success)
    assertTrue(result.result.contains("MacBook"))
    assertTrue(result.executionTime < 5000)
}
```

## Troubleshooting

### Step Not Executing

**Problem**: Step is configured but not running.

**Solution**: Make sure you're using the unary plus operator:

```kotlin
// ❌ Wrong
step(tool).output("result")

// ✅ Correct
+step(tool).output("result")
```

### Output Not Found

**Problem**: `requireOutputOf()` throws "Output not found" error.

**Solution**:
1. Check step order - output must be created before it's accessed
2. Verify output name matches: `.output("sku")` → `requireOutputOf("sku")`
3. Use debug mode to see what outputs are available

```kotlin
val chain = toolChain("debug") {
    debugEnabled = true  // See step execution details
    // ...
}
```

### Type Mismatch

**Problem**: `input { context -> ... }` has type mismatch errors.

**Solution**: Ensure lambda returns `Map<String, Any>` (non-nullable Any):

```kotlin
// ❌ Wrong - nullable
+step(tool).input { context ->
    mapOf("value" to context.sharedData["key"])  // Any?
}

// ✅ Correct
+step(tool).input { context ->
    mapOf("value" to (context.sharedData["key"] ?: "default"))  // Any
}
```

### Chain Builds But Fails at Runtime

**Problem**: Chain builds successfully but fails during execution.

**Solution**:
1. Check tool registration - all tools must be in `ToolRegistry`
2. Verify parameter names match tool expectations
3. Use `.named()` to track which step is failing

## Migration from Traditional Syntax

### Before (Traditional)

```kotlin
val chain = toolChain("old-style") {
    name = "Old Style"

    step("step1", "resolve-tool", mapOf("text" to "iPhone"))

    stepWithTransform("step2", "lookup-tool", mapOf()) { result, context ->
        mapOf("sku" to result.result)
    }

    step("step3", "format-tool")
}
```

### After (Fluent)

```kotlin
val chain = toolChain("new-style") {
    name = "New Style"

    +step(resolveTool)
        .named("step1")
        .output("sku")
        .input { mapOf("text" to "iPhone") }

    +step(lookupTool)
        .named("step2")
        .output("product")
        .input { context ->
            mapOf("sku" to context.requireOutputOf("sku"))
        }

    +step(formatTool)
        .named("step3")
        .input { context ->
            mapOf("data" to context.requireOutputOf("product"))
        }
}
```

**Benefits:**
- Type-safe tool references
- Clearer data flow
- No manual transformers needed
- More readable

## See Also

- [Context-Aware Tools](../dsl-guide/context-aware-tools.md) - Build tools that use AgentContext
- [Multi-Agent Orchestration](multi-agent.md) - Coordinate multiple agents
- [Tool Caching](../performance/tool-caching.md) - Cache tool results
- [Output Validation](../dsl-guide/output-validation.md) - Validate tool outputs

## Summary

The Tool Pipeline DSL provides a fluent, type-safe way to build complex tool chains:

✅ **Method Chaining**: `.named()`, `.output()`, `.input()`
✅ **Context-Aware**: Access previous outputs with `requireOutputOf()`
✅ **Type-Safe**: Use Tool objects directly
✅ **Readable**: Clear data flow between steps
✅ **Flexible**: Mix fluent and traditional syntax
✅ **Backward Compatible**: Existing chains still work

Start building powerful tool pipelines today! 🚀
