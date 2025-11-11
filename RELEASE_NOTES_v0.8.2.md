# Spice Framework v0.8.2 Release Notes

**Released:** November 11, 2025
**Type:** Minor Feature Release
**Breaking Changes:** None

## 🎉 Overview

Version 0.8.2 introduces first-class OpenAI Function Calling specification support, enabling seamless integration between Spice Tools and OpenAI's function calling API. This release makes it trivial to export your Spice `ToolSchema` and `Tool` definitions to OpenAI-compatible format, eliminating the need for manual schema translation and ensuring perfect compatibility with OpenAI's latest function calling features.

## ✨ New Features

### OpenAI Function Calling Spec Export

Spice now provides built-in conversion from `ToolSchema` and `Tool` to OpenAI Function Calling specification format, making it effortless to use Spice tools with OpenAI's API.

**Key Benefits:**
- ✅ Zero-effort OpenAI API integration
- ✅ Automatic parameter schema conversion
- ✅ Smart handling of required/optional parameters
- ✅ Full support for default values
- ✅ Type-safe, idiomatic Kotlin API

**API Overview:**
```kotlin
// Extension function for ToolSchema
fun ToolSchema.toOpenAIFunctionSpec(strict: Boolean = false): Map<String, Any>

// Extension function for Tool
fun Tool.toOpenAIFunctionSpec(strict: Boolean = false): Map<String, Any>
```

**Parameters:**
- `strict`: Enable OpenAI strict mode (enforces schema validation with `additionalProperties: false`). Default: `false`

### Usage Examples

#### Basic Conversion

```kotlin
import io.github.noailabs.spice.*

// Define a Spice Tool
val searchTool = SimpleTool(
    name = "web_search",
    description = "Search the web for information",
    parameterSchemas = mapOf(
        "query" to ParameterSchema("string", "Search query", required = true),
        "limit" to ParameterSchema("number", "Maximum results", required = false)
    )
) { params ->
    // Tool implementation
    ToolResult.success("...")
}

// Convert to OpenAI spec
val openAISpec = searchTool.toOpenAIFunctionSpec()
```

**Output:**
```json
{
  "type": "function",
  "name": "web_search",
  "description": "Search the web for information",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "Search query"
      },
      "limit": {
        "type": "number",
        "description": "Maximum results"
      }
    },
    "required": ["query"]
  }
}
```

#### Direct ToolSchema Conversion

```kotlin
val schema = ToolSchema(
    name = "create_task",
    description = "Create a new task in the system",
    parameters = mapOf(
        "title" to ParameterSchema("string", "Task title", required = true),
        "description" to ParameterSchema("string", "Task description", required = false),
        "priority" to ParameterSchema("number", "Priority level (1-5)", required = false),
        "tags" to ParameterSchema("array", "Task tags", required = false)
    )
)

val openAISpec = schema.toOpenAIFunctionSpec()
// Ready to send to OpenAI API!
```

#### Integration with OpenAI SDK

```kotlin
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI

// Your Spice tools
val tools = listOf(
    WebSearchTool(),
    FileReadTool(),
    CalculatorTool()
)

// Convert to OpenAI function specs
val functions = tools.map { it.toOpenAIFunctionSpec() }

// Use with OpenAI API
val chatRequest = ChatCompletionRequest(
    model = ModelId("gpt-4"),
    messages = listOf(
        ChatMessage(
            role = ChatRole.User,
            content = "Search for latest AI news and save to file"
        )
    ),
    functions = functions  // ✅ Spice tools → OpenAI functions
)

val response = openai.chatCompletion(chatRequest)
```

#### Unified Workflow Tool Architecture

This feature enables perfect unification of WorkflowTool and Spice Tool:

```kotlin
// Before: Manual schema translation
class WorkflowTool(val spiceTool: Tool) {
    fun toOpenAISpec(): Map<String, Any> {
        // Manual mapping code...
    }
}

// After: Direct conversion
val workflowTools = spiceTools.map { it.toOpenAIFunctionSpec() }
// Perfect OpenAI spec, zero boilerplate!
```

## 🔍 Implementation Details

### Automatic Type Conversion

The conversion handles all standard JSON Schema types:
- `string` → `"string"`
- `number` → `"number"`
- `boolean` → `"boolean"`
- `array` → `"array"`
- `object` → `"object"`

### Required Parameter Handling

Only parameters marked with `required = true` appear in the `required` array. If no parameters are required, the `required` field is omitted entirely (following OpenAI best practices).

```kotlin
// No required parameters
ToolSchema(
    name = "random_number",
    description = "Generate a random number",
    parameters = mapOf(
        "min" to ParameterSchema("number", "Minimum", required = false),
        "max" to ParameterSchema("number", "Maximum", required = false)
    )
)
// Result: No "required" field in output ✅
```

### Default Value Support

If a `ParameterSchema` has a `default` value, it's automatically included in the OpenAI spec:

```kotlin
ParameterSchema(
    type = "number",
    description = "Request timeout in seconds",
    required = false,
    default = JsonPrimitive(30)
)
// Output includes: "default": 30
```

### Strict Mode Support

OpenAI's strict mode enforces schema validation and prevents additional properties:

```kotlin
// Default: non-strict mode (flexible)
val spec = tool.toOpenAIFunctionSpec()
// Output: { "type": "function", ... }

// Strict mode: enforces schema validation
val strictSpec = tool.toOpenAIFunctionSpec(strict = true)
```

**Strict Mode Output:**
```json
{
  "type": "function",
  "name": "get_weather",
  "description": "Get current weather",
  "parameters": {
    "type": "object",
    "properties": { ... },
    "required": ["location"],
    "additionalProperties": false
  },
  "strict": true
}
```

**When to use strict mode:**
- ✅ Production APIs requiring exact schema compliance
- ✅ Type-safe function calling with validation
- ✅ Preventing unexpected properties in function arguments
- ❌ Prototyping and development (use default non-strict mode)

## ✅ Testing

### Comprehensive Test Coverage

Added 8 new test cases covering all scenarios:

1. **Required parameters**: Verifies `required` array generation and `type` field
2. **Optional parameters only**: Verifies `required` field omission
3. **Default values**: Verifies `default` field handling
4. **Tool delegation**: Verifies `Tool.toOpenAIFunctionSpec()` delegates to schema
5. **Complex schema**: Verifies multi-parameter tools with mixed types
6. **Strict mode**: Verifies `strict: true` and `additionalProperties: false`
7. **Default non-strict mode**: Verifies strict fields are omitted by default
8. **Tool strict mode**: Verifies strict mode works from Tool interface

**Test Results:**
```
ToolBasicTest > test ToolSchema toOpenAIFunctionSpec with required parameters() PASSED
ToolBasicTest > test ToolSchema toOpenAIFunctionSpec without required parameters() PASSED
ToolBasicTest > test ToolSchema toOpenAIFunctionSpec with default values() PASSED
ToolBasicTest > test Tool toOpenAIFunctionSpec delegates to schema() PASSED
ToolBasicTest > test ToolSchema toOpenAIFunctionSpec with complex example() PASSED
ToolBasicTest > test ToolSchema toOpenAIFunctionSpec with strict mode() PASSED
ToolBasicTest > test ToolSchema toOpenAIFunctionSpec default mode is non-strict() PASSED
ToolBasicTest > test Tool toOpenAIFunctionSpec with strict mode() PASSED
```

All existing tests continue to pass (100% backward compatibility).

## 🎯 Use Cases

### 1. Multi-LLM Agent Systems

Use Spice tools with multiple LLM providers:

```kotlin
// Define tools once
val tools = listOf(WebSearchTool(), CalculatorTool())

// Use with OpenAI
val openAIFunctions = tools.map { it.toOpenAIFunctionSpec() }

// Use with Anthropic Claude (custom format)
val claudeTools = tools.map { it.toClaudeToolSpec() }

// Use with Spice Agents
val spiceAgent = agent {
    this.tools = tools  // Native support
}
```

### 2. LLM-Powered Workflows

Build workflows that dynamically call OpenAI with context-aware tools:

```kotlin
val workflowGraph = graph {
    // Collect user requirements
    agent("planner") {
        tools = emptyList()
    }

    // Execute with OpenAI + Spice tools
    agent("executor") {
        tools = listOf(
            DatabaseQueryTool(),
            EmailSenderTool(),
            SlackNotifierTool()
        )

        // Convert tools for OpenAI
        val openAIFunctions = tools.map { it.toOpenAIFunctionSpec() }
    }

    edge("planner" to "executor")
}
```

### 3. Testing OpenAI Integrations

Use Spice's local tool execution for testing OpenAI function calling:

```kotlin
@Test
fun `test OpenAI function calling with Spice tools`() = runTest {
    val mockTool = SimpleTool(
        name = "get_weather",
        description = "Get current weather",
        parameterSchemas = mapOf(
            "city" to ParameterSchema("string", "City name", required = true)
        )
    ) { params ->
        ToolResult.success("""{"temp": 72, "condition": "sunny"}""")
    }

    // Verify spec compatibility
    val spec = mockTool.toOpenAIFunctionSpec()
    assertEquals("get_weather", spec["name"])

    // Test local execution
    val result = mockTool.execute(mapOf("city" to "Seoul"))
    assertTrue(result.isSuccess)
}
```

## 📚 Documentation Updates

### Updated Files
- **spice-core/src/main/kotlin/io/github/noailabs/spice/Tool.kt**:243-304
  - Added `ToolSchema.toOpenAIFunctionSpec()` extension
  - Added `Tool.toOpenAIFunctionSpec()` extension
  - Comprehensive KDoc with examples

### Test Files
- **spice-core/src/test/kotlin/io/github/noailabs/spice/model/ToolBasicTest.kt**:74-226
  - 5 new test cases
  - Coverage for all conversion scenarios

## 🚀 Migration Guide

### No Breaking Changes

This is a purely additive release. All existing code continues to work without modification.

### Getting Started

Simply upgrade to 0.8.2 and start using the new extensions:

```kotlin
// 1. Upgrade dependency
implementation("com.github.no-ai-labs.spice-framework:spice-core:0.8.2")

// 2. Convert your tools
val openAISpec = myTool.toOpenAIFunctionSpec()

// 3. Use with OpenAI API
// ... (see examples above)
```

## 🔗 Related Standards

- [OpenAI Function Calling Documentation](https://platform.openai.com/docs/guides/function-calling)
- [JSON Schema Specification](https://json-schema.org/)
- [Spice Tool Documentation](https://github.com/no-ai-labs/spice/tree/main/docs/docs/api/tool.md)

## 🎓 Design Philosophy

This feature follows Spice's core principles:

1. **Type Safety**: Leverages Kotlin's type system for compile-time safety
2. **Idiomatic API**: Extension functions feel natural in Kotlin
3. **Zero Boilerplate**: One function call, no manual mapping
4. **Framework Agnostic**: Pure data conversion, no vendor lock-in
5. **Test Driven**: Comprehensive test coverage from day one

## 📦 Upgrade Instructions

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:0.8.2")
}
```

### Gradle (Groovy)
```groovy
dependencies {
    implementation 'com.github.no-ai-labs.spice-framework:spice-core:0.8.2'
}
```

### Maven
```xml
<dependency>
    <groupId>com.github.no-ai-labs.spice-framework</groupId>
    <artifactId>spice-core</artifactId>
    <version>0.8.2</version>
</dependency>
```

## 📄 Full Changelog

### Added
- `ToolSchema.toOpenAIFunctionSpec()`: Convert ToolSchema to OpenAI function calling spec
- `Tool.toOpenAIFunctionSpec()`: Convert Tool to OpenAI function calling spec
- Comprehensive test suite for OpenAI spec conversion (5 new tests)

### Documentation
- Added KDoc with usage examples and links to OpenAI documentation
- Added test coverage for all conversion scenarios
- Updated release notes with comprehensive examples

## 🙏 Acknowledgments

- Feature requested by KAI-Core team for unified WorkflowTool architecture
- Inspired by OpenAI's function calling specification
- Built with Spice's commitment to developer experience

## 🔮 Future Enhancements

Potential future additions based on community feedback:

- Anthropic Claude tool spec conversion
- Google Gemini function calling format
- Automatic JSON Schema validation
- Tool spec versioning support

---

**Full Documentation:** [github.com/no-ai-labs/spice/tree/main/docs](https://github.com/no-ai-labs/spice/tree/main/docs)
**GitHub:** [github.com/no-ai-labs/spice](https://github.com/no-ai-labs/spice)
**Report Issues:** [github.com/no-ai-labs/spice/issues](https://github.com/no-ai-labs/spice/issues)
