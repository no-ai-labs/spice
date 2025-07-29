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
5. **JSON Schema Support**: Full JSON Schema draft-07 compliance for tool definitions

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

## AgentToolSerializer

`AgentToolSerializer` provides JSON Schema conversion and file persistence:

### JSON Schema Conversion

```kotlin
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonSchema
import io.github.noailabs.spice.serialization.SpiceSerializer.agentToolFromJsonSchema

// Convert to JSON Schema
val schema = myTool.toJsonSchema()

// Create from JSON Schema
val tool = SpiceSerializer.agentToolFromJsonSchema(schema)

// Validate schema
val result = SpiceSerializer.validateJsonSchema(schema)
```

### File Operations

```kotlin
import io.github.noailabs.spice.model.AgentToolSerializer.saveToFile
import io.github.noailabs.spice.model.AgentToolSerializer.loadFromFile

// Save to file
myTool.saveToFile("tools/calculator.json", SerializationFormat.JSON)

// Load from file
val loadedTool = loadFromFile("tools/calculator.json")
```

### JSON Schema Format

Tools are serialized using JSON Schema draft-07 format with custom extensions:

```json
{
  "$schema": "https://json-schema.org/draft-07/schema#",
  "title": "tool-name",
  "description": "Tool description",
  "type": "object",
  "required": ["param1"],
  "properties": {
    "param1": {
      "type": "string",
      "description": "Parameter description"
    }
  },
  "x-tags": ["tag1", "tag2"],
  "x-metadata": {
    "version": "1.0"
  },
  "x-implementation": {
    "type": "kotlin-function"
  }
}
```

### Future Extensions

- Full YAML support with proper parser
- Tool validation and testing utilities
- Tool composition and chaining
- Remote tool execution support
- VS Code extension for tool editing 