package io.github.noailabs.spice.neo4j

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.swarm.SwarmAgent

import kotlinx.serialization.json.*

/**
 * 🔄 Direct DSL to Neo4j Converter
 * 
 * Converts Spice DSL components directly to Neo4j graph format
 * without the intermediate PSI representation for better efficiency.
 */
object SpiceToNeo4j {
    
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
    ) {
        fun merge(other: Neo4jGraph): Neo4jGraph {
            return Neo4jGraph(
                nodes = nodes + other.nodes,
                relationships = relationships + other.relationships
            )
        }
    }
    
    /**
     * Convert Agent directly to Neo4j graph
     */
    fun Agent.toNeo4jGraph(): Neo4jGraph {
        val nodes = mutableListOf<Neo4jNode>()
        val relationships = mutableListOf<Neo4jRelationship>()
        
        // Create agent node
        val agentId = "agent:$id"
        nodes.add(Neo4jNode(
            id = agentId,
            labels = listOf("Agent", "SpiceComponent"),
            properties = mapOf<String, Any?>(
                "id" to id,
                "name" to name,
                "description" to description,
                "createdAt" to System.currentTimeMillis()
            )
        ))
        
        // TODO: Add persona support when available in Agent interface
        
        return Neo4jGraph(nodes, relationships)
    }
    
    /**
     * Convert CoreAgentBuilder (from DSL) directly to Neo4j
     */
    fun CoreAgentBuilder.toNeo4jGraph(): Neo4jGraph {
        val nodes = mutableListOf<Neo4jNode>()
        val relationships = mutableListOf<Neo4jRelationship>()
        
        // Create agent node
        val agentId = "agent:$id"
        nodes.add(Neo4jNode(
            id = agentId,
            labels = listOf("Agent", "SpiceComponent", "DSLAgent"),
            properties = mapOf(
                "id" to id,
                "name" to name,
                "description" to description,
                "hasTools" to getAllAgentTools().isNotEmpty()
            )
        ))
        
        // Add tools
        getAllAgentTools().forEach { tool ->
            val toolGraph = when (tool) {
                is Tool -> tool.toNeo4jGraph()
                is InlineTool -> tool.toNeo4jGraph()
                is String -> {
                    // External tool reference
                    val toolId = "tool:$tool"
                    relationships.add(Neo4jRelationship(
                        fromId = agentId,
                        toId = toolId,
                        type = "USES_TOOL",
                        properties = mapOf("external" to true)
                    ))
                    null
                }
                else -> null
            }
            
            toolGraph?.let { graph ->
                nodes.addAll(graph.nodes)
                relationships.addAll(graph.relationships)
                
                // Connect tool to agent
                graph.nodes.firstOrNull()?.let { toolNode ->
                    relationships.add(Neo4jRelationship(
                        fromId = agentId,
                        toId = toolNode.id,
                        type = "HAS_TOOL"
                    ))
                }
            }
        }
        
        // Add vector stores
        getAllVectorStores().forEach { (name, config) ->
            val vsId = "vectorstore:$name"
            nodes.add(Neo4jNode(
                id = vsId,
                labels = listOf("VectorStore", "Storage"),
                properties = mapOf(
                    "name" to name,
                    "provider" to config.provider,
                    "collection" to config.collection,
                    "host" to config.host,
                    "port" to config.port
                )
            ))
            relationships.add(Neo4jRelationship(
                fromId = agentId,
                toId = vsId,
                type = "USES_VECTORSTORE"
            ))
        }
        
        return Neo4jGraph(nodes, relationships)
    }
    
    /**
     * Convert Tool directly to Neo4j
     */
    fun Tool.toNeo4jGraph(): Neo4jGraph {
        val toolId = "tool:$name"
        val node = Neo4jNode(
            id = toolId,
            labels = listOf("Tool", "SpiceComponent"),
            properties = mapOf(
                "name" to name,
                "description" to description,
                "parameters" to schema.parameters.size
            )
        )
        
        // Add parameter nodes if needed
        val nodes = mutableListOf(node)
        val relationships = mutableListOf<Neo4jRelationship>()
        
        schema.parameters.forEach { (paramName, paramType) ->
            val paramId = "param:$name:$paramName"
            nodes.add(Neo4jNode(
                id = paramId,
                labels = listOf("Parameter", "Schema"),
                properties = mapOf(
                    "name" to paramName,
                    "type" to paramType.type,
                    "required" to paramType.required,
                    "description" to paramType.description
                )
            ))
            relationships.add(Neo4jRelationship(
                fromId = toolId,
                toId = paramId,
                type = "HAS_PARAMETER"
            ))
        }
        
        return Neo4jGraph(nodes, relationships)
    }
    
    /**
     * Convert InlineTool to Neo4j
     */
    fun InlineTool.toNeo4jGraph(): Neo4jGraph {
        return (this as Tool).toNeo4jGraph()
    }
    
    /**
     * Convert Flow to Neo4j
     * Note: Due to private steps field, we can only create basic flow node
     */
    fun CoreFlow.toNeo4jGraph(): Neo4jGraph {
        val nodes = mutableListOf<Neo4jNode>()
        val relationships = mutableListOf<Neo4jRelationship>()
        
        // Create flow node
        val flowId = "flow:$id"
        nodes.add(Neo4jNode(
            id = flowId,
            labels = listOf("Flow", "SpiceComponent", "Workflow"),
            properties = mapOf(
                "id" to id,
                "name" to name,
                "description" to description
            )
        ))
        
        // TODO: Add steps when accessible via public API
        
        return Neo4jGraph(nodes, relationships)
    }
    
    /**
     * Convert SwarmAgent directly to Neo4j
     */
    fun SwarmAgent.toNeo4jGraph(): Neo4jGraph {
        val nodes = mutableListOf<Neo4jNode>()
        val relationships = mutableListOf<Neo4jRelationship>()
        
        // Create swarm node
        val swarmId = "swarm:$id"
        nodes.add(Neo4jNode(
            id = swarmId,
            labels = listOf("SwarmAgent", "Agent", "SpiceComponent"),
            properties = mapOf(
                "id" to id,
                "name" to name,
                "description" to description,
                "capabilities" to capabilities
            )
        ))
        
        // Note: memberAgents is private, so we can't access member details
        // This is a limitation of the current SwarmAgent design
        
        return Neo4jGraph(nodes, relationships)
    }
    
    /**
     * Convert entire application graph
     */
    fun buildApplicationGraph(
        agents: List<Agent> = emptyList(),
        flows: List<CoreFlow> = emptyList(),
        swarms: List<SwarmAgent> = emptyList()
    ): Neo4jGraph {
        var graph = Neo4jGraph(emptyList(), emptyList())
        
        // Add all agents
        agents.forEach { agent ->
            graph = graph.merge(agent.toNeo4jGraph())
        }
        
        // Add all flows
        flows.forEach { flow ->
            // Note: CoreFlow doesn't have toNeo4jGraph, would need CoreFlowBuilder
            // This is a limitation of the current design
        }
        
        // Add all swarms
        swarms.forEach { swarm ->
            graph = graph.merge(swarm.toNeo4jGraph())
        }
        
        // Add application root node
        val appNode = Neo4jNode(
            id = "app:spice",
            labels = listOf("Application", "Root"),
            properties = mapOf(
                "name" to "Spice Application",
                "agentCount" to agents.size,
                "flowCount" to flows.size,
                "swarmCount" to swarms.size,
                "timestamp" to System.currentTimeMillis()
            )
        )
        
        val finalNodes = listOf(appNode) + graph.nodes
        val finalRelationships = graph.relationships.toMutableList()
        
        // Connect app to top-level components
        agents.forEach { agent ->
            finalRelationships.add(Neo4jRelationship(
                fromId = "app:spice",
                toId = "agent:${agent.id}",
                type = "CONTAINS_AGENT"
            ))
        }
        
        return Neo4jGraph(finalNodes, finalRelationships)
    }
    
    /**
     * Convert to mnemo-ready JSON format
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
                                is List<*> -> putJsonArray(key) {
                                    value.forEach { item ->
                                        when (item) {
                                            is String -> add(item)
                                            is Number -> add(item)
                                            is Boolean -> add(item)
                                            else -> add(item.toString())
                                        }
                                    }
                                }
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
}

/**
 * Extension functions for easy conversion
 */
fun Agent.toNeo4jGraph() = SpiceToNeo4j.run { toNeo4jGraph() }
fun CoreAgentBuilder.toNeo4jGraph() = SpiceToNeo4j.run { toNeo4jGraph() }
fun Tool.toNeo4jGraph() = SpiceToNeo4j.run { toNeo4jGraph() }
fun CoreFlow.toNeo4jGraph() = SpiceToNeo4j.run { toNeo4jGraph() }
fun SwarmAgent.toNeo4jGraph() = SpiceToNeo4j.run { toNeo4jGraph() }

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