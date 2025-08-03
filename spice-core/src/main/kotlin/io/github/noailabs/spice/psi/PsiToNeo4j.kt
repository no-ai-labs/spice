package io.github.noailabs.spice.psi

import kotlinx.serialization.json.*

/**
 * 🔄 PSI to Neo4j Graph Converter
 * 
 * Converts PSI tree structures to Neo4j-friendly node/relationship format
 * so mnemo can directly store them without needing LLM interpretation.
 */
object PsiToNeo4j {
    
    data class Neo4jNode(
        val id: String,
        val labels: List<String>,
        val properties: Map<String, Any?>
    )
    
    data class Neo4jRelationship(
        val fromId: String,
        val toId: String,
        val type: String,
        val properties: Map<String, Any?> = emptyMap()
    )
    
    data class Neo4jGraph(
        val nodes: List<Neo4jNode>,
        val relationships: List<Neo4jRelationship>
    )
    
    /**
     * Convert PSI tree to Neo4j graph format
     */
    fun PsiNode.toNeo4jGraph(): Neo4jGraph {
        val nodes = mutableListOf<Neo4jNode>()
        val relationships = mutableListOf<Neo4jRelationship>()
        
        // Convert PSI tree to graph with unique IDs
        convertNode(this, null, nodes, relationships)
        
        return Neo4jGraph(nodes, relationships)
    }
    
    private fun convertNode(
        psiNode: PsiNode,
        parentId: String?,
        nodes: MutableList<Neo4jNode>,
        relationships: MutableList<Neo4jRelationship>
    ): String {
        // Generate unique ID based on type and properties
        val nodeId = generateNodeId(psiNode)
        
        // Determine Neo4j labels based on PSI type
        val labels = when (psiNode.type) {
            PsiTypes.AGENT -> listOf("Agent", "SpiceComponent")
            PsiTypes.TOOL -> listOf("Tool", "SpiceComponent")
            PsiTypes.FLOW -> listOf("Flow", "SpiceComponent")
            PsiTypes.VECTOR_STORES -> listOf("VectorStore", "Storage")
            PsiTypes.PERSONA -> listOf("Persona", "Configuration")
            PsiTypes.SWARM -> listOf("SwarmAgent", "Agent", "SpiceComponent")
            PsiTypes.HANDLER -> listOf("Handler", "Function")
            PsiTypes.STEP -> listOf("FlowStep", "Execution")
            else -> listOf(psiNode.type.toPascalCase())
        }
        
        // Create Neo4j node
        val neo4jNode = Neo4jNode(
            id = nodeId,
            labels = labels,
            properties = psiNode.props + mapOf(
                "_psi_type" to psiNode.type,
                "_metadata" to psiNode.metadata.toJsonObject()
            )
        )
        nodes.add(neo4jNode)
        
        // Create relationship to parent if exists
        if (parentId != null) {
            val relationshipType = determineRelationshipType(psiNode.type)
            relationships.add(Neo4jRelationship(
                fromId = parentId,
                toId = nodeId,
                type = relationshipType
            ))
        }
        
        // Process children
        psiNode.children.forEach { child ->
            convertNode(child, nodeId, nodes, relationships)
        }
        
        // Handle special relationships based on node type
        when (psiNode.type) {
            PsiTypes.STEP -> {
                // Create CALLS relationship to the agent
                val agentRef = psiNode.props["agentRef"] as? String
                if (agentRef != null) {
                    relationships.add(Neo4jRelationship(
                        fromId = nodeId,
                        toId = "agent:$agentRef",
                        type = "CALLS_AGENT",
                        properties = mapOf("step_name" to psiNode.props["name"])
                    ))
                }
            }
            PsiTypes.TOOL_REF -> {
                // Create USES relationship to external tool
                val toolRef = psiNode.props["ref"] as? String
                if (toolRef != null) {
                    relationships.add(Neo4jRelationship(
                        fromId = nodeId,
                        toId = "tool:$toolRef",
                        type = "USES_TOOL",
                        properties = mapOf("external" to true)
                    ))
                }
            }
        }
        
        return nodeId
    }
    
    private fun generateNodeId(psiNode: PsiNode): String {
        return when (psiNode.type) {
            PsiTypes.AGENT -> "agent:${psiNode.props["id"] ?: System.nanoTime()}"
            PsiTypes.TOOL -> "tool:${psiNode.props["name"] ?: System.nanoTime()}"
            PsiTypes.FLOW -> "flow:${psiNode.props["id"] ?: System.nanoTime()}"
            PsiTypes.VECTOR_STORES -> "vectorstore:${psiNode.props["name"] ?: System.nanoTime()}"
            PsiTypes.STEP -> "step:${psiNode.props["name"] ?: System.nanoTime()}"
            else -> "${psiNode.type.lowercase()}:${System.nanoTime()}"
        }
    }
    
    private fun determineRelationshipType(childType: String): String {
        return when (childType) {
            PsiTypes.TOOL -> "HAS_TOOL"
            PsiTypes.TOOLS -> "HAS_TOOLS"
            PsiTypes.VECTOR_STORES -> "HAS_VECTORSTORES"
            PsiTypes.HANDLER -> "HAS_HANDLER"
            PsiTypes.STEP -> "CONTAINS_STEP"
            PsiTypes.STEPS -> "HAS_STEPS"
            PsiTypes.MEMBERS -> "HAS_MEMBERS"
            PsiTypes.AGENT_REF -> "REFERENCES_AGENT"
            PsiTypes.TOOL_REF -> "REFERENCES_TOOL"
            PsiTypes.PERSONA -> "HAS_PERSONA"
            PsiTypes.CONFIG -> "HAS_CONFIG"
            else -> "HAS_${childType.uppercase()}"
        }
    }
    
    /**
     * Convert to JSON format suitable for mnemo
     */
    fun Neo4jGraph.toMnemoFormat(): JsonObject = buildJsonObject {
        putJsonArray("nodes") {
            nodes.forEach { node ->
                addJsonObject {
                    put("id", node.id)
                    putJsonArray("labels") {
                        node.labels.forEach { add(it) }
                    }
                    putJsonObject("properties") {
                        node.properties.forEach { (key, value) ->
                            when (value) {
                                null -> put(key, JsonNull)
                                is String -> put(key, value)
                                is Number -> put(key, value)
                                is Boolean -> put(key, value)
                                is JsonObject -> put(key, value)
                                is Map<*, *> -> put(key, (value as Map<String, Any?>).toJsonObject())
                                else -> put(key, value.toString())
                            }
                        }
                    }
                }
            }
        }
        
        putJsonArray("relationships") {
            relationships.forEach { rel ->
                addJsonObject {
                    put("from", rel.fromId)
                    put("to", rel.toId)
                    put("type", rel.type)
                    if (rel.properties.isNotEmpty()) {
                        putJsonObject("properties") {
                            rel.properties.forEach { (key, value) ->
                                when (value) {
                                    null -> put(key, JsonNull)
                                    is String -> put(key, value)
                                    is Number -> put(key, value)
                                    is Boolean -> put(key, value)
                                    else -> put(key, value.toString())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun String.toPascalCase(): String {
        return split("_", "-").joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
    
    private fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
        forEach { (key, value) ->
            when (value) {
                null -> put(key, JsonNull)
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                is JsonObject -> put(key, value)
                is Map<*, *> -> put(key, (value as Map<String, Any?>).toJsonObject())
                else -> put(key, value.toString())
            }
        }
    }
}

/**
 * Extension function for easy conversion
 */
fun PsiNode.toNeo4jGraph() = PsiToNeo4j.run { toNeo4jGraph() }