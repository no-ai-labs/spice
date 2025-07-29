package io.github.noailabs.spice.model

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.ToolSchema
import io.github.noailabs.spice.ParameterSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

/**
 * 🔧 Serializable representation of a Tool
 * 
 * AgentTool serves as an intermediate representation that can be:
 * - Serialized to/from YAML/JSON
 * - Created from GUI or programmatically
 * - Converted to/from DSL-based tools
 * - Stored and loaded for reuse
 */
@Serializable
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterSchema> = emptyMap(),
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    
    /**
     * Implementation type identifier for deserialization
     * Examples: "kotlin-function", "http-api", "script", "custom"
     */
    val implementationType: String = "kotlin-function",
    
    /**
     * Implementation details (varies by type)
     * For "http-api": {"url": "...", "method": "POST"}
     * For "script": {"language": "python", "code": "..."}
     */
    val implementationDetails: Map<String, String> = emptyMap(),
    
    /**
     * The actual implementation function (transient, not serialized)
     */
    @Transient
    private val implementation: (suspend (Map<String, Any>) -> ToolResult)? = null
) {
    
    /**
     * Convert to executable Tool interface
     */
    fun toTool(): Tool = AgentToolAdapter(this)
    
    /**
     * Create a copy with implementation
     */
    fun withImplementation(impl: suspend (Map<String, Any>) -> ToolResult): AgentTool {
        return copy(implementation = impl)
    }
    
    /**
     * Execute the tool (if implementation is available)
     */
    suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return implementation?.invoke(parameters)
            ?: ToolResult.error("No implementation available for tool '$name'")
    }
    
    /**
     * Check if this tool has an executable implementation
     */
    fun hasImplementation(): Boolean = implementation != null
    
    /**
     * Get tool schema
     */
    fun getSchema(): ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = parameters
    )
}

/**
 * 🔄 Adapter to convert AgentTool to Tool interface
 */
internal class AgentToolAdapter(
    private val agentTool: AgentTool
) : Tool {
    override val name: String = agentTool.name
    override val description: String = agentTool.description
    override val schema: ToolSchema = agentTool.getSchema()
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return agentTool.execute(parameters)
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return agentTool.hasImplementation()
    }
}

/**
 * 🏗️ Builder for AgentTool
 */
class AgentToolBuilder(private val name: String) {
    var description: String = ""
    private val parameters = mutableMapOf<String, ParameterSchema>()
    private val tags = mutableListOf<String>()
    private val metadata = mutableMapOf<String, String>()
    var implementationType: String = "kotlin-function"
    private val implementationDetails = mutableMapOf<String, String>()
    private var implementation: (suspend (Map<String, Any>) -> ToolResult)? = null
    
    /**
     * Set description
     */
    fun description(desc: String) {
        description = desc
    }
    
    /**
     * Add parameter
     */
    fun parameter(name: String, type: String, description: String = "", required: Boolean = true) {
        parameters[name] = ParameterSchema(
            type = type,
            description = description,
            required = required
        )
    }
    
    /**
     * Add multiple parameters
     */
    fun parameters(block: ParametersBuilder.() -> Unit) {
        val builder = ParametersBuilder()
        builder.block()
        parameters.putAll(builder.build())
    }
    
    /**
     * Add tags for categorization
     */
    fun tags(vararg tagList: String) {
        tags.addAll(tagList)
    }
    
    /**
     * Add metadata
     */
    fun metadata(key: String, value: String) {
        metadata[key] = value
    }
    
    /**
     * Set implementation type and details
     */
    fun implementationType(type: String, details: Map<String, String> = emptyMap()) {
        implementationType = type
        implementationDetails.clear()
        implementationDetails.putAll(details)
    }
    
    /**
     * Set Kotlin function implementation
     */
    fun implement(handler: suspend (Map<String, Any>) -> ToolResult) {
        implementation = handler
        implementationType = "kotlin-function"
    }
    
    /**
     * Build the AgentTool
     */
    fun build(): AgentTool {
        return AgentTool(
            name = name,
            description = description,
            parameters = parameters.toMap(),
            tags = tags.toList(),
            metadata = metadata.toMap(),
            implementationType = implementationType,
            implementationDetails = implementationDetails.toMap(),
            implementation = implementation
        )
    }
}

/**
 * 🔧 Parameters builder helper
 */
class ParametersBuilder {
    private val parameters = mutableMapOf<String, ParameterSchema>()
    
    fun string(name: String, description: String = "", required: Boolean = true) {
        parameters[name] = ParameterSchema("string", description, required)
    }
    
    fun number(name: String, description: String = "", required: Boolean = true) {
        parameters[name] = ParameterSchema("number", description, required)
    }
    
    fun boolean(name: String, description: String = "", required: Boolean = true) {
        parameters[name] = ParameterSchema("boolean", description, required)
    }
    
    fun array(name: String, description: String = "", required: Boolean = true) {
        parameters[name] = ParameterSchema("array", description, required)
    }
    
    fun `object`(name: String, description: String = "", required: Boolean = true) {
        parameters[name] = ParameterSchema("object", description, required)
    }
    
    internal fun build() = parameters.toMap()
}

/**
 * 🏗️ DSL function to build AgentTool
 */
fun agentTool(name: String, block: AgentToolBuilder.() -> Unit): AgentTool {
    val builder = AgentToolBuilder(name)
    builder.block()
    return builder.build()
}

/**
 * 🔄 Extension function to convert Tool to AgentTool
 */
fun Tool.toAgentTool(
    tags: List<String> = emptyList(),
    metadata: Map<String, String> = emptyMap()
): AgentTool {
    return AgentTool(
        name = this.name,
        description = this.description,
        parameters = this.schema.parameters,
        tags = tags,
        metadata = metadata,
        implementationType = "tool-reference",
        implementationDetails = mapOf("originalType" to (this::class.simpleName ?: "Unknown"))
    ).withImplementation { params -> this.execute(params) }
} 