@file:JvmName("SpiceSerializerSample")

package samples.basic

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.model.*
import io.github.noailabs.spice.serialization.SpiceSerializer
import io.github.noailabs.spice.serialization.SpiceSerializer.toJson
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonElement
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonObject
import kotlinx.serialization.json.*

/**
 * 🔄 Unified JSON Serialization Sample
 * 
 * Demonstrates consistent JSON conversion across all Spice components
 */
fun main() {
    println("🎯 Spice Unified JSON Serialization Demo\n")
    
    // 1. Complex metadata conversion (goodbye hungry converter!)
    println("📊 Metadata Conversion (The Right Way™):")
    val complexMetadata = mapOf(
        "version" to "2.0",
        "count" to 42,
        "active" to true,
        "tags" to listOf("ai", "agent", "tool"),
        "config" to mapOf(
            "timeout" to 30,
            "retries" to 3
        ),
        "timestamp" to java.time.Instant.now()
    )
    
    val jsonMetadata = SpiceSerializer.toJsonMetadata(complexMetadata)
    println(Json { prettyPrint = true }.encodeToString(
        JsonObject(jsonMetadata)
    ))
    
    // 2. Agent serialization
    println("\n🤖 Agent Serialization:")
    val agent = buildAgent {
        id = "data-processor"
        name = "Data Processing Agent"
        description = "Handles data transformation and analysis"
        
        tools("calculator", "formatter")
        
        handle { comm ->
            comm.reply("Processed", from = id)
        }
    }
    
    val agentJson = agent.toJson()
    println(Json { prettyPrint = true }.encodeToString(agentJson))
    
    // 3. AgentTool serialization (clean JSON, not Schema)
    println("\n🔧 AgentTool Clean JSON:")
    val tool = agentTool("data-transformer") {
        description("Transform data between formats")
        
        parameters {
            string("input", "Input data")
            string("format", "Target format (json/csv/xml)")
        }
        
        tags("data", "transformation", "utility")
        metadata("version", "1.5")
        metadata("author", "Data Team")
        
        implement { params ->
            ToolResult.success("Transformed data")
        }
    }
    
    val toolJson = tool.toJson()
    println(Json { prettyPrint = true }.encodeToString(toolJson))
    
    // 4. VectorStore configuration
    println("\n💾 VectorStore Config:")
    val vectorConfig = VectorStoreConfig(
        provider = "qdrant",
        host = "localhost",
        port = 6333,
        apiKey = "super-secret-key",
        collection = "documents",
        vectorSize = 768,
        config = mapOf(
            "distance" to "cosine",
            "shard_count" to "2"
        )
    )
    
    val vectorJson = vectorConfig.toJson()
    println(Json { prettyPrint = true }.encodeToString(vectorJson))
    println("  Notice: API key is redacted for security! ✅")
    
    // 5. VectorStore collection metadata
    println("\n💾 Complex VectorStore Metadata:")
    val complexVectorMetadata = mapOf(
        "collection" to "documents",
        "count" to 1500,
        "embeddings" to mapOf(
            "model" to "text-embedding-ada-002",
            "dimensions" to 1536
        ),
        "lastUpdated" to java.time.Instant.now(),
        "tags" to listOf("production", "indexed", "active")
    )
    
    // This now properly handles nested structures!
    val vectorMetadataJson = SpiceSerializer.toJsonMetadata(complexVectorMetadata)
    println(Json { prettyPrint = true }.encodeToString(
        JsonObject(vectorMetadataJson)
    ))
    
    // 6. Complex nested structure
    println("\n🏗️ Complex Nested Structure:")
    val complexData = mapOf(
        "agent" to mapOf(
            "id" to "complex-001",
            "tools" to listOf("tool1", "tool2"),
            "settings" to mapOf(
                "debug" to true,
                "timeout" to 60
            )
        ),
        "results" to listOf(
            mapOf("score" to 0.95, "label" to "positive"),
            mapOf("score" to 0.12, "label" to "negative")
        ),
        "timestamp" to java.time.Instant.now()
    )
    
    val complexJson = complexData.toJsonObject()
    println(Json { prettyPrint = true }.encodeToString(complexJson))
    
    // 7. Type handling showcase
    println("\n🎨 Type Handling Showcase:")
    val types = mapOf(
        "string" to "Hello World",
        "int" to 42,
        "double" to 3.14159,
        "boolean" to true,
        "null" to null,
        "list" to listOf(1, 2, 3),
        "map" to mapOf("nested" to "value"),
        "enum" to Thread.State.RUNNABLE,
        "instant" to java.time.Instant.now()
    )
    
    types.forEach { (name, value) ->
        val json = value.toJsonElement()
        println("  $name (${value?.javaClass?.simpleName ?: "null"}) → $json")
    }
    
    // 8. Comparison: Old vs New metadata conversion
    println("\n🔄 Old vs New Metadata Conversion:")
    val testData = mapOf(
        "list" to listOf("a", "b", "c"),
        "map" to mapOf("key" to "value", "count" to 42)
    )
    
    // Old way would have done: list -> "[a, b, c]", map -> "{key=value, count=42}"
    println("  Old way (toString): list='${testData["list"]}', map='${testData["map"]}'")
    
    // New way preserves structure
    val newWay = SpiceSerializer.toJsonMetadata(testData)
    println("  New way (JSON):")
    println(Json { prettyPrint = true }.encodeToString(JsonObject(newWay)))
}

/**
 * Helper extension for demo
 */
fun JsonObject.prettyPrint() {
    println(Json { prettyPrint = true }.encodeToString(this))
} 