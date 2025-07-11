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
 * RAG(Retrieval-Augmented Generation)를 위한 벡터 저장소 인터페이스와 구현체
 * Qdrant, Pinecone, Weaviate 등 다양한 벡터 DB를 지원합니다.
 */

/**
 * 벡터 저장소 인터페이스
 */
interface VectorStore {
    
    /**
     * 벡터와 메타데이터 저장
     */
    suspend fun upsert(
        collectionName: String,
        vectors: List<VectorDocument>
    ): VectorOperationResult
    
    /**
     * 유사도 검색
     */
    suspend fun search(
        collectionName: String,
        queryVector: List<Float>,
        topK: Int = 10,
        filter: VectorFilter? = null,
        scoreThreshold: Float? = null
    ): List<VectorResult>
    
    /**
     * 텍스트 기반 검색 (임베딩 자동 생성)
     */
    suspend fun searchByText(
        collectionName: String,
        queryText: String,
        topK: Int = 10,
        filter: VectorFilter? = null,
        scoreThreshold: Float? = null
    ): List<VectorResult>
    
    /**
     * 벡터 삭제
     */
    suspend fun delete(
        collectionName: String,
        ids: List<String>
    ): VectorOperationResult
    
    /**
     * 컬렉션 생성
     */
    suspend fun createCollection(
        collectionName: String,
        vectorSize: Int,
        distance: DistanceMetric = DistanceMetric.COSINE
    ): VectorOperationResult
    
    /**
     * 컬렉션 삭제
     */
    suspend fun deleteCollection(collectionName: String): VectorOperationResult
    
    /**
     * 컬렉션 정보 조회
     */
    suspend fun getCollectionInfo(collectionName: String): CollectionInfo?
    
    /**
     * 연결 상태 확인
     */
    suspend fun healthCheck(): Boolean
}

/**
 * 벡터 문서
 */
@Serializable
data class VectorDocument(
    val id: String,
    val vector: List<Float>,
    val metadata: Map<String, JsonElement> = emptyMap()
)

/**
 * 벡터 검색 결과
 */
@Serializable
data class VectorResult(
    val id: String,
    val score: Float,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val vector: List<Float>? = null
)

/**
 * 벡터 필터
 */
@Serializable
data class VectorFilter(
    val must: List<FilterCondition> = emptyList(),
    val mustNot: List<FilterCondition> = emptyList(),
    val should: List<FilterCondition> = emptyList()
)

/**
 * 필터 조건
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
 * 거리 측정 방식
 */
enum class DistanceMetric {
    COSINE, EUCLIDEAN, DOT_PRODUCT
}

/**
 * 벡터 연산 결과
 */
@Serializable
data class VectorOperationResult(
    val success: Boolean,
    val message: String = "",
    val affectedCount: Int = 0,
    val operationId: String? = null
)

/**
 * 컬렉션 정보
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
 * Qdrant 벡터 저장소 구현
 */
class QdrantVectorStore(
    private val host: String = "localhost",
    private val port: Int = 6333,
    private val apiKey: String? = null,
    private val timeout: Duration = Duration.ofSeconds(30)
) : VectorStore {
    
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
                payload = doc.metadata
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
        // 실제 구현에서는 임베딩 서비스를 호출해서 벡터로 변환
        // 여기서는 예시로 더미 벡터를 사용
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
    
    /**
     * 더미 임베딩 생성 (실제로는 OpenAI, Sentence Transformers 등 사용)
     */
    private fun generateDummyEmbedding(text: String): List<Float> {
        // 텍스트 기반으로 간단한 해시 벡터 생성 (실제 사용 금지!)
        val hash = text.hashCode()
        return (0 until 384).map { i ->
            ((hash + i) % 1000) / 1000.0f - 0.5f
        }
    }
}

/**
 * === Qdrant REST API 데이터 클래스들 ===
 */

@Serializable
data class QdrantPoint(
    val id: String,
    val vector: List<Float>,
    val payload: Map<String, JsonElement>? = null
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
 * === 확장 함수들 ===
 */

/**
 * VectorFilter를 QdrantFilter로 변환
 */
fun VectorFilter.toQdrantFilter(): QdrantFilter {
    return QdrantFilter(
        must = must.map { it.toQdrantCondition() }.takeIf { it.isNotEmpty() },
        must_not = mustNot.map { it.toQdrantCondition() }.takeIf { it.isNotEmpty() },
        should = should.map { it.toQdrantCondition() }.takeIf { it.isNotEmpty() }
    )
}

/**
 * FilterCondition을 QdrantCondition으로 변환
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
            match = QdrantMatch(values.first()) // Qdrant에서는 배열 매치가 다름
        )
        is FilterCondition.Match -> QdrantCondition(
            key = field,
            match = QdrantMatch(JsonPrimitive(text))
        )
    }
}

/**
 * DistanceMetric을 Qdrant 문자열로 변환
 */
fun DistanceMetric.toQdrantDistance(): String = when (this) {
    DistanceMetric.COSINE -> "Cosine"
    DistanceMetric.EUCLIDEAN -> "Euclid"
    DistanceMetric.DOT_PRODUCT -> "Dot"
}

/**
 * Qdrant 문자열을 DistanceMetric으로 변환
 */
fun String.fromQdrantDistance(): DistanceMetric = when (this) {
    "Cosine" -> DistanceMetric.COSINE
    "Euclid" -> DistanceMetric.EUCLIDEAN
    "Dot" -> DistanceMetric.DOT_PRODUCT
    else -> DistanceMetric.COSINE
}

/**
 * === 편의 함수들 ===
 */

/**
 * 간단한 텍스트 검색
 */
suspend fun VectorStore.searchText(
    collection: String,
    query: String,
    limit: Int = 5
): List<VectorResult> {
    return searchByText(collection, query, limit)
}

/**
 * 메타데이터 필터링 DSL
 */
fun buildFilter(init: FilterBuilder.() -> Unit): VectorFilter {
    val builder = FilterBuilder()
    builder.init()
    return builder.build()
}

/**
 * 필터 빌더
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
 * VectorStore 팩토리
 */
object VectorStoreFactory {
    
    fun createQdrant(
        host: String = "localhost",
        port: Int = 6333,
        apiKey: String? = null
    ): VectorStore {
        return QdrantVectorStore(host, port, apiKey)
    }
    
    // 향후 다른 벡터 DB 지원
    // fun createPinecone(...): VectorStore
    // fun createWeaviate(...): VectorStore
} 