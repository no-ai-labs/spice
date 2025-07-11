package io.github.spice

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * 🔍 VectorStore test cases
 */
class VectorStoreTest {

    private lateinit var vectorStore: VectorStore

    @BeforeEach
    fun setup() {
        // Use QdrantVectorStore for testing (can be mocked in real tests)
        vectorStore = QdrantVectorStore(
            host = "localhost",
            port = 6333,
            apiKey = null
        )
    }

    @Test
    @DisplayName("Vector document creation")
    fun testVectorDocumentCreation() {
        val document = VectorDocument(
            id = "test-doc-1",
            vector = listOf(0.1f, 0.2f, 0.3f),
            metadata = mapOf(
                "title" to JsonPrimitive("Test Document"),
                "category" to JsonPrimitive("test")
            )
        )

        assertEquals("test-doc-1", document.id)
        assertEquals(3, document.vector.size)
        assertEquals(0.1f, document.vector[0])
        assertEquals("Test Document", document.metadata["title"]?.toString()?.trim('"'))
    }

    @Test
    @DisplayName("Vector filter creation")
    fun testVectorFilterCreation() {
        val filter = VectorFilter(
            must = listOf(
                FilterCondition.Equals("category", JsonPrimitive("test")),
                FilterCondition.Range("score", gte = 0.5f, lte = 1.0f)
            ),
            mustNot = listOf(
                FilterCondition.Equals("status", JsonPrimitive("deleted"))
            ),
            should = listOf(
                FilterCondition.Match("title", "important")
            )
        )

        assertEquals(2, filter.must.size)
        assertEquals(1, filter.mustNot.size)
        assertEquals(1, filter.should.size)
    }

    @Test
    @DisplayName("Distance metric conversion")
    fun testDistanceMetricConversion() {
        assertEquals("Cosine", DistanceMetric.COSINE.toQdrantDistance())
        assertEquals("Euclid", DistanceMetric.EUCLIDEAN.toQdrantDistance())
        assertEquals("Dot", DistanceMetric.DOT_PRODUCT.toQdrantDistance())

        assertEquals(DistanceMetric.COSINE, "Cosine".fromQdrantDistance())
        assertEquals(DistanceMetric.EUCLIDEAN, "Euclid".fromQdrantDistance())
        assertEquals(DistanceMetric.DOT_PRODUCT, "Dot".fromQdrantDistance())
        assertEquals(DistanceMetric.COSINE, "Unknown".fromQdrantDistance()) // Default
    }

    @Test
    @DisplayName("Filter condition conversion to Qdrant")
    fun testFilterConditionConversion() {
        val equalsCondition = FilterCondition.Equals("field1", JsonPrimitive("value1"))
        val qdrantEquals = equalsCondition.toQdrantCondition()
        assertEquals("field1", qdrantEquals.key)
        assertNotNull(qdrantEquals.match)

        val rangeCondition = FilterCondition.Range("field2", gte = 0.1f, lte = 0.9f)
        val qdrantRange = rangeCondition.toQdrantCondition()
        assertEquals("field2", qdrantRange.key)
        assertNotNull(qdrantRange.range)
        assertEquals(0.1f, qdrantRange.range?.gte)
        assertEquals(0.9f, qdrantRange.range?.lte)
    }

    @Test
    @DisplayName("Vector filter to Qdrant filter conversion")
    fun testVectorFilterToQdrantFilter() {
        val vectorFilter = VectorFilter(
            must = listOf(FilterCondition.Equals("type", JsonPrimitive("document"))),
            mustNot = listOf(FilterCondition.Equals("deleted", JsonPrimitive("true"))),
            should = listOf(FilterCondition.Range("score", gte = 0.8f))
        )

        val qdrantFilter = vectorFilter.toQdrantFilter()

        assertNotNull(qdrantFilter.must)
        assertEquals(1, qdrantFilter.must?.size)
        
        assertNotNull(qdrantFilter.must_not)
        assertEquals(1, qdrantFilter.must_not?.size)
        
        assertNotNull(qdrantFilter.should)
        assertEquals(1, qdrantFilter.should?.size)
    }

    @Test
    @DisplayName("Collection info data structure")
    fun testCollectionInfo() {
        val collectionInfo = CollectionInfo(
            name = "test-collection",
            vectorSize = 384,
            pointsCount = 1000L,
            distance = DistanceMetric.COSINE,
            status = "green"
        )

        assertEquals("test-collection", collectionInfo.name)
        assertEquals(384, collectionInfo.vectorSize)
        assertEquals(1000L, collectionInfo.pointsCount)
        assertEquals(DistanceMetric.COSINE, collectionInfo.distance)
        assertEquals("green", collectionInfo.status)
    }

    @Test
    @DisplayName("Vector operation result")
    fun testVectorOperationResult() {
        val successResult = VectorOperationResult(
            success = true,
            message = "Operation completed",
            affectedCount = 5,
            operationId = "op-123"
        )

        assertTrue(successResult.success)
        assertEquals("Operation completed", successResult.message)
        assertEquals(5, successResult.affectedCount)
        assertEquals("op-123", successResult.operationId)

        val failureResult = VectorOperationResult(
            success = false,
            message = "Operation failed",
            affectedCount = 0
        )

        assertFalse(failureResult.success)
        assertEquals("Operation failed", failureResult.message)
        assertEquals(0, failureResult.affectedCount)
    }
}

/**
 * 🏗️ Filter builder test cases
 */
class FilterBuilderTest {

    @Test
    @DisplayName("Filter builder DSL")
    fun testFilterBuilderDSL() {
        val filter = buildFilter {
            equals("category", "test")
            range("score", min = 0.5f, max = 1.0f)
            must(FilterCondition.Match("title", "important"))
            mustNot(FilterCondition.Equals("status", JsonPrimitive("archived")))
            should(FilterCondition.In("tags", listOf(JsonPrimitive("urgent"))))
        }

        assertTrue(filter.must.size >= 3) // equals, range, and explicit must
        assertEquals(1, filter.mustNot.size)
        assertEquals(1, filter.should.size)
    }

    @Test
    @DisplayName("Filter builder with empty conditions")
    fun testFilterBuilderEmpty() {
        val filter = buildFilter {
            // No conditions added
        }

        assertTrue(filter.must.isEmpty())
        assertTrue(filter.mustNot.isEmpty())
        assertTrue(filter.should.isEmpty())
    }

    @Test
    @DisplayName("Filter builder method chaining")
    fun testFilterBuilderChaining() {
        val builder = FilterBuilder()
        
        builder.equals("field1", "value1")
        builder.range("field2", min = 0.0f, max = 1.0f)
        builder.must(FilterCondition.Match("field3", "pattern"))

        val filter = builder.build()

        assertTrue(filter.must.size >= 3)
        assertTrue(filter.mustNot.isEmpty())
        assertTrue(filter.should.isEmpty())
    }
}

/**
 * 🏭 VectorStore factory test cases  
 */
class VectorStoreFactoryTest {

    @Test
    @DisplayName("Create Qdrant vector store")
    fun testCreateQdrantVectorStore() {
        val vectorStore = VectorStoreFactory.createQdrant(
            host = "test-host",
            port = 6333,
            apiKey = "test-key"
        )

        assertNotNull(vectorStore)
        assertTrue(vectorStore is QdrantVectorStore)
    }

    @Test
    @DisplayName("Create Qdrant with defaults")
    fun testCreateQdrantWithDefaults() {
        val vectorStore = VectorStoreFactory.createQdrant()

        assertNotNull(vectorStore)
        assertTrue(vectorStore is QdrantVectorStore)
    }
}

/**
 * 🔧 Extension functions test cases
 */
class VectorStoreExtensionsTest {

    private lateinit var vectorStore: VectorStore

    @BeforeEach
    fun setup() {
        vectorStore = QdrantVectorStore()
    }

    @Test
    @DisplayName("Search text convenience function")
    fun testSearchTextExtension() = runTest {
        // Note: This will create a dummy embedding in the test implementation
        val results = vectorStore.searchText(
            collection = "test-collection",
            query = "test query",
            limit = 5
        )

        assertNotNull(results)
        assertTrue(results.size <= 5)
    }
}

/**
 * 📊 Qdrant data classes test cases
 */
class QdrantDataClassesTest {

    @Test
    @DisplayName("Qdrant point creation")
    fun testQdrantPointCreation() {
        val point = QdrantPoint(
            id = "point-1",
            vector = listOf(0.1f, 0.2f, 0.3f),
            payload = mapOf("key" to JsonPrimitive("value"))
        )

        assertEquals("point-1", point.id)
        assertEquals(3, point.vector.size)
        assertEquals("value", point.payload?.get("key")?.toString()?.trim('"'))
    }

    @Test
    @DisplayName("Qdrant search request creation")
    fun testQdrantSearchRequestCreation() {
        val searchRequest = QdrantSearchRequest(
            vector = listOf(0.1f, 0.2f, 0.3f),
            limit = 10,
            score_threshold = 0.8f,
            with_payload = true,
            with_vector = false
        )

        assertEquals(3, searchRequest.vector.size)
        assertEquals(10, searchRequest.limit)
        assertEquals(0.8f, searchRequest.score_threshold)
        assertTrue(searchRequest.with_payload)
        assertFalse(searchRequest.with_vector)
    }

    @Test
    @DisplayName("Qdrant collection config creation")
    fun testQdrantCollectionConfigCreation() {
        val config = QdrantCreateCollectionRequest(
            vectors = QdrantVectorConfig(
                size = 384,
                distance = "Cosine"
            )
        )

        assertEquals(384, config.vectors.size)
        assertEquals("Cosine", config.vectors.distance)
    }

    @Test
    @DisplayName("Qdrant response structures")
    fun testQdrantResponseStructures() {
        val response = QdrantResponse(
            status = "ok",
            time = 0.05f,
            result = QdrantOperationResult(
                operation_id = 12345L,
                status = "acknowledged"
            )
        )

        assertEquals("ok", response.status)
        assertEquals(0.05f, response.time)
        assertEquals(12345L, response.result?.operation_id)
        assertEquals("acknowledged", response.result?.status)
    }

    @Test
    @DisplayName("Qdrant search result structures")
    fun testQdrantSearchResultStructures() {
        val searchResult = QdrantSearchResult(
            id = "result-1",
            score = 0.95f,
            payload = mapOf("title" to JsonPrimitive("Test Document")),
            vector = listOf(0.1f, 0.2f, 0.3f)
        )

        assertEquals("result-1", searchResult.id)
        assertEquals(0.95f, searchResult.score)
        assertEquals("Test Document", searchResult.payload?.get("title")?.toString()?.trim('"'))
        assertEquals(3, searchResult.vector?.size)
    }
}

/**
 * 🔄 Mock VectorStore for testing
 */
class MockVectorStore : VectorStore {

    private val collections = mutableMapOf<String, MutableList<VectorDocument>>()

    override suspend fun upsert(
        collectionName: String,
        vectors: List<VectorDocument>
    ): VectorOperationResult {
        collections.getOrPut(collectionName) { mutableListOf() }.addAll(vectors)
        return VectorOperationResult(
            success = true,
            message = "Mock upsert successful",
            affectedCount = vectors.size
        )
    }

    override suspend fun search(
        collectionName: String,
        queryVector: List<Float>,
        topK: Int,
        filter: VectorFilter?,
        scoreThreshold: Float?
    ): List<VectorResult> {
        val docs = collections[collectionName] ?: return emptyList()
        
        return docs.take(topK).mapIndexed { index, doc ->
            VectorResult(
                id = doc.id,
                score = 1.0f - (index * 0.1f), // Mock score
                metadata = doc.metadata,
                vector = doc.vector
            )
        }
    }

    override suspend fun searchByText(
        collectionName: String,
        queryText: String,
        topK: Int,
        filter: VectorFilter?,
        scoreThreshold: Float?
    ): List<VectorResult> {
        // Mock implementation - just call regular search with dummy vector
        val dummyVector = List(384) { 0.5f }
        return search(collectionName, dummyVector, topK, filter, scoreThreshold)
    }

    override suspend fun delete(
        collectionName: String,
        ids: List<String>
    ): VectorOperationResult {
        val docs = collections[collectionName]
        if (docs != null) {
            docs.removeIf { it.id in ids }
        }
        return VectorOperationResult(
            success = true,
            message = "Mock delete successful",
            affectedCount = ids.size
        )
    }

    override suspend fun createCollection(
        collectionName: String,
        vectorSize: Int,
        distance: DistanceMetric
    ): VectorOperationResult {
        collections[collectionName] = mutableListOf()
        return VectorOperationResult(
            success = true,
            message = "Mock collection created"
        )
    }

    override suspend fun deleteCollection(collectionName: String): VectorOperationResult {
        collections.remove(collectionName)
        return VectorOperationResult(
            success = true,
            message = "Mock collection deleted"
        )
    }

    override suspend fun getCollectionInfo(collectionName: String): CollectionInfo? {
        return if (collections.containsKey(collectionName)) {
            CollectionInfo(
                name = collectionName,
                vectorSize = 384,
                pointsCount = collections[collectionName]?.size?.toLong() ?: 0L,
                distance = DistanceMetric.COSINE,
                status = "green"
            )
        } else null
    }

    override suspend fun healthCheck(): Boolean = true
}

/**
 * 🧪 Mock VectorStore test cases
 */
class MockVectorStoreTest {

    private lateinit var mockStore: MockVectorStore

    @BeforeEach
    fun setup() {
        mockStore = MockVectorStore()
    }

    @Test
    @DisplayName("Mock vector store operations")
    fun testMockVectorStoreOperations() = runTest {
        val collectionName = "test-collection"
        
        // Create collection
        val createResult = mockStore.createCollection(collectionName, 384)
        assertTrue(createResult.success)

        // Add documents
        val documents = listOf(
            VectorDocument("doc1", listOf(0.1f, 0.2f), mapOf("title" to JsonPrimitive("Doc 1"))),
            VectorDocument("doc2", listOf(0.3f, 0.4f), mapOf("title" to JsonPrimitive("Doc 2")))
        )
        
        val upsertResult = mockStore.upsert(collectionName, documents)
        assertTrue(upsertResult.success)
        assertEquals(2, upsertResult.affectedCount)

        // Search
        val searchResults = mockStore.search(collectionName, listOf(0.1f, 0.2f), 10)
        assertEquals(2, searchResults.size)
        assertTrue(searchResults[0].score > searchResults[1].score)

        // Get collection info
        val collectionInfo = mockStore.getCollectionInfo(collectionName)
        assertNotNull(collectionInfo)
        assertEquals(collectionName, collectionInfo?.name)
        assertEquals(2L, collectionInfo?.pointsCount)

        // Delete documents
        val deleteResult = mockStore.delete(collectionName, listOf("doc1"))
        assertTrue(deleteResult.success)

        // Verify deletion
        val afterDeleteResults = mockStore.search(collectionName, listOf(0.1f, 0.2f), 10)
        assertEquals(1, afterDeleteResults.size)

        // Health check
        assertTrue(mockStore.healthCheck())
    }
} 