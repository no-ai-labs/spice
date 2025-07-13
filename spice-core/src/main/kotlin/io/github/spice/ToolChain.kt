package io.github.spice

import kotlinx.coroutines.runBlocking

/**
 * Tool Chain System
 * 
 * Allows Tools to call other Tools, creating a chain structure.
 * Provides sequential execution and dependency management between Tools.
 */

/**
 * Tool chain execution context
 */
data class ToolChainContext(
    val chainId: String,
    val currentDepth: Int = 0,
    val maxDepth: Int = 10,
    val executionHistory: MutableList<ToolExecutionRecord> = mutableListOf(),
    val sharedData: MutableMap<String, Any> = mutableMapOf()
) {
    
    /**
     * Execution depth check
     */
    fun canExecute(): Boolean = currentDepth < maxDepth
    
    /**
     * Move to new execution level
     */
    fun nextLevel(): ToolChainContext = copy(currentDepth = currentDepth + 1)
    
    /**
     * Record tool execution
     */
    fun recordExecution(toolName: String, parameters: Map<String, Any>, result: ToolResult) {
        executionHistory.add(
            ToolExecutionRecord(
                toolName = toolName,
                parameters = parameters,
                result = result,
                executionTime = System.currentTimeMillis(),
                depth = currentDepth
            )
        )
    }
}

/**
 * Tool execution record
 */
data class ToolExecutionRecord(
    val toolName: String,
    val parameters: Map<String, Any>,
    val result: ToolResult,
    val executionTime: Long,
    val depth: Int
)

/**
 * Tool chain executor interface
 */
interface ToolChainExecutor {
    /**
     * Execute tool in chain
     */
    suspend fun executeInChain(
        toolName: String,
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult
    
    /**
     * Get registered Tool
     */
    fun getTool(name: String): Tool?
    
    /**
     * Get all registered Tools
     */
    fun getAllTools(): List<Tool>
}

/**
 * Tool chain executor implementation extending existing ToolRunner
 */
class ChainableToolExecutor(
    private val tools: Map<String, Tool>
) : ToolChainExecutor {
    
    override fun getTool(name: String): Tool? = tools[name]
    override fun getAllTools(): List<Tool> = tools.values.toList()
    
    override suspend fun executeInChain(
        toolName: String,
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        // Recursion depth check
        if (!context.canExecute()) {
            return ToolResult(
                success = false,
                error = "Maximum execution depth reached: ${context.maxDepth}"
            )
        }
        
        val tool = getTool(toolName)
            ?: return ToolResult(
                success = false,
                error = "Tool not found: $toolName"
            )
        
        return try {
            val result = if (tool is ChainableTool) {
                // Execute with chain context
                tool.executeInChain(parameters, context.nextLevel())
            } else {
                // Execute regular tool
                tool.execute(parameters)
            }
            
            // Record execution
            context.recordExecution(toolName, parameters, result)
            
            result
        } catch (e: Exception) {
            val errorResult = ToolResult(
                success = false,
                error = "Tool execution failed: ${e.message}"
            )
            
            context.recordExecution(toolName, parameters, errorResult)
            errorResult
        }
    }
}

/**
 * Chainable Tool interface
 */
interface ChainableTool : Tool {
    /**
     * Tool chain executor
     */
    var chainExecutor: ToolChainExecutor?
    
    /**
     * Execute with chain context
     */
    suspend fun executeInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult
    
    /**
     * Default execute calls chain execution
     */
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return executeInChain(parameters, ToolChainContext(chainId = "default-${System.currentTimeMillis()}"))
    }
    
    /**
     * Chain execution logic to implement in subclasses
     */
    suspend fun doExecuteInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult
}

/**
 * Tool chain definition
 */
data class ToolChainDefinition(
    val name: String,
    val description: String,
    val steps: List<ToolChainStep>
)

/**
 * Tool chain step
 */
data class ToolChainStep(
    val name: String,
    val toolName: String,
    val parameters: Map<String, Any>,
    val parameterMapping: Map<String, String> = emptyMap(), // source -> target
    val condition: ((ToolChainContext) -> Boolean)? = null,
    val onSuccess: ((ToolResult, ToolChainContext) -> Unit)? = null,
    val onFailure: ((ToolResult, ToolChainContext) -> Unit)? = null
)

/**
 * Tool chain builder
 */
class ToolChainBuilder(private val name: String) {
    private val steps = mutableListOf<ToolChainStep>()
    
    fun step(
        name: String,
        toolName: String,
        parameters: Map<String, Any> = emptyMap(),
        parameterMapping: Map<String, String> = emptyMap(),
        condition: ((ToolChainContext) -> Boolean)? = null,
        onSuccess: ((ToolResult, ToolChainContext) -> Unit)? = null,
        onFailure: ((ToolResult, ToolChainContext) -> Unit)? = null
    ): ToolChainBuilder {
        steps.add(
            ToolChainStep(
                name = name,
                toolName = toolName,
                parameters = parameters,
                parameterMapping = parameterMapping,
                condition = condition,
                onSuccess = onSuccess,
                onFailure = onFailure
            )
        )
        return this
    }
    
    fun build(): ToolChainDefinition {
        return ToolChainDefinition(
            name = name,
            description = "Tool chain: $name",
            steps = steps.toList()
        )
    }
}

/**
 * Tool chain executor
 */
class ToolChainRunner(
    private val chainExecutor: ToolChainExecutor
) {
    
    suspend fun executeChain(
        definition: ToolChainDefinition,
        initialParameters: Map<String, Any> = emptyMap()
    ): ToolChainResult {
        val context = ToolChainContext(chainId = definition.name)
        context.sharedData.putAll(initialParameters)
        
        val stepResults = mutableListOf<ToolResult>()
        
        for (step in definition.steps) {
            // Check condition
            if (step.condition != null && !step.condition.invoke(context)) {
                continue
            }
            
            // Map parameters
            val stepParameters = buildStepParameters(step, context)
            
            // Execute step
            val result = chainExecutor.executeInChain(step.toolName, stepParameters, context)
            stepResults.add(result)
            
            // Handle result
            if (result.success) {
                step.onSuccess?.invoke(result, context)
                // Store result data in shared context
                result.data.forEach { (key, value) ->
                    context.sharedData[key] = value
                }
            } else {
                step.onFailure?.invoke(result, context)
                // Stop chain on failure
                break
            }
        }
        
        return ToolChainResult(
            chainName = definition.name,
            success = stepResults.all { it.success },
            stepResults = stepResults,
            executionHistory = context.executionHistory.toList(),
            finalData = context.sharedData.toMap()
        )
    }
    
    private fun buildStepParameters(step: ToolChainStep, context: ToolChainContext): Map<String, Any> {
        val parameters = step.parameters.toMutableMap()
        
        // Apply parameter mapping
        step.parameterMapping.forEach { (source, target) ->
            context.sharedData[source]?.let { value ->
                parameters[target] = value
            }
        }
        
        // Extract from previous results
        step.parameterMapping.forEach { (source, target) ->
            if (source.startsWith("result.")) {
                val resultKey = source.removePrefix("result.")
                context.executionHistory.lastOrNull()?.result?.data?.get(resultKey)?.let { value ->
                    parameters[target] = value
                }
            }
        }
        
        // Copy from current parameters
        step.parameterMapping.forEach { (source, target) ->
            if (parameters.containsKey(source)) {
                parameters[target] = parameters[source]!!
            }
        }
        
        return parameters
    }
}

/**
 * Tool chain execution result
 */
data class ToolChainResult(
    val chainName: String,
    val success: Boolean,
    val stepResults: List<ToolResult>,
    val executionHistory: List<ToolExecutionRecord>,
    val finalData: Map<String, Any>
)

// Example chainable tool implementations

/**
 * Data processing Tool (calls other Tools in chain)
 */
class DataProcessingTool : ChainableTool {
    override val name: String = "data_processor"
    override val description: String = "Processes data using multiple tools"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "input_data" to ParameterSchema("string", "Input data to process", required = true),
            "processing_steps" to ParameterSchema("array", "Array of processing steps", required = true)
        )
    )
    
    override var chainExecutor: ToolChainExecutor? = null
    
    override suspend fun executeInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        val inputData = parameters["input_data"] as? String
            ?: return ToolResult(success = false, error = "input_data parameter required")
        
        val processingSteps = parameters["processing_steps"] as? List<String> ?: emptyList()
        
        var currentData = inputData
        val results = mutableListOf<String>()
        
        for (step in processingSteps) {
            val result = when (step) {
                "validate" -> {
                    chainExecutor?.executeInChain(
                        "data_validator",
                        mapOf("data" to currentData),
                        context
                    )
                }
                "clean" -> {
                    chainExecutor?.executeInChain(
                        "data_cleaner",
                        mapOf("data" to currentData),
                        context
                    )
                }
                "transform" -> {
                    chainExecutor?.executeInChain(
                        "data_transformer",
                        mapOf("data" to currentData),
                        context
                    )
                }
                else -> {
                    ToolResult(success = false, error = "Unknown processing step: $step")
                }
            }
            
            if (result?.success == true) {
                currentData = result.data["result"] as? String ?: currentData
                results.add("Step '$step' completed successfully")
            } else {
                return ToolResult(
                    success = false,
                    error = "Processing step '$step' failed: ${result?.error}"
                )
            }
        }
        
        return ToolResult(
            success = true,
            data = mapOf(
                "processed_data" to currentData,
                "processing_log" to results
            )
        )
    }
    
    override suspend fun doExecuteInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        return executeInChain(parameters, context)
    }
}

/**
 * Data validation tool
 */
class DataValidationTool : ChainableTool {
    override val name: String = "data_validator"
    override val description: String = "Validates data quality and structure"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "data" to ParameterSchema("any", "Data to validate", required = true),
            "validation_rules" to ParameterSchema("array", "Validation rules to apply", required = true)
        )
    )
    
    override var chainExecutor: ToolChainExecutor? = null
    
    override suspend fun executeInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult(success = false, error = "data parameter required")
        
        // Simple validation logic
        val isValid = data.isNotBlank() && data.length > 5
        
        return if (isValid) {
            ToolResult(
                success = true,
                data = mapOf(
                    "result" to data,
                    "validation_status" to "valid"
                )
            )
        } else {
            ToolResult(
                success = false,
                error = "Data validation failed: data too short or empty"
            )
        }
    }
    
    override suspend fun doExecuteInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        return executeInChain(parameters, context)
    }
}

/**
 * Data cleaning tool
 */
class DataCleaningTool : ChainableTool {
    override val name: String = "data_cleaner"
    override val description: String = "Cleans and preprocesses data"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "data" to ParameterSchema("any", "Data to clean", required = true),
            "cleaning_rules" to ParameterSchema("array", "Cleaning rules to apply", required = true)
        )
    )
    
    override var chainExecutor: ToolChainExecutor? = null
    
    override suspend fun executeInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult(success = false, error = "data parameter required")
        
        // Data cleaning work
        val cleanedData = data.trim()
            .replace(Regex("\\s+"), " ")  // Remove consecutive spaces
            .replace(Regex("[^a-zA-Z0-9가-힣\\s.,!?]"), "") // Remove special characters
        
        return ToolResult(
            success = true,
            data = mapOf(
                "result" to cleanedData,
                "original_length" to data.length,
                "cleaned_length" to cleanedData.length
            )
        )
    }
    
    override suspend fun doExecuteInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        return executeInChain(parameters, context)
    }
}

/**
 * Data transformation Tool
 */
class DataTransformationTool : ChainableTool {
    override val name: String = "data_transformer"
    override val description: String = "Transforms data to target format"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to transform", required = true),
            "target_format" to ParameterSchema("string", "Target format for transformation", required = true)
        )
    )
    
    override var chainExecutor: ToolChainExecutor? = null
    
    override suspend fun executeInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult(success = false, error = "data parameter required")
        
        val targetFormat = parameters["target_format"] as? String ?: "uppercase"
        
        val transformedData = when (targetFormat) {
            "uppercase" -> data.uppercase()
            "lowercase" -> data.lowercase()
            "json" -> """{"data": "$data"}"""
            "csv" -> data.replace(" ", ",")
            else -> data
        }
        
        return ToolResult(
            success = true,
            data = mapOf(
                "result" to transformedData,
                "format" to targetFormat
            )
        )
    }
    
    override suspend fun doExecuteInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        return executeInChain(parameters, context)
    }
}

/**
 * Data analysis Tool
 */
class DataAnalysisTool : ChainableTool {
    override val name: String = "data_analyzer"
    override val description: String = "Analyzes data and provides insights"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to analyze", required = true)
        )
    )
    
    override var chainExecutor: ToolChainExecutor? = null
    
    override suspend fun executeInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult(success = false, error = "data parameter required")
        
        // Simple data analysis
        val wordCount = data.split("\\s+".toRegex()).size
        val charCount = data.length
        val hasNumbers = data.any { it.isDigit() }
        
        return ToolResult(
            success = true,
            data = mapOf(
                "word_count" to wordCount,
                "char_count" to charCount,
                "has_numbers" to hasNumbers,
                "analysis_summary" to "Data contains $wordCount words and $charCount characters"
            )
        )
    }
    
    override suspend fun doExecuteInChain(
        parameters: Map<String, Any>,
        context: ToolChainContext
    ): ToolResult {
        return executeInChain(parameters, context)
    }
}

// DSL for tool chain creation
fun toolChain(name: String, init: ToolChainBuilder.() -> Unit): ToolChainDefinition {
    val builder = ToolChainBuilder(name)
    builder.init()
    return builder.build()
} 