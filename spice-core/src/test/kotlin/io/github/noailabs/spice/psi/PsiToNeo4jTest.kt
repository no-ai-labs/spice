package io.github.noailabs.spice.psi

import io.github.noailabs.spice.dsl.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 🧪 Test Suite for PSI to Neo4j Conversion
 */
class PsiToNeo4jTest {
    
    @Test
    fun `test PsiNode to Neo4j conversion`() {
        // Create a PSI tree
        val psi = psiNode(PsiTypes.AGENT) {
            prop("id", "test-agent")
            prop("name", "Test Agent")
            prop("description", "Agent for testing")
            meta("created", System.currentTimeMillis())
            
            add(psiNode(PsiTypes.TOOLS) {
                add(psiNode(PsiTypes.TOOL) {
                    prop("name", "calculator")
                    prop("description", "Calculates things")
                })
                add(psiNode(PsiTypes.TOOL) {
                    prop("name", "analyzer")
                    prop("description", "Analyzes data")
                })
            })
            
            add(psiNode(PsiTypes.HANDLER) {
                prop("type", "message")
                meta("hasImplementation", true)
            })
        }
        
        // Convert to Neo4j
        val graph = psi.toNeo4jGraph()
        
        // Verify nodes
        assertTrue(graph.nodes.size >= 4, "Should have agent, tools container, 2 tools, and handler")
        
        val agentNode = graph.nodes.find { it.id.startsWith("agent:") }
        assertNotNull(agentNode)
        assertEquals(listOf("Agent", "SpiceComponent"), agentNode.labels)
        
        val toolNodes = graph.nodes.filter { it.labels.contains("Tool") }
        assertEquals(2, toolNodes.size)
        
        // Verify relationships
        assertTrue(graph.relationships.any { it.type == "HAS_TOOLS" })
        assertTrue(graph.relationships.any { it.type == "HAS_TOOL" })
        assertTrue(graph.relationships.any { it.type == "HAS_HANDLER" })
    }
    
    @Test
    fun `test Neo4j node ID generation`() {
        val agentPsi = psiNode(PsiTypes.AGENT) {
            prop("id", "my-agent")
        }
        
        val toolPsi = psiNode(PsiTypes.TOOL) {
            prop("name", "my-tool")
        }
        
        val flowPsi = psiNode(PsiTypes.FLOW) {
            prop("id", "my-flow")
        }
        
        val agentGraph = agentPsi.toNeo4jGraph()
        val toolGraph = toolPsi.toNeo4jGraph()
        val flowGraph = flowPsi.toNeo4jGraph()
        
        // Verify ID formats
        assertTrue(agentGraph.nodes.first().id.startsWith("agent:"))
        assertTrue(toolGraph.nodes.first().id.startsWith("tool:"))
        assertTrue(flowGraph.nodes.first().id.startsWith("flow:"))
    }
    
    @Test
    fun `test relationship type mapping`() {
        val psi = psiNode(PsiTypes.FLOW) {
            prop("id", "test-flow")
            
            add(psiNode(PsiTypes.STEPS) {
                add(psiNode(PsiTypes.STEP) {
                    prop("name", "step1")
                    prop("agentRef", "agent1")
                })
            })
            
            add(psiNode(PsiTypes.CONFIG) {
                prop("timeout", 5000)
            })
        }
        
        val graph = psi.toNeo4jGraph()
        
        // Verify relationship types
        assertTrue(graph.relationships.any { it.type == "HAS_STEPS" })
        assertTrue(graph.relationships.any { it.type == "CONTAINS_STEP" })
        assertTrue(graph.relationships.any { it.type == "HAS_CONFIG" })
    }
    
    @Test
    fun `test Neo4j graph to mnemo format`() {
        val psi = psiNode(PsiTypes.AGENT) {
            prop("id", "test")
            prop("name", "Test")
            
            add(psiNode(PsiTypes.PERSONA) {
                prop("name", "TestPersona")
                prop("role", "assistant")
            })
        }
        
        val graph = psi.toNeo4jGraph()
        val json = PsiToNeo4j.run { graph.toMnemoFormat() }
        
        // Verify JSON structure
        assertNotNull(json["nodes"])
        assertNotNull(json["relationships"])
        
        val nodes = json["nodes"]?.jsonArray
        assertNotNull(nodes)
        assertTrue(nodes.size >= 2)
        
        // Verify node structure in JSON
        val firstNode = nodes.first().jsonObject
        assertNotNull(firstNode["id"])
        assertNotNull(firstNode["labels"])
        assertNotNull(firstNode["properties"])
        
        val relationships = json["relationships"]?.jsonArray
        assertNotNull(relationships)
        assertTrue(relationships.size >= 1)
        
        // Verify relationship structure
        val firstRel = relationships.first().jsonObject
        assertNotNull(firstRel["from"])
        assertNotNull(firstRel["to"])
        assertNotNull(firstRel["type"])
    }
    
    @Test
    fun `test complex PSI structure conversion`() {
        val complexPsi = psiNode(PsiTypes.SWARM) {
            prop("id", "swarm1")
            prop("name", "Test Swarm")
            
            add(psiNode(PsiTypes.MEMBERS) {
                add(psiNode(PsiTypes.AGENT_REF) {
                    prop("ref", "agent1")
                })
                add(psiNode(PsiTypes.AGENT_REF) {
                    prop("ref", "agent2")
                })
            })
            
            add(psiNode(PsiTypes.CONFIG) {
                prop("strategy", "consensus")
                prop("timeout", 30000)
            })
        }
        
        val graph = complexPsi.toNeo4jGraph()
        
        // Verify swarm structure
        val swarmNode = graph.nodes.find { it.labels.contains("SwarmAgent") }
        assertNotNull(swarmNode)
        
        // Verify member references create relationships
        val agentRefRelationships = graph.relationships.filter { it.type == "REFERENCES_AGENT" }
        assertEquals(2, agentRefRelationships.size)
    }
}