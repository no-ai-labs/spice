# Spring Integration

Use Spice agents as Spring beans.

## Inject Agents

```kotlin
@Service
class MyService(
    private val gptAgent: Agent?,
    private val spiceConfig: SpiceConfig
) {
    suspend fun process(message: String): String {
        val response = gptAgent?.processComm(
            Comm(content = message, from = "user")
        )
        return response?.content ?: "No response"
    }
}
```
