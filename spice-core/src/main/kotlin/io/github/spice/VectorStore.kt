package io.github.spice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 🔍 Spice VectorStore System
 * 
 * Vector storage interface and implementations for RAG (Retrieval-Augmented Generation)
 * Supports various vector databases like Qdrant, Pinecone, Weaviate, etc.
 * 
 * ## Quick Start - Text Storage (90% of use cases)
 * ```kotlin
 * // Create a text-oriented vector store
 * val vectorStore = VectorStoreFactory.createTextQdrant(
 *     host = "localhost",
 *     port = 6333,
 *     embeddingGenerator = { text -> embeddingAgent.generateEmbedding(text) }
 * )
 * 
 * // Store text documents
 * vectorStore.storeText("my-collection", "doc1", "Today's weather is really nice")
 * vectorStore.storeTexts("my-collection", listOf(
 *     TextDocument("doc2", "I love programming in Kotlin"),
 *     TextDocument("doc3", "Spice Framework makes AI development easy")
 * ))
 * 
 * // Search for similar texts
 * val results = vectorStore.searchSimilarText("my-collection", "How's the weather?", topK = 5)
 * results.forEach { result ->
 *     println("Found: ${result.text} (score: ${result.score})")
 * }
 * ```
 * 
 * ## Advanced Usage - Custom Metadata
 * ```kotlin
 * // Store with metadata
 * vectorStore.storeText(
 *     collectionName = "articles",
 *     id = "article-123",
 *     text = "Kotlin Coroutines are powerful for async programming",
 *     metadata = mapOf(
 *         "author" to "John Doe",
 *         "category" to "Programming",
 *         "date" to "2024-01-15"
 *     )
 * )
 * ```
 */

/**
 * Vector store interface
 */
interface VectorStore {
    
    /**
     * Store vectors and metadata
     */
    suspend fun upsert(
        collectionName: String,
        vectors: List<VectorDocument>
    ): VectorOperationResult
    
    /**
     * Similarity search
     */
    suspend fun search(
        collectionName: String,
        queryVector: List<Float>,
        topK: Int = 10,
        filter: VectorFilter? = null,
        scoreThreshold: Float? = null
    ): List<VectorResult>
    
    /**
     * Text-based search (automatic embedding generation)
     */
    suspend fun searchByText(
        collectionName: String,
        queryText: String,
        topK: Int = 10,
        filter: VectorFilter? = null,
        scoreThreshold: Float? = null
    ): List<VectorResult>
    
    /**
     * Delete vectors
     */
    suspend fun delete(
        collectionName: String,
        ids: List<String>
    ): VectorOperationResult
    
    /**
     * Create collection
     */
    suspend fun createCollection(
        collectionName: String,
        vectorSize: Int,
        distance: DistanceMetric = DistanceMetric.COSINE
    ): VectorOperationResult
    
    /**
     * Delete collection
     */
    suspend fun deleteCollection(collectionName: String): VectorOperationResult
    
    /**
     * Get collection information
     */
    suspend fun getCollectionInfo(collectionName: String): CollectionInfo?
    
    /**
     * Check connection status
     */
    suspend fun healthCheck(): Boolean
}

/**
 * Simple text-oriented vector store interface for common use cases
 */
interface TextVectorStore : VectorStore {
    
    /**
     * Store text with automatic embedding generation
     * @param collectionName The collection to store in
     * @param id Unique identifier for the text
     * @param text The text content to store
     * @param metadata Additional metadata as simple key-value pairs
     */
    suspend fun storeText(
        collectionName: String,
        id: String,
        text: String,
        metadata: Map<String, String> = emptyMap()
    ): VectorOperationResult
    
    /**
     * Store multiple texts with automatic embedding generation
     */
    suspend fun storeTexts(
        collectionName: String,
        texts: List<TextDocument>
    ): VectorOperationResult
    
    /**
     * Search for similar texts
     * @param collectionName The collection to search in
     * @param query The query text
     * @param topK Number of results to return
     * @return List of similar texts with scores
     */
    suspend fun searchSimilarText(
        collectionName: String,
        query: String,
        topK: Int = 10
    ): List<TextSearchResult>
}

/**
 * Simple text document for easy storage
 */
data class TextDocument(
    val id: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Text search result with simple metadata
 */
data class TextSearchResult(
    val id: String,
    val text: String,
    val score: Float,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Vector document
 */
@Serializable
data class VectorDocument(
    val id: String,
    val vector: List<Float>,
    val metadata: Map<String, JsonElement> = emptyMap()
) {
    companion object {
        /**
         * Create a VectorDocument from text
         */
        fun fromText(
            id: String,
            text: String,
            vector: List<Float>,
            additionalMetadata: Map<String, String> = emptyMap()
        ): VectorDocument {
            val metadata = mutableMapOf<String, JsonElement>(
                "text" to JsonPrimitive(text)
            )
            additionalMetadata.forEach { (key, value) ->
                metadata[key] = JsonPrimitive(value)
            }
            return VectorDocument(id, vector, metadata)
        }
    }
}

/**
 * Vector search result
 */
@Serializable
data class VectorResult(
    val id: String,
    val score: Float,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val vector: List<Float>? = null
)

/**
 * Vector filter
 */
@Serializable
data class VectorFilter(
    val must: List<FilterCondition> = emptyList(),
    val mustNot: List<FilterCondition> = emptyList(),
    val should: List<FilterCondition> = emptyList()
)

/**
 * Filter condition
 */
@Serializable
sealed class FilterCondition {
    @Serializable
    data class Equals(val field: String, val value: JsonElement) : FilterCondition()
    
    @Serializable
    data class Range(val field: String, val gte: Float? = null, val lte: Float? = null) : FilterCondition()
    
    @Serializable
    data class In(val field: String, val values: List<JsonElement>) : FilterCondition()
    
    @Serializable
    data class Match(val field: String, val text: String) : FilterCondition()
}

/**
 * Distance metric
 */
enum class DistanceMetric {
    COSINE, EUCLIDEAN, DOT_PRODUCT
}

/**
 * Vector operation result
 */
@Serializable
data class VectorOperationResult(
    val success: Boolean,
    val message: String = "",
    val affectedCount: Int = 0,
    val operationId: String? = null
)

/**
 * Collection information
 */
@Serializable
data class CollectionInfo(
    val name: String,
    val vectorSize: Int,
    val pointsCount: Long,
    val distance: DistanceMetric,
    val status: String
)

/**
 * Qdrant vector store implementation with text-oriented features
 */
class QdrantVectorStore(
    private val host: String = "localhost",
    private val port: Int = 6333,
    private val apiKey: String? = null,
    private val timeout: Duration = Duration.ofSeconds(30),
    private val embeddingGenerator: ((String) -> List<Float>)? = null
) : TextVectorStore {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val baseUrl = "http://$host:$port"
    
    override suspend fun upsert(
        collectionName: String,
        vectors: List<VectorDocument>
    ): VectorOperationResult = withContext(Dispatchers.IO) {
        
        val points = vectors.map { doc ->
            QdrantPoint(
                id = doc.id,
                vector = doc.vector,
                payload = JsonObject(doc.metadata)
            )
        }
        
        val requestBody = QdrantUpsertRequest(points = points)
        val jsonBody = json.encodeToString(QdrantUpsertRequest.serializer(), requestBody)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/collections/$collectionName/points"))
            .header("Content-Type", "application/json")
            .apply { apiKey?.let { header("api-key", it) } }
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(timeout)
            .build()
        
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299) {
                val qdrantResponse = json.decodeFromString<QdrantResponse>(response.body())
                VectorOperationResult(
                    success = true,
                    message = qdrantResponse.status,
                    affectedCount = vectors.size,
                    operationId = qdrantResponse.result?.operation_id?.toString()
                )
            } else {
                VectorOperationResult(
                    success = false,
                    message = "HTTP ${response.statusCode()}: ${response.body()}"
                )
            }
        } catch (e: Exception) {
            VectorOperationResult(
                success = false,
                message = "Upsert failed: ${e.message}"
            )
        }
    }
    
    override suspend fun search(
        collectionName: String,
        queryVector: List<Float>,
        topK: Int,
        filter: VectorFilter?,
        scoreThreshold: Float?
    ): List<VectorResult> = withContext(Dispatchers.IO) {
        
        val searchRequest = QdrantSearchRequest(
            vector = queryVector,
            limit = topK,
            filter = filter?.toQdrantFilter(),
            score_threshold = scoreThreshold,
            with_payload = true,
            with_vector = false
        )
        
        val jsonBody = json.encodeToString(QdrantSearchRequest.serializer(), searchRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/collections/$collectionName/points/search"))
            .header("Content-Type", "application/json")
            .apply { apiKey?.let { header("api-key", it) } }
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(timeout)
            .build()
        
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299) {
                val qdrantResponse = json.decodeFromString<QdrantSearchResponse>(response.body())
                qdrantResponse.result.map { point ->
                    VectorResult(
                        id = point.id,
                        score = point.score,
                        metadata = point.payload ?: emptyMap(),
                        vector = point.vector
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("🌶️ Search failed: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun searchByText(
        collectionName: String,
        queryText: String,
        topK: Int,
        filter: VectorFilter?,
        scoreThreshold: Float?
    ): List<VectorResult> {
        // In real implementation, call embedding service to convert to vector
        // Here we use dummy vector for example
        val dummyVector = generateDummyEmbedding(queryText)
        return search(collectionName, dummyVector, topK, filter, scoreThreshold)
    }
    
    override suspend fun delete(
        collectionName: String,
        ids: List<String>
    ): VectorOperationResult = withContext(Dispatchers.IO) {
        
        val deleteRequest = QdrantDeleteRequest(points = ids)
        val jsonBody = json.encodeToString(QdrantDeleteRequest.serializer(), deleteRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/collections/$collectionName/points/delete"))
            .header("Content-Type", "application/json")
            .apply { apiKey?.let { header("api-key", it) } }
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(timeout)
            .build()
        
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299) {
                VectorOperationResult(
                    success = true,
                    message = "Deleted successfully",
                    affectedCount = ids.size
                )
            } else {
                VectorOperationResult(
                    success = false,
                    message = "HTTP ${response.statusCode()}: ${response.body()}"
                )
            }
        } catch (e: Exception) {
            VectorOperationResult(
                success = false,
                message = "Delete failed: ${e.message}"
            )
        }
    }
    
    override suspend fun createCollection(
        collectionName: String,
        vectorSize: Int,
        distance: DistanceMetric
    ): VectorOperationResult = withContext(Dispatchers.IO) {
        
        val createRequest = QdrantCreateCollectionRequest(
            vectors = QdrantVectorConfig(
                size = vectorSize,
                distance = distance.toQdrantDistance()
            )
        )
        
        val jsonBody = json.encodeToString(QdrantCreateCollectionRequest.serializer(), createRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/collections/$collectionName"))
            .header("Content-Type", "application/json")
            .apply { apiKey?.let { header("api-key", it) } }
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(timeout)
            .build()
        
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299) {
                VectorOperationResult(
                    success = true,
                    message = "Collection created successfully"
                )
            } else {
                VectorOperationResult(
                    success = false,
                    message = "HTTP ${response.statusCode()}: ${response.body()}"
                )
            }
        } catch (e: Exception) {
            VectorOperationResult(
                success = false,
                message = "Collection creation failed: ${e.message}"
            )
        }
    }
    
    override suspend fun deleteCollection(collectionName: String): VectorOperationResult = withContext(Dispatchers.IO) {
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/collections/$collectionName"))
            .apply { apiKey?.let { header("api-key", it) } }
            .DELETE()
            .timeout(timeout)
            .build()
        
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299) {
                VectorOperationResult(
                    success = true,
                    message = "Collection deleted successfully"
                )
            } else {
                VectorOperationResult(
                    success = false,
                    message = "HTTP ${response.statusCode()}: ${response.body()}"
                )
            }
        } catch (e: Exception) {
            VectorOperationResult(
                success = false,
                message = "Collection deletion failed: ${e.message}"
            )
        }
    }
    
    override suspend fun getCollectionInfo(collectionName: String): CollectionInfo? = withContext(Dispatchers.IO) {
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/collections/$collectionName"))
            .apply { apiKey?.let { header("api-key", it) } }
            .GET()
            .timeout(timeout)
            .build()
        
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299) {
                val qdrantInfo = json.decodeFromString<QdrantCollectionResponse>(response.body())
                CollectionInfo(
                    name = collectionName,
                    vectorSize = qdrantInfo.result.config.params.vectors.size,
                    pointsCount = qdrantInfo.result.points_count,
                    distance = qdrantInfo.result.config.params.vectors.distance.fromQdrantDistance(),
                    status = qdrantInfo.result.status
                )
            } else {
                null
            }
        } catch (e: Exception) {
            println("🌶️ Failed to get collection info: ${e.message}")
            null
        }
    }
    
    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build()
        
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
    
    // === TextVectorStore Implementation ===
    
    override suspend fun storeText(
        collectionName: String,
        id: String,
        text: String,
        metadata: Map<String, String>
    ): VectorOperationResult {
        val embedding = embeddingGenerator?.invoke(text) ?: generateDummyEmbedding(text)
        
        val jsonMetadata = mutableMapOf<String, JsonElement>(
            "text" to JsonPrimitive(text)
        )
        metadata.forEach { (key, value) ->
            jsonMetadata[key] = JsonPrimitive(value)
        }
        
        val doc = VectorDocument(id, embedding, jsonMetadata)
        return upsert(collectionName, listOf(doc))
    }
    
    override suspend fun storeTexts(
        collectionName: String,
        texts: List<TextDocument>
    ): VectorOperationResult {
        val docs = texts.map { textDoc ->
            val embedding = embeddingGenerator?.invoke(textDoc.text) ?: generateDummyEmbedding(textDoc.text)
            
            val jsonMetadata = mutableMapOf<String, JsonElement>(
                "text" to JsonPrimitive(textDoc.text)
            )
            textDoc.metadata.forEach { (key, value) ->
                jsonMetadata[key] = JsonPrimitive(value)
            }
            
            VectorDocument(textDoc.id, embedding, jsonMetadata)
        }
        
        return upsert(collectionName, docs)
    }
    
    override suspend fun searchSimilarText(
        collectionName: String,
        query: String,
        topK: Int
    ): List<TextSearchResult> {
        val results = searchByText(collectionName, query, topK)
        return results.mapNotNull { it.toTextSearchResult() }
    }
    
    /**
     * Generate dummy embedding (in real use, use OpenAI, Sentence Transformers, etc.)
     */
    private fun generateDummyEmbedding(text: String): List<Float> {
        // Generate simple hash vector based on text (DO NOT use in production!)
        val hash = text.hashCode()
        return (0 until 384).map { i ->
            ((hash + i) % 1000) / 1000.0f - 0.5f
        }
    }
}

/**
 * === Qdrant REST API Data Classes ===
 */

@Serializable
data class QdrantPoint(
    val id: String,
    val vector: List<Float>,
    val payload: JsonObject? = null  // Map<String, JsonElement> 대신 JsonObject 사용
)

@Serializable
data class QdrantUpsertRequest(
    val points: List<QdrantPoint>
)

@Serializable
data class QdrantSearchRequest(
    val vector: List<Float>,
    val limit: Int,
    val filter: QdrantFilter? = null,
    val score_threshold: Float? = null,
    val with_payload: Boolean = true,
    val with_vector: Boolean = false
)

@Serializable
data class QdrantDeleteRequest(
    val points: List<String>
)

@Serializable
data class QdrantCreateCollectionRequest(
    val vectors: QdrantVectorConfig
)

@Serializable
data class QdrantVectorConfig(
    val size: Int,
    val distance: String
)

@Serializable
data class QdrantFilter(
    val must: List<QdrantCondition>? = null,
    val must_not: List<QdrantCondition>? = null,
    val should: List<QdrantCondition>? = null
)

@Serializable
data class QdrantCondition(
    val key: String,
    val match: QdrantMatch? = null,
    val range: QdrantRange? = null
)

@Serializable
data class QdrantMatch(
    val value: JsonElement
)

@Serializable
data class QdrantRange(
    val gte: Float? = null,
    val lte: Float? = null
)

@Serializable
data class QdrantResponse(
    val status: String,
    val time: Float,
    val result: QdrantOperationResult? = null
)

@Serializable
data class QdrantOperationResult(
    val operation_id: Long? = null,
    val status: String? = null
)

@Serializable
data class QdrantSearchResponse(
    val status: String,
    val time: Float,
    val result: List<QdrantSearchResult>
)

@Serializable
data class QdrantSearchResult(
    val id: String,
    val score: Float,
    val payload: Map<String, JsonElement>? = null,
    val vector: List<Float>? = null
)

@Serializable
data class QdrantCollectionResponse(
    val status: String,
    val time: Float,
    val result: QdrantCollectionResult
)

@Serializable
data class QdrantCollectionResult(
    val status: String,
    val points_count: Long,
    val config: QdrantCollectionConfig
)

@Serializable
data class QdrantCollectionConfig(
    val params: QdrantCollectionParams
)

@Serializable
data class QdrantCollectionParams(
    val vectors: QdrantVectorParams
)

@Serializable
data class QdrantVectorParams(
    val size: Int,
    val distance: String
)

/**
 * === Extension Functions ===
 */

/**
 * Convert Map<String, JsonElement> to JsonObject
 */
fun Map<String, JsonElement>.toJsonObject(): JsonObject {
    return JsonObject(this)
}

/**
 * Convert VectorFilter to QdrantFilter
 */
fun VectorFilter.toQdrantFilter(): QdrantFilter {
    return QdrantFilter(
        must = must.map { it.toQdrantCondition() }.takeIf { it.isNotEmpty() },
        must_not = mustNot.map { it.toQdrantCondition() }.takeIf { it.isNotEmpty() },
        should = should.map { it.toQdrantCondition() }.takeIf { it.isNotEmpty() }
    )
}

/**
 * Convert FilterCondition to QdrantCondition
 */
fun FilterCondition.toQdrantCondition(): QdrantCondition {
    return when (this) {
        is FilterCondition.Equals -> QdrantCondition(
            key = field,
            match = QdrantMatch(value)
        )
        is FilterCondition.Range -> QdrantCondition(
            key = field,
            range = QdrantRange(gte, lte)
        )
        is FilterCondition.In -> QdrantCondition(
            key = field,
            match = QdrantMatch(values.first()) // Array matching is different in Qdrant
        )
        is FilterCondition.Match -> QdrantCondition(
            key = field,
            match = QdrantMatch(JsonPrimitive(text))
        )
    }
}

/**
 * Convert DistanceMetric to Qdrant string
 */
fun DistanceMetric.toQdrantDistance(): String = when (this) {
    DistanceMetric.COSINE -> "Cosine"
    DistanceMetric.EUCLIDEAN -> "Euclid"
    DistanceMetric.DOT_PRODUCT -> "Dot"
}

/**
 * Convert Qdrant string to DistanceMetric
 */
fun String.fromQdrantDistance(): DistanceMetric = when (this) {
    "Cosine" -> DistanceMetric.COSINE
    "Euclid" -> DistanceMetric.EUCLIDEAN
    "Dot" -> DistanceMetric.DOT_PRODUCT
    else -> DistanceMetric.COSINE
}

/**
 * === Utility Functions ===
 */

/**
 * Simple text search
 */
suspend fun VectorStore.searchText(
    collection: String,
    query: String,
    limit: Int = 5
): List<VectorResult> {
    return searchByText(collection, query, limit)
}

/**
 * Convert any Map to JsonElement metadata
 * Automatically converts common types (String, Number, Boolean) to JsonPrimitive
 */
fun Map<String, Any>.toJsonMetadata(): Map<String, JsonElement> {
    return this.mapValues { (_, value) ->
        when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> JsonPrimitive(value.toString()) // Convert lists to string for simplicity
            is Map<*, *> -> JsonPrimitive(value.toString()) // Convert maps to string for simplicity
            else -> JsonPrimitive(value.toString())
        }
    }
}

/**
 * Extract text content from VectorResult metadata
 */
fun VectorResult.getText(): String? {
    return metadata["text"]?.let { element ->
        when (element) {
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }
}

/**
 * Convert VectorResult to TextSearchResult
 */
fun VectorResult.toTextSearchResult(): TextSearchResult? {
    val text = getText() ?: return null
    val stringMetadata = metadata.mapValues { (_, value) ->
        when (value) {
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    }
    return TextSearchResult(id, text, score, stringMetadata)
}

/**
 * Metadata filtering DSL
 */
fun buildFilter(init: FilterBuilder.() -> Unit): VectorFilter {
    val builder = FilterBuilder()
    builder.init()
    return builder.build()
}

/**
 * Filter builder
 */
class FilterBuilder {
    private val mustConditions = mutableListOf<FilterCondition>()
    private val mustNotConditions = mutableListOf<FilterCondition>()
    private val shouldConditions = mutableListOf<FilterCondition>()
    
    fun must(condition: FilterCondition) {
        mustConditions.add(condition)
    }
    
    fun mustNot(condition: FilterCondition) {
        mustNotConditions.add(condition)
    }
    
    fun should(condition: FilterCondition) {
        shouldConditions.add(condition)
    }
    
    fun equals(field: String, value: String) {
        mustConditions.add(FilterCondition.Equals(field, JsonPrimitive(value)))
    }
    
    fun range(field: String, min: Float? = null, max: Float? = null) {
        mustConditions.add(FilterCondition.Range(field, min, max))
    }
    
    internal fun build(): VectorFilter {
        return VectorFilter(
            must = mustConditions,
            mustNot = mustNotConditions,
            should = shouldConditions
        )
    }
}

/**
 * VectorStore factory
 */
object VectorStoreFactory {
    
    fun createQdrant(
        host: String = "localhost",
        port: Int = 6333,
        apiKey: String? = null,
        embeddingGenerator: ((String) -> List<Float>)? = null
    ): VectorStore {
        return QdrantVectorStore(host, port, apiKey, Duration.ofSeconds(30), embeddingGenerator)
    }
    
    /**
     * Create a text-oriented Qdrant store with embedding generation
     */
    fun createTextQdrant(
        host: String = "localhost",
        port: Int = 6333,
        apiKey: String? = null,
        embeddingGenerator: (String) -> List<Float>
    ): TextVectorStore {
        return QdrantVectorStore(host, port, apiKey, Duration.ofSeconds(30), embeddingGenerator)
    }
    
    // Future support for other vector DBs
    // fun createPinecone(...): VectorStore
    // fun createWeaviate(...): VectorStore
} 