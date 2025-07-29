@file:JvmName("UnifiedJsonSerializationDemo")

package samples.basic

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.model.*
import io.github.noailabs.spice.serialization.SpiceSerializer
import io.github.noailabs.spice.serialization.SpiceSerializer.toJson
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonSchema
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonElement
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonObject
import kotlinx.serialization.json.*

/**
 * 🌟 Unified JSON Serialization Demo
 * 
 * Demonstrates how all Spice components use the same serialization pattern
 * for consistent JSON output across the entire framework.
 */
fun main() {
    println("🌶️ Spice Framework - Unified JSON Serialization Demo")
    println("=" * 60)
    
    // 1. Agent JSON Serialization
    println("\n📌 1. Agent Serialization")
    val agent = buildAgent {
        id = "data-processor-v2"
        name = "Advanced Data Processor"
        description = "Handles complex data transformations"
        
        tools("analyzer", "transformer")
        globalTools("formatter")
        
        tool("custom-validator") {
            description("Validate data format")
            parameter("data", "string", "Data to validate")
            parameter("format", "string", "Expected format")
            
            execute { params: Map<String, Any> ->
                ToolResult.success("Valid")
            }
        }
        
        handle { comm ->
            comm.reply("Processing complete", from = id)
        }
    }
    
    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), agent.toJson()))
    
    // 2. Tool JSON Serialization
    println("\n\n📌 2. Tool Serialization")
    val tool = agent.getTools().first { it.name == "custom-validator" }
    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), tool.toJson()))
    
    // 3. AgentTool with JSON Schema
    println("\n\n📌 3. AgentTool Serialization (Regular JSON)")
    val agentTool = agentTool("ml-predictor") {
        description("Machine learning prediction tool")
        
        parameters {
            array("features", "Input features")
            string("model", "Model name")
            number("threshold", "Confidence threshold")
        }
        
        tags("ml", "prediction", "ai")
        metadata("version", "3.0")
        metadata("accuracy", "0.95")
        
        implementationType("python-script", mapOf(
            "runtime" to "python3.9",
            "requirements" to "numpy,scikit-learn"
        ))
    }
    
    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), agentTool.toJson()))
    
    println("\n\n📌 4. AgentTool as JSON Schema")
    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), agentTool.toJsonSchema()))
    
    // 5. VectorStore Configuration
    println("\n\n📌 5. VectorStore Configuration")
    val vectorConfig = VectorStoreConfig(
        provider = "pinecone",
        host = "api.pinecone.io",
        port = 443,
        apiKey = "pk-12345-secret-key",
        collection = "embeddings",
        vectorSize = 1536,
        config = mapOf(
            "metric" to "cosine",
            "pods" to "2",
            "replicas" to "3"
        )
    )
    
    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), vectorConfig.toJson()))
    
    // 6. Complex Metadata Conversion (No more hungry converter!)
    println("\n\n📌 6. Complex Metadata Conversion")
    val complexMetadata = mapOf(
        "timestamp" to java.time.Instant.now(),
        "stats" to mapOf(
            "total" to 1000,
            "processed" to 950,
            "failed" to 50,
            "success_rate" to 0.95
        ),
        "tags" to listOf("production", "v2", "optimized"),
        "config" to mapOf(
            "batch_size" to 100,
            "workers" to 4,
            "features" to listOf(
                mapOf("name" to "parallel", "enabled" to true),
                mapOf("name" to "cache", "enabled" to false)
            )
        ),
        "status" to Thread.State.RUNNABLE
    )
    
    val jsonMetadata = SpiceSerializer.toJsonMetadata(complexMetadata)
    println("Complex metadata properly converted:")
    println(Json { prettyPrint = true }.encodeToString(JsonObject(jsonMetadata)))
    
    // 7. Everything is JsonElement!
    println("\n\n📌 7. Universal toJsonElement() Conversion")
    val mixedData = mapOf(
        "agent" to agent,
        "tool" to tool,
        "agentTool" to agentTool,
        "vectorStore" to vectorConfig,
        "primitives" to mapOf(
            "string" to "Hello Spice!",
            "number" to 42,
            "decimal" to 3.14159,
            "boolean" to true,
            "null" to null
        ),
        "collections" to mapOf(
            "list" to listOf(1, 2, 3),
            "set" to setOf("a", "b", "c"),
            "map" to mapOf("nested" to "value")
        )
    )
    
    println("Everything converts seamlessly:")
    val everythingJson = mixedData.toJsonObject()
    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), everythingJson))
    
    // 8. Summary
    println("\n\n📌 Summary")
    println("✅ All Spice components use unified JSON serialization")
    println("✅ Proper type handling (no more toString() for collections!)")
    println("✅ Consistent structure across Agent, Tool, VectorStore, etc.")
    println("✅ JSON Schema support for tools")
    println("✅ Security features (API key redaction)")
    println("✅ Ready for UI visualization in Mentat!")
    
    println("\n🎉 This is production-grade serialization! 🎉")
}

// Helper
operator fun String.times(n: Int) = this.repeat(n) 