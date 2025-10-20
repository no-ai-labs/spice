# Tool API

Tool interface.

## Interface

```kotlin
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema

    suspend fun execute(parameters: Map<String, Any>): ToolResult
}
```
