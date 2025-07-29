@file:JvmName("AgentToolSample")

package samples.basic

import io.github.noailabs.spice.model.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 🔧 AgentTool Sample - Demonstrates intermediate tool representation
 */

fun main() {
    // 1. Create AgentTool using builder DSL
    val calculateTool = agentTool("calculate") {
        description("Perform basic arithmetic calculations")
        
        parameters {
            string("expression", "Mathematical expression to evaluate", required = true)
            boolean("verbose", "Show calculation steps", required = false)
        }
        
        tags("math", "utility", "calculator")
        metadata("version", "1.0")
        metadata("author", "Spice Framework")
        
        implement { params ->
            val expression = params["expression"] as? String 
                ?: return@implement ToolResult.error("Expression required")
            
            try {
                // Simple expression evaluation (in real code, use proper parser)
                val result = when {
                    expression.contains("+") -> {
                        val parts = expression.split("+")
                        parts[0].toDouble() + parts[1].toDouble()
                    }
                    expression.contains("-") -> {
                        val parts = expression.split("-")
                        parts[0].toDouble() - parts[1].toDouble()
                    }
                    else -> expression.toDouble()
                }
                
                ToolResult.success(
                    result = result.toString(),
                    metadata = if (params["verbose"] == true) {
                        mapOf("steps" to "Parsed and calculated: $expression = $result")
                    } else emptyMap()
                )
            } catch (e: Exception) {
                ToolResult.error("Failed to calculate: ${e.message}")
            }
        }
    }
    
    // 2. Use the AgentTool
    println("🧮 Calculator Tool Created:")
    println("  Name: ${calculateTool.name}")
    println("  Description: ${calculateTool.description}")
    println("  Tags: ${calculateTool.tags}")
    println("  Has implementation: ${calculateTool.hasImplementation()}")
    
    // 3. Execute the tool
    val result = runBlocking {
        calculateTool.execute(mapOf(
            "expression" to "10+5",
            "verbose" to true
        ))
    }
    println("\n📊 Calculation Result: ${result.result}")
    result.metadata?.get("steps")?.let { println("  Steps: $it") }
    
    // 4. Convert to Tool interface
    val tool = calculateTool.toTool()
    println("\n🔄 Converted to Tool interface: ${tool.name}")
    
    // 5. Create AgentTool for external API
    val weatherTool = agentTool("get-weather") {
        description("Get current weather for a city")
        
        parameters {
            string("city", "City name", required = true)
            string("units", "Temperature units (C/F)", required = false)
        }
        
        implementationType("http-api", mapOf(
            "url" to "https://api.weather.com/v1/current",
            "method" to "GET",
            "auth" to "api-key"
        ))
        
        // For demo, provide a mock implementation
        implement { params ->
            ToolResult.success("Weather: Sunny, 22°C")
        }
    }
    
    // 6. Serialize to JSON (without implementation)
    val toolWithoutImpl = weatherTool.copy(implementation = null)
    val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    println("\n📄 Serialized AgentTool (for storage):")
    println(json.encodeToString(toolWithoutImpl))
    
    // 7. Convert existing Tool to AgentTool
    val existingTool = buildAgent {
        name = "helper"
        description = "Helper agent"
        
        tool("echo-tool") {
            description("Echo the input")
            parameter("message", "string", "Message to echo")
            execute { params: Map<String, Any> ->
                ToolResult.success(params["message"]?.toString() ?: "No message")
            }
        }
        
        handle { comm -> comm.reply("Done", from = id) }
    }.getTools().first()
    
    val convertedAgentTool = existingTool.toAgentTool(
        tags = listOf("echo", "test"),
        metadata = mapOf("converted" to "true")
    )
    
    println("\n🔄 Converted from existing Tool:")
    println("  Name: ${convertedAgentTool.name}")
    println("  Tags: ${convertedAgentTool.tags}")
    println("  Implementation type: ${convertedAgentTool.implementationType}")
}

// Helper for blocking execution in sample
fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
} 