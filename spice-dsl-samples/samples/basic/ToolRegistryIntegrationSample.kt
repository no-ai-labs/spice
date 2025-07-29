@file:JvmName("ToolRegistryIntegrationSample")

package samples.basic

import io.github.noailabs.spice.model.*
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.ToolResult

/**
 * 🔧 ToolRegistry + AgentTool Integration Sample
 * 
 * Demonstrates how AgentTool seamlessly integrates with ToolRegistry
 * while preserving metadata for enhanced tool management.
 */

fun main() {
    // 1. Create and register AgentTools
    val mathTool = agentTool("math-calculator") {
        description("Advanced mathematical calculator")
        
        parameters {
            string("expression", "Mathematical expression", required = true)
            string("format", "Output format (decimal/fraction)", required = false)
        }
        
        tags("math", "calculator", "utility")
        metadata("version", "2.0")
        metadata("author", "Math Team")
        
        implement { params ->
            val expr = params["expression"] as? String ?: return@implement ToolResult.error("No expression")
            // Simple demo calculation
            ToolResult.success("Result: ${expr.length * 42}")
        }
    }
    
    val dataTool = agentTool("data-processor") {
        description("Process and transform data")
        
        parameters {
            array("data", "Input data array", required = true)
            string("operation", "Operation to perform", required = true)
        }
        
        tags("data", "processing", "etl")
        metadata("version", "1.5")
        
        implementationType("http-api", mapOf(
            "endpoint" to "/api/process",
            "method" to "POST"
        ))
        
        implement { params ->
            ToolResult.success("Processed data")
        }
    }
    
    // 2. Register AgentTools in ToolRegistry
    println("📦 Registering AgentTools...")
    ToolRegistry.register(mathTool)
    ToolRegistry.register(dataTool, namespace = "data")
    
    // 3. Traditional tool registration (for comparison)
    val simpleTool = object : io.github.noailabs.spice.Tool {
        override val name = "simple-echo"
        override val description = "Echo tool"
        override val schema = io.github.noailabs.spice.ToolSchema(
            name = name,
            description = description,
            parameters = mapOf(
                "message" to io.github.noailabs.spice.ParameterSchema("string", "Message to echo", true)
            )
        )
        override suspend fun execute(parameters: Map<String, Any>) = 
            ToolResult.success(parameters["message"]?.toString() ?: "")
    }
    ToolRegistry.register(simpleTool)
    
    // 4. Search by tags
    println("\n🔍 Finding tools by tag 'math':")
    val mathTools = ToolRegistry.getByTag("math")
    mathTools.forEach { tool ->
        println("  - ${tool.name}: ${tool.description}")
    }
    
    // 5. Get tools by source
    println("\n📊 Tools by source:")
    println("  Agent Tools: ${ToolRegistry.getBySource("agent-tool").size}")
    println("  Direct Tools: ${ToolRegistry.getBySource("direct").size}")
    
    // 6. Get AgentTool metadata
    println("\n📋 AgentTool metadata:")
    ToolRegistry.getAgentTools().forEach { (tool, metadata) ->
        println("  ${tool.name}:")
        println("    Tags: ${metadata["tags"]}")
        println("    Metadata: ${metadata["metadata"]}")
    }
    
    // 7. Namespace support
    println("\n🗂️ Namespace access:")
    val dataProcessor = ToolRegistry.getTool("data-processor", "data")
    println("  Found in 'data' namespace: ${dataProcessor?.name}")
    
    // 8. Execute a registered tool
    println("\n⚡ Executing registered tool:")
    val result = runBlocking {
        mathTools.first().execute(mapOf("expression" to "2+2"))
    }
    println("  Result: ${result.result}")
    
    // 9. List all tools in a namespace
    println("\n📑 Tools in namespaces:")
    println("  Global: ${ToolRegistry.getByNamespace("global").map { it.name }}")
    println("  Data: ${ToolRegistry.getByNamespace("data").map { it.name }}")
    
    // 10. Advanced filtering
    println("\n🎯 Advanced filtering:")
    val processingTools = ToolRegistry.getByTag("processing")
    val versionedTools = ToolRegistry.getAgentTools()
        .filter { (_, meta) -> 
            (meta["metadata"] as? Map<*, *>)?.containsKey("version") == true
        }
    println("  Processing tools: ${processingTools.size}")
    println("  Versioned tools: ${versionedTools.size}")
}

// Helper for blocking execution
fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
} 