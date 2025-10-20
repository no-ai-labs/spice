# Agent API

Agent interface and implementations.

## Interface

```kotlin
interface Agent : Identifiable {
    val id: String
    val name: String
    val description: String
    val capabilities: List<String>

    suspend fun processComm(comm: Comm): Comm
    fun canHandle(comm: Comm): Boolean
    fun getTools(): List<Tool>
    fun isReady(): Boolean
}
```
