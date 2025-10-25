# Feature Integration Guide

A comprehensive guide showing how to combine Spice Framework 0.4.1 features to build powerful, production-ready agentic systems.

## Table of Contents

1. [Overview](#overview)
2. [Integration Patterns](#integration-patterns)
   - [Multi-Tenant Knowledge Graph System](#multi-tenant-knowledge-graph-system)
   - [Cached API Gateway with Validation](#cached-api-gateway-with-validation)
   - [Evidence Pipeline with Citations](#evidence-pipeline-with-citations)
   - [Smart Query System](#smart-query-system)
   - [Multi-Stage Data Processing](#multi-stage-data-processing)
   - [Real-Time Analytics Dashboard](#real-time-analytics-dashboard)
3. [Architecture Patterns](#architecture-patterns)
4. [Best Practices](#best-practices)
5. [Performance Optimization](#performance-optimization)
6. [Testing Strategies](#testing-strategies)
7. [Production Deployment](#production-deployment)
8. [Complete Examples](#complete-examples)

## Overview

Spice Framework 0.4.1 introduces several powerful features that work seamlessly together:

- **Tool-Level Caching**: Intelligent caching with TTL, LRU eviction, and context-aware keys
- **Output Validation**: Declarative validation DSL for enforcing data quality
- **Named Graphs Extension**: Multi-tenant graph isolation and selection strategies
- **SPARQL Template Repository**: Centralized template management with Handlebars
- **Tool Pipeline DSL**: Fluent API for building complex data pipelines

This guide demonstrates how to combine these features effectively.

## Integration Patterns

### Multi-Tenant Knowledge Graph System

Combine **Named Graphs Extension** + **SPARQL Templates** + **Tool-Level Caching** for a complete multi-tenant knowledge graph solution.

#### Architecture

```
User Request → AgentContext → Named Graphs Extension → SPARQL Tool → Cached Results
                   ↓              ↓                        ↓              ↓
              tenant/user    auto-inject graphs     execute query    context-aware
              session data                                           cache key
```

#### Implementation

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.extensions.sparql.*
import io.github.noailabs.spice.performance.*

// Step 1: Register SPARQL templates
fun registerKnowledgeGraphTemplates() {
    SparqlTemplateRepository.registerCommonTemplates()

    // Custom template for product queries
    SparqlTemplateRepository.register("product-query") {
        template = """
            SELECT ?product ?name ?price ?category
            {{namedGraphsClause}}
            WHERE {
                ?product a :Product ;
                         :name ?name ;
                         :price ?price ;
                         :category ?category .
                {{#if categoryFilter}}
                FILTER(?category = "{{categoryFilter}}")
                {{/if}}
                {{#if minPrice}}
                FILTER(?price >= {{minPrice}})
                {{/if}}
            }
            ORDER BY {{#if orderBy}}?{{orderBy}}{{else}}?name{{/if}}
            {{#if limit}}LIMIT {{limit}}{{/if}}
        """

        prefixes = mapOf(
            ":" to "http://example.com/product#",
            "rdfs" to "http://www.w3.org/2000/01/rdf-schema#"
        )
    }

    // Template for hierarchical data
    SparqlTemplateRepository.register("category-hierarchy") {
        template = """
            SELECT ?category ?parent ?depth
            {{namedGraphsClause}}
            WHERE {
                ?category a :Category .
                OPTIONAL {
                    ?category :parentCategory ?parent .
                }
                BIND(
                    IF(BOUND(?parent),
                       (SELECT COUNT(?ancestor) WHERE {
                           ?category :parentCategory+ ?ancestor
                       }),
                       0
                    ) AS ?depth
                )
            }
            ORDER BY ?depth ?category
        """

        prefixes = mapOf(":" to "http://example.com/product#")
    }
}

// Step 2: Create named graphs extension with strategy
val namedGraphsExtension = NamedGraphsExtension(
    NamedGraphsStrategies.tenantWithShared(baseUri = "http://example.com/graphs/")
)
// This will auto-generate graphs like:
// - http://example.com/graphs/tenant-ACME
// - http://example.com/graphs/shared

// Step 3: Create cached SPARQL tools
val productQueryTool = contextAwareTool("query-products") {
    description = "Query products from tenant knowledge graph"

    parameters {
        string("categoryFilter", "Filter by category", required = false)
        number("minPrice", "Minimum price", required = false)
        string("orderBy", "Order results by field", required = false)
        number("limit", "Max results", required = false)
    }

    // Enable caching with tenant-aware keys
    cache {
        ttl = 1800 // 30 minutes
        maxSize = 500
        enableMetrics = true

        // Custom key builder includes tenant + params
        keyBuilder = { params, context ->
            val tenantId = context?.get("tenantId") as? String ?: "default"
            val category = params["categoryFilter"] as? String ?: "all"
            val price = params["minPrice"]?.toString() ?: "0"
            "$tenantId:products:$category:$price"
        }
    }

    // Validate output structure
    validate {
        requireField("results", "Query must return results array")
        fieldType("results", FieldType.ARRAY)

        custom("non-empty results") { output, _ ->
            val results = (output as? Map<*, *>)?.get("results") as? List<*>
            results?.isNotEmpty() == true
        }
    }

    sparql {
        endpoint = "https://neptune.example.com:8182/sparql"
        templateRef = "product-query" // Use registered template

        // Auto-inject named graphs from context
        useNamedGraphsFromContext = true
    }

    execute { params, context ->
        // Template variables from params
        val variables = mapOf(
            "categoryFilter" to params["categoryFilter"],
            "minPrice" to params["minPrice"],
            "orderBy" to params["orderBy"],
            "limit" to params["limit"]
        )

        // Execute SPARQL query (graphs auto-injected)
        executeSparql(variables, context)
    }
}

val categoryHierarchyTool = contextAwareTool("query-categories") {
    description = "Get category hierarchy for tenant"

    cache {
        ttl = 3600 // 1 hour (categories change less frequently)
        maxSize = 100
        keyBuilder = { _, context ->
            val tenantId = context?.get("tenantId") as? String ?: "default"
            "$tenantId:categories:hierarchy"
        }
    }

    validate {
        requireField("results")
        fieldType("results", FieldType.ARRAY)
    }

    sparql {
        endpoint = "https://neptune.example.com:8182/sparql"
        templateRef = "category-hierarchy"
        useNamedGraphsFromContext = true
    }

    execute { params, context ->
        executeSparql(emptyMap(), context)
    }
}

// Step 4: Create agent with extensions
val knowledgeGraphAgent = agent("kg-agent") {
    name = "Multi-Tenant Knowledge Graph Agent"
    description = "Query tenant-specific knowledge graphs with caching"

    // Add named graphs extension
    extension(namedGraphsExtension)

    tools {
        +productQueryTool
        +categoryHierarchyTool
    }

    systemPrompt = """
        You are a knowledge graph query assistant.
        You have access to a multi-tenant product catalog.
        Each tenant has isolated data in their own graph.
        Use the available tools to answer user queries about products and categories.
    """
}

// Step 5: Usage
suspend fun queryTenantProducts() {
    registerKnowledgeGraphTemplates()

    // Execute with tenant context
    withAgentContext(
        "tenantId" to "ACME-Corp",
        "userId" to "user-123",
        "sessionId" to "session-abc"
    ) {
        // First query (cache miss)
        val result1 = knowledgeGraphAgent.run(
            "Find all electronics under $500"
        )
        println(result1.content)
        // Named graphs auto-injected: http://example.com/graphs/tenant-ACME-Corp
        // Results cached with key: "ACME-Corp:products:electronics:500"

        // Second query (cache hit - same tenant, category, price)
        val result2 = knowledgeGraphAgent.run(
            "Show me electronics under $500 again"
        )
        println(result2.content)
        // Served from cache instantly!

        // Get category hierarchy (cached separately)
        val categories = knowledgeGraphAgent.run(
            "Show me the product category hierarchy"
        )
        println(categories.content)
    }

    // Different tenant - different cache + graphs
    withAgentContext("tenantId" to "TechStart") {
        val result = knowledgeGraphAgent.run(
            "Find all electronics under $500"
        )
        // Uses graph: http://example.com/graphs/tenant-TechStart
        // Cache key: "TechStart:products:electronics:500"
        // Completely isolated from ACME-Corp!
    }
}

// Step 6: Monitor cache performance
fun printCacheMetrics() {
    productQueryTool.cacheMetrics?.let { metrics ->
        println("""
            Product Query Tool Cache Metrics:
            - Total requests: ${metrics.hits + metrics.misses}
            - Cache hits: ${metrics.hits}
            - Cache misses: ${metrics.misses}
            - Hit rate: ${metrics.hitRate()}%
            - Current size: ${metrics.currentSize}
            - Evictions: ${metrics.evictions}
        """.trimIndent())
    }
}
```

#### Benefits

1. **Automatic Tenant Isolation**: Named graphs auto-selected based on context
2. **Performance**: Cache hit rates typically 70-90% for repeated queries
3. **Consistency**: Template repository ensures query consistency across tools
4. **Scalability**: LRU eviction prevents memory bloat
5. **Debugging**: Cache metrics track performance per tenant

#### Graph Selection Strategies

```kotlin
// Strategy 1: Tenant-only (strict isolation)
val tenantOnly = NamedGraphsExtension(
    NamedGraphsStrategies.tenantOnly("http://example.com/graphs/")
)
// Generates: ["http://example.com/graphs/tenant-{tenantId}"]

// Strategy 2: Tenant + Shared (common + private data)
val tenantWithShared = NamedGraphsExtension(
    NamedGraphsStrategies.tenantWithShared("http://example.com/graphs/")
)
// Generates: [
//   "http://example.com/graphs/tenant-{tenantId}",
//   "http://example.com/graphs/shared"
// ]

// Strategy 3: Hierarchical (tenant + user + session)
val hierarchical = NamedGraphsExtension(
    NamedGraphsStrategies.hierarchical("http://example.com/graphs/")
)
// Generates: [
//   "http://example.com/graphs/tenant-{tenantId}",
//   "http://example.com/graphs/tenant-{tenantId}/user-{userId}",
//   "http://example.com/graphs/tenant-{tenantId}/user-{userId}/session-{sessionId}"
// ]

// Strategy 4: Type-partitioned (different graphs for different data types)
val typePartitioned = NamedGraphsExtension(
    NamedGraphsStrategies.typePartitioned(
        baseUri = "http://example.com/graphs/",
        types = listOf("products", "customers", "orders")
    )
)
// Generates: [
//   "http://example.com/graphs/tenant-{tenantId}/products",
//   "http://example.com/graphs/tenant-{tenantId}/customers",
//   "http://example.com/graphs/tenant-{tenantId}/orders"
// ]

// Strategy 5: Custom strategy
val customStrategy = NamedGraphsExtension { context ->
    val tenantId = context.get("tenantId") as? String ?: "default"
    val region = context.get("region") as? String ?: "us-east-1"
    val env = context.get("environment") as? String ?: "prod"

    listOf(
        "http://example.com/graphs/$env/$region/tenant-$tenantId",
        "http://example.com/graphs/$env/shared"
    )
}
```

---

### Cached API Gateway with Validation

Combine **Tool-Level Caching** + **Output Validation** + **Pipeline DSL** for a robust API gateway pattern.

#### Architecture

```
External API Request → Cache Check → API Call → Validate → Transform → Cache Store
                           ↓            ↓          ↓           ↓           ↓
                       cache hit?   rate limit  schema     normalize   context-aware
                                    retry       check      data        key
```

#### Implementation

```kotlin
// Step 1: Create cached API tool with validation
val weatherApiTool = contextAwareTool("weather-api") {
    description = "Fetch weather data from external API"

    parameters {
        string("city", "City name", required = true)
        string("units", "Temperature units (metric/imperial)", required = false)
    }

    // Cache configuration
    cache {
        ttl = 900 // 15 minutes (weather changes slowly)
        maxSize = 1000
        enableMetrics = true

        keyBuilder = { params, context ->
            val city = params["city"] as? String ?: ""
            val units = params["units"] as? String ?: "metric"
            val userId = context?.get("userId") as? String ?: "anonymous"
            "weather:$city:$units:$userId"
        }
    }

    // Validate API response
    validate {
        // Required fields
        requireField("temperature", "Temperature is required")
        requireField("humidity", "Humidity is required")
        requireField("conditions", "Weather conditions are required")
        requireField("timestamp", "Timestamp is required")

        // Type validation
        fieldType("temperature", FieldType.NUMBER)
        fieldType("humidity", FieldType.NUMBER)
        fieldType("conditions", FieldType.STRING)
        fieldType("timestamp", FieldType.NUMBER)

        // Range validation
        range("temperature", min = -100.0, max = 60.0,
              message = "Temperature out of valid range")
        range("humidity", min = 0.0, max = 100.0,
              message = "Humidity must be 0-100%")

        // Pattern validation for city
        pattern("city", Regex("^[a-zA-Z\\s-]+$"),
                message = "Invalid city name format")

        // Custom validation
        rule("timestamp is recent") { output, _ ->
            val data = output as? Map<*, *>
            val timestamp = data?.get("timestamp") as? Long ?: 0L
            val now = System.currentTimeMillis() / 1000
            val age = now - timestamp
            age < 3600 // Must be within last hour
        }

        rule("conditions is valid") { output, _ ->
            val data = output as? Map<*, *>
            val conditions = data?.get("conditions") as? String ?: ""
            conditions in listOf("sunny", "cloudy", "rainy", "snowy", "foggy")
        }
    }

    execute { params, context ->
        val city = params["city"] as String
        val units = params["units"] as? String ?: "metric"

        try {
            // Call external API
            val response = httpClient.get("https://api.weather.com/v3/current") {
                parameter("city", city)
                parameter("units", units)
                parameter("apiKey", System.getenv("WEATHER_API_KEY"))
            }

            val data = response.bodyAsJson()

            // Transform to standard format
            val result = mapOf(
                "city" to city,
                "temperature" to data["temp"],
                "humidity" to data["humidity"],
                "conditions" to data["weather"],
                "timestamp" to (System.currentTimeMillis() / 1000),
                "units" to units
            )

            ToolResult.success(result)
        } catch (e: Exception) {
            ToolResult.failure("Weather API error: ${e.message}")
        }
    }
}

val geocodingApiTool = contextAwareTool("geocoding-api") {
    description = "Convert city name to coordinates"

    parameters {
        string("city", "City name", required = true)
    }

    cache {
        ttl = 86400 // 24 hours (coordinates don't change)
        maxSize = 5000

        keyBuilder = { params, _ ->
            val city = params["city"] as? String ?: ""
            "geocode:$city"
        }
    }

    validate {
        requireField("latitude")
        requireField("longitude")
        fieldType("latitude", FieldType.NUMBER)
        fieldType("longitude", FieldType.NUMBER)

        range("latitude", min = -90.0, max = 90.0)
        range("longitude", min = -180.0, max = 180.0)
    }

    execute { params, context ->
        val city = params["city"] as String

        // Call geocoding API
        val response = httpClient.get("https://api.geocode.com/v1/search") {
            parameter("q", city)
            parameter("apiKey", System.getenv("GEOCODE_API_KEY"))
        }

        val data = response.bodyAsJson()
        val result = mapOf(
            "city" to city,
            "latitude" to data["lat"],
            "longitude" to data["lon"],
            "country" to data["country"]
        )

        ToolResult.success(result)
    }
}

// Step 2: Create pipeline combining tools
val weatherPipeline = toolChain("weather-pipeline") {
    name = "Weather Data Pipeline"
    description = "Fetch and enrich weather data"
    debugEnabled = false

    // Step 1: Geocode city
    +step(geocodingApiTool)
        .named("geocode")
        .output("coordinates")
        .input { context ->
            val city = context.sharedData["city"] as? String ?: ""
            mapOf("city" to city)
        }

    // Step 2: Fetch weather
    +step(weatherApiTool)
        .named("weather")
        .output("weather")
        .input { context ->
            val city = context.sharedData["city"] as? String ?: ""
            val units = context.sharedData["units"] as? String ?: "metric"
            mapOf("city" to city, "units" to units)
        }

    // Step 3: Combine results
    +step(contextAwareTool("combine-weather-data") {
        description = "Combine weather and location data"

        execute { params, context ->
            val coordinates = context.requireOutputOf("coordinates") as Map<*, *>
            val weather = context.requireOutputOf("weather") as Map<*, *>

            val combined = mapOf(
                "city" to weather["city"],
                "temperature" to weather["temperature"],
                "conditions" to weather["conditions"],
                "humidity" to weather["humidity"],
                "latitude" to coordinates["latitude"],
                "longitude" to coordinates["longitude"],
                "country" to coordinates["country"],
                "timestamp" to weather["timestamp"],
                "units" to weather["units"]
            )

            ToolResult.success(combined)
        }
    }).named("combine").output("result")
}

// Step 3: Usage
suspend fun fetchWeatherData() {
    withAgentContext(
        "userId" to "user-456",
        "requestId" to "req-123"
    ) {
        // Execute pipeline
        val result = weatherPipeline.execute(mapOf(
            "city" to "San Francisco",
            "units" to "metric"
        ))

        if (result.success) {
            val weatherData = result.getStepOutput("result")
            println(weatherData)
            // Output:
            // {
            //   "city": "San Francisco",
            //   "temperature": 18.5,
            //   "conditions": "foggy",
            //   "humidity": 75,
            //   "latitude": 37.7749,
            //   "longitude": -122.4194,
            //   "country": "US",
            //   "timestamp": 1234567890,
            //   "units": "metric"
            // }
        }
    }
}

// Step 4: Monitor cache performance
fun monitorApiGateway() {
    weatherApiTool.cacheMetrics?.let { metrics ->
        println("Weather API Cache Hit Rate: ${metrics.hitRate()}%")
        println("Geocoding API Cache Hit Rate: ${geocodingApiTool.cacheMetrics?.hitRate()}%")

        // Calculate cost savings
        val weatherHits = metrics.hits.get()
        val costPerRequest = 0.001 // $0.001 per API call
        val savings = weatherHits * costPerRequest
        println("Weather API Cost Savings: $$savings")
    }
}
```

#### Benefits

1. **Cost Reduction**: Cache hit rates of 80%+ reduce API costs by 80%
2. **Reliability**: Validation catches malformed API responses before they propagate
3. **Performance**: Cached responses return in &lt;1ms vs. 200-500ms for API calls
4. **Type Safety**: Output validation ensures downstream consumers get expected data
5. **Observability**: Cache metrics track API gateway health

---

### Evidence Pipeline with Citations

Combine **Pipeline DSL** + **Output Validation** + **Caching** for an AI-powered evidence retrieval system with citation tracking.

#### Architecture

```
Query → Search → Extract → Validate → Rank → Format → Cached Result
  ↓       ↓        ↓         ↓         ↓       ↓          ↓
input  vector    entities  citations  score   JSON    context-aware
       search              required            Evidence  cache key
```

#### Implementation

```kotlin
// Step 1: Define Evidence data model
data class Evidence(
    val claim: String,
    val sources: List<Source>,
    val confidence: Double,
    val extractedAt: Long
)

data class Source(
    val url: String,
    val title: String,
    val excerpt: String,
    val relevanceScore: Double
)

// Step 2: Create validated evidence extraction tool
val extractEvidenceTool = contextAwareTool("extract-evidence") {
    description = "Extract evidence from text with citations"

    parameters {
        string("text", "Source text", required = true)
        string("claim", "Claim to verify", required = true)
    }

    // Cache evidence extraction results
    cache {
        ttl = 3600 // 1 hour
        maxSize = 500

        keyBuilder = { params, context ->
            val claim = params["claim"] as? String ?: ""
            val textHash = (params["text"] as? String ?: "").hashCode()
            "evidence:$claim:$textHash"
        }
    }

    // CRITICAL: Validate citation structure
    validate {
        // Required top-level fields
        requireField("claim", "Claim is required")
        requireField("sources", "Sources array is required")
        requireField("confidence", "Confidence score is required")

        // Type validation
        fieldType("claim", FieldType.STRING)
        fieldType("sources", FieldType.ARRAY)
        fieldType("confidence", FieldType.NUMBER)

        // Range validation
        range("confidence", min = 0.0, max = 1.0,
              message = "Confidence must be between 0 and 1")

        // Custom validation: sources array structure
        rule("sources must be non-empty") { output, _ ->
            val data = output as? Map<*, *>
            val sources = data?.get("sources") as? List<*>
            sources?.isNotEmpty() == true
        }

        rule("each source must have required fields") { output, _ ->
            val data = output as? Map<*, *>
            val sources = data?.get("sources") as? List<*>

            sources?.all { source ->
                val s = source as? Map<*, *>
                s?.containsKey("url") == true &&
                s.containsKey("title") == true &&
                s.containsKey("excerpt") == true &&
                s.containsKey("relevanceScore") == true
            } == true
        }

        rule("source URLs must be valid") { output, _ ->
            val data = output as? Map<*, *>
            val sources = data?.get("sources") as? List<*>

            sources?.all { source ->
                val url = (source as? Map<*, *>)?.get("url") as? String
                url?.matches(Regex("^https?://.*")) == true
            } == true
        }

        rule("relevance scores must be valid") { output, _ ->
            val data = output as? Map<*, *>
            val sources = data?.get("sources") as? List<*>

            sources?.all { source ->
                val score = (source as? Map<*, *>)?.get("relevanceScore") as? Number
                val scoreValue = score?.toDouble() ?: -1.0
                scoreValue in 0.0..1.0
            } == true
        }
    }

    execute { params, context ->
        val text = params["text"] as String
        val claim = params["claim"] as String

        // Use LLM to extract evidence
        val llmResponse = llmClient.complete(
            prompt = """
                Analyze the following text and extract evidence for the claim.
                Provide citations with URLs, titles, and relevant excerpts.

                Claim: $claim

                Text: $text

                Return a JSON object with:
                - claim: The original claim
                - sources: Array of source objects with url, title, excerpt, relevanceScore
                - confidence: Overall confidence score (0-1)
            """,
            model = "claude-3-5-sonnet-20241022"
        )

        // Parse LLM response
        val evidence = Json.decodeFromString<Map<String, Any>>(llmResponse.content)

        ToolResult.success(evidence)
    }
}

val searchDocumentsTool = contextAwareTool("search-documents") {
    description = "Search document corpus for relevant sources"

    parameters {
        string("query", "Search query", required = true)
        number("limit", "Max results", required = false)
    }

    cache {
        ttl = 1800 // 30 minutes
        maxSize = 1000

        keyBuilder = { params, context ->
            val query = params["query"] as? String ?: ""
            val limit = params["limit"]?.toString() ?: "10"
            val tenantId = context?.get("tenantId") as? String ?: "default"
            "search:$tenantId:$query:$limit"
        }
    }

    validate {
        requireField("documents")
        fieldType("documents", FieldType.ARRAY)

        rule("documents must contain required fields") { output, _ ->
            val data = output as? Map<*, *>
            val docs = data?.get("documents") as? List<*>

            docs?.all { doc ->
                val d = doc as? Map<*, *>
                d?.containsKey("id") == true &&
                d.containsKey("content") == true &&
                d.containsKey("metadata") == true
            } == true
        }
    }

    execute { params, context ->
        val query = params["query"] as String
        val limit = (params["limit"] as? Number)?.toInt() ?: 10

        // Vector search in document corpus
        val results = vectorStore.search(query, limit)

        ToolResult.success(mapOf(
            "documents" to results.map { doc ->
                mapOf(
                    "id" to doc.id,
                    "content" to doc.content,
                    "metadata" to doc.metadata,
                    "score" to doc.score
                )
            }
        ))
    }
}

val rankSourcesTool = contextAwareTool("rank-sources") {
    description = "Rank sources by relevance and credibility"

    parameters {
        // Expects sources array from previous step
    }

    validate {
        requireField("rankedSources")
        fieldType("rankedSources", FieldType.ARRAY)

        rule("sources ordered by score descending") { output, _ ->
            val data = output as? Map<*, *>
            val sources = data?.get("rankedSources") as? List<*>

            val scores = sources?.mapNotNull { source ->
                ((source as? Map<*, *>)?.get("relevanceScore") as? Number)?.toDouble()
            } ?: emptyList()

            scores.zipWithNext().all { (a, b) -> a >= b }
        }
    }

    execute { params, context ->
        val sources = params["sources"] as List<Map<*, *>>

        // Rank by relevance score (descending)
        val ranked = sources.sortedByDescending { source ->
            (source["relevanceScore"] as? Number)?.toDouble() ?: 0.0
        }

        ToolResult.success(mapOf("rankedSources" to ranked))
    }
}

// Step 3: Create evidence pipeline
val evidencePipeline = toolChain("evidence-pipeline") {
    name = "Evidence Retrieval Pipeline"
    description = "Search, extract, validate, and rank evidence with citations"
    debugEnabled = false

    // Step 1: Search for relevant documents
    +step(searchDocumentsTool)
        .named("search")
        .output("searchResults")
        .input { context ->
            val query = context.sharedData["query"] as? String ?: ""
            mapOf("query" to query, "limit" to 20)
        }

    // Step 2: Extract evidence from documents
    +step(extractEvidenceTool)
        .named("extract")
        .output("evidence")
        .input { context ->
            val query = context.sharedData["query"] as? String ?: ""
            val documents = (context.requireOutputOf("searchResults") as Map<*, *>)["documents"] as List<*>

            // Combine all document content
            val combinedText = documents.joinToString("\n\n") { doc ->
                (doc as Map<*, *>)["content"] as String
            }

            mapOf(
                "claim" to query,
                "text" to combinedText
            )
        }

    // Step 3: Rank sources by relevance
    +step(rankSourcesTool)
        .named("rank")
        .output("rankedEvidence")
        .input { context ->
            val evidence = context.requireOutputOf("evidence") as Map<*, *>
            val sources = evidence["sources"] as List<Map<*, *>>
            mapOf("sources" to sources)
        }

    // Step 4: Format final output
    +step(contextAwareTool("format-evidence") {
        description = "Format evidence as JSON with metadata"

        validate {
            requireField("evidence")
            requireField("metadata")

            fieldType("evidence", FieldType.OBJECT)
            fieldType("metadata", FieldType.OBJECT)

            // Ensure evidence has citations
            rule("evidence must have citations") { output, _ ->
                val data = output as? Map<*, *>
                val evidence = data?.get("evidence") as? Map<*, *>
                val sources = evidence?.get("sources") as? List<*>
                sources?.isNotEmpty() == true
            }
        }

        execute { params, context ->
            val evidence = context.requireOutputOf("evidence") as Map<*, *>
            val rankedSources = (context.requireOutputOf("rankedEvidence") as Map<*, *>)["rankedSources"] as List<*>

            val formatted = mapOf(
                "evidence" to mapOf(
                    "claim" to evidence["claim"],
                    "sources" to rankedSources.take(5), // Top 5 sources
                    "confidence" to evidence["confidence"]
                ),
                "metadata" to mapOf(
                    "query" to context.sharedData["query"],
                    "extractedAt" to System.currentTimeMillis(),
                    "sourceCount" to rankedSources.size,
                    "pipelineVersion" to "1.0"
                )
            )

            ToolResult.success(formatted)
        }
    }).named("format").output("result")
}

// Step 4: Usage
suspend fun retrieveEvidence() {
    withAgentContext(
        "tenantId" to "research-org",
        "userId" to "researcher-789"
    ) {
        val result = evidencePipeline.execute(mapOf(
            "query" to "Climate change impacts on ocean temperatures"
        ))

        if (result.success) {
            val evidence = result.getStepOutput("result") as Map<*, *>
            println(Json.encodeToString(evidence))

            // Output example:
            // {
            //   "evidence": {
            //     "claim": "Climate change impacts on ocean temperatures",
            //     "sources": [
            //       {
            //         "url": "https://ipcc.ch/report/ar6",
            //         "title": "IPCC AR6 Climate Change Report",
            //         "excerpt": "Ocean temperatures have risen by 0.88°C...",
            //         "relevanceScore": 0.95
            //       },
            //       {
            //         "url": "https://nature.com/articles/s41586-023-12345",
            //         "title": "Global Ocean Warming Trends",
            //         "excerpt": "Analysis of satellite data shows...",
            //         "relevanceScore": 0.89
            //       }
            //     ],
            //     "confidence": 0.92
            //   },
            //   "metadata": {
            //     "query": "Climate change impacts on ocean temperatures",
            //     "extractedAt": 1234567890,
            //     "sourceCount": 15,
            //     "pipelineVersion": "1.0"
            //   }
            // }
        } else {
            println("Pipeline failed:")
            result.stepResults.forEach { stepResult ->
                if (!stepResult.success) {
                    println("  ${stepResult.stepId}: ${stepResult.error}")
                }
            }
        }
    }
}
```

#### Benefits

1. **Citation Integrity**: Validation ensures every evidence claim has proper citations
2. **Performance**: Caching prevents redundant LLM calls and vector searches
3. **Reliability**: Failed validation blocks propagation of incomplete evidence
4. **Traceability**: Metadata tracking enables audit trails
5. **Quality**: Multi-stage validation (extraction + ranking + formatting) ensures high-quality output

---

### Smart Query System

Combine **SPARQL Templates** + **Caching** + **Validation** for an intelligent query system with natural language interface.

#### Implementation

```kotlin
// Step 1: Register query templates
fun registerQueryTemplates() {
    SparqlTemplateRepository.registerCommonTemplates()

    // User profile query
    SparqlTemplateRepository.register("user-profile") {
        template = """
            SELECT ?user ?name ?email ?role ?department
            {{namedGraphsClause}}
            WHERE {
                ?user a :User ;
                      :id "{{userId}}" ;
                      :name ?name ;
                      :email ?email ;
                      :role ?role .
                OPTIONAL { ?user :department ?department }
            }
        """
        prefixes = mapOf(":" to "http://example.com/user#")
    }

    // Activity log query
    SparqlTemplateRepository.register("user-activities") {
        template = """
            SELECT ?activity ?action ?timestamp ?resource
            {{namedGraphsClause}}
            WHERE {
                ?activity a :Activity ;
                          :user <{{userUri}}> ;
                          :action ?action ;
                          :timestamp ?timestamp ;
                          :resource ?resource .
                {{#if startDate}}
                FILTER(?timestamp >= "{{startDate}}"^^xsd:dateTime)
                {{/if}}
                {{#if endDate}}
                FILTER(?timestamp <= "{{endDate}}"^^xsd:dateTime)
                {{/if}}
                {{#if actionType}}
                FILTER(?action = "{{actionType}}")
                {{/if}}
            }
            ORDER BY DESC(?timestamp)
            {{#if limit}}LIMIT {{limit}}{{/if}}
        """
        prefixes = mapOf(
            ":" to "http://example.com/activity#",
            "xsd" to "http://www.w3.org/2001/XMLSchema#"
        )
    }

    // Aggregation query
    SparqlTemplateRepository.register("activity-summary") {
        template = """
            SELECT ?action (COUNT(?activity) AS ?count)
            {{namedGraphsClause}}
            WHERE {
                ?activity a :Activity ;
                          :user <{{userUri}}> ;
                          :action ?action ;
                          :timestamp ?timestamp .
                {{#if startDate}}
                FILTER(?timestamp >= "{{startDate}}"^^xsd:dateTime)
                {{/if}}
                {{#if endDate}}
                FILTER(?timestamp <= "{{endDate}}"^^xsd:dateTime)
                {{/if}}
            }
            GROUP BY ?action
            ORDER BY DESC(?count)
        """
        prefixes = mapOf(
            ":" to "http://example.com/activity#",
            "xsd" to "http://www.w3.org/2001/XMLSchema#"
        )
    }
}

// Step 2: Create query tools with caching + validation
val queryUserProfileTool = contextAwareTool("query-user-profile") {
    description = "Get user profile information"

    parameters {
        string("userId", "User ID", required = true)
    }

    cache {
        ttl = 600 // 10 minutes
        maxSize = 1000

        keyBuilder = { params, context ->
            val userId = params["userId"] as? String ?: ""
            val tenantId = context?.get("tenantId") as? String ?: "default"
            "profile:$tenantId:$userId"
        }
    }

    validate {
        requireField("user")
        requireField("name")
        requireField("email")
        requireField("role")

        fieldType("user", FieldType.STRING)
        fieldType("name", FieldType.STRING)
        fieldType("email", FieldType.STRING)
        fieldType("role", FieldType.STRING)

        pattern("email", Regex("^[^@]+@[^@]+\\.[^@]+$"),
                message = "Invalid email format")
    }

    sparql {
        endpoint = "https://neptune.example.com:8182/sparql"
        templateRef = "user-profile"
        useNamedGraphsFromContext = true
    }

    execute { params, context ->
        val userId = params["userId"] as String
        executeSparql(mapOf("userId" to userId), context)
    }
}

val queryUserActivitiesTool = contextAwareTool("query-user-activities") {
    description = "Get user activity log"

    parameters {
        string("userId", "User ID", required = true)
        string("startDate", "Start date (ISO 8601)", required = false)
        string("endDate", "End date (ISO 8601)", required = false)
        string("actionType", "Filter by action type", required = false)
        number("limit", "Max results", required = false)
    }

    cache {
        ttl = 300 // 5 minutes (activities update frequently)
        maxSize = 500

        keyBuilder = { params, context ->
            val userId = params["userId"] as? String ?: ""
            val start = params["startDate"] as? String ?: "all"
            val end = params["endDate"] as? String ?: "now"
            val action = params["actionType"] as? String ?: "all"
            val tenantId = context?.get("tenantId") as? String ?: "default"
            "activities:$tenantId:$userId:$start:$end:$action"
        }
    }

    validate {
        requireField("activities")
        fieldType("activities", FieldType.ARRAY)

        rule("activities have required fields") { output, _ ->
            val data = output as? Map<*, *>
            val activities = data?.get("activities") as? List<*>

            activities?.all { activity ->
                val a = activity as? Map<*, *>
                a?.containsKey("action") == true &&
                a.containsKey("timestamp") == true &&
                a.containsKey("resource") == true
            } == true
        }
    }

    sparql {
        endpoint = "https://neptune.example.com:8182/sparql"
        templateRef = "user-activities"
        useNamedGraphsFromContext = true
    }

    execute { params, context ->
        val userId = params["userId"] as String
        val userUri = "http://example.com/user#$userId"

        val variables = mapOf(
            "userUri" to userUri,
            "startDate" to params["startDate"],
            "endDate" to params["endDate"],
            "actionType" to params["actionType"],
            "limit" to params["limit"]
        )

        executeSparql(variables, context)
    }
}

val queryActivitySummaryTool = contextAwareTool("query-activity-summary") {
    description = "Get aggregated activity summary"

    parameters {
        string("userId", "User ID", required = true)
        string("startDate", "Start date (ISO 8601)", required = false)
        string("endDate", "End date (ISO 8601)", required = false)
    }

    cache {
        ttl = 1800 // 30 minutes (aggregates change slowly)
        maxSize = 200

        keyBuilder = { params, context ->
            val userId = params["userId"] as? String ?: ""
            val start = params["startDate"] as? String ?: "all"
            val end = params["endDate"] as? String ?: "now"
            val tenantId = context?.get("tenantId") as? String ?: "default"
            "summary:$tenantId:$userId:$start:$end"
        }
    }

    validate {
        requireField("summary")
        fieldType("summary", FieldType.ARRAY)

        rule("summary has counts") { output, _ ->
            val data = output as? Map<*, *>
            val summary = data?.get("summary") as? List<*>

            summary?.all { item ->
                val i = item as? Map<*, *>
                i?.containsKey("action") == true &&
                i.containsKey("count") == true
            } == true
        }
    }

    sparql {
        endpoint = "https://neptune.example.com:8182/sparql"
        templateRef = "activity-summary"
        useNamedGraphsFromContext = true
    }

    execute { params, context ->
        val userId = params["userId"] as String
        val userUri = "http://example.com/user#$userId"

        val variables = mapOf(
            "userUri" to userUri,
            "startDate" to params["startDate"],
            "endDate" to params["endDate"]
        )

        executeSparql(variables, context)
    }
}

// Step 3: Create natural language query agent
val queryAgent = agent("query-agent") {
    name = "Smart Query Agent"
    description = "Natural language interface to knowledge graph"

    // Add named graphs extension
    extension(NamedGraphsExtension(
        NamedGraphsStrategies.tenantWithShared("http://example.com/graphs/")
    ))

    tools {
        +queryUserProfileTool
        +queryUserActivitiesTool
        +queryActivitySummaryTool
    }

    systemPrompt = """
        You are a query assistant with access to a knowledge graph.
        Users can ask questions in natural language, and you will:
        1. Determine which query tool to use
        2. Extract parameters from the user's question
        3. Execute the appropriate query
        4. Format results in a user-friendly way

        Available queries:
        - User profiles (name, email, role, department)
        - User activities (action logs with timestamps)
        - Activity summaries (aggregated statistics)

        All queries are tenant-scoped and cached for performance.
    """
}

// Step 4: Usage
suspend fun runSmartQueries() {
    registerQueryTemplates()

    withAgentContext(
        "tenantId" to "ACME-Corp",
        "userId" to "admin-001"
    ) {
        // Natural language queries

        // Query 1: User profile
        val profile = queryAgent.run(
            "Tell me about user john.doe"
        )
        println(profile.content)
        // Cache miss → SPARQL query → cache store

        // Query 2: Same user (cache hit)
        val profileAgain = queryAgent.run(
            "Show me john.doe's profile again"
        )
        println(profileAgain.content)
        // Cache hit → instant response

        // Query 3: Activities
        val activities = queryAgent.run(
            "What did user john.doe do in the last 7 days?"
        )
        println(activities.content)
        // Automatically adds date filters

        // Query 4: Aggregation
        val summary = queryAgent.run(
            "Summarize john.doe's activities this month"
        )
        println(summary.content)
        // Aggregation query with date range
    }

    // Different tenant - isolated cache and graphs
    withAgentContext("tenantId" to "StartupXYZ") {
        val profile = queryAgent.run("Show me user jane.smith")
        // Different tenant → different named graph → different cache
    }
}
```

#### Benefits

1. **Natural Language Interface**: Users query in plain English, not SPARQL
2. **Performance**: Cache hit rates 60-80% for common queries
3. **Consistency**: Templates ensure query structure correctness
4. **Multi-Tenancy**: Automatic tenant isolation via named graphs
5. **Data Quality**: Validation ensures query results have expected structure

---

## Architecture Patterns

### Layered Architecture

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Natural Language Interface / API)     │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│         Orchestration Layer             │
│    (Agents, Tool Chains, Pipelines)     │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│           Tool Layer                    │
│  (Context-Aware Tools with DSL blocks)  │
│   • cache { }                           │
│   • validate { }                        │
│   • sparql { }                          │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│         Infrastructure Layer            │
│  (Caching, Validation, SPARQL Engine)   │
│   • CachedTool                          │
│   • OutputValidator                     │
│   • SparqlTemplateRepository            │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│          Data Layer                     │
│  (RDF Store, Vector DB, External APIs)  │
└─────────────────────────────────────────┘
```

### Multi-Tenant Architecture

```
┌───────────────────────────────────────────────────┐
│              Request with AgentContext            │
│  { tenantId: "ACME", userId: "u123", ... }       │
└───────────────────────────────────────────────────┘
                        ↓
        ┌───────────────┴───────────────┐
        ↓                               ↓
┌───────────────┐              ┌────────────────┐
│ Named Graphs  │              │  Cache Keys    │
│  Extension    │              │   with Tenant  │
└───────────────┘              └────────────────┘
        ↓                               ↓
  Auto-inject:                  "tenant:ACME:..."
  - tenant-ACME                        ↓
  - shared                     Isolated cache
        ↓                          per tenant
   SPARQL Query                        ↓
        ↓                          Cache hit/miss
   Tenant-specific                     ↓
      results                      Return result
```

### Caching Strategy Layers

```
┌────────────────────────────────────────┐
│         L1: Tool-Level Cache           │
│  • Context-aware keys                  │
│  • TTL: 5-30 minutes                   │
│  • Size: 100-1000 entries              │
│  • Hit rate: 70-90%                    │
└────────────────────────────────────────┘
                 ↓ (on miss)
┌────────────────────────────────────────┐
│      L2: Template Result Cache         │
│  • Template + params hash              │
│  • TTL: 1-24 hours                     │
│  • Size: 500-5000 entries              │
│  • Hit rate: 40-60%                    │
└────────────────────────────────────────┘
                 ↓ (on miss)
┌────────────────────────────────────────┐
│       L3: Database Query Cache         │
│  • RDF store / Vector DB cache         │
│  • TTL: Hours to days                  │
│  • Hit rate: 20-40%                    │
└────────────────────────────────────────┘
                 ↓ (on miss)
┌────────────────────────────────────────┐
│         Data Source                    │
│  • RDF triple store (Neptune)          │
│  • Vector database (Pinecone)          │
│  • External APIs                       │
└────────────────────────────────────────┘
```

---

## Best Practices

### 1. Cache Key Design

**DO:**
```kotlin
// Include all relevant context
keyBuilder = { params, context ->
    val tenantId = context?.get("tenantId") as? String ?: "default"
    val userId = context?.get("userId") as? String ?: ""
    val query = params["query"] as? String ?: ""
    val hash = query.hashCode()
    "$tenantId:$userId:search:$hash"
}
```

**DON'T:**
```kotlin
// Too generic - cache collision across tenants!
keyBuilder = { params, _ ->
    val query = params["query"] as? String ?: ""
    "search:$query"
}
```

### 2. TTL Selection

| Data Type | TTL | Rationale |
|-----------|-----|-----------|
| User Profile | 10-30 min | Changes infrequently |
| Real-time Data | 1-5 min | Must be fresh |
| Historical Data | 1-24 hours | Immutable |
| Aggregations | 30-60 min | Expensive to compute |
| External API | 15-60 min | Rate limits + cost |
| SPARQL Results | 10-30 min | Balance freshness & performance |

### 3. Validation Strategy

**Layer 1: Input Validation**
```kotlin
parameters {
    string("email", "User email", required = true)
    // Validate at parameter level
}
```

**Layer 2: Output Validation**
```kotlin
validate {
    // Validate structure
    requireField("user")
    requireField("email")

    // Validate types
    fieldType("email", FieldType.STRING)

    // Validate format
    pattern("email", Regex("^[^@]+@[^@]+\\.[^@]+$"))
}
```

**Layer 3: Business Logic Validation**
```kotlin
rule("user must be active") { output, context ->
    val user = output as? Map<*, *>
    val status = user?.get("status") as? String
    status == "active"
}
```

### 4. Pipeline Design

**DO: Break into logical stages**
```kotlin
+step(searchTool).output("results")
+step(enrichTool).output("enriched")
+step(rankTool).output("ranked")
+step(formatTool).output("formatted")
```

**DON'T: Create monolithic steps**
```kotlin
+step(doEverythingTool) // Hard to debug, test, cache
```

### 5. Error Handling

```kotlin
// Pipeline execution
val result = pipeline.execute(params)

if (!result.success) {
    // Find which step failed
    val failedStep = result.stepResults.find { !it.success }

    logger.error("Pipeline failed at step: ${failedStep?.stepId}")
    logger.error("Error: ${failedStep?.error}")

    // Handle specific failures
    when (failedStep?.stepId) {
        "search" -> {
            // Fallback to cached results
            fallbackSearch()
        }
        "validate" -> {
            // Log validation error, return partial results
            logValidationFailure(failedStep.error)
            return partialResults()
        }
        else -> {
            // Generic error handling
            throw PipelineException("Unknown error at ${failedStep?.stepId}")
        }
    }
}
```

### 6. Named Graph Strategies

**Strategy Selection Guide:**

| Use Case | Strategy | Graphs Generated |
|----------|----------|------------------|
| Strict tenant isolation | `tenantOnly()` | `tenant-{id}` |
| Shared reference data | `tenantWithShared()` | `tenant-{id}`, `shared` |
| User-specific data | `hierarchical()` | `tenant-{id}`, `user-{id}`, `session-{id}` |
| Type-partitioned data | `typePartitioned()` | `tenant-{id}/products`, `tenant-{id}/orders` |
| Session-scoped data | `sessionBased()` | `session-{id}` |

### 7. Template Organization

```kotlin
// Group templates by domain
object UserTemplates {
    fun register() {
        SparqlTemplateRepository.register("user-profile") { /* ... */ }
        SparqlTemplateRepository.register("user-activities") { /* ... */ }
        SparqlTemplateRepository.register("user-permissions") { /* ... */ }
    }
}

object ProductTemplates {
    fun register() {
        SparqlTemplateRepository.register("product-search") { /* ... */ }
        SparqlTemplateRepository.register("product-details") { /* ... */ }
        SparqlTemplateRepository.register("product-inventory") { /* ... */ }
    }
}

// Register all templates at startup
fun registerAllTemplates() {
    SparqlTemplateRepository.registerCommonTemplates()
    UserTemplates.register()
    ProductTemplates.register()
}
```

---

## Performance Optimization

### Cache Warming

```kotlin
// Warm cache at startup
suspend fun warmCache() {
    val commonQueries = listOf(
        "user-profile" to mapOf("userId" to "admin"),
        "product-search" to mapOf("category" to "electronics"),
        "category-hierarchy" to emptyMap()
    )

    withAgentContext("tenantId" to "default") {
        commonQueries.forEach { (toolName, params) ->
            val tool = ToolRegistry.get(toolName)
            tool?.execute(params) // Populate cache
        }
    }

    logger.info("Cache warmed with ${commonQueries.size} queries")
}
```

### Batch Processing

```kotlin
// Process multiple items efficiently
suspend fun batchProcess(items: List<String>) {
    val results = items.chunked(10).flatMap { batch ->
        coroutineScope {
            batch.map { item ->
                async {
                    processTool.execute(mapOf("item" to item))
                }
            }.awaitAll()
        }
    }
}
```

### Conditional Caching

```kotlin
cache {
    ttl = 1800
    maxSize = 1000

    // Only cache successful, non-empty results
    keyBuilder = { params, context ->
        val shouldCache = params["cache"] as? Boolean ?: true
        if (shouldCache) {
            generateCacheKey(params, context)
        } else {
            null // Skip caching
        }
    }
}
```

### Monitoring Dashboards

```kotlin
// Collect metrics
data class PerformanceMetrics(
    val toolName: String,
    val cacheHitRate: Double,
    val avgExecutionTime: Long,
    val totalRequests: Long,
    val errorRate: Double
)

fun collectMetrics(): List<PerformanceMetrics> {
    return ToolRegistry.getAllTools().mapNotNull { tool ->
        (tool as? CachedTool)?.cacheMetrics?.let { metrics ->
            PerformanceMetrics(
                toolName = tool.name,
                cacheHitRate = metrics.hitRate(),
                avgExecutionTime = calculateAvgTime(tool),
                totalRequests = metrics.hits.get() + metrics.misses.get(),
                errorRate = calculateErrorRate(tool)
            )
        }
    }
}

// Export to monitoring system
fun exportMetrics() {
    val metrics = collectMetrics()

    metrics.forEach { metric ->
        prometheusRegistry.gauge("tool_cache_hit_rate") {
            help("Cache hit rate per tool")
            labelNames("tool")
        }.labels(metric.toolName).set(metric.cacheHitRate)

        prometheusRegistry.counter("tool_requests_total") {
            help("Total requests per tool")
            labelNames("tool")
        }.labels(metric.toolName).inc(metric.totalRequests.toDouble())
    }
}
```

---

## Testing Strategies

### Unit Testing Tools

```kotlin
@Test
fun `test cached tool with validation`() = runBlocking {
    val tool = contextAwareTool("test-tool") {
        cache {
            ttl = 3600
            maxSize = 100
        }

        validate {
            requireField("result")
            fieldType("result", FieldType.STRING)
        }

        execute { params, context ->
            ToolResult.success(mapOf("result" to "success"))
        }
    }

    withAgentContext("tenantId" to "test") {
        // First call - cache miss
        val result1 = tool.execute(mapOf("input" to "test"))
        assertTrue(result1.isSuccess)

        // Second call - cache hit
        val result2 = tool.execute(mapOf("input" to "test"))
        assertTrue(result2.isSuccess)

        // Verify cache metrics
        val metrics = (tool as CachedTool).cacheMetrics
        assertEquals(1, metrics?.hits?.get())
        assertEquals(1, metrics?.misses?.get())
    }
}
```

### Integration Testing Pipelines

```kotlin
@Test
fun `test evidence pipeline integration`() = runBlocking {
    // Setup test data
    val testDocuments = listOf(
        Document("doc1", "Test content 1"),
        Document("doc2", "Test content 2")
    )

    // Mock external dependencies
    val mockSearchTool = mockk<Tool> {
        coEvery { execute(any()) } returns SpiceResult.success(
            ToolResult.success(mapOf("documents" to testDocuments))
        )
    }

    // Execute pipeline
    withAgentContext("tenantId" to "test") {
        val result = evidencePipeline.execute(mapOf(
            "query" to "test query"
        ))

        assertTrue(result.success)

        // Verify each step
        assertEquals(4, result.stepResults.size)
        result.stepResults.forEach { step ->
            assertTrue(step.success, "Step ${step.stepId} should succeed")
        }

        // Verify final output
        val finalOutput = result.getStepOutput("result") as Map<*, *>
        assertTrue(finalOutput.containsKey("evidence"))
        assertTrue(finalOutput.containsKey("metadata"))

        val evidence = finalOutput["evidence"] as Map<*, *>
        assertTrue(evidence.containsKey("sources"))
    }
}
```

### Performance Testing

```kotlin
@Test
fun `test cache performance under load`() = runBlocking {
    val tool = /* create cached tool */

    val iterations = 1000
    val uniqueKeys = 100

    val startTime = System.currentTimeMillis()

    withAgentContext("tenantId" to "perf-test") {
        repeat(iterations) { i ->
            val key = "key-${i % uniqueKeys}"
            tool.execute(mapOf("input" to key))
        }
    }

    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    val metrics = (tool as CachedTool).cacheMetrics!!
    val hitRate = metrics.hitRate()

    println("Performance Test Results:")
    println("  Total time: ${duration}ms")
    println("  Requests: $iterations")
    println("  Unique keys: $uniqueKeys")
    println("  Cache hit rate: $hitRate%")
    println("  Avg time per request: ${duration.toDouble() / iterations}ms")

    // Assert performance targets
    assertTrue(hitRate >= 90.0, "Hit rate should be >= 90%")
    assertTrue(duration < 5000, "Should complete in < 5 seconds")
}
```

---

## Production Deployment

### Configuration Management

```kotlin
// config/production.conf
spice {
    caching {
        defaultTtl = 1800
        defaultMaxSize = 5000
        metricsEnabled = true
    }

    sparql {
        endpoint = "https://neptune.prod.example.com:8182/sparql"
        connectionTimeout = 30000
        readTimeout = 60000
        maxRetries = 3
    }

    validation {
        failFast = true
        strictMode = true
    }

    namedGraphs {
        baseUri = "https://graphs.example.com/"
        strategy = "tenant-with-shared"
    }
}
```

### Logging

```kotlin
// Configure structured logging
val logger = LoggerFactory.getLogger("SpiceFramework")

// Tool execution logging
fun logToolExecution(
    toolName: String,
    params: Map<String, Any>,
    context: AgentContext?,
    result: SpiceResult<ToolResult>,
    duration: Long
) {
    val logData = mapOf(
        "tool" to toolName,
        "tenantId" to (context?.get("tenantId") as? String ?: "unknown"),
        "userId" to (context?.get("userId") as? String ?: "unknown"),
        "success" to result.isSuccess,
        "duration" to duration,
        "cacheHit" to (params["__cacheHit"] as? Boolean ?: false)
    )

    if (result.isSuccess) {
        logger.info("Tool executed: {}", logData)
    } else {
        logger.error("Tool failed: {}", logData)
    }
}

// Cache metrics logging
fun logCacheMetrics() {
    ToolRegistry.getAllTools().forEach { tool ->
        (tool as? CachedTool)?.cacheMetrics?.let { metrics ->
            logger.info("""
                Cache metrics for ${tool.name}:
                  Hit rate: ${metrics.hitRate()}%
                  Hits: ${metrics.hits.get()}
                  Misses: ${metrics.misses.get()}
                  Size: ${metrics.currentSize.get()}
                  Evictions: ${metrics.evictions.get()}
            """.trimIndent())
        }
    }
}
```

### Health Checks

```kotlin
// Health check endpoint
suspend fun healthCheck(): HealthStatus {
    val toolHealth = checkToolHealth()
    val cacheHealth = checkCacheHealth()
    val sparqlHealth = checkSparqlHealth()

    return HealthStatus(
        overall = toolHealth && cacheHealth && sparqlHealth,
        components = mapOf(
            "tools" to toolHealth,
            "cache" to cacheHealth,
            "sparql" to sparqlHealth
        )
    )
}

suspend fun checkSparqlHealth(): Boolean {
    return try {
        // Execute simple ASK query
        val result = sparqlClient.query("ASK { ?s ?p ?o } LIMIT 1")
        result.isSuccess
    } catch (e: Exception) {
        logger.error("SPARQL health check failed", e)
        false
    }
}

fun checkCacheHealth(): Boolean {
    return try {
        // Verify cache operations
        val testTool = ToolRegistry.get("test-health-tool")
        (testTool as? CachedTool)?.cacheMetrics != null
    } catch (e: Exception) {
        logger.error("Cache health check failed", e)
        false
    }
}
```

---

## Complete Examples

### Example 1: E-Commerce Product Recommendation System

```kotlin
suspend fun buildProductRecommendationSystem() {
    // Register SPARQL templates
    SparqlTemplateRepository.register("similar-products") {
        template = """
            SELECT ?product ?name ?category ?price ?similarity
            {{namedGraphsClause}}
            WHERE {
                <{{productUri}}> :category ?baseCategory ;
                                 :price ?basePrice .

                ?product a :Product ;
                         :name ?name ;
                         :category ?category ;
                         :price ?price .

                FILTER(?product != <{{productUri}}>)
                FILTER(?category = ?baseCategory)
                FILTER(ABS(?price - ?basePrice) < {{priceRange}})

                BIND(
                    (1.0 - ABS(?price - ?basePrice) / {{priceRange}})
                    AS ?similarity
                )
            }
            ORDER BY DESC(?similarity)
            LIMIT {{limit}}
        """
    }

    // Create tools
    val similarProductsTool = contextAwareTool("find-similar-products") {
        cache {
            ttl = 1800
            maxSize = 1000
            keyBuilder = { params, context ->
                val productId = params["productId"] as? String ?: ""
                val tenantId = context?.get("tenantId") as? String ?: "default"
                "$tenantId:similar:$productId"
            }
        }

        validate {
            requireField("products")
            fieldType("products", FieldType.ARRAY)

            rule("has recommendations") { output, _ ->
                val products = (output as? Map<*, *>)?.get("products") as? List<*>
                products?.isNotEmpty() == true
            }
        }

        sparql {
            endpoint = System.getenv("NEPTUNE_ENDPOINT")
            templateRef = "similar-products"
            useNamedGraphsFromContext = true
        }

        execute { params, context ->
            val productId = params["productId"] as String
            val priceRange = params["priceRange"] as? Number ?: 50
            val limit = params["limit"] as? Number ?: 10

            executeSparql(mapOf(
                "productUri" to "http://example.com/product#$productId",
                "priceRange" to priceRange,
                "limit" to limit
            ), context)
        }
    }

    val getUserPreferencesTool = contextAwareTool("get-user-preferences") {
        cache {
            ttl = 600
            maxSize = 5000
            keyBuilder = { params, context ->
                val userId = params["userId"] as? String ?: ""
                val tenantId = context?.get("tenantId") as? String ?: "default"
                "$tenantId:prefs:$userId"
            }
        }

        execute { params, context ->
            val userId = params["userId"] as String
            // Fetch from database
            val prefs = database.getUserPreferences(userId)
            ToolResult.success(prefs)
        }
    }

    val rankByPreferencesTool = contextAwareTool("rank-by-preferences") {
        execute { params, context ->
            val products = params["products"] as List<Map<*, *>>
            val preferences = params["preferences"] as Map<*, *>

            val rankedProducts = products.map { product ->
                val score = calculatePreferenceScore(product, preferences)
                product + ("preferenceScore" to score)
            }.sortedByDescending { it["preferenceScore"] as Double }

            ToolResult.success(mapOf("rankedProducts" to rankedProducts))
        }
    }

    // Create recommendation pipeline
    val recommendationPipeline = toolChain("product-recommendations") {
        name = "Product Recommendation Pipeline"

        extension(NamedGraphsExtension(
            NamedGraphsStrategies.tenantOnly("http://example.com/graphs/")
        ))

        +step(similarProductsTool)
            .output("similarProducts")
            .input { context ->
                mapOf(
                    "productId" to context.sharedData["productId"],
                    "priceRange" to 100,
                    "limit" to 20
                )
            }

        +step(getUserPreferencesTool)
            .output("preferences")
            .input { context ->
                mapOf("userId" to context.sharedData["userId"])
            }

        +step(rankByPreferencesTool)
            .output("recommendations")
            .input { context ->
                val products = (context.requireOutputOf("similarProducts") as Map<*, *>)["products"]
                val prefs = context.requireOutputOf("preferences")
                mapOf(
                    "products" to products,
                    "preferences" to prefs
                )
            }
    }

    // Usage
    withAgentContext("tenantId" to "shop-123", "userId" to "customer-456") {
        val result = recommendationPipeline.execute(mapOf(
            "productId" to "prod-789",
            "userId" to "customer-456"
        ))

        if (result.success) {
            val recommendations = result.getStepOutput("recommendations")
            println("Recommendations: $recommendations")
        }
    }
}
```

---

## Conclusion

This integration guide demonstrates how Spice Framework 0.4.1 features work together to build production-ready agentic systems. Key takeaways:

1. **Combine Features**: Use caching + validation + templates + pipelines together
2. **Multi-Tenancy**: Context-aware caching and named graphs enable secure isolation
3. **Performance**: Intelligent caching reduces costs and latency by 70-90%
4. **Reliability**: Multi-layer validation ensures data quality
5. **Maintainability**: Template repositories and pipelines improve code organization

For more details, see:
- [Tool-Level Caching Documentation](../performance/tool-caching.md)
- [Output Validation Documentation](../dsl-guide/output-validation.md)
- [SPARQL Features Documentation](../extensions/sparql-features.md)
- [Tool Pipeline Documentation](../orchestration/tool-pipeline.md)
