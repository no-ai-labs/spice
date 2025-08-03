package io.github.noailabs.spice.mcp

import io.github.noailabs.spice.*
import io.github.noailabs.spice.neo4j.*
import io.github.noailabs.spice.serialization.SpiceSerializer.toJson
import kotlinx.serialization.json.*

/**
 * 🧠 Mnemo MCP Adapter for Spice
 * 
 * Provides high-level methods to interact with mnemo MCP server
 * for saving and retrieving Spice structures and patterns.
 */
class MnemoMCPAdapter(
    serverUrl: String = "http://localhost:8080",
    apiKey: String? = null
) {
    private val mcpClient = MCPClient(serverUrl, apiKey)
    
    /**
     * Save agent directly to mnemo as Neo4j graph (no PSI)
     */
    suspend fun saveAgent(agent: Agent) {
        // Direct conversion to Neo4j graph - more efficient!
        val neo4jGraph = agent.toNeo4jGraph()
        val graphJson = SpiceToNeo4j.run { neo4jGraph.toMnemoFormat() }
        
        // Send as Neo4j-ready structure
        mcpClient.callTool("mcp_mnemo_remember", mapOf(
            "key" to "spice-agent-graph-${agent.id}",
            "content" to Json.encodeToString(JsonObject.serializer(), graphJson),
            "memory_type" to "code_pattern",
            "tags" to listOf("spice", "agent", "neo4j", "graph", agent.id)
        ))
    }
    
    /**
     * Save flow execution history
     */
    suspend fun saveFlowExecution(
        flowId: String,
        input: Comm,
        output: Comm,
        duration: Long? = null
    ) {
        val executionData = buildJsonObject {
            put("flowId", flowId)
            put("input", input.toJson())
            put("output", output.toJson())
            duration?.let { put("duration_ms", it) }
            put("timestamp", System.currentTimeMillis())
        }
        
        mcpClient.callTool("mcp_mnemo_remember", mapOf(
            "key" to "spice-flow-exec-$flowId-${System.currentTimeMillis()}",
            "content" to Json.encodeToString(JsonObject.serializer(), executionData),
            "memory_type" to "fact",
            "tags" to listOf("spice", "flow", "execution", flowId)
        ))
    }
    
    /**
     * Search for similar agent patterns
     */
    suspend fun findSimilarAgents(agent: Agent, limit: Int = 5): List<JsonObject> {
        val query = "Agent ${agent.name} ${agent.description}"
        
        val response = mcpClient.callTool("mcp_mnemo_search", mapOf(
            "query" to query,
            "memory_types" to listOf("code_pattern"),
            "limit" to limit
        ))
        
        val results = response["result"]?.jsonObject?.get("results")?.jsonArray
        return results?.map { it.jsonObject } ?: emptyList()
    }
    
    /**
     * Detect vibe coding patterns
     */
    suspend fun detectVibeCoding(agents: List<Agent>): List<String> {
        val issues = mutableListOf<String>()
        
        // Check for similar agent names
        val agentNames = agents.map { it.name }
        val duplicateNames = agentNames.groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        
        if (duplicateNames.isNotEmpty()) {
            issues.add("Duplicate agent names found: ${duplicateNames.joinToString()}")
        }
        
        // Check mnemo for known anti-patterns
        val antiPatterns = mcpClient.callTool("mcp_mnemo_search", mapOf(
            "query" to "vibe coding anti-pattern spice",
            "memory_types" to listOf("fact", "skill"),
            "limit" to 10
        ))
        
        val patternCount = antiPatterns["result"]?.jsonObject?.get("count")?.jsonPrimitive?.int ?: 0
        if (patternCount > 0) {
            issues.add("Found $patternCount known anti-patterns in mnemo")
        }
        
        return issues
    }
    
    /**
     * Save successful context injection pattern
     */
    suspend fun saveContextPattern(
        agentId: String,
        context: Map<String, Any>,
        effectiveness: Double
    ) {
        val pattern = buildJsonObject {
            put("agentId", agentId)
            put("context", context.toJsonObject())
            put("effectiveness", effectiveness)
            put("timestamp", System.currentTimeMillis())
        }
        
        mcpClient.callTool("mcp_mnemo_remember_code_pattern", mapOf(
            "pattern_name" to "context-injection-$agentId",
            "code" to Json.encodeToString(JsonObject.serializer(), pattern),
            "language" to "json",
            "description" to "Effective context injection for agent $agentId (score: $effectiveness)"
        ))
    }
    
    /**
     * Get recommended tools for an agent based on mnemo's knowledge
     */
    suspend fun getRecommendedTools(agentDescription: String): List<String> {
        val response = mcpClient.callTool("mcp_mnemo_find_pattern", mapOf(
            "pattern" to "tools for $agentDescription"
        ))
        
        // Parse recommendations from response
        val patterns = response["result"]?.jsonObject?.get("patterns")?.jsonArray
        return patterns?.mapNotNull { pattern ->
            pattern.jsonObject["name"]?.jsonPrimitive?.content
        } ?: emptyList()
    }
    
    /**
     * Close the adapter
     */
    fun close() {
        mcpClient.close()
    }
}

/**
 * Extension function for easy PSI saving
 */
suspend fun Agent.savePSIToMnemo(adapter: MnemoMCPAdapter) {
    adapter.saveAgent(this)
}

/**
 * Extension function for vibe coding check
 */
suspend fun List<Agent>.checkVibeCoding(adapter: MnemoMCPAdapter): List<String> {
    return adapter.detectVibeCoding(this)
}