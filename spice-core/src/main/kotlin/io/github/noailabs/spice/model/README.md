# Spice Model Package

This package contains data model classes that serve as intermediate representations for various Spice components.

## AgentTool

`AgentTool` is a serializable representation of a Tool that enables:

- **Persistence**: Save/load tools to/from YAML, JSON, or databases
- **GUI Integration**: Create tools visually without writing DSL code  
- **Tool Marketplace**: Share and distribute reusable tools
- **Dynamic Loading**: Load tools from external sources at runtime

### Key Features

1. **Serializable Structure**: All properties except the implementation function can be serialized
2. **Implementation Types**: Supports various implementation types (kotlin-function, http-api, script, etc.)
3. **Metadata & Tags**: Additional information for categorization and discovery
4. **Bidirectional Conversion**: Convert between Tool ↔ AgentTool seamlessly

### Usage Examples

```kotlin
// Create an AgentTool
val tool = agentTool("my-tool") {
    description("Tool description")
    parameters {
        string("input", "Input parameter")
    }
    tags("utility", "text")
    implement { params ->
        ToolResult.success("Result")
    }
}

// Convert to Tool interface
val executableTool = tool.toTool()

// Convert existing Tool to AgentTool
val agentTool = existingTool.toAgentTool(
    tags = listOf("converted"),
    metadata = mapOf("version" to "1.0")
)

// Serialize for storage (without implementation)
val json = Json.encodeToString(tool.copy(implementation = null))
```

### Future Extensions

- YAML/JSON loader for tool definitions
- Tool validation and testing utilities
- Tool composition and chaining
- Remote tool execution support 