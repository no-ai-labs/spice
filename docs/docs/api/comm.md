# Comm API

Communication data class.

## Comm

```kotlin
data class Comm(
    val id: String,
    val content: String,
    val from: String,
    val to: String?,
    val type: CommType,
    val role: CommRole,
    // ...
)
```
