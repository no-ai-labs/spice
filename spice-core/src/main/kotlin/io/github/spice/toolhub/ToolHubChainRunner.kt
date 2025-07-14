package io.github.spice.toolhub

import io.github.spice.*

/**
 * 🔗 ToolHubChainRunner - ToolChain execution engine using ToolHub
 * 
 * Chain execution engine that replaces existing ToolChainRunner to execute tools through ToolHub.
 * Provides enhanced tool management and execution tracking capabilities.
 * 
 * Usage example:
 * ```kotlin
 * val toolHub = StaticToolHub.builder()
 *     .addTool(WebSearchTool())
 *     .addTool(FileReadTool())
 *     .build()
 * 
 * val runner = ToolHubChainRunner(toolHub)
 * val result = runner.execute(chainDefinition, initialContext)
 * ```
 */
class ToolHubChainRunner(
    private val toolHub: ToolHub
) {
    
    /**
     * 🚀 Execute ToolChain using ToolHub
     */
    suspend fun execute(
        chain: ToolChainDefinition,
        initialContext: Map<String, Any> = emptyMap()
    ): ToolHubChainResult {
        val toolContext = ToolContext()
        val executionLogs = mutableListOf<ToolChainStepLog>()
        
        // Set initial parameters in context
        initialContext.forEach { (key, value) ->
            toolContext.setMetadata(key, value)
        }
        
        // Execute each step
        for ((stepIndex, step) in chain.steps.withIndex()) {
            val stepStartTime = System.currentTimeMillis()
            
            try {
                // Check conditions
                if (!step.condition(convertToolContext(toolContext))) {
                    continue
                }
                
                // Build step parameters
                val stepParameters = buildStepParameters(step, toolContext)
                
                // Execute tool
                val toolResult = toolHub.execute(
                    name = step.toolName,
                    parameters = stepParameters
                )
                
                // Record execution log
                val stepLog = ToolChainStepLog(
                    stepIndex = stepIndex,
                    toolName = step.toolName,
                    parameters = stepParameters,
                    success = toolResult.success,
                    result = if (toolResult.success) toolResult.result else null,
                    error = if (!toolResult.success) toolResult.error else null,
                    executionTimeMs = System.currentTimeMillis() - stepStartTime
                )
                executionLogs.add(stepLog)
                
                // Process results
                if (toolResult.success) {
                    // Execute success callback
                    step.onSuccess?.invoke(convertToolContext(toolContext))
                    
                    // Store result data in context
                    toolContext.setMetadata("${step.toolName}_result", toolResult.result)
                    toolContext.setMetadata("${step.toolName}_success", true)
                    
                    // Store in execution history
                    toolContext.addExecutionLog(ToolExecutionLog(
                        toolName = step.toolName,
                        parameters = stepParameters,
                        success = true,
                        result = toolResult.result,
                        executionTimeMs = stepLog.executionTimeMs
                    ))
                } else {
                    // Execute failure callback
                    step.onFailure?.invoke(convertToolContext(toolContext))
                    
                    // Break chain
                    if (step.breakOnFailure) {
                        break
                    }
                }
                
            } catch (e: Exception) {
                val stepLog = ToolChainStepLog(
                    stepIndex = stepIndex,
                    toolName = step.toolName,
                    parameters = emptyMap(),
                    success = false,
                    error = "Exception: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - stepStartTime
                )
                executionLogs.add(stepLog)
                
                if (step.breakOnFailure) {
                    break
                }
            }
        }
        
        return ToolHubChainResult(
            success = executionLogs.any { it.success },
            logs = executionLogs,
            finalContext = toolContext
        )
    }
    
    /**
     * 🔧 Build step parameters
     */
    private fun buildStepParameters(
        step: ToolChainStep,
        toolContext: ToolContext
    ): Map<String, Any> {
        // Apply parameter mapping
        return step.parameterMapping.mapValues { (_, source) ->
            when {
                // Get value from context
                source.startsWith("context.") -> {
                    val key = source.removePrefix("context.")
                    toolContext.getMetadata(key) ?: ""
                }
                // Get value from previous result
                source.startsWith("result.") -> {
                    val key = source.removePrefix("result.")
                    toolContext.getMetadata(key) ?: ""
                }
                // Get value directly from context
                else -> toolContext.getMetadata(source) ?: source
            }
        }
    }
    
    /**
     * 🔄 Convert ToolContext to existing ToolChainContext
     */
    private fun convertToolContext(toolContext: ToolContext): ToolChainContext {
        return ToolChainContext().apply {
            // Copy metadata
            toolContext.metadata.forEach { (key, value) ->
                set(key, value)
            }
            
            // Copy execution history
            toolContext.executionHistory.forEach { log ->
                // Add execution history (if ToolChainContext supports it)
            }
        }
    }
    
    /**
     * 🚨 Extract error message
     */
    private fun extractErrorMessage(error: Any?): String {
        return when (error) {
            is String -> error
            is Exception -> error.message ?: "Unknown error"
            else -> error?.toString() ?: "Unknown error"
        }
    }
}

/**
 * 📊 ToolHub chain execution result
 */
data class ToolHubChainResult(
    /**
     * Execution success status
     */
    val success: Boolean,
    
    /**
     * Number of successful steps
     */
    val successfulSteps: Int = logs.count { it.success },
    
    /**
     * Number of failed steps
     */
    val failedSteps: Int = logs.count { !it.success },
    
    /**
     * Total number of steps
     */
    val totalSteps: Int = logs.size,
    
    /**
     * Success rate
     */
    val successRate: Double = if (totalSteps > 0) (successfulSteps.toDouble() / totalSteps) * 100 else 0.0,
    
    /**
     * Execution logs
     */
    val logs: List<ToolChainStepLog>,
    
    /**
     * Final context
     */
    val finalContext: ToolContext,
    
    /**
     * Detailed log
     */
    val detailedLog: String = buildString {
        appendLine("=== ToolHub Chain Execution Summary ===")
        appendLine("Success: $success")
        appendLine("Steps: $successfulSteps/$totalSteps successful")
        appendLine("Success Rate: ${"%.1f".format(successRate)}%")
        appendLine()
        appendLine("=== Step Details ===")
        logs.forEach { log ->
            appendLine("Step ${log.stepIndex}: ${log.toolName}")
            appendLine("  Success: ${log.success}")
            if (log.success) {
                appendLine("  Result: ${log.result}")
            } else {
                appendLine("  Error: ${log.error}")
            }
            appendLine("  Time: ${log.executionTimeMs}ms")
            appendLine()
        }
    }
)

/**
 * 📝 Chain step execution log
 */
data class ToolChainStepLog(
    val stepIndex: Int,
    val toolName: String,
    val parameters: Map<String, Any>,
    val success: Boolean,
    val result: String? = null,
    val error: String? = null,
    val executionTimeMs: Long,
    
    /**
     * Execution success status
     */
    val isSuccessful: Boolean = success,
    
    /**
     * Execution summary
     */
    val summary: String = if (success) {
        "✅ $toolName completed in ${executionTimeMs}ms"
    } else {
        "❌ $toolName failed: $error (${executionTimeMs}ms)"
    }
)

/**
 * 🔧 ToolChainDefinition extension function - Execute with ToolHub
 */
suspend fun ToolChainDefinition.executeWithToolHub(
    toolHub: ToolHub,
    initialContext: Map<String, Any> = emptyMap()
): ToolHubChainResult {
    val runner = ToolHubChainRunner(toolHub)
    return runner.execute(this, initialContext)
}

/**
 * 🎯 DSL for ToolHub chain execution
 */
fun toolHubChain(toolHub: ToolHub, block: ToolChainDefinition.() -> Unit): ToolHubChainRunner {
    return ToolHubChainRunner(toolHub)
} 