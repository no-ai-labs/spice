package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File

class GraphvizFlowGeneratorTest {

    @Test
    fun `basic message flow DOT generation test`() {
        // Given: Simple message list
        val messages = listOf(
            Message(
                id = "msg1",
                type = MessageType.TEXT,
                content = "Hello",
                agentId = "agent1",
                metadata = mapOf("nodeType" to "start")
            ),
            Message(
                id = "msg2",
                type = MessageType.TEXT,
                content = "World",
                agentId = "agent2",
                parentId = "msg1",
                metadata = mapOf("nodeType" to "process")
            ),
            Message(
                id = "msg3",
                type = MessageType.TEXT,
                content = "End",
                agentId = "agent1",
                parentId = "msg2",
                metadata = mapOf("nodeType" to "end")
            )
        )

        // When: Generate DOT file
        val dotContent = GraphvizFlowGenerator.generateDot(messages)

        // Then: Verify DOT structure
        assertTrue(dotContent.contains("digraph"), "Should contain digraph declaration")
        assertTrue(dotContent.contains("rankdir=TB"), "Should contain top-to-bottom direction")
        assertTrue(dotContent.contains("node [shape=box]"), "Should contain node shape definition")

        // Verify node definitions
        assertTrue(dotContent.contains("msg1"), "Should contain msg1 node")
        assertTrue(dotContent.contains("msg2"), "Should contain msg2 node")
        assertTrue(dotContent.contains("msg3"), "Should contain msg3 node")

        // Verify edge definitions
        assertTrue(dotContent.contains("msg1 -> msg2"), "Should contain msg1 to msg2 edge")
        assertTrue(dotContent.contains("msg2 -> msg3"), "Should contain msg2 to msg3 edge")

        println("Generated DOT content:")
        println(dotContent)
    }

    @Test
    fun `sample workflow DOT generation test`() {
        // Given: Complex workflow messages
        val messages = GraphvizFlowGenerator.generateSampleWorkflow()

        // When: Generate DOT
        val dotContent = GraphvizFlowGenerator.generateDot(messages)

        // Then: Verify workflow structure
        assertTrue(dotContent.contains("workflow_start"), "Should contain workflow start")
        assertTrue(dotContent.contains("data_collection"), "Should contain data collection")
        assertTrue(dotContent.contains("tool_call"), "Should contain tool call")
        assertTrue(dotContent.contains("tool_result"), "Should contain tool result")
        assertTrue(dotContent.contains("result_generation"), "Should contain result generation")
        assertTrue(dotContent.contains("workflow_end"), "Should contain workflow end")

        // Verify node type colors
        assertTrue(dotContent.contains("fillcolor=lightblue"), "Should contain start node color")
        assertTrue(dotContent.contains("fillcolor=lightgreen"), "Should contain process node color")
        assertTrue(dotContent.contains("fillcolor=lightcoral"), "Should contain end node color")

        println("Sample workflow DOT:")
        println(dotContent)
    }

    @Test
    fun `error flow DOT generation test`() {
        // Given: Error flow messages
        val messages = GraphvizFlowGenerator.generateErrorFlow()

        // When: Generate DOT
        val dotContent = GraphvizFlowGenerator.generateDot(messages)

        // Then: Verify error handling structure
        assertTrue(dotContent.contains("normal_processing"), "Should contain normal processing")
        assertTrue(dotContent.contains("error_occurred"), "Should contain error occurrence")
        assertTrue(dotContent.contains("error_handling"), "Should contain error handling")
        assertTrue(dotContent.contains("recovery_attempt"), "Should contain recovery attempt")
        assertTrue(dotContent.contains("fallback_response"), "Should contain fallback response")

        // Verify error styling
        assertTrue(dotContent.contains("fillcolor=red"), "Should contain error node color")
        assertTrue(dotContent.contains("style=filled"), "Should contain filled style")

        println("Error flow DOT:")
        println(dotContent)
    }

    @Test
    fun `interrupt resume flow DOT generation test`() {
        // Given: Interrupt/resume flow messages
        val messages = GraphvizFlowGenerator.generateInterruptResumeFlow()

        // When: Generate DOT
        val dotContent = GraphvizFlowGenerator.generateDot(messages)

        // Then: Verify interrupt structure
        assertTrue(dotContent.contains("normal_processing"), "Should contain normal processing")
        assertTrue(dotContent.contains("interrupt_triggered"), "Should contain interrupt trigger")
        assertTrue(dotContent.contains("interrupt_handling"), "Should contain interrupt handling")
        assertTrue(dotContent.contains("resume_processing"), "Should contain resume processing")

        // Verify metadata-based processing
        assertTrue(dotContent.contains("interrupted"), "Should contain interrupted metadata")
        assertTrue(dotContent.contains("resumed"), "Should contain resumed metadata")

        println("Interrupt/Resume flow DOT:")
        println(dotContent)
    }

    @Test
    fun `AgentEngine integration flow DOT generation test`() = runBlocking {
        // Given: Simple Agent setup
        val agent = object : Agent {
            override val id: String = "test-agent"
            override val name: String = "Test Agent"
            override val description: String = "Test agent for DOT generation"
            override val capabilities: Set<String> = setOf("text-processing")
            override val supportedMessageTypes: Set<MessageType> = setOf(MessageType.TEXT)

            override suspend fun canHandle(message: Message): Boolean = true

            override suspend fun process(message: Message): Message {
                return Message(
                    id = "response-${message.id}",
                    type = MessageType.TEXT,
                    content = "Processed: ${message.content}",
                    agentId = id,
                    parentId = message.id
                )
            }
        }

        val engine = AgentEngine()
        engine.registerAgent(agent)

        // When: Generate flow DOT from AgentEngine
        val dotContent = engine.generateFlowDot(listOf(
            Message(
                id = "input1",
                type = MessageType.TEXT,
                content = "Test input",
                agentId = "test-agent"
            )
        ))

        // Then: Verify DOT generation
        assertTrue(dotContent.contains("digraph"), "Should contain digraph declaration")
        assertTrue(dotContent.contains("input1"), "Should contain input message")

        println("AgentEngine flow DOT:")
        println(dotContent)
    }

    @Test
    fun `various message type colors and styles test`() {
        // Given: Messages with different types
        val messages = listOf(
            Message(id = "1", type = MessageType.TEXT, content = "Text", agentId = "agent1"),
            Message(id = "2", type = MessageType.DATA, content = "Data", agentId = "agent2"),
            Message(id = "3", type = MessageType.TOOL_CALL, content = "Tool", agentId = "agent3"),
            Message(id = "4", type = MessageType.TOOL_RESULT, content = "Result", agentId = "agent4"),
            Message(id = "5", type = MessageType.ERROR, content = "Error", agentId = "agent5"),
            Message(id = "6", type = MessageType.INTERRUPT, content = "Interrupt", agentId = "agent6"),
            Message(id = "7", type = MessageType.SYSTEM, content = "System", agentId = "agent7")
        )

        // When: Generate DOT
        val dotContent = GraphvizFlowGenerator.generateDot(messages)

        // Then: Verify different colors and styles
        assertTrue(dotContent.contains("fillcolor=lightblue"), "Should contain TEXT color")
        assertTrue(dotContent.contains("fillcolor=lightgreen"), "Should contain DATA color")
        assertTrue(dotContent.contains("fillcolor=yellow"), "Should contain TOOL_CALL color")
        assertTrue(dotContent.contains("fillcolor=orange"), "Should contain TOOL_RESULT color")
        assertTrue(dotContent.contains("fillcolor=red"), "Should contain ERROR color")
        assertTrue(dotContent.contains("fillcolor=purple"), "Should contain INTERRUPT color")
        assertTrue(dotContent.contains("fillcolor=gray"), "Should contain SYSTEM color")

        println("Message type colors DOT:")
        println(dotContent)
    }

    @Test
    fun `node type shapes and colors test`() {
        // Given: Messages with various node types
        val messages = listOf(
            Message(id = "1", type = MessageType.TEXT, content = "Start", agentId = "agent1", 
                   metadata = mapOf("nodeType" to "start")),
            Message(id = "2", type = MessageType.TEXT, content = "Process", agentId = "agent2", 
                   metadata = mapOf("nodeType" to "process")),
            Message(id = "3", type = MessageType.TEXT, content = "End", agentId = "agent3", 
                   metadata = mapOf("nodeType" to "end"))
        )

        // When: Generate DOT
        val dotContent = GraphvizFlowGenerator.generateDot(messages)

        // Then: Verify node shapes
        assertTrue(dotContent.contains("shape=ellipse"), "Should contain ellipse shape for start")
        assertTrue(dotContent.contains("shape=box"), "Should contain box shape for process")
        assertTrue(dotContent.contains("shape=doublecircle"), "Should contain double circle for end")

        // Verify node colors
        assertTrue(dotContent.contains("fillcolor=lightblue"), "Should contain start node color")
        assertTrue(dotContent.contains("fillcolor=lightgreen"), "Should contain process node color")
        assertTrue(dotContent.contains("fillcolor=lightcoral"), "Should contain end node color")

        println("Node shapes and colors DOT:")
        println(dotContent)
    }

    @Test
    fun `DOT file save functionality test`() {
        // Given: Simple message list
        val messages = listOf(
            Message(id = "1", type = MessageType.TEXT, content = "Test", agentId = "agent1")
        )

        // When: Save to file
        val tempFile = File.createTempFile("test_flow", ".dot")
        GraphvizFlowGenerator.saveDotToFile(messages, tempFile.absolutePath)

        // Then: Verify file creation
        assertTrue(tempFile.exists(), "DOT file should be created")
        assertTrue(tempFile.length() > 0, "DOT file should not be empty")
        
        val content = tempFile.readText()
        assertTrue(content.contains("digraph"), "File should contain DOT content")

        // Cleanup
        tempFile.delete()
    }

    @Test
    fun `empty message list DOT generation test`() {
        // Given: Empty message list
        val messages = emptyList<Message>()

        // When: Generate DOT
        val dotContent = GraphvizFlowGenerator.generateDot(messages)

        // Then: Basic structure should be maintained
        assertTrue(dotContent.contains("digraph"), "Should contain digraph declaration")
        assertTrue(dotContent.contains("rankdir=TB"), "Should contain direction")
        assertTrue(dotContent.contains("node [shape=box]"), "Should contain node definition")

        // Should not contain any nodes or edges (except legend)
        assertFalse(dotContent.contains(" -> "), "Should not contain edges")

        println("Empty messages DOT:")
        println(dotContent)
    }
} 