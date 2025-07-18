package io.github.spice

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * 💫 Communication Hub - Central Messaging System
 * 
 * The heart of Spice Framework's communication infrastructure.
 * Manages agent registration, message routing, and system analytics.
 */
object CommHub {
    private val agents = mutableMapOf<String, SmartAgent>()
    private val tools = mutableMapOf<String, ExternalTool>()
    private val channels = mutableMapOf<String, Channel<Comm>>()
    private val history = mutableListOf<Comm>()
    
    // ===== Agent Management =====
    
    /**
     * Register a smart agent
     */
    fun register(agent: SmartAgent): SmartAgent {
        agents[agent.id] = agent
        channels[agent.id] = Channel(capacity = 1000)
        println("✅ Registered agent: ${agent.name} (${agent.id})")
        return agent
    }
    
    /**
     * Get agent by ID
     */
    fun agent(id: String): SmartAgent? = agents[id]
    
    /**
     * Get all registered agents
     */
    fun agents(): List<SmartAgent> = agents.values.toList()
    
    /**
     * Unregister an agent
     */
    fun unregister(agentId: String) {
        agents.remove(agentId)
        channels[agentId]?.close()
        channels.remove(agentId)
    }
    
    // ===== Tool Management =====
    
    /**
     * Register an external tool
     */
    fun registerTool(tool: ExternalTool): ExternalTool {
        tools[tool.id] = tool
        println("✅ Registered external tool: ${tool.name}")
        return tool
    }
    
    /**
     * Get tool by ID
     */
    fun tool(id: String): ExternalTool? = tools[id]
    
    /**
     * Get all registered tools
     */
    fun tools(): List<ExternalTool> = tools.values.toList()
    
    // ===== Communication =====
    
    /**
     * Send a message
     */
    suspend fun send(comm: Comm): CommResult {
        try {
            // Store in history
            history.add(comm)
            
            // Route to recipient
            comm.to?.let { recipient ->
                channels[recipient]?.send(comm)
            }
            
            println("📤 ${comm.from} → ${comm.to}: ${comm.content.take(50)}...")
            
            return CommResult.success(comm.id, listOfNotNull(comm.to))
        } catch (e: Exception) {
            return CommResult.failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Receive messages for agent
     */
    suspend fun receive(agentId: String): Comm? {
        return channels[agentId]?.tryReceive()?.getOrNull()
    }
    
    /**
     * Receive with timeout
     */
    suspend fun receiveTimeout(agentId: String, timeoutMs: Long): Comm? {
        return withTimeoutOrNull(timeoutMs) {
            channels[agentId]?.receive()
        }
    }
    
    /**
     * Broadcast to multiple agents
     */
    suspend fun broadcast(comm: Comm, recipients: List<String>): List<CommResult> {
        return recipients.map { recipient ->
            send(comm.copy(to = recipient))
        }
    }
    
    /**
     * Broadcast to all agents except sender
     */
    suspend fun broadcastAll(comm: Comm): List<CommResult> {
        val recipients = agents.keys.filter { it != comm.from }
        return broadcast(comm, recipients)
    }
    
    // ===== History & Analytics =====
    
    /**
     * Get communication history
     */
    fun history(limit: Int = 100): List<Comm> = 
        history.takeLast(limit)
    
    /**
     * Get history for specific agent
     */
    fun historyFor(agentId: String, limit: Int = 100): List<Comm> =
        history.filter { it.from == agentId || it.to == agentId }.takeLast(limit)
    
    /**
     * Get system analytics
     */
    fun getAnalytics(): CommAnalytics = CommAnalytics(
        totalComms = history.size,
        activeAgents = agents.count { it.value.active },
        toolUsage = history.count { it.type == CommType.TOOL_CALL },
        lastActivity = history.lastOrNull()?.timestamp ?: 0
    )
    
    /**
     * Get agent-specific analytics
     */
    fun getAgentAnalytics(agentId: String): AgentAnalytics {
        val sent = history.count { it.from == agentId }
        val received = history.count { it.to == agentId }
        val toolCalls = history.count { it.from == agentId && it.type == CommType.TOOL_CALL }
        
        return AgentAnalytics(
            agentId = agentId,
            messagesSent = sent,
            messagesReceived = received,
            toolCalls = toolCalls,
            active = agents[agentId]?.active ?: false
        )
    }
    
    // ===== System Management =====
    
    /**
     * Clear all data
     */
    fun reset() {
        agents.clear()
        tools.clear()
        channels.values.forEach { it.close() }
        channels.clear()
        history.clear()
    }
    
    /**
     * Get system status
     */
    fun status(): SystemStatus = SystemStatus(
        registeredAgents = agents.size,
        activeAgents = agents.count { it.value.active },
        registeredTools = tools.size,
        messagesInHistory = history.size,
        channelsOpen = channels.size
    )
}


/**
 * System-wide analytics
 */
data class CommAnalytics(
    val totalComms: Int,
    val activeAgents: Int,
    val toolUsage: Int,
    val lastActivity: Long
)

/**
 * Agent-specific analytics
 */
data class AgentAnalytics(
    val agentId: String,
    val messagesSent: Int,
    val messagesReceived: Int,
    val toolCalls: Int,
    val active: Boolean
)

/**
 * System status
 */
data class SystemStatus(
    val registeredAgents: Int,
    val activeAgents: Int,
    val registeredTools: Int,
    val messagesInHistory: Int,
    val channelsOpen: Int
)