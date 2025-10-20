# Creating Custom Tools

Build powerful custom tools for your agents.

## Basic Tool

```kotlin
class MyTool : BaseTool() {
    override val name = "my_tool"
    override val description = "My custom tool"
    override val schema = ToolSchema(/*...*/)

    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        // Implementation
        return ToolResult.success("Done")
    }
}
```
