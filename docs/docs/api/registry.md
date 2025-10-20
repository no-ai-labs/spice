# Registry API

Generic registry system.

## Registry

```kotlin
class Registry<T : Identifiable>(val name: String) {
    fun register(item: T): T
    fun get(id: String): T?
    fun getAll(): List<T>
}
```
