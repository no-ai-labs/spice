package io.github.noailabs.spice

import io.github.noailabs.spice.swarm.*
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

/**
 * 🧪 Swarm Basic Test Suite
 * 
 * Simple tests for SwarmAgent functionality
 */
class SwarmBasicTest {
    
    private lateinit var agent1: Agent
    private lateinit var agent2: Agent
    private lateinit var agent3: Agent
    
    @BeforeEach
    fun setup() {
        // Create simple test agents
        agent1 = buildAgent {
            id = "agent-1"
            name = "Agent 1"
            description = "First test agent"
            
            handle { comm ->
                comm.reply(
                    content = "Agent 1 says: ${comm.content}",
                    from = id
                )
            }
        }
        
        agent2 = buildAgent {
            id = "agent-2"
            name = "Agent 2"
            description = "Second test agent"
            
            handle { comm ->
                comm.reply(
                    content = "Agent 2 thinks: ${comm.content}",
                    from = id
                )
            }
        }
        
        agent3 = buildAgent {
            id = "agent-3"
            name = "Agent 3"
            description = "Third test agent"
            
            handle { comm ->
                comm.reply(
                    content = "Agent 3 analyzes: ${comm.content}",
                    from = id
                )
            }
        }
    }
    
    @Test
    fun `test SwarmAgent creation`() {
        val memberAgents = mapOf(
            agent1.id to agent1,
            agent2.id to agent2,
            agent3.id to agent3
        )
        
        val coordinator = SmartSwarmCoordinator(memberAgents)
        
        val swarm = SwarmAgent(
            id = "test-swarm",
            name = "Test Swarm",
            description = "Testing swarm functionality",
            memberAgents = memberAgents,
            coordinator = coordinator
        )
        
        assertEquals("test-swarm", swarm.id)
        assertEquals("Test Swarm", swarm.name)
        assertTrue(swarm.isReady())
        assertTrue(swarm.capabilities.contains("swarm-coordination"))
    }
    
    @Test
    fun `test SwarmAgent parallel execution`() = runBlocking {
        val memberAgents = mapOf(
            agent1.id to agent1,
            agent2.id to agent2,
            agent3.id to agent3
        )
        
        val coordinator = SmartSwarmCoordinator(
            memberAgents = memberAgents,
            config = SwarmConfig(debugEnabled = true)
        )
        
        val swarm = SwarmAgent(
            id = "parallel-swarm",
            name = "Parallel Swarm",
            description = "Test parallel execution",
            memberAgents = memberAgents,
            coordinator = coordinator,
            config = SwarmConfig(debugEnabled = true)
        )
        
        val testComm = Comm(
            content = "Hello swarm!",
            from = "user",
            to = swarm.id
        )
        
        val result = swarm.processComm(testComm)
        
        assertNotNull(result)
        assertEquals(swarm.id, result.from)
        assertTrue(result.content.contains("Swarm"))
        
        // Check metadata
        assertNotNull(result.data["swarm_operation_id"])
        assertEquals("PARALLEL", result.data["strategy_type"])
        assertNotNull(result.data["execution_time_ms"])
    }
    
    @Test
    fun `test SwarmAgent sequential execution`() = runBlocking {
        val memberAgents = mapOf(
            agent1.id to agent1,
            agent2.id to agent2
        )
        
        val coordinator = SmartSwarmCoordinator(memberAgents)
        
        val swarm = SwarmAgent(
            id = "sequential-swarm",
            name = "Sequential Swarm",
            description = "Test sequential execution",
            memberAgents = memberAgents,
            coordinator = coordinator
        )
        
        val testComm = Comm(
            content = "Process this step by step",
            from = "user",
            to = swarm.id
        )
        
        val result = swarm.processComm(testComm)
        
        assertNotNull(result)
        assertTrue(result.content.contains("Sequential Processing Result"))
    }
    
    @Test
    fun `test SwarmAgent status`() {
        val memberAgents = mapOf(
            agent1.id to agent1,
            agent2.id to agent2
        )
        
        val swarm = SwarmAgent(
            id = "status-swarm",
            name = "Status Test Swarm",
            description = "Test swarm status",
            memberAgents = memberAgents,
            coordinator = SmartSwarmCoordinator(memberAgents)
        )
        
        val status = swarm.getSwarmStatus()
        
        assertEquals(2, status.totalAgents)
        assertEquals(2, status.readyAgents)
        assertEquals(0, status.activeOperations)
        assertEquals(0, status.completedOperations)
        assertEquals(0.0, status.averageSuccessRate)
    }
    
    @Test
    fun `test SwarmAgent with different task types`() = runBlocking {
        val memberAgents = mapOf(agent1.id to agent1)
        val coordinator = SmartSwarmCoordinator(memberAgents)
        
        val swarm = SwarmAgent(
            id = "task-swarm",
            name = "Task Type Swarm",
            description = "Test different task types",
            memberAgents = memberAgents,
            coordinator = coordinator
        )
        
        // Test different task keywords
        val tasks = listOf(
            "compare these options" to "CONSENSUS",
            "find the best solution" to "COMPETITION",
            "step by step process" to "SEQUENTIAL",
            "general question" to "PARALLEL"
        )
        
        for ((content, expectedStrategy) in tasks) {
            val comm = Comm(
                content = content,
                from = "user",
                to = swarm.id
            )
            
            val result = swarm.processComm(comm)
            assertEquals(expectedStrategy, result.data["strategy_type"])
        }
    }
}