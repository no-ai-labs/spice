package io.github.spice

import java.time.format.DateTimeFormatter

/**
 * Graphviz Flow Generator
 * 
 * Represents the message flow between Agents based on MessageHistory as a graph.
 * Generates Graphviz DOT files for visualization.
 */
object GraphvizFlowGenerator {
    
    /**
     * Generate DOT file based on message history
     */
    fun generateDot(messages: List<Message>, title: String = "Message Flow"): String {
        val nodes = extractNodes(messages)
        val edges = extractEdges(messages)
        
        return buildString {
            appendLine("digraph \"$title\" {")
            appendLine("    rankdir=TB;")
            appendLine("    node [shape=box, style=filled];")
            appendLine("    edge [fontsize=10];")
            appendLine()
            
            // Add nodes
            nodes.forEach { node ->
                appendLine("    \"${node.id}\" [${node.attributes}];")
            }
            
            appendLine()
            
            // Add edges
            edges.forEach { edge ->
                appendLine("    \"${edge.from}\" -> \"${edge.to}\" [${edge.attributes}];")
            }
            
            appendLine("}")
        }
    }
    
    /**
     * Generate DOT directly from ExecutionContext (if implemented)
     */
    fun generateDot(context: ExecutionContext): String {
        // Extract messageHistory from ExecutionContext if implemented
        // For now, process with empty message list temporarily
        return generateDot(emptyList(), "Execution Context Flow")
    }
    
    /**
     * Simulate AgentEngine processing flow and generate DOT
     */
    suspend fun generateFlowDot(engine: AgentEngine, messages: List<Message>): String {
        val processedMessages = mutableListOf<Message>()
        
        // Process sample messages through AgentEngine to track flow
        for (message in messages) {
            try {
                val result = engine.receive(message)
                processedMessages.add(message)
                processedMessages.add(result.response)
            } catch (e: Exception) {
                // Add original message even if error occurs
                processedMessages.add(message)
            }
        }
        
        return generateDot(processedMessages, "Agent Processing Flow")
    }
    
    private fun extractNodes(messages: List<Message>): List<GraphNode> {
        return messages.map { message ->
            val nodeType = message.metadata["nodeType"] as? String ?: "process"
            val shape = when (nodeType) {
                "start" -> "ellipse"
                "end" -> "doublecircle"
                else -> "box"
            }
            
            val color = when (message.type) {
                MessageType.TEXT -> "lightblue"
                MessageType.DATA -> "lightgreen"
                MessageType.TOOL_CALL -> "yellow"
                MessageType.TOOL_RESULT -> "orange"
                MessageType.ERROR -> "red"
                MessageType.INTERRUPT -> "purple"
                MessageType.SYSTEM -> "gray"
                else -> "white"
            }
            
            val label = buildString {
                append("${message.sender}\\n")
                append("${message.type}\\n")
                append("${message.content.take(30)}")
                if (message.content.length > 30) append("...")
            }
            
            GraphNode(
                id = message.id,
                label = label,
                shape = shape,
                color = color,
                nodeType = nodeType
            )
        }
    }
    
    private fun extractEdges(messages: List<Message>): List<GraphEdge> {
        val edges = mutableListOf<GraphEdge>()
        
        messages.forEach { message ->
            message.parentId?.let { parentId ->
                edges.add(
                    GraphEdge(
                        from = parentId,
                        to = message.id,
                        label = message.type.toString(),
                        color = when (message.type) {
                            MessageType.TEXT -> "blue"
                            MessageType.DATA -> "green"
                            MessageType.TOOL_CALL -> "orange"
                            MessageType.TOOL_RESULT -> "purple"
                            MessageType.ERROR -> "red"
                            MessageType.INTERRUPT -> "crimson"
                            MessageType.SYSTEM -> "gray"
                            else -> "black"
                        }
                    )
                )
            }
        }
        
        // Add conversation flow or parentId relationships if they exist
        for (i in 0 until messages.size - 1) {
            val current = messages[i]
            val next = messages[i + 1]
            
            // Connect sequential messages if no parent relationship exists
            if (next.parentId == null && current.sender != next.sender) {
                edges.add(
                    GraphEdge(
                        from = current.id,
                        to = next.id,
                        label = "flow",
                        color = "gray",
                        style = "dashed"
                    )
                )
            }
        }
        
        return edges
    }
    
    private val GraphNode.attributes: String
        get() = buildString {
            append("label=\"$label\"")
            append(", shape=$shape")
            append(", fillcolor=$color")
            append(", style=filled")
            
            // Add metadata to tooltip
            append(", tooltip=\"Type: $nodeType\"")
        }
    
    private val GraphEdge.attributes: String
        get() = buildString {
            append("label=\"$label\"")
            append(", color=$color")
            if (style != null) {
                append(", style=$style")
            }
        }
}

/**
 * Graph node types
 */
enum class GraphNodeType {
    START, PROCESS, END, DECISION, ERROR
}

/**
 * Message node information
 */
data class GraphNode(
    val id: String,
    val label: String,
    val shape: String,
    val color: String,
    val nodeType: String
)

/**
 * Message edge information
 */
data class GraphEdge(
    val from: String,
    val to: String,
    val label: String,
    val color: String,
    val style: String? = null
)

/**
 * Extension function: Generate DOT from Message list
 */
fun List<Message>.generateGraphvizDot(title: String = "Message Flow"): String {
    return GraphvizFlowGenerator.generateDot(this, title)
}

/**
 * Extension function: Generate flow DOT from AgentEngine
 */
suspend fun AgentEngine.generateFlowDot(messages: List<Message>, title: String = "Agent Flow"): String {
    return GraphvizFlowGenerator.generateFlowDot(this, messages)
}

/**
 * Save DOT to file
 */
fun GraphvizFlowGenerator.saveDotToFile(messages: List<Message>, filePath: String, title: String = "Message Flow") {
    val dotContent = generateDot(messages, title)
    java.io.File(filePath).writeText(dotContent)
}

/**
 * Convenience function: Generate sample messages for workflow simulation
 */
fun GraphvizFlowGenerator.generateSampleWorkflow(): List<Message> {
    return listOf(
        // 1. Workflow start
        Message(
            id = "workflow_start",
            type = MessageType.SYSTEM,
            content = "Starting data processing workflow",
            sender = "system",
            metadata = mapOf("nodeType" to "start")
        ),
        
        // 2. Data collection
        Message(
            id = "data_collection",
            type = MessageType.DATA,
            content = "Collecting user data",
            sender = "data-collector",
            parentId = "workflow_start"
        ),
        
        // 3. Tool call
        Message(
            id = "tool_call",
            type = MessageType.TOOL_CALL,
            content = "Calling data validation tool",
            sender = "validator",
            parentId = "data_collection"
        ),
        
        // 4. Tool result
        Message(
            id = "tool_result",
            type = MessageType.TOOL_RESULT,
            content = "Data validation completed",
            sender = "validator",
            parentId = "tool_call"
        ),
        
        // 5. Processing
        Message(
            id = "processing",
            type = MessageType.TEXT,
            content = "Processing validated data",
            sender = "processor",
            parentId = "tool_result"
        ),
        
        // 6. Result generation
        Message(
            id = "result_generation",
            type = MessageType.TEXT,
            content = "Generating final results",
            sender = "result-generator",
            parentId = "processing"
        ),
        
        // 7. Workflow end
        Message(
            id = "workflow_end",
            type = MessageType.SYSTEM,
            content = "Workflow completed successfully",
            sender = "system",
            parentId = "result_generation",
            metadata = mapOf("nodeType" to "end")
        )
    )
}

/**
 * Error flow simulation
 */
fun GraphvizFlowGenerator.generateErrorFlow(): List<Message> {
    return listOf(
        Message(
            id = "normal_processing",
            type = MessageType.TEXT,
            content = "Normal processing started",
            sender = "processor"
        ),
        Message(
            id = "error_occurred",
            type = MessageType.ERROR,
            content = "Processing error occurred",
            sender = "processor",
            parentId = "normal_processing"
        ),
        Message(
            id = "error_handling",
            type = MessageType.SYSTEM,
            content = "Error handling initiated",
            sender = "error-handler",
            parentId = "error_occurred"
        ),
        Message(
            id = "recovery_attempt",
            type = MessageType.TEXT,
            content = "Attempting recovery",
            sender = "recovery-agent",
            parentId = "error_handling"
        ),
        Message(
            id = "fallback_response",
            type = MessageType.TEXT,
            content = "Fallback response generated",
            sender = "fallback-agent",
            parentId = "recovery_attempt"
        )
    )
}

/**
 * Interrupt/resume flow simulation
 */
fun GraphvizFlowGenerator.generateInterruptResumeFlow(): List<Message> {
    return listOf(
        Message(
            id = "normal_processing",
            type = MessageType.TEXT,
            content = "Normal processing",
            sender = "processor"
        ),
        Message(
            id = "interrupt_triggered",
            type = MessageType.INTERRUPT,
            content = "User input required",
            sender = "processor",
            parentId = "normal_processing",
            metadata = mapOf("interrupted" to "true")
        ),
        Message(
            id = "interrupt_handling",
            type = MessageType.SYSTEM,
            content = "Handling interrupt",
            sender = "interrupt-handler",
            parentId = "interrupt_triggered"
        ),
        Message(
            id = "resume_processing",
            type = MessageType.TEXT,
            content = "Resuming processing",
            sender = "processor",
            parentId = "interrupt_handling",
            metadata = mapOf("resumed" to "true")
        )
    )
} 