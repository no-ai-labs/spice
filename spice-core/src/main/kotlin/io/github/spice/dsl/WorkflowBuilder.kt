package io.github.spice.dsl

import io.github.spice.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 🌊 Workflow Builder DSL
 * 
 * Provides a declarative way to define complex agent workflows with nodes and edges.
 * Enables sophisticated data flow patterns and conditional routing.
 */

/**
 * Workflow node types
 */
enum class NodeType {
    AGENT,          // Agent execution node
    TOOL,           // Tool execution node
    CONDITION,      // Conditional routing node
    MERGE,          // Data merge node
    SPLIT,          // Data split node
    TRANSFORM,      // Data transformation node
    TRIGGER,        // External trigger node
    SINK            // Final output node
}

/**
 * Workflow node definition
 */
data class WorkflowNode(
    val id: String,
    val name: String,
    val type: NodeType,
    val processor: suspend (Message) -> Message,
    val condition: (suspend (Message) -> Boolean)? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Workflow edge definition
 */
data class WorkflowEdge(
    val id: String,
    val from: String,
    val to: String,
    val condition: (suspend (Message) -> Boolean)? = null,
    val transformer: (suspend (Message) -> Message)? = null,
    val weight: Double = 1.0
)

/**
 * Workflow execution context
 */
data class WorkflowContext(
    val workflowId: String,
    val currentNode: String,
    val visitedNodes: MutableSet<String> = mutableSetOf(),
    val sharedData: MutableMap<String, Any> = mutableMapOf(),
    val executionHistory: MutableList<WorkflowExecution> = mutableListOf()
)

/**
 * Workflow execution record
 */
data class WorkflowExecution(
    val nodeId: String,
    val input: Message,
    val output: Message,
    val executionTime: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Workflow definition
 */
data class WorkflowDefinition(
    val id: String,
    val name: String,
    val description: String,
    val nodes: List<WorkflowNode>,
    val edges: List<WorkflowEdge>,
    val startNode: String,
    val endNodes: Set<String> = emptySet()
) {
    /**
     * Validate workflow structure
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        // Check start node exists
        if (nodes.none { it.id == startNode }) {
            errors.add("Start node '$startNode' not found")
        }
        
        // Check all edge references exist
        edges.forEach { edge ->
            if (nodes.none { it.id == edge.from }) {
                errors.add("Edge '${edge.id}' references non-existent from node '${edge.from}'")
            }
            if (nodes.none { it.id == edge.to }) {
                errors.add("Edge '${edge.id}' references non-existent to node '${edge.to}'")
            }
        }
        
        // Check for cycles (optional warning)
        val hasCycles = detectCycles()
        if (hasCycles) {
            errors.add("Workflow contains cycles - may lead to infinite loops")
        }
        
        return errors
    }
    
    /**
     * Detect cycles in workflow graph
     */
    private fun detectCycles(): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        
        fun hasCycleDFS(nodeId: String): Boolean {
            visited.add(nodeId)
            recursionStack.add(nodeId)
            
            val outgoingEdges = edges.filter { it.from == nodeId }
            for (edge in outgoingEdges) {
                if (!visited.contains(edge.to)) {
                    if (hasCycleDFS(edge.to)) return true
                } else if (recursionStack.contains(edge.to)) {
                    return true
                }
            }
            
            recursionStack.remove(nodeId)
            return false
        }
        
        for (node in nodes) {
            if (!visited.contains(node.id)) {
                if (hasCycleDFS(node.id)) return true
            }
        }
        
        return false
    }
}

/**
 * Workflow builder DSL
 */
class WorkflowBuilder(private val id: String, private val name: String) {
    private val nodes = mutableListOf<WorkflowNode>()
    private val edges = mutableListOf<WorkflowEdge>()
    private var description: String = ""
    private var startNodeId: String? = null
    private val endNodeIds = mutableSetOf<String>()
    
    /**
     * Set workflow description
     */
    fun description(desc: String) {
        description = desc
    }
    
    /**
     * Define agent node
     */
    fun agent(id: String, agent: Agent, name: String = agent.name) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.AGENT,
            processor = { message -> agent.processMessage(message) }
        ))
    }
    
    /**
     * Define tool node
     */
    fun tool(id: String, tool: Tool, name: String = tool.name) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.TOOL,
            processor = { message ->
                val parameters = message.metadata.toMap()
                val result = tool.execute(parameters)
                message.createReply(
                    content = result.result ?: result.error ?: "Tool execution completed",
                    sender = "tool-$id",
                    metadata = result.metadata + mapOf("tool_success" to result.success.toString())
                )
            }
        ))
    }
    
    /**
     * Define condition node
     */
    fun condition(
        id: String, 
        name: String = "Condition $id",
        condition: suspend (Message) -> Boolean
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.CONDITION,
            processor = { message -> message }, // Pass through
            condition = condition
        ))
    }
    
    /**
     * Define transform node
     */
    fun transform(
        id: String,
        name: String = "Transform $id",
        transformer: suspend (Message) -> Message
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.TRANSFORM,
            processor = transformer
        ))
    }
    
    /**
     * Define merge node
     */
    fun merge(
        id: String,
        name: String = "Merge $id",
        merger: suspend (List<Message>) -> Message
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.MERGE,
            processor = { message ->
                // For now, just pass through - proper merge logic needs multiple inputs
                message
            }
        ))
    }
    
    /**
     * Define split node
     */
    fun split(
        id: String,
        name: String = "Split $id",
        splitter: suspend (Message) -> List<Message>
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.SPLIT,
            processor = { message ->
                // Return first split for single-output flow
                val splits = splitter(message)
                splits.firstOrNull() ?: message
            }
        ))
    }
    
    /**
     * Define sink node (final output)
     */
    fun sink(
        id: String,
        name: String = "Sink $id",
        finalizer: suspend (Message) -> Message
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.SINK,
            processor = finalizer
        ))
        endNodeIds.add(id)
    }
    
    /**
     * Define edge between nodes
     */
    fun edge(
        from: String,
        to: String,
        id: String = "${from}_to_${to}",
        condition: (suspend (Message) -> Boolean)? = null,
        transformer: (suspend (Message) -> Message)? = null,
        weight: Double = 1.0
    ) {
        edges.add(WorkflowEdge(
            id = id,
            from = from,
            to = to,
            condition = condition,
            transformer = transformer,
            weight = weight
        ))
    }
    
    /**
     * Set start node
     */
    fun start(nodeId: String) {
        startNodeId = nodeId
    }
    
    /**
     * Set end node
     */
    fun end(nodeId: String) {
        endNodeIds.add(nodeId)
    }
    
    /**
     * Convenience method for sequential flow
     */
    fun flow(vararg nodeIds: String) {
        for (i in 0 until nodeIds.size - 1) {
            edge(nodeIds[i], nodeIds[i + 1])
        }
        if (startNodeId == null && nodeIds.isNotEmpty()) {
            start(nodeIds[0])
        }
        if (nodeIds.isNotEmpty()) {
            end(nodeIds.last())
        }
    }
    
    /**
     * Convenience method for conditional routing
     */
    fun whenCondition(
        condition: suspend (Message) -> Boolean,
        thenNode: String,
        elseNode: String? = null
    ) {
        val conditionId = "condition_${System.currentTimeMillis()}"
        condition(conditionId, condition = condition)
        
        edge(conditionId, thenNode, condition = condition)
        elseNode?.let { 
            edge(conditionId, it, condition = { msg -> !condition(msg) })
        }
    }
    
    /**
     * Build workflow definition
     */
    fun build(): WorkflowDefinition {
        val startNode = startNodeId ?: nodes.firstOrNull()?.id 
            ?: throw IllegalStateException("No start node defined")
        
        val definition = WorkflowDefinition(
            id = id,
            name = name,
            description = description,
            nodes = nodes.toList(),
            edges = edges.toList(),
            startNode = startNode,
            endNodes = endNodeIds.toSet()
        )
        
        // Validate workflow
        val errors = definition.validate()
        if (errors.isNotEmpty()) {
            throw IllegalStateException("Workflow validation failed: ${errors.joinToString(", ")}")
        }
        
        return definition
    }
}

/**
 * Workflow executor
 */
class WorkflowExecutor {
    
    /**
     * Execute workflow with given message
     */
    suspend fun execute(
        workflow: WorkflowDefinition,
        input: Message
    ): Message {
        val context = WorkflowContext(workflow.id, workflow.startNode)
        return executeNode(workflow, input, context)
    }
    
    /**
     * Execute specific node in workflow
     */
    private suspend fun executeNode(
        workflow: WorkflowDefinition,
        message: Message,
        context: WorkflowContext
    ): Message {
        val currentNode = workflow.nodes.find { it.id == context.currentNode }
            ?: return message.createReply(
                content = "Node not found: ${context.currentNode}",
                sender = "workflow-executor",
                type = MessageType.ERROR
            )
        
        // Check if already visited (cycle prevention)
        if (context.visitedNodes.contains(currentNode.id)) {
            return message.createReply(
                content = "Cycle detected at node: ${currentNode.id}",
                sender = "workflow-executor",
                type = MessageType.ERROR
            )
        }
        
        context.visitedNodes.add(currentNode.id)
        
        // Execute node
        val startTime = System.currentTimeMillis()
        val result = try {
            currentNode.processor(message)
        } catch (e: Exception) {
            message.createReply(
                content = "Node execution failed: ${e.message}",
                sender = "workflow-executor",
                type = MessageType.ERROR,
                metadata = mapOf(
                    "node_id" to currentNode.id, 
                    "error" to (e::class.simpleName ?: "Unknown")
                )
            )
        }
        val executionTime = System.currentTimeMillis() - startTime
        
        // Record execution
        context.executionHistory.add(
            WorkflowExecution(
                nodeId = currentNode.id,
                input = message,
                output = result,
                executionTime = executionTime
            )
        )
        
        // Check if end node
        if (workflow.endNodes.contains(currentNode.id)) {
            return result.copy(
                metadata = result.metadata + mapOf(
                    "workflow_id" to workflow.id,
                    "execution_path" to context.visitedNodes.joinToString(" -> "),
                    "total_execution_time" to context.executionHistory.sumOf { it.executionTime }.toString()
                )
            )
        }
        
        // Find next node
        val nextEdge = workflow.edges
            .filter { it.from == currentNode.id }
            .find { edge ->
                edge.condition?.invoke(result) ?: true
            }
        
        if (nextEdge == null) {
            return result.copy(
                metadata = result.metadata + mapOf(
                    "workflow_end" to "no_valid_path",
                    "current_node" to currentNode.id
                )
            )
        }
        
        // Transform message if needed
        val transformedMessage = nextEdge.transformer?.invoke(result) ?: result
        
        // Continue to next node
        val nextContext = context.copy(currentNode = nextEdge.to)
        return executeNode(workflow, transformedMessage, nextContext)
    }
}

/**
 * DSL entry point
 */
fun workflow(id: String, name: String = id, init: WorkflowBuilder.() -> Unit): WorkflowDefinition {
    val builder = WorkflowBuilder(id, name)
    builder.init()
    return builder.build()
}

/**
 * Extension function for Agent to workflow conversion
 */
fun Agent.toWorkflowNode(id: String = this.id): WorkflowNode {
    return WorkflowNode(
        id = id,
        name = name,
        type = NodeType.AGENT,
        processor = { message -> processMessage(message) }
    )
}

/**
 * Extension function for Tool to workflow conversion
 */
fun Tool.toWorkflowNode(id: String = this.name): WorkflowNode {
    return WorkflowNode(
        id = id,
        name = name,
        type = NodeType.TOOL,
        processor = { message ->
            val parameters = message.metadata.toMap()
            val result = execute(parameters)
            message.createReply(
                content = result.result ?: result.error ?: "Tool execution completed",
                sender = "tool-$id",
                metadata = result.metadata + mapOf("tool_success" to result.success.toString())
            )
        }
    )
} 