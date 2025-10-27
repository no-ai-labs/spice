# Vector Stores

RAG and semantic search integration.

## Supported Providers

- Qdrant
- Pinecone
- Weaviate
- Chroma
- Milvus

## Usage

```kotlin
vectorStore("docs") {
    provider("qdrant")
    connection("localhost", 6333)
}
```
