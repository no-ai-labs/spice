package io.github.spice.dsl

import io.github.spice.*

/**
 * 🔧 Plugin Tools System
 * 
 * Simplified plugin and tool chain system that works behind the scenes.
 * Users only need to declare tool("name") and the system handles the complexity.
 */

// =====================================
// PLUGIN TOOL REGISTRY
// =====================================

/**
 * Enhanced tool registry with plugin support
 */
object PluginToolRegistry {
    private val simplePlools = mutableMapOf<String, Tool>()
    private val pluginTools = mutableMapOf<String, PluginTool>()
    private val toolChains = mutableMapOf<String, ToolChain>()
    
    /**
     * Register simple tool
     */
    fun registerTool(tool: Tool) {
        simplePlools[tool.name] = tool
        ToolRegistry.register(tool)
    }
    
    /**
     * Register plugin tool
     */
    fun registerPluginTool(pluginTool: PluginTool) {
        pluginTools[pluginTool.name] = pluginTool
        // Create wrapper tool for registry
        val wrapperTool = PluginToolWrapper(pluginTool)
        ToolRegistry.register(wrapperTool)
    }
    
    /**
     * Register tool chain
     */
    fun registerToolChain(chain: ToolChain) {
        toolChains[chain.name] = chain
        // Create wrapper tool for registry
        val wrapperTool = ToolChainWrapper(chain)
        ToolRegistry.register(wrapperTool)
    }
    
    /**
     * Get tool (any type)
     */
    fun getTool(name: String): Tool? {
        return ToolRegistry.getTool(name)
    }
    
    /**
     * Clear all registries
     */
    fun clear() {
        simplePlools.clear()
        pluginTools.clear()
        toolChains.clear()
        ToolRegistry.clear()
    }
}

// =====================================
// PLUGIN TOOL DEFINITION
// =====================================

/**
 * Plugin tool interface
 */
interface PluginTool {
    val name: String
    val description: String
    val pluginId: String
    val inputMapping: Map<String, String>
    val outputMapping: Map<String, String>
    
    suspend fun execute(parameters: Map<String, Any>): ToolResult
}

/**
 * Plugin tool builder
 */
class PluginToolBuilder(private val name: String, private val pluginId: String) {
    var description: String = ""
    private val inputMappings = mutableMapOf<String, String>()
    private val outputMappings = mutableMapOf<String, String>()
    private var executor: (suspend (Map<String, Any>) -> ToolResult)? = null
    
    /**
     * Map input parameter
     */
    fun mapInput(from: String, to: String) {
        inputMappings[from] = to
    }
    
    /**
     * Map output parameter
     */
    fun mapOutput(from: String, to: String) {
        outputMappings[from] = to
    }
    
    /**
     * Set executor
     */
    fun execute(executor: suspend (Map<String, Any>) -> ToolResult) {
        this.executor = executor
    }
    
    fun build(): PluginTool {
        require(executor != null) { "Plugin tool executor is required" }
        
        return PluginToolImpl(
            name = name,
            description = description,
            pluginId = pluginId,
            inputMapping = inputMappings.toMap(),
            outputMapping = outputMappings.toMap(),
            executor = executor!!
        )
    }
}

/**
 * Plugin tool implementation
 */
class PluginToolImpl(
    override val name: String,
    override val description: String,
    override val pluginId: String,
    override val inputMapping: Map<String, String>,
    override val outputMapping: Map<String, String>,
    private val executor: suspend (Map<String, Any>) -> ToolResult
) : PluginTool {
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        // Apply input mapping
        val mappedInputs = parameters.mapKeys { (key, _) ->
            inputMapping[key] ?: key
        }
        
        // Execute
        val result = executor(mappedInputs)
        
        // Apply output mapping if successful
        if (result.success && outputMapping.isNotEmpty()) {
            val mappedData = result.metadata.mapKeys { (key, _) ->
                outputMapping[key] ?: key
            }
            return result.copy(metadata = mappedData)
        }
        
        return result
    }
}

/**
 * Plugin tool wrapper for registry
 */
class PluginToolWrapper(private val pluginTool: PluginTool) : Tool {
    override val name: String = pluginTool.name
    override val description: String = pluginTool.description
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "input" to ParameterSchema("any", "Plugin input parameters", required = true)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return pluginTool.execute(parameters)
    }
}

// =====================================
// TOOL CHAIN DEFINITION
// =====================================

/**
 * Tool chain interface
 */
interface ToolChain {
    val name: String
    val description: String
    val steps: List<ChainStep>
    
    suspend fun execute(parameters: Map<String, Any>): ToolResult
}

/**
 * Chain step definition
 */
data class ChainStep(
    val id: String,
    val toolName: String,
    val parameterMapping: Map<String, String> = emptyMap(),
    val condition: ((Map<String, Any>) -> Boolean)? = null
)

/**
 * Tool chain builder
 */
class ToolChainBuilder(private val name: String) {
    var description: String = ""
    private val steps = mutableListOf<ChainStep>()
    
    /**
     * Add step to chain
     */
    fun step(id: String, toolName: String, parameterMapping: Map<String, String> = emptyMap()) {
        steps.add(ChainStep(id, toolName, parameterMapping))
    }
    
    /**
     * Add conditional step
     */
    fun stepIf(
        id: String, 
        toolName: String, 
        condition: (Map<String, Any>) -> Boolean,
        parameterMapping: Map<String, String> = emptyMap()
    ) {
        steps.add(ChainStep(id, toolName, parameterMapping, condition))
    }
    
    fun build(): ToolChain {
        require(steps.isNotEmpty()) { "Tool chain must have at least one step" }
        
        return ToolChainImpl(
            name = name,
            description = description,
            steps = steps.toList()
        )
    }
}

/**
 * Tool chain implementation
 */
class ToolChainImpl(
    override val name: String,
    override val description: String,
    override val steps: List<ChainStep>
) : ToolChain {
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        var currentData = parameters.toMutableMap()
        val results = mutableListOf<ToolResult>()
        
        for (step in steps) {
            // Check condition
            if (step.condition != null && !step.condition.invoke(currentData)) {
                continue
            }
            
            // Get tool
            val tool = ToolRegistry.getTool(step.toolName)
                ?: return ToolResult.error("Tool not found in chain: ${step.toolName}")
            
            // Apply parameter mapping
            val stepParameters = if (step.parameterMapping.isNotEmpty()) {
                step.parameterMapping.mapValues { (_, sourceKey) ->
                    currentData[sourceKey] ?: ""
                }
            } else {
                currentData.toMap()
            }
            
            // Execute step
            val stepResult = tool.execute(stepParameters)
            results.add(stepResult)
            
            // Stop on error
            if (!stepResult.success) {
                return ToolResult.error(
                    "Chain failed at step ${step.id}: ${stepResult.error}",
                    metadata = mapOf(
                        "failed_step" to step.id,
                        "step_results" to results.size.toString()
                    )
                )
            }
            
            // Update data for next step
            currentData.putAll(stepResult.metadata)
            stepResult.result?.let { currentData["result"] = it }
        }
        
        // Return combined result
        val finalResult = results.lastOrNull()?.result ?: "Chain completed"
        val finalMetadata = currentData.toMap().mapValues { it.value.toString() }
        
        return ToolResult.success(
            result = finalResult,
            metadata = finalMetadata + mapOf(
                "chain_steps" to steps.size.toString(),
                "chain_name" to name
            )
        )
    }
}

/**
 * Tool chain wrapper for registry
 */
class ToolChainWrapper(private val toolChain: ToolChain) : Tool {
    override val name: String = toolChain.name
    override val description: String = toolChain.description
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "input" to ParameterSchema("any", "Chain input parameters", required = true)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return toolChain.execute(parameters)
    }
}

// =====================================
// DSL BUILDERS
// =====================================

/**
 * Build plugin tool
 */
fun pluginTool(name: String, pluginId: String, init: PluginToolBuilder.() -> Unit): PluginTool {
    val builder = PluginToolBuilder(name, pluginId)
    builder.init()
    val pluginTool = builder.build()
    PluginToolRegistry.registerPluginTool(pluginTool)
    return pluginTool
}

/**
 * Build tool chain
 */
fun toolChain(name: String, init: ToolChainBuilder.() -> Unit): ToolChain {
    val builder = ToolChainBuilder(name)
    builder.init()
    val chain = builder.build()
    PluginToolRegistry.registerToolChain(chain)
    return chain
}

// =====================================
// CONVENIENCE FUNCTIONS
// =====================================

/**
 * Execute plugin tool
 */
suspend fun executePluginTool(name: String, parameters: Map<String, Any>): ToolResult {
    val tool = PluginToolRegistry.getTool(name)
        ?: return ToolResult.error("Plugin tool not found: $name")
    
    return tool.execute(parameters)
}

/**
 * Execute tool chain
 */
suspend fun executeToolChain(name: String, parameters: Map<String, Any>): ToolResult {
    val tool = PluginToolRegistry.getTool(name)
        ?: return ToolResult.error("Tool chain not found: $name")
    
    return tool.execute(parameters)
}

/**
 * Check if tool exists (any type)
 */
fun toolExists(name: String): Boolean {
    return PluginToolRegistry.getTool(name) != null
}

// =====================================
// INTEGRATION WITH CORE DSL
// =====================================

/**
 * Enhanced tool function that handles all tool types
 */
fun toolEnhanced(name: String, init: CoreToolBuilder.() -> Unit): Tool {
    val builder = CoreToolBuilder(name)
    builder.init()
    val tool = builder.build()
    PluginToolRegistry.registerTool(tool)
    return tool
}

/**
 * Enhanced tool registration (internal use)
 */
internal fun registerEnhancedTool(tool: Tool) {
    PluginToolRegistry.registerTool(tool)
} 