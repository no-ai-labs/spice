package io.github.noailabs.spice.psi

import io.github.noailabs.spice.dsl.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SimplePsiTest {
    
    @Test
    fun `test simple PSI node creation`() {
        // Create a simple PSI node
        val node = psiNode("TestNode") {
            prop("id", "test-1")
            prop("name", "Test Node")
            meta("version", "1.0")
            
            add(psiNode("ChildNode") {
                prop("type", "child")
            })
        }
        
        // Verify structure
        assertEquals("TestNode", node.type)
        assertEquals("test-1", node.props["id"])
        assertEquals("Test Node", node.props["name"])
        assertEquals("1.0", node.metadata["version"])
        assertEquals(1, node.children.size)
        assertEquals("ChildNode", node.children[0].type)
    }
    
    @Test
    fun `test agent to PSI conversion`() {
        // Create a simple agent builder
        val agentBuilder = CoreAgentBuilder().apply {
            id = "test-agent"
            name = "Test Agent"
            description = "A test agent"
            
            handle { comm ->
                comm.reply("Hello from test agent!", id)
            }
        }
        
        // Convert to PSI
        val psi = SpicePsiBuilder.run {
            agentBuilder.toPsi()
        }
        
        // Verify basic structure
        assertEquals(PsiTypes.AGENT, psi.type)
        assertEquals("test-agent", psi.props["id"])
        assertEquals("Test Agent", psi.props["name"])
        assertEquals("A test agent", psi.props["description"])
        
        // Check for handler
        val handlerNode = psi.findFirstByType(PsiTypes.HANDLER)
        assertNotNull(handlerNode)
        assertEquals("message", handlerNode!!.props["type"])
    }
    
    @Test
    fun `test PSI serialization`() {
        val originalNode = psiNode("TestNode") {
            prop("name", "Test")
            prop("value", 42)
            
            add(psiNode("Child") {
                prop("id", "child-1")
            })
        }
        
        // Serialize to JSON
        val json = PsiSerializer.run { originalNode.toJson() }
        
        // Deserialize back
        val restoredNode = PsiSerializer.fromJson(json)
        
        // Verify
        assertEquals(originalNode.type, restoredNode.type)
        assertEquals(originalNode.props["name"], restoredNode.props["name"])
        assertEquals(originalNode.props["value"], restoredNode.props["value"])
        assertEquals(originalNode.children.size, restoredNode.children.size)
    }
    
    @Test
    fun `test PSI to Mermaid conversion`() {
        val node = psiNode("Root") {
            prop("id", "root-1")
            
            add(psiNode("Child1") {
                prop("name", "First")
            })
            
            add(psiNode("Child2") {
                prop("name", "Second")
                
                add(psiNode("Grandchild") {
                    prop("level", 3)
                })
            })
        }
        
        val mermaid = PsiSerializer.run { node.toMermaid() }
        
        // Verify it contains Mermaid syntax
        assertTrue(mermaid.contains("graph TD"))
        assertTrue(mermaid.contains("Root"))
        assertTrue(mermaid.contains("Child1"))
        assertTrue(mermaid.contains("Child2"))
        assertTrue(mermaid.contains("Grandchild"))
    }
}