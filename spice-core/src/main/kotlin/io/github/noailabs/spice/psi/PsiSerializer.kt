package io.github.noailabs.spice.psi

import kotlinx.serialization.json.*
import io.github.noailabs.spice.serialization.SpiceSerializer

/**
 * 🔄 PSI Serialization for mnemo storage and exchange
 * 
 * Converts PSI trees to various formats for storage, visualization, and LLM interaction.
 */
object PsiSerializer {
    
    /**
     * Convert PSI tree to JSON format
     */
    fun PsiNode.toJson(): JsonObject = buildJsonObject {
        put("type", type)
        
        if (props.isNotEmpty()) {
            putJsonObject("props") {
                props.forEach { (key, value) ->
                    put(key, SpiceSerializer.run { value.toJsonElement() })
                }
            }
        }
        
        if (metadata.isNotEmpty()) {
            putJsonObject("metadata") {
                metadata.forEach { (key, value) ->
                    put(key, SpiceSerializer.run { value.toJsonElement() })
                }
            }
        }
        
        if (children.isNotEmpty()) {
            putJsonArray("children") {
                children.forEach { child ->
                    add(child.toJson())
                }
            }
        }
    }
    
    /**
     * Create PSI tree from JSON
     */
    fun fromJson(json: JsonObject): PsiNode {
        val node = PsiNode(
            type = json["type"]?.jsonPrimitive?.content ?: "Unknown"
        )
        
        // Props
        json["props"]?.jsonObject?.forEach { (key, value) ->
            node.prop(key, jsonElementToValue(value))
        }
        
        // Metadata
        json["metadata"]?.jsonObject?.forEach { (key, value) ->
            node.meta(key, jsonElementToValue(value))
        }
        
        // Children
        json["children"]?.jsonArray?.forEach { childJson ->
            node.add(fromJson(childJson.jsonObject))
        }
        
        return node
    }
    
    /**
     * Convert PSI to mnemo-friendly format
     */
    fun PsiNode.toMnemoFormat(): String {
        val json = this.toJson()
        return Json.encodeToString(JsonObject.serializer(), json)
    }
    
    /**
     * Parse PSI from mnemo format
     */
    fun fromMnemoFormat(data: String): PsiNode {
        val json = Json.decodeFromString(JsonObject.serializer(), data)
        return fromJson(json)
    }
    
    /**
     * Convert PSI to Mermaid diagram for visualization
     */
    fun PsiNode.toMermaid(): String = buildString {
        appendLine("graph TD")
        appendMermaidNode(this@toMermaid, "root", mutableSetOf())
    }
    
    private fun StringBuilder.appendMermaidNode(
        node: PsiNode,
        parentId: String,
        visited: MutableSet<String>
    ) {
        val nodeId = "${parentId}_${node.type}_${node.hashCode()}"
        
        if (nodeId in visited) return
        visited.add(nodeId)
        
        // Node label
        val label = buildString {
            append(node.type)
            if (node.props.isNotEmpty()) {
                val mainProps = node.props.entries
                    .take(3)
                    .joinToString(", ") { "${it.key}=${formatMermaidValue(it.value)}" }
                append("<br/>$mainProps")
                if (node.props.size > 3) append("...")
            }
        }
        
        // Node definition
        appendLine("    $nodeId[\"$label\"]")
        
        // Connect to parent
        if (parentId != "root") {
            appendLine("    $parentId --> $nodeId")
        }
        
        // Process children
        node.children.forEach { child ->
            appendMermaidNode(child, nodeId, visited)
        }
    }
    
    private fun formatMermaidValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> if (value.length > 20) "${value.take(20)}..." else value
        is List<*> -> "[${value.size} items]"
        is Map<*, *> -> "{${value.size} entries}"
        else -> value.toString()
    }
    
    /**
     * Convert PSI to GraphML format for advanced visualization
     */
    fun PsiNode.toGraphML(): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">")
        appendLine("  <key id=\"type\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>")
        appendLine("  <key id=\"props\" for=\"node\" attr.name=\"props\" attr.type=\"string\"/>")
        appendLine("  <graph id=\"G\" edgedefault=\"directed\">")
        
        val nodeIds = mutableMapOf<PsiNode, String>()
        var nodeCounter = 0
        
        // First pass: create all nodes
        fun collectNodes(node: PsiNode) {
            val nodeId = "n${nodeCounter++}"
            nodeIds[node] = nodeId
            
            appendLine("    <node id=\"$nodeId\">")
            appendLine("      <data key=\"type\">${node.type}</data>")
            if (node.props.isNotEmpty()) {
                val propsStr = node.props.entries
                    .joinToString(", ") { "${it.key}=${it.value}" }
                appendLine("      <data key=\"props\">$propsStr</data>")
            }
            appendLine("    </node>")
            
            node.children.forEach { collectNodes(it) }
        }
        
        collectNodes(this@toGraphML)
        
        // Second pass: create edges
        var edgeCounter = 0
        fun createEdges(node: PsiNode) {
            val parentId = nodeIds[node]!!
            node.children.forEach { child ->
                val childId = nodeIds[child]!!
                appendLine("    <edge id=\"e${edgeCounter++}\" source=\"$parentId\" target=\"$childId\"/>")
                createEdges(child)
            }
        }
        
        createEdges(this@toGraphML)
        
        appendLine("  </graph>")
        appendLine("</graphml>")
    }
    
    /**
     * Convert PSI to compact string format for LLM context
     */
    fun PsiNode.toLLMFormat(): String = buildString {
        appendLLMNode(this@toLLMFormat, 0)
    }
    
    private fun StringBuilder.appendLLMNode(node: PsiNode, depth: Int) {
        val indent = "  ".repeat(depth)
        append(indent)
        append(node.type)
        
        // Include important props inline
        val importantProps = listOf("id", "name", "type", "source")
        val inlineProps = node.props.entries
            .filter { it.key in importantProps && it.value != null }
            .joinToString(" ") { "${it.key}:${formatLLMValue(it.value)}" }
        
        if (inlineProps.isNotEmpty()) {
            append(" [$inlineProps]")
        }
        
        appendLine()
        
        // Children
        node.children.forEach { child ->
            appendLLMNode(child, depth + 1)
        }
    }
    
    private fun formatLLMValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> if (value.contains(" ")) "\"$value\"" else value
        is List<*> -> value.joinToString(",")
        else -> value.toString()
    }
    
    // Helper function to convert JsonElement back to value
    private fun jsonElementToValue(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonArray -> element.map { jsonElementToValue(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToValue(v) }
    }
}