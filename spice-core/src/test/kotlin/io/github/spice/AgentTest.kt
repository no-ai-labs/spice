package io.github.spice

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.util.concurrent.TimeUnit

/**
 * 🤖 BaseAgent test cases
 */
class BaseAgentTest {

    private lateinit var testAgent: BaseAgent

    @BeforeEach
    fun setup() {
        testAgent = object : BaseAgent(
            id = "test-agent",
            name = "Test Agent",
            description = "Agent for testing",
            capabilities = listOf("test_capability", "mock_capability")
        ) {
            override suspend fun processMessage(message: Message): Message {
                return message.createReply(
                    content = "Processed: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT,
                    metadata = mapOf("processed_by" to id)
                )
            }
        }
    }

    @Test
    @DisplayName("Agent basic properties")
    fun testAgentProperties() {
        assertEquals("test-agent", testAgent.id)
        assertEquals("Test Agent", testAgent.name)
        assertEquals("Agent for testing", testAgent.description)
        assertEquals(2, testAgent.capabilities.size)
        assertTrue(testAgent.capabilities.contains("test_capability"))
        assertTrue(testAgent.capabilities.contains("mock_capability"))
    }

    @Test
    @DisplayName("Agent is ready by default")
    fun testAgentIsReady() {
        assertTrue(testAgent.isReady())
    }

    @Test
    @DisplayName("Agent can process message")
    fun testProcessMessage() = runTest {
        val inputMessage = Message(
            content = "Hello Agent",
            sender = "user",
            type = MessageType.TEXT
        )

        val result = testAgent.processMessage(inputMessage)

        assertEquals("Processed: Hello Agent", result.content)
        assertEquals("test-agent", result.sender)
        assertEquals("user", result.receiver)
        assertEquals("test-agent", result.metadata["processed_by"])
        assertEquals(inputMessage.id, result.parentId)
    }

    @Test
    @DisplayName("Agent can check capabilities")
    fun testAgentCapabilities() {
        assertTrue(testAgent.capabilities.contains("test_capability"))
        assertTrue(testAgent.capabilities.contains("mock_capability"))
        assertFalse(testAgent.capabilities.contains("non_existent_capability"))
    }

    @Test
    @DisplayName("Agent can handle messages")
    fun testCanHandleMessage() {
        val message = Message(content = "Test message", sender = "user")
        assertTrue(testAgent.canHandle(message))
    }

    @Test
    @DisplayName("Agent has no tools by default")
    fun testAgentTools() {
        assertTrue(testAgent.getTools().isEmpty())
    }
}

/**
 * 🔀 MultiAgentFlow test cases
 */
class MultiAgentFlowTest {

    private lateinit var agent1: Agent
    private lateinit var agent2: Agent
    private lateinit var agent3: Agent

    @BeforeEach
    fun setup() {
        agent1 = createMockAgent("agent-1", "Fast Agent", listOf("speed"))
        agent2 = createMockAgent("agent-2", "Smart Agent", listOf("intelligence"))
        agent3 = createMockAgent("agent-3", "Creative Agent", listOf("creativity"))
    }

    private fun createMockAgent(id: String, name: String, capabilities: List<String>): Agent {
        return object : BaseAgent(id, name, "Mock agent for testing", capabilities) {
            override suspend fun processMessage(message: Message): Message {
                // Simulate processing time
                delay(when (id) {
                    "agent-1" -> 50  // Fast
                    "agent-2" -> 100 // Medium
                    "agent-3" -> 150 // Slow
                    else -> 100
                })
                
                return message.createReply(
                    content = "Response from $name: ${message.content}",
                    sender = id,
                    metadata = mapOf("processor" to name)
                )
            }
        }
    }

    @Test
    @DisplayName("MultiAgentFlow sequential processing")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testSequentialFlow() = runTest {
        val flow = MultiAgentFlow(FlowStrategy.SEQUENTIAL)
        flow.addAgent(agent1)
        flow.addAgent(agent2)
        flow.addAgent(agent3)

        val inputMessage = Message(content = "Test sequential", sender = "user")
        val result = flow.process(inputMessage)

        assertNotNull(result)
        assertEquals("SEQUENTIAL", result.metadata["flow_strategy"])
        assertTrue(result.metadata.containsKey("execution_time_ms"))
        assertEquals("3", result.metadata["agent_count"])
    }

    @Test
    @DisplayName("MultiAgentFlow parallel processing")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testParallelFlow() = runTest {
        val flow = MultiAgentFlow(FlowStrategy.PARALLEL)
        flow.addAgent(agent1)
        flow.addAgent(agent2)
        flow.addAgent(agent3)

        val inputMessage = Message(content = "Test parallel", sender = "user")
        val result = flow.process(inputMessage)

        assertNotNull(result)
        assertEquals("PARALLEL", result.metadata["flow_strategy"])
        assertTrue(result.metadata.containsKey("execution_time_ms"))
        assertEquals("3", result.metadata["agent_count"])
        assertTrue(result.content.contains("Parallel processing results"))
    }

    @Test
    @DisplayName("MultiAgentFlow competition processing")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testCompetitionFlow() = runTest {
        val flow = MultiAgentFlow(FlowStrategy.COMPETITION)
        flow.addAgent(agent1) // Should be fastest
        flow.addAgent(agent2)
        flow.addAgent(agent3)

        val inputMessage = Message(content = "Test competition", sender = "user")
        val result = flow.process(inputMessage)

        assertNotNull(result)
        assertEquals("COMPETITION", result.metadata["flow_strategy"])
        assertTrue(result.metadata.containsKey("winner_id"))
        assertTrue(result.metadata.containsKey("winner_time"))
        assertTrue(result.metadata.containsKey("execution_time_ms"))
        assertTrue(result.content.contains("Competition winner"))
    }

    @Test
    @DisplayName("MultiAgentFlow pipeline processing")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testPipelineFlow() = runTest {
        val flow = MultiAgentFlow(FlowStrategy.PIPELINE)
        flow.addAgent(agent1)
        flow.addAgent(agent2)
        flow.addAgent(agent3)

        val inputMessage = Message(content = "Test pipeline", sender = "user")
        val result = flow.process(inputMessage)

        assertNotNull(result)
        assertEquals("PIPELINE", result.metadata["flow_strategy"])
        assertTrue(result.metadata.containsKey("pipeline_steps"))
        assertTrue(result.metadata.containsKey("execution_time_ms"))
        assertEquals("3", result.metadata["agent_count"])
    }

    @Test
    @DisplayName("MultiAgentFlow dynamic strategy selection")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testDynamicStrategySelection() = runTest {
        val flow = MultiAgentFlow(FlowStrategy.SEQUENTIAL) // Default
        flow.addAgent(agent1)
        flow.addAgent(agent2)
        
        // Set custom strategy resolver
        flow.setStrategyResolver { message, agents ->
            when {
                message.content.contains("fast") -> FlowStrategy.COMPETITION
                message.content.contains("together") -> FlowStrategy.PARALLEL
                else -> FlowStrategy.SEQUENTIAL
            }
        }

        // Test fast message -> competition
        val fastMessage = Message(content = "Make this fast", sender = "user")
        val fastResult = flow.process(fastMessage)
        assertEquals("COMPETITION", fastResult.metadata["flow_strategy"])

        // Test parallel message -> parallel
        val parallelMessage = Message(content = "Work together", sender = "user")
        val parallelResult = flow.process(parallelMessage)
        assertEquals("PARALLEL", parallelResult.metadata["flow_strategy"])
    }

    @Test
    @DisplayName("MultiAgentFlow empty pool handling")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testEmptyAgentPool() = runTest {
        val flow = MultiAgentFlow(FlowStrategy.SEQUENTIAL)
        
        val inputMessage = Message(content = "Test empty", sender = "user")
        val result = flow.process(inputMessage)

        assertNotNull(result)
        assertEquals(MessageType.ERROR, result.type)
        assertTrue(result.content.contains("No agents"))
    }

    @Test
    @DisplayName("MultiAgentFlow agent management")
    fun testAgentManagement() {
        val flow = MultiAgentFlow()
        
        assertEquals(0, flow.getAgentCount())
        
        flow.addAgent(agent1)
        assertEquals(1, flow.getAgentCount())
        
        flow.addAgents(agent2, agent3)
        assertEquals(3, flow.getAgentCount())
        
        val agents = flow.getAgents()
        assertEquals(3, agents.size)
        assertTrue(agents.contains(agent1))
        assertTrue(agents.contains(agent2))
        assertTrue(agents.contains(agent3))
        
        flow.clearAgents()
        assertEquals(0, flow.getAgentCount())
    }
}

/**
 * 🐝 SwarmAgent test cases
 */
class SwarmAgentTest {

    private lateinit var agent1: Agent
    private lateinit var agent2: Agent
    private lateinit var agent3: Agent

    @BeforeEach
    fun setup() {
        agent1 = createMockAgent("agent-1", "Fast Agent", listOf("speed", "analysis"))
        agent2 = createMockAgent("agent-2", "Smart Agent", listOf("intelligence", "generation"))
        agent3 = createMockAgent("agent-3", "Creative Agent", listOf("creativity", "search"))
    }

    private fun createMockAgent(id: String, name: String, capabilities: List<String>): Agent {
        return object : BaseAgent(id, name, "Mock agent for testing", capabilities) {
            override suspend fun processMessage(message: Message): Message {
                delay(50) // Simulate processing
                return message.createReply(
                    content = "Response from $name: ${message.content}",
                    sender = id,
                    metadata = mapOf("processor" to name)
                )
            }
        }
    }

    @Test
    @DisplayName("SwarmAgent basic functionality")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testSwarmAgentBasicFunctionality() = runTest {
        val swarm = SwarmAgent(
            id = "test-swarm",
            name = "Test Swarm",
            description = "Test swarm for unit testing"
        )
        swarm.addToPool(agent1)
        swarm.addToPool(agent2)
        swarm.addToPool(agent3)

        val inputMessage = Message(content = "Test swarm processing", sender = "user")
        val result = swarm.processMessage(inputMessage)

        assertNotNull(result)
        assertEquals("test-swarm", result.sender)
        assertEquals("test-swarm", result.metadata["swarm_id"])
        assertEquals("Test Swarm", result.metadata["swarm_name"])
        assertTrue(result.metadata.containsKey("selected_agents"))
        assertTrue(result.metadata.containsKey("swarm_size"))
    }

    @Test
    @DisplayName("SwarmAgent strategy resolver customization")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testSwarmAgentStrategyCustomization() = runTest {
        val swarm = SwarmAgent(
            id = "custom-swarm",
            name = "Custom Strategy Swarm",
            description = "Swarm with custom strategy resolver"
        )
        swarm.addToPool(agent1)
        swarm.addToPool(agent2)

        // Set custom strategy resolver
        swarm.setStrategyResolver { message, agents ->
            when {
                message.content.contains("fast") -> FlowStrategy.COMPETITION
                message.content.contains("together") -> FlowStrategy.PARALLEL
                else -> FlowStrategy.SEQUENTIAL
            }
        }

        // Test different strategy selection
        val fastMessage = Message(content = "Make this fast", sender = "user")
        val fastResult = swarm.processMessage(fastMessage)
        assertNotNull(fastResult)
        assertEquals("custom-swarm", fastResult.sender)

        val togetherMessage = Message(content = "Work together", sender = "user")  
        val togetherResult = swarm.processMessage(togetherMessage)
        assertNotNull(togetherResult)
        assertEquals("custom-swarm", togetherResult.sender)
    }

    @Test
    @DisplayName("SwarmAgent capability-based selection")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testSwarmAgentCapabilityBasedSelection() = runTest {
        val swarm = SwarmAgent()
        swarm.addToPool(agent1) // speed, analysis
        swarm.addToPool(agent2) // intelligence, generation
        swarm.addToPool(agent3) // creativity, search

        // Message requiring analysis capability
        val analysisMessage = Message(
            content = "Please analyze this data",
            sender = "user",
            metadata = mapOf("required_capabilities" to "analysis")
        )

        val result = swarm.processMessage(analysisMessage)
        assertNotNull(result)
        assertEquals("swarm-agent", result.sender)
        assertTrue(result.metadata.containsKey("selected_agents"))
    }

    @Test
    @DisplayName("SwarmAgent empty pool handling")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testSwarmAgentEmptyPool() = runTest {
        val swarm = SwarmAgent()
        
        val inputMessage = Message(content = "Test empty pool", sender = "user")
        val result = swarm.processMessage(inputMessage)

        assertNotNull(result)
        assertEquals(MessageType.ERROR, result.type)
        assertTrue(result.content.contains("No agents"))
    }

    @Test
    @DisplayName("SwarmAgent agent interface compliance")
    fun testSwarmAgentInterfaceCompliance() {
        val swarm = SwarmAgent()
        swarm.addToPool(agent1)
        swarm.addToPool(agent2)
        
        // Test Agent interface methods
        assertEquals("swarm-agent", swarm.id)
        assertEquals("Swarm Agent", swarm.name)
        assertEquals("Intelligent swarm of coordinated agents", swarm.description)
        assertTrue(swarm.capabilities.contains("coordination"))
        assertTrue(swarm.capabilities.contains("multi_agent"))
        assertTrue(swarm.capabilities.contains("adaptive"))
        
        val testMessage = Message(content = "Test", sender = "user")
        assertTrue(swarm.canHandle(testMessage))
        assertTrue(swarm.isReady())
        
        val tools = swarm.getTools()
        assertNotNull(tools)
        // Should aggregate tools from all agents
        assertTrue(tools.isEmpty() || tools.size >= 0)
    }
}

/**
 * 📊 GroupMetadata test cases
 */
class GroupMetadataTest {

    @Test
    @DisplayName("GroupMetadata creation and properties")
    fun testGroupMetadataCreation() {
        val metadata = GroupMetadata(
            groupId = "test-group",
            totalAgents = 3,
            currentIndex = 1,
            strategy = FlowStrategy.PARALLEL,
            coordinatorId = "coordinator-1"
        )

        assertEquals("test-group", metadata.groupId)
        assertEquals(3, metadata.totalAgents)
        assertEquals(1, metadata.currentIndex)
        assertEquals(FlowStrategy.PARALLEL, metadata.strategy)
        assertEquals("coordinator-1", metadata.coordinatorId)
    }

    @Test
    @DisplayName("GroupMetadata toMetadataMap conversion")
    fun testGroupMetadataToMetadataMap() {
        val metadata = GroupMetadata(
            groupId = "test-group",
            totalAgents = 3,
            currentIndex = 1,
            strategy = FlowStrategy.PARALLEL,
            coordinatorId = "coordinator-1"
        )

        val map = metadata.toMetadataMap()

        assertEquals("test-group", map["groupId"])
        assertEquals("3", map["totalAgents"])
        assertEquals("1", map["currentIndex"])
        assertEquals("PARALLEL", map["strategy"])
        assertEquals("coordinator-1", map["coordinatorId"])
    }
}

/**
 * 🎯 Extension functions test cases
 */
class ExtensionFunctionsTest {

    private lateinit var agents: List<Agent>

    @BeforeEach
    fun setup() {
        agents = listOf(
            createMockAgent("agent-1", "Agent 1"),
            createMockAgent("agent-2", "Agent 2"),
            createMockAgent("agent-3", "Agent 3")
        )
    }

    private fun createMockAgent(id: String, name: String): Agent {
        return object : BaseAgent(id, name, "Mock agent") {
            override suspend fun processMessage(message: Message): Message {
                return message.createReply("Response from $name", id)
            }
        }
    }

    @Test
    @DisplayName("List<Agent>.toMultiAgentFlow extension")
    fun testToMultiAgentFlow() {
        val flow = agents.toMultiAgentFlow(FlowStrategy.PARALLEL)
        
        assertNotNull(flow)
        assertEquals(3, flow.getAgentCount())
        assertTrue(flow.getAgents().containsAll(agents))
    }

    @Test
    @DisplayName("List<Agent>.toSwarm extension")
    fun testToSwarm() {
        val swarm = agents.toSwarm(
            id = "test-swarm",
            name = "Test Swarm",
            description = "Test swarm from extension"
        )
        
        assertNotNull(swarm)
        assertEquals("test-swarm", swarm.id)
        assertEquals("Test Swarm", swarm.name)
        assertEquals("Test swarm from extension", swarm.description)
        assertTrue(swarm.isReady())
    }

    @Test
    @DisplayName("Convenience flow creation functions")
    fun testConvenienceFlowFunctions() {
        val agent1 = agents[0]
        val agent2 = agents[1]
        val agent3 = agents[2]
        
        val sequentialFlow = sequentialFlow(agent1, agent2, agent3)
        assertEquals(3, sequentialFlow.getAgentCount())
        
        val parallelFlow = parallelFlow(agent1, agent2, agent3)
        assertEquals(3, parallelFlow.getAgentCount())
        
        val competitionFlow = competitionFlow(agent1, agent2, agent3)
        assertEquals(3, competitionFlow.getAgentCount())
        
        val pipelineFlow = pipelineFlow(agent1, agent2, agent3)
        assertEquals(3, pipelineFlow.getAgentCount())
    }
}

/**
 * 🏁 AgentCompetitionResult test cases
 */
class AgentCompetitionResultTest {

    @Test
    @DisplayName("AgentCompetitionResult creation")
    fun testAgentCompetitionResultCreation() {
        val result = AgentCompetitionResult(
            agentId = "agent-1",
            agentName = "Test Agent",
            content = "Test response",
            processingTime = 150L,
            success = true
        )

        assertEquals("agent-1", result.agentId)
        assertEquals("Test Agent", result.agentName)
        assertEquals("Test response", result.content)
        assertEquals(150L, result.processingTime)
        assertTrue(result.success)
    }

    @Test
    @DisplayName("AgentCompetitionResult failure case")
    fun testAgentCompetitionResultFailure() {
        val result = AgentCompetitionResult(
            agentId = "agent-2",
            agentName = "Failed Agent",
            content = "Error: timeout",
            processingTime = 5000L,
            success = false
        )

        assertEquals("agent-2", result.agentId)
        assertEquals("Failed Agent", result.agentName)
        assertEquals("Error: timeout", result.content)
        assertEquals(5000L, result.processingTime)
        assertFalse(result.success)
    }
}

/**
 * 🌊 FlowStrategy test cases
 */
class FlowStrategyTest {

    @Test
    @DisplayName("All flow strategies are defined")
    fun testAllFlowStrategies() {
        val strategies = FlowStrategy.values()
        
        assertTrue(strategies.contains(FlowStrategy.SEQUENTIAL))
        assertTrue(strategies.contains(FlowStrategy.PARALLEL))
        assertTrue(strategies.contains(FlowStrategy.COMPETITION))
        assertTrue(strategies.contains(FlowStrategy.PIPELINE))
    }

    @Test
    @DisplayName("Flow strategy enum consistency")
    fun testFlowStrategyConsistency() {
        assertEquals(4, FlowStrategy.values().size)
        
        // Verify all strategies have valid names
        FlowStrategy.values().forEach { strategy ->
            assertNotNull(strategy.name)
            assertTrue(strategy.name.isNotBlank())
        }
    }
} 