# RAG Application

Retrieval-Augmented Generation.

```kotlin
val ragAgent = buildAgent {
    vectorStore("knowledge") {
        provider("qdrant")
        connection("localhost", 6333)
    }

    handle { comm ->
        val results = run("search-knowledge", mapOf("query" to comm.content))
        comm.reply(results.result, id)
    }
}
```
