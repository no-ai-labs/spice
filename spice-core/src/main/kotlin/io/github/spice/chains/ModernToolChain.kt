package io.github.spice.chains

import io.github.spice.*
import io.github.spice.dsl.*
import kotlinx.coroutines.delay

/**
 * 🔗 Modern ToolChain System
 * 
 * Simplified tool chaining implementation using new DSL structure.
 * Clean, maintainable, and optimized for Mentat workflows.
 */

/**
 * Chain step definition
 */
data class ChainStep(
    val id: String,
    val toolName: String,
    val parameters: Map<String, Any> = emptyMap(),
    val condition: (suspend (ChainContext) -> Boolean)? = null,
    val transformer: (suspend (ToolResult) -> Map<String, Any>)? = null
)

/**
 * Chain execution context
 */
data class ChainContext(
    val chainId: String,
    var currentStep: Int = 0,
    val results: MutableList<ToolResult> = mutableListOf(),
    val sharedData: MutableMap<String, Any> = mutableMapOf(),
    val startTime: Long = System.currentTimeMillis()
) {
    fun addResult(result: ToolResult) {
        results.add(result)
    }
    
    fun getLastResult(): ToolResult? = results.lastOrNull()
    
    fun getExecutionTime(): Long = System.currentTimeMillis() - startTime
}

/**
 * Modern tool chain
 */
class ModernToolChain(
    override val id: String,
    val name: String,
    val description: String,
    val steps: List<ChainStep>,
    val debugEnabled: Boolean = false
) : Identifiable {
    
    /**
     * Execute the tool chain
     */
    suspend fun execute(initialParameters: Map<String, Any>): ChainResult {
        val context = ChainContext(chainId = id)
        context.sharedData.putAll(initialParameters)
        
        if (debugEnabled) {
            println("[CHAIN] Starting execution of '$name' with ${steps.size} steps")
        }
        
        try {
            for ((index, step) in steps.withIndex()) {
                context.currentStep = index
                
                // Check condition
                if (step.condition != null && !step.condition.invoke(context)) {
                    if (debugEnabled) {
                        println("[CHAIN] Skipping step '${step.id}' - condition not met")
                    }
                    continue
                }
                
                // Prepare parameters
                val stepParams = prepareStepParameters(step, context)
                
                if (debugEnabled) {
                    println("[CHAIN] Executing step '${step.id}' with tool '${step.toolName}'")
                }
                
                // Execute tool
                val tool = ToolRegistry.getTool(step.toolName)
                    ?: return ChainResult.error("Tool not found: ${step.toolName}")
                
                val result = tool.execute(stepParams)
                context.addResult(result)
                
                if (!result.success) {
                    return ChainResult.error("Step '${step.id}' failed: ${result.error}")
                }
                
                // Transform result for next step
                if (step.transformer != null) {
                    val transformedData = step.transformer.invoke(result)
                    context.sharedData.putAll(transformedData)
                }
                
                if (debugEnabled) {
                    println("[CHAIN] Step '${step.id}' completed successfully")
                }
            }
            
            return ChainResult.success(
                result = context.getLastResult()?.result ?: "Chain completed",
                executionTime = context.getExecutionTime(),
                stepResults = context.results.toList()
            )
            
        } catch (e: Exception) {
            return ChainResult.error("Chain execution failed: ${e.message}")
        }
    }
    
    /**
     * Prepare parameters for step execution
     */
    private fun prepareStepParameters(step: ChainStep, context: ChainContext): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        
        // Add step-specific parameters
        params.putAll(step.parameters)
        
        // Add shared data
        params.putAll(context.sharedData)
        
        // Add previous result if available
        context.getLastResult()?.let { lastResult ->
            params["previous_result"] = lastResult.result
            params["previous_success"] = lastResult.success
        }
        
        return params
    }
}

/**
 * Chain execution result
 */
data class ChainResult(
    val success: Boolean,
    val result: String = "",
    val error: String = "",
    val executionTime: Long = 0,
    val stepResults: List<ToolResult> = emptyList()
) {
    companion object {
        fun success(result: String, executionTime: Long = 0, stepResults: List<ToolResult> = emptyList()): ChainResult {
            return ChainResult(
                success = true,
                result = result,
                executionTime = executionTime,
                stepResults = stepResults
            )
        }
        
        fun error(error: String): ChainResult {
            return ChainResult(success = false, error = error)
        }
    }
}

/**
 * Tool chain builder using DSL
 */
class ToolChainBuilder(private val id: String) {
    var name: String = ""
    var description: String = ""
    var debugEnabled: Boolean = false
    private val steps = mutableListOf<ChainStep>()
    
    /**
     * Add a step to the chain
     */
    fun step(
        stepId: String,
        toolName: String,
        parameters: Map<String, Any> = emptyMap(),
        condition: (suspend (ChainContext) -> Boolean)? = null,
        transformer: (suspend (ToolResult) -> Map<String, Any>)? = null
    ) {
        steps.add(ChainStep(stepId, toolName, parameters, condition, transformer))
    }
    
    /**
     * Add a simple step
     */
    fun step(toolName: String, parameters: Map<String, Any> = emptyMap()) {
        step("step-${steps.size + 1}", toolName, parameters)
    }
    
    /**
     * Add a conditional step
     */
    fun stepIf(
        stepId: String,
        toolName: String,
        condition: suspend (ChainContext) -> Boolean,
        parameters: Map<String, Any> = emptyMap()
    ) {
        step(stepId, toolName, parameters, condition)
    }
    
    /**
     * Add a step with data transformation
     */
    fun stepWithTransform(
        stepId: String,
        toolName: String,
        parameters: Map<String, Any> = emptyMap(),
        transformer: suspend (ToolResult) -> Map<String, Any>
    ) {
        step(stepId, toolName, parameters, null, transformer)
    }
    
    fun build(): ModernToolChain {
        require(name.isNotEmpty()) { "Chain name is required" }
        require(steps.isNotEmpty()) { "At least one step is required" }
        
        return ModernToolChain(id, name, description, steps.toList(), debugEnabled)
    }
}

/**
 * DSL for building tool chains
 */
fun toolChain(id: String, init: ToolChainBuilder.() -> Unit): ModernToolChain {
    val builder = ToolChainBuilder(id)
    builder.init()
    return builder.build()
}

/**
 * Tool chain registry
 */

// =====================================
// PREDEFINED TOOL CHAINS
// =====================================

/**
 * Text analysis chain
 */
fun textAnalysisChain(debugEnabled: Boolean = false): ModernToolChain {
    return toolChain("text-analysis") {
        name = "Text Analysis Chain"
        description = "Comprehensive text analysis pipeline"
        this.debugEnabled = debugEnabled
        
        step("extract-emails", "text-processor", mapOf(
            "operation" to "extract_emails"
        ))
        
        step("sentiment-analysis", "text-processor", mapOf(
            "operation" to "sentiment"  
        ))
        
        step("summary", "text-processor", mapOf(
            "operation" to "summary"
        ))
    }
}

/**
 * Data processing chain
 */
fun dataProcessingChain(debugEnabled: Boolean = false): ModernToolChain {
    return toolChain("data-processing") {
        name = "Data Processing Chain"
        description = "Clean and process data pipeline"
        this.debugEnabled = debugEnabled
        
        step("clean-text", "text-processor", mapOf(
            "operation" to "clean"
        ))
        
        step("extract-info", "text-processor", mapOf(
            "operation" to "extract_urls"
        ))
        
        step("generate-timestamp", "datetime", mapOf(
            "operation" to "timestamp"
        ))
    }
}

/**
 * Mathematical computation chain
 */
fun mathChain(debugEnabled: Boolean = false): ModernToolChain {
    return toolChain("math-computation") {
        name = "Mathematical Computation Chain"
        description = "Multi-step mathematical calculations"
        this.debugEnabled = debugEnabled
        
        step("basic-calc", "calculator", mapOf(
            "operation" to "multiply",
            "a" to 10,
            "b" to 5
        )) { result ->
            // Transform result for next step
            mapOf<String, Any>("computed_value" to (result.result.toDoubleOrNull() ?: 0.0))
        }
        
        step("power-calc", "calculator", mapOf(
            "operation" to "power",
            "a" to 2,
            "b" to 3
        ))
        
        step("random-number", "random", mapOf(
            "type" to "number",
            "min" to 1,
            "max" to 100
        ))
    }
}

// =====================================
// CONVENIENCE FUNCTIONS
// =====================================

/**
 * Execute a tool chain by ID
 */
suspend fun executeToolChain(chainId: String, parameters: Map<String, Any>): ChainResult {
    val chain = ToolChainRegistry.get(chainId)
        ?: return ChainResult.error("Chain not found: $chainId")
    
    return chain.execute(parameters)
}

/**
 * Register predefined chains
 */
fun registerPredefinedChains(debugEnabled: Boolean = false) {
    ToolChainRegistry.register(textAnalysisChain(debugEnabled))
    ToolChainRegistry.register(dataProcessingChain(debugEnabled))
    ToolChainRegistry.register(mathChain(debugEnabled))
}

/**
 * Create a simple sequential chain
 */
fun simpleChain(
    id: String,
    name: String,
    tools: List<String>,
    debugEnabled: Boolean = false
): ModernToolChain {
    return toolChain(id) {
        this.name = name
        this.description = "Simple sequential execution of tools"
        this.debugEnabled = debugEnabled
        
        tools.forEach { toolName ->
            step(toolName)
        }
    }
} 