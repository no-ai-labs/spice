# Vector Store DSL

Integrate vector stores with your agents.

## Qdrant

```kotlin
vectorStore("knowledge") {
    provider("qdrant")
    connection("localhost", 6333)
    apiKey("your-api-key")
    collection("documents")
    vectorSize(384)
}
```

## Pinecone

```kotlin
vectorStore("embeddings") {
    provider("pinecone")
    connection("api.pinecone.io", 443)
    apiKey("pinecone-key")
    collection("vectors")
    vectorSize(1536)
}
```

## Automatic Search Tool

When you add a vector store, Spice automatically creates a search tool:

```kotlin
buildAgent {
    vectorStore("docs") {
        provider("qdrant")
        connection("localhost", 6333)
    }

    handle { comm ->
        // Auto-generated "search-docs" tool available
        val result = run("search-docs", mapOf(
            "query" to comm.content,
            "topK" to 5
        ))
        comm.reply(result.result, id)
    }
}
```
