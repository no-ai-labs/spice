package io.github.noailabs.spice.chains

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.error.SpiceResult
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
    val transformer: (suspend (ToolResult) -> Map<String, Any>)? = null,
    val transformerWithContext: (suspend (ToolResult, ChainContext) -> Map<String, Any>)? = null,  // 🆕 Context-aware transformer
    val parameterProvider: (suspend (ChainContext) -> Map<String, Any>)? = null  // 🆕 Dynamic parameters for fluent API
)

/**
 * Chain execution context
 */
data class ChainContext(
    val chainId: String,
    var currentStep: Int = 0,
    val results: MutableList<ToolResult> = mutableListOf(),
    val sharedData: MutableMap<String, Any> = mutableMapOf(),
    val stepOutputs: MutableMap<String, Any> = mutableMapOf(),  // 🆕 Step outputs by step ID
    val startTime: Long = System.currentTimeMillis()
) {
    fun addResult(result: ToolResult) {
        results.add(result)
    }

    fun getLastResult(): ToolResult? = results.lastOrNull()

    fun getExecutionTime(): Long = System.currentTimeMillis() - startTime

    /**
     * Store output for a specific step
     */
    fun setStepOutput(stepId: String, output: Any) {
        stepOutputs[stepId] = output
    }
}

// =====================================
// 🆕 CHAINCONTEXT EXTENSION FUNCTIONS
// =====================================

/**
 * Get output from a previous step (nullable)
 *
 * Example:
 * ```
 * val sku = context.getOutputOf("resolve")
 * ```
 */
fun ChainContext.getOutputOf(stepId: String): Any? {
    return stepOutputs[stepId]
}

/**
 * Require output from a previous step (throws if not found)
 *
 * Example:
 * ```
 * val sku = context.requireOutputOf("resolve")
 * ```
 */
fun ChainContext.requireOutputOf(stepId: String): Any {
    return stepOutputs[stepId]
        ?: throw IllegalStateException("Output of step '$stepId' not found. Available steps: ${stepOutputs.keys}")
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

                result.fold(
                    onSuccess = { toolResult ->
                        context.addResult(toolResult)

                        if (!toolResult.success) {
                            return ChainResult.error("Step '${step.id}' failed: ${toolResult.error}")
                        }

                        // 🆕 Store step output (raw result by default)
                        context.setStepOutput(step.id, toolResult.result ?: "")

                        // Transform result for next step (prefer context-aware transformer)
                        if (step.transformerWithContext != null) {
                            val transformedData = step.transformerWithContext.invoke(toolResult, context)
                            context.sharedData.putAll(transformedData)

                            // 🆕 If transformer returns single value, update step output
                            if (transformedData.size == 1) {
                                context.setStepOutput(step.id, transformedData.values.first())
                            }
                        } else if (step.transformer != null) {
                            val transformedData = step.transformer.invoke(toolResult)
                            context.sharedData.putAll(transformedData)

                            // 🆕 If transformer returns single value, update step output
                            if (transformedData.size == 1) {
                                context.setStepOutput(step.id, transformedData.values.first())
                            }
                        }
                    },
                    onFailure = { error ->
                        return ChainResult.error("Step '${step.id}' execution failed: ${error.message}")
                    }
                )
                
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
    private suspend fun prepareStepParameters(step: ChainStep, context: ChainContext): Map<String, Any> {
        val params = mutableMapOf<String, Any>()

        // 🆕 Use dynamic parameter provider if available (fluent API)
        if (step.parameterProvider != null) {
            params.putAll(step.parameterProvider.invoke(context))
        } else {
            // Add step-specific parameters (traditional API)
            params.putAll(step.parameters)
        }

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

// =====================================
// 🆕 FLUENT PIPELINE DSL
// =====================================

/**
 * Step builder for fluent API
 *
 * Example:
 * ```
 * +step(resolveTool).output("sku").input { mapOf("text" to "에센스") }
 * +step(getSpecsTool).input { context -> mapOf("sku" to context.requireOutputOf("sku")) }
 * ```
 */
class StepBuilder(
    internal val tool: Tool,
    internal var stepId: String? = null
) {
    internal var outputName: String? = null
    internal var inputProvider: (suspend (ChainContext) -> Map<String, Any>)? = null

    /**
     * Specify output name for this step
     */
    fun output(name: String): StepBuilder {
        this.outputName = name
        return this
    }

    /**
     * Specify input parameters (context-aware)
     */
    fun input(provider: suspend (ChainContext) -> Map<String, Any>): StepBuilder {
        this.inputProvider = provider
        return this
    }

    /**
     * Set step ID
     */
    fun named(id: String): StepBuilder {
        this.stepId = id
        return this
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
    private var stepCounter = 0

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
        steps.add(ChainStep(stepId, toolName, parameters, condition, transformer, null))
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
        steps.add(ChainStep(stepId, toolName, parameters, null, transformer, null))
    }

    /**
     * 🆕 Add a step with context-aware transformation
     *
     * Example:
     * ```
     * stepWithTransform("get-specs", "get-specs-tool") { result, context ->
     *     val sku = context.requireOutputOf("resolve")
     *     mapOf("specs" to result.result, "sku" to sku)
     * }
     * ```
     */
    fun stepWithTransform(
        stepId: String,
        toolName: String,
        parameters: Map<String, Any> = emptyMap(),
        transformer: suspend (ToolResult, ChainContext) -> Map<String, Any>
    ) {
        steps.add(ChainStep(stepId, toolName, parameters, null, null, transformer))
    }

    // =====================================
    // 🆕 TOOL OBJECT OVERLOADS
    // =====================================

    /**
     * Add a step using Tool object directly (type-safe!)
     *
     * Example:
     * ```
     * step("resolve", resolveTool, mapOf("text" to "에센스"))
     * ```
     */
    fun step(
        stepId: String,
        tool: Tool,
        parameters: Map<String, Any> = emptyMap(),
        condition: (suspend (ChainContext) -> Boolean)? = null,
        transformer: (suspend (ToolResult) -> Map<String, Any>)? = null
    ) {
        steps.add(ChainStep(stepId, tool.name, parameters, condition, transformer, null))
    }

    /**
     * Add a simple step using Tool object
     */
    fun step(tool: Tool, parameters: Map<String, Any> = emptyMap()) {
        step("step-${steps.size + 1}", tool.name, parameters)
    }

    /**
     * Add a step with transformation using Tool object
     *
     * Example:
     * ```
     * stepWithTransform("get-specs", getSpecsTool) { result ->
     *     mapOf("specs" to result.result)
     * }
     * ```
     */
    fun stepWithTransform(
        stepId: String,
        tool: Tool,
        parameters: Map<String, Any> = emptyMap(),
        transformer: suspend (ToolResult) -> Map<String, Any>
    ) {
        steps.add(ChainStep(stepId, tool.name, parameters, null, transformer, null))
    }

    /**
     * 🆕 Add a step with context-aware transformation using Tool object
     *
     * Example:
     * ```
     * stepWithTransform("get-specs", getSpecsTool) { result, context ->
     *     val sku = context.requireOutputOf("resolve")
     *     mapOf("specs" to result.result, "sku" to sku)
     * }
     * ```
     */
    fun stepWithTransform(
        stepId: String,
        tool: Tool,
        parameters: Map<String, Any> = emptyMap(),
        transformer: suspend (ToolResult, ChainContext) -> Map<String, Any>
    ) {
        steps.add(ChainStep(stepId, tool.name, parameters, null, null, transformer))
    }

    /**
     * 🆕 Add a step with named output (convenience method)
     *
     * Example:
     * ```
     * stepWithOutput("resolve", resolveTool, "sku", mapOf("text" to "에센스"))
     * ```
     */
    fun stepWithOutput(
        stepId: String,
        tool: Tool,
        outputName: String,
        parameters: Map<String, Any> = emptyMap()
    ) {
        steps.add(ChainStep(
            id = stepId,
            toolName = tool.name,
            parameters = parameters,
            condition = null,
            transformer = null,
            transformerWithContext = { result, context ->
                // Store output with custom name AND add to sharedData for next steps
                val output = result.result ?: ""
                context.setStepOutput(outputName, output)
                mapOf(outputName to output)  // Also add to sharedData
            }
        ))
    }

    /**
     * 🆕 Add a step with named output (string tool name version)
     */
    fun stepWithOutput(
        stepId: String,
        toolName: String,
        outputName: String,
        parameters: Map<String, Any> = emptyMap()
    ) {
        steps.add(ChainStep(
            id = stepId,
            toolName = toolName,
            parameters = parameters,
            condition = null,
            transformer = null,
            transformerWithContext = { result, context ->
                // Store output with custom name AND add to sharedData for next steps
                val output = result.result ?: ""
                context.setStepOutput(outputName, output)
                mapOf(outputName to output)  // Also add to sharedData
            }
        ))
    }

    // =====================================
    // 🆕 FLUENT PIPELINE DSL METHODS
    // =====================================

    /**
     * Create a step builder (fluent API)
     *
     * Example:
     * ```
     * +step(resolveTool) output "sku" input { mapOf("text" to "에센스") }
     * +step(getSpecsTool) input { context -> mapOf("sku" to context.requireOutputOf("sku")) }
     * ```
     *
     * Note: You must use the unary `+` operator to add the step to the chain.
     */
    fun step(tool: Tool): StepBuilder {
        return StepBuilder(tool, stepId = null)
    }

    /**
     * Process fluent step using unary plus operator
     *
     * Example:
     * ```
     * +step(resolveTool) output "sku"
     * +step(getSpecsTool) input { context -> mapOf("sku" to context.requireOutputOf("sku")) }
     * ```
     */
    operator fun StepBuilder.unaryPlus() {
        stepCounter++
        val stepId = this.stepId ?: "step-$stepCounter"

        steps.add(ChainStep(
            id = stepId,
            toolName = this.tool.name,
            parameters = emptyMap(),  // Dynamic parameters via parameterProvider
            condition = null,
            transformer = null,
            transformerWithContext = { result, context ->
                val output = result.result ?: ""

                // If output name specified, store it
                if (this.outputName != null) {
                    context.setStepOutput(this.outputName!!, output)
                    mapOf(this.outputName!! to output)
                } else {
                    // Store with step ID
                    context.setStepOutput(stepId, output)
                    emptyMap()
                }
            },
            parameterProvider = this.inputProvider  // 🆕 Use dynamic parameter provider
        ))
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