package io.github.noailabs.spice.psi

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import kotlinx.serialization.json.*

/**
 * 🧠 Mnemo Integration for Spice PSI
 * 
 * Provides utilities to save Spice structures and execution patterns to mnemo
 * for intelligent context management and vibe coding prevention.
 */
object MnemoIntegration {
    
    /**
     * Save agent structure to mnemo
     */
    fun Agent.saveToMnemo(mnemo: MnemoClient) {
        val psi = SpicePsiBuilder.run { this@saveToMnemo.toPsi() }
        val json = PsiSerializer.run { psi.toMnemoFormat() }
        
        mnemo.remember(
            key = "spice-agent-${this.id}",
            content = json,
            memory_type = "code_pattern",
            tags = listOf("spice", "agent", "structure", "dsl")
        )
    }
    
    /**
     * Save flow execution pattern
     */
    fun saveFlowExecution(
        flow: CoreFlow,
        input: Comm,
        output: Comm,
        mnemo: MnemoClient
    ) {
        val flowPsi = SpicePsiBuilder.run { flow.toPsi() }
        
        val executionPsi = psiNode("FlowExecution") {
            prop("flowId", flow.id)
            prop("timestamp", System.currentTimeMillis())
            
            add(psiNode("Input") {
                prop("content", input.content)
                prop("from", input.from)
            })
            
            add(psiNode("Output") {
                prop("content", output.content)
                prop("success", true)
            })
            
            add(flowPsi) // Include flow structure
        }
        
        mnemo.remember(
            key = "flow-execution-${flow.id}-${System.currentTimeMillis()}",
            content = PsiSerializer.run { executionPsi.toMnemoFormat() },
            memory_type = "fact",
            tags = listOf("spice", "flow", "execution", "history")
        )
    }
    
    /**
     * Save context injection pattern
     */
    fun saveContextInjection(
        agentId: String,
        injectionPoint: String,
        context: Map<String, Any>,
        mnemo: MnemoClient
    ) {
        val contextPsi = psiNode("ContextInjection") {
            prop("agentId", agentId)
            prop("injectionPoint", injectionPoint)
            prop("timestamp", System.currentTimeMillis())
            
            add(psiNode("Context") {
                context.forEach { (key, value) ->
                    prop(key, value)
                }
            })
        }
        
        mnemo.remember(
            key = "context-injection-$agentId-${System.currentTimeMillis()}",
            content = PsiSerializer.run { contextPsi.toMnemoFormat() },
            memory_type = "skill",
            tags = listOf("spice", "context", "injection", agentId)
        )
    }
    
    /**
     * Detect vibe coding patterns in agent definitions
     */
    fun detectVibeCoding(agents: List<Agent>, mnemo: MnemoClient): List<VibeCodingPattern> {
        val patterns = mutableListOf<VibeCodingPattern>()
        
        // Convert all agents to PSI
        val agentPsis = agents.map { agent ->
            agent to SpicePsiBuilder.run { agent.toPsi() }
        }
        
        // Check for duplicate tool usage
        val toolUsage = mutableMapOf<String, MutableList<String>>()
        agentPsis.forEach { (agent, psi) ->
            psi.findByType(PsiTypes.TOOL).forEach { toolNode ->
                val toolName = toolNode.props["name"] as? String ?: return@forEach
                toolUsage.getOrPut(toolName) { mutableListOf() }.add(agent.id)
            }
        }
        
        // Find tools used by multiple agents (potential duplication)
        toolUsage.filter { it.value.size > 1 }.forEach { (tool, agents) ->
            patterns.add(VibeCodingPattern(
                type = "duplicate_tool_usage",
                description = "Tool '$tool' is used by multiple agents: ${agents.joinToString()}",
                severity = "medium"
            ))
        }
        
        // Check for similar agent names (potential confusion)
        agents.forEach { agent1 ->
            agents.forEach { agent2 ->
                if (agent1.id != agent2.id && 
                    agent1.name.lowercase().contains(agent2.name.lowercase())) {
                    patterns.add(VibeCodingPattern(
                        type = "similar_agent_names",
                        description = "Agents '${agent1.name}' and '${agent2.name}' have similar names",
                        severity = "low"
                    ))
                }
            }
        }
        
        // Save detected patterns to mnemo
        if (patterns.isNotEmpty()) {
            mnemo.remember(
                key = "vibe-coding-patterns-${System.currentTimeMillis()}",
                content = Json.encodeToString(JsonArray.serializer(), 
                    JsonArray(patterns.map { JsonPrimitive(it.toString()) })
                ),
                memory_type = "fact",
                tags = listOf("spice", "vibe-coding", "analysis")
            )
        }
        
        return patterns
    }
}

/**
 * Vibe coding pattern detection result
 */
data class VibeCodingPattern(
    val type: String,
    val description: String,
    val severity: String
)

/**
 * Mock MnemoClient interface
 * Replace with actual mnemo MCP client implementation
 */
interface MnemoClient {
    fun remember(
        key: String,
        content: String,
        memory_type: String = "fact",
        tags: List<String> = emptyList()
    )
    
    fun recall(key: String): String?
    
    fun search(
        query: String,
        memory_types: List<String>? = null,
        limit: Int = 10
    ): List<Memory>
}

data class Memory(
    val key: String,
    val content: String,
    val memory_type: String,
    val tags: List<String>
)