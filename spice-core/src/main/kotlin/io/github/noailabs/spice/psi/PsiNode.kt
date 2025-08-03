package io.github.noailabs.spice.psi

/**
 * 🧱 PSI Node for internal structural representation
 * 
 * Represents a node in the PSI (Program Structure Interface) tree.
 * This is designed to be LLM-friendly and easily serializable.
 */
data class PsiNode(
    val type: String,
    val props: MutableMap<String, Any?> = mutableMapOf(),
    val children: MutableList<PsiNode> = mutableListOf(),
    val metadata: MutableMap<String, Any?> = mutableMapOf()
) {
    /**
     * Add a child node
     */
    fun add(child: PsiNode): PsiNode {
        children.add(child)
        return this
    }
    
    /**
     * Add multiple children
     */
    fun addAll(children: Collection<PsiNode>): PsiNode {
        this.children.addAll(children)
        return this
    }
    
    /**
     * Set a property
     */
    fun prop(key: String, value: Any?): PsiNode {
        props[key] = value
        return this
    }
    
    /**
     * Set metadata
     */
    fun meta(key: String, value: Any?): PsiNode {
        metadata[key] = value
        return this
    }
    
    /**
     * Find child nodes by type
     */
    fun findByType(type: String): List<PsiNode> {
        val result = mutableListOf<PsiNode>()
        if (this.type == type) result.add(this)
        children.forEach { child ->
            result.addAll(child.findByType(type))
        }
        return result
    }
    
    /**
     * Find first child by type
     */
    fun findFirstByType(type: String): PsiNode? {
        if (this.type == type) return this
        children.forEach { child ->
            child.findFirstByType(type)?.let { return it }
        }
        return null
    }
    
    /**
     * Convert to pretty string representation
     */
    override fun toString(): String = toString(0)
    
    /**
     * Convert to string with indentation
     */
    fun toString(indent: Int): String = buildString {
        val indentStr = "  ".repeat(indent)
        append(indentStr)
        append(type)
        
        if (props.isNotEmpty()) {
            append("(")
            append(props.entries.joinToString(", ") { "${it.key}=${formatValue(it.value)}" })
            append(")")
        }
        
        if (metadata.isNotEmpty()) {
            append(" [")
            append(metadata.entries.joinToString(", ") { "${it.key}=${formatValue(it.value)}" })
            append("]")
        }
        
        if (children.isNotEmpty()) {
            append(" {\n")
            children.forEach { child ->
                append(child.toString(indent + 1))
                append("\n")
            }
            append(indentStr)
            append("}")
        }
    }
    
    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"$value\""
        is List<*> -> "[${value.joinToString(", ") { formatValue(it) }}]"
        is Map<*, *> -> "{${value.entries.joinToString(", ") { "${it.key}=${formatValue(it.value)}" }}}"
        else -> value.toString()
    }
}

/**
 * DSL for building PSI nodes
 */
fun psiNode(type: String, block: PsiNode.() -> Unit = {}): PsiNode {
    return PsiNode(type).apply(block)
}

/**
 * PSI Type constants
 */
object PsiTypes {
    // Core types
    const val AGENT = "Agent"
    const val TOOL = "Tool"
    const val FLOW = "Flow"
    const val SWARM = "SwarmAgent"
    
    // Container types
    const val TOOLS = "Tools"
    const val GLOBAL_TOOLS = "GlobalTools"
    const val INLINE_TOOLS = "InlineTools"
    const val AGENT_TOOLS = "AgentTools"
    const val VECTOR_STORES = "VectorStores"
    const val MEMBERS = "Members"
    
    // Reference types
    const val TOOL_REF = "ToolRef"
    const val AGENT_REF = "AgentRef"
    const val VECTOR_STORE_REF = "VectorStoreRef"
    
    // Schema types
    const val SCHEMA = "Schema"
    const val PARAMETER = "Parameter"
    const val HANDLER = "Handler"
    
    // Metadata types
    const val PERSONA = "Persona"
    const val CONFIG = "Config"
    const val CONTEXT = "Context"
    
    // Flow types
    const val STEP = "Step"
    const val STEPS = "Steps"
}