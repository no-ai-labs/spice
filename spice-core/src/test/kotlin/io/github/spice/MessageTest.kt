package io.github.spice

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import java.time.Instant

/**
 * 🧪 Message system test cases
 */
class MessageTest {

    private lateinit var testMessage: Message
    
    @BeforeEach
    fun setup() {
        testMessage = Message(
            id = "test-msg-1",
            content = "Hello World",
            type = MessageType.TEXT,
            sender = "agent-1",
            receiver = "agent-2"
        )
    }

    @Test
    @DisplayName("Message creation with default values")
    fun testMessageCreation() {
        val message = Message(
            content = "Test content",
            sender = "test-sender"
        )
        
        assertNotNull(message.id)
        assertEquals("Test content", message.content)
        assertEquals(MessageType.TEXT, message.type)
        assertEquals("test-sender", message.sender)
        assertNull(message.receiver)
        assertTrue(message.metadata.isEmpty())
        assertTrue(message.timestamp > 0)
    }

    @Test
    @DisplayName("Create reply message")
    fun testCreateReply() {
        val reply = testMessage.createReply(
            content = "Reply content",
            sender = "agent-2",
            type = MessageType.SYSTEM,
            metadata = mapOf("key" to "value")
        )
        
        assertNotEquals(testMessage.id, reply.id)
        assertEquals("Reply content", reply.content)
        assertEquals(MessageType.SYSTEM, reply.type)
        assertEquals("agent-2", reply.sender)
        assertEquals("agent-1", reply.receiver) // Original sender becomes receiver
        assertEquals(testMessage.id, reply.parentId)
        assertEquals("value", reply.metadata["key"])
        assertTrue(reply.timestamp >= testMessage.timestamp)
    }

    @Test
    @DisplayName("Forward message to new receiver")
    fun testForwardMessage() {
        val originalTimestamp = testMessage.timestamp
        Thread.sleep(1) // Ensure timestamp difference
        
        val forwarded = testMessage.forward("agent-3")
        
        assertEquals(testMessage.id, forwarded.id)
        assertEquals(testMessage.content, forwarded.content)
        assertEquals(testMessage.type, forwarded.type)
        assertEquals(testMessage.sender, forwarded.sender)
        assertEquals("agent-3", forwarded.receiver)
        assertTrue(forwarded.timestamp > originalTimestamp)
    }

    @Test
    @DisplayName("Add metadata to message")
    fun testWithMetadata() {
        val withMeta = testMessage.withMetadata("priority", "high")
        
        assertEquals(testMessage.id, withMeta.id)
        assertEquals(testMessage.content, withMeta.content)
        assertEquals("high", withMeta.metadata["priority"])
    }

    @Test
    @DisplayName("Change message type")
    fun testWithType() {
        val withType = testMessage.withType(MessageType.ERROR)
        
        assertEquals(testMessage.id, withType.id)
        assertEquals(testMessage.content, withType.content)
        assertEquals(MessageType.ERROR, withType.type)
    }

    @Test
    @DisplayName("Message conversation chain")
    fun testConversationChain() {
        val reply1 = testMessage.createReply("First reply", "agent-2")
        val reply2 = reply1.createReply("Second reply", "agent-1")
        
        assertEquals(testMessage.id, reply1.parentId)
        assertEquals(reply1.id, reply2.parentId)
        assertEquals(testMessage.id, reply1.conversationId)
        assertEquals(testMessage.id, reply2.conversationId)
    }
}

/**
 * 🔄 MessageRouter test cases
 */
class MessageRouterTest {

    private lateinit var router: MessageRouter

    @BeforeEach
    fun setup() {
        router = MessageRouter()
    }

    @Test
    @DisplayName("Route message without rules returns original")
    fun testRouteWithoutRules() {
        val message = Message(content = "test", sender = "agent-1")
        val routed = router.route(message)
        
        assertEquals(1, routed.size)
        assertEquals(message, routed.first())
    }

    @Test
    @DisplayName("Route message with applicable rule")
    fun testRouteWithApplicableRule() {
        val rule = MessageRoutingRule(
            sourceType = MessageType.TEXT,
            targetType = MessageType.SYSTEM,
            condition = { it.content.contains("convert") },
            transformer = { it.withMetadata("converted", "true") }
        )
        router.addRule(rule)
        
        val message = Message(
            content = "Please convert this",
            sender = "agent-1",
            type = MessageType.TEXT
        )
        
        val routed = router.route(message)
        
        assertEquals(1, routed.size)
        val routedMsg = routed.first()
        assertEquals(MessageType.SYSTEM, routedMsg.type)
        assertEquals("true", routedMsg.metadata["converted"])
    }

    @Test
    @DisplayName("Route message with non-applicable rule")
    fun testRouteWithNonApplicableRule() {
        val rule = MessageRoutingRule(
            sourceType = MessageType.ERROR,
            targetType = MessageType.SYSTEM
        )
        router.addRule(rule)
        
        val message = Message(
            content = "test",
            sender = "agent-1",
            type = MessageType.TEXT
        )
        
        val routed = router.route(message)
        
        assertEquals(1, routed.size)
        assertEquals(message, routed.first())
    }

    @Test
    @DisplayName("Route message with multiple applicable rules")
    fun testRouteWithMultipleRules() {
        val rule1 = MessageRoutingRule(
            sourceType = MessageType.TEXT,
            targetType = MessageType.SYSTEM,
            transformer = { it.withMetadata("rule", "1") }
        )
        val rule2 = MessageRoutingRule(
            sourceType = MessageType.TEXT,
            targetType = MessageType.PROMPT,
            transformer = { it.withMetadata("rule", "2") }
        )
        
        router.addRule(rule1)
        router.addRule(rule2)
        
        val message = Message(
            content = "test",
            sender = "agent-1",
            type = MessageType.TEXT
        )
        
        val routed = router.route(message)
        
        assertEquals(2, routed.size)
        assertTrue(routed.any { it.type == MessageType.SYSTEM && it.metadata["rule"] == "1" })
        assertTrue(routed.any { it.type == MessageType.PROMPT && it.metadata["rule"] == "2" })
    }

    @Test
    @DisplayName("Create Spice Flow rules")
    fun testCreateSpiceFlowRules() {
        val spiceRouter = MessageRouter.createSpiceFlowRules()
        
        // Test PROMPT → SYSTEM rule
        val promptMessage = Message(
            content = "test",
            sender = "agent-1",
            type = MessageType.PROMPT,
            metadata = mapOf("isSecondPrompt" to "true")
        )
        
        val routed = spiceRouter.route(promptMessage)
        
        assertTrue(routed.any { 
            it.type == MessageType.SYSTEM && 
            it.metadata["autoUpgraded"] == "promptToSystem" 
        })
    }
}

/**
 * 🔧 MessageRoutingRule test cases
 */
class MessageRoutingRuleTest {

    @Test
    @DisplayName("Rule can route matching message")
    fun testCanRouteMatching() {
        val rule = MessageRoutingRule(
            sourceType = MessageType.TEXT,
            targetType = MessageType.SYSTEM,
            condition = { it.sender == "specific-agent" }
        )
        
        val matchingMessage = Message(
            content = "test",
            sender = "specific-agent",
            type = MessageType.TEXT
        )
        
        val nonMatchingMessage = Message(
            content = "test",
            sender = "other-agent",
            type = MessageType.TEXT
        )
        
        assertTrue(rule.canRoute(matchingMessage))
        assertFalse(rule.canRoute(nonMatchingMessage))
    }

    @Test
    @DisplayName("Rule routes message with transformation")
    fun testRouteWithTransformation() {
        val rule = MessageRoutingRule(
            sourceType = MessageType.TEXT,
            targetType = MessageType.SYSTEM,
            transformer = { 
                it.withMetadata("transformed", "true")
                  .copy(content = "Transformed: ${it.content}")
            }
        )
        
        val message = Message(
            content = "original",
            sender = "agent-1",
            type = MessageType.TEXT
        )
        
        val routed = rule.route(message)
        
        assertEquals(MessageType.SYSTEM, routed.type)
        assertEquals("Transformed: original", routed.content)
        assertEquals("true", routed.metadata["transformed"])
    }

    @Test
    @DisplayName("Rule with default condition always matches")
    fun testDefaultCondition() {
        val rule = MessageRoutingRule(
            sourceType = MessageType.TEXT,
            targetType = MessageType.SYSTEM
        )
        
        val message1 = Message(content = "test1", sender = "agent1", type = MessageType.TEXT)
        val message2 = Message(content = "test2", sender = "agent2", type = MessageType.TEXT)
        
        assertTrue(rule.canRoute(message1))
        assertTrue(rule.canRoute(message2))
    }
}

/**
 * 📨 MessageType test cases  
 */
class MessageTypeTest {

    @Test
    @DisplayName("All message types are defined")
    fun testAllMessageTypes() {
        val types = MessageType.values()
        
        assertTrue(types.contains(MessageType.TEXT))
        assertTrue(types.contains(MessageType.SYSTEM))
        assertTrue(types.contains(MessageType.TOOL_CALL))
        assertTrue(types.contains(MessageType.TOOL_RESULT))
        assertTrue(types.contains(MessageType.ERROR))
        assertTrue(types.contains(MessageType.DATA))
        assertTrue(types.contains(MessageType.PROMPT))
        assertTrue(types.contains(MessageType.RESULT))
        assertTrue(types.contains(MessageType.BRANCH))
        assertTrue(types.contains(MessageType.MERGE))
        assertTrue(types.contains(MessageType.WORKFLOW_START))
        assertTrue(types.contains(MessageType.WORKFLOW_END))
        assertTrue(types.contains(MessageType.INTERRUPT))
        assertTrue(types.contains(MessageType.RESUME))
    }

    @Test
    @DisplayName("Message type enum consistency")
    fun testMessageTypeConsistency() {
        assertEquals(14, MessageType.values().size)
        
        // Check that all types can be used in messages
        MessageType.values().forEach { type ->
            val message = Message(content = "test", sender = "test", type = type)
            assertEquals(type, message.type)
        }
    }
}

/**
 * 🔀 InterruptReason test cases
 */
class InterruptReasonTest {

    @Test
    @DisplayName("All interrupt reasons are defined")
    fun testAllInterruptReasons() {
        val reasons = InterruptReason.values()
        
        assertTrue(reasons.contains(InterruptReason.CLARIFICATION_NEEDED))
        assertTrue(reasons.contains(InterruptReason.USER_CONFIRMATION))
        assertTrue(reasons.contains(InterruptReason.MISSING_DATA))
    }

    @Test
    @DisplayName("Interrupt reason enum consistency")
    fun testInterruptReasonConsistency() {
        assertEquals(3, InterruptReason.values().size)
        
        // Verify all reasons are valid
        InterruptReason.values().forEach { reason ->
            assertNotNull(reason.name)
            assertTrue(reason.name.isNotBlank())
        }
    }
} 