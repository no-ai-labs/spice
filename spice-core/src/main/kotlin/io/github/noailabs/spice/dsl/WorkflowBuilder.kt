package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.*
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
    val processor: suspend (Comm) -> Comm,
    val condition: (suspend (Comm) -> Boolean)? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Workflow edge definition
 */
data class WorkflowEdge(
    val id: String,
    val from: String,
    val to: String,
    val condition: (suspend (Comm) -> Boolean)? = null,
    val transformer: (suspend (Comm) -> Comm)? = null,
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
    val input: Comm,
    val output: Comm,
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
            processor = { comm -> agent.processComm(comm) }
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
            processor = { comm ->
                val parameters = comm.data.toMap()
                val result = tool.execute(parameters)
                comm.reply(
                    content = result.result ?: result.error ?: "Tool execution completed",
                    from = "tool-$id",
                    data = result.metadata + mapOf("tool_success" to result.success.toString())
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
        condition: suspend (Comm) -> Boolean
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.CONDITION,
            processor = { comm -> comm }, // Pass through
            condition = condition
        ))
    }
    
    /**
     * Define transform node
     */
    fun transform(
        id: String,
        name: String = "Transform $id",
        transformer: suspend (Comm) -> Comm
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
        merger: suspend (List<Comm>) -> Comm
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.MERGE,
            processor = { comm ->
                // For now, just pass through - proper merge logic needs multiple inputs
                comm
            }
        ))
    }
    
    /**
     * Define split node
     */
    fun split(
        id: String,
        name: String = "Split $id",
        splitter: suspend (Comm) -> List<Comm>
    ) {
        nodes.add(WorkflowNode(
            id = id,
            name = name,
            type = NodeType.SPLIT,
            processor = { comm ->
                // Return first split for single-output flow
                val splits = splitter(comm)
                splits.firstOrNull() ?: comm
            }
        ))
    }
    
    /**
     * Define sink node (final output)
     */
    fun sink(
        id: String,
        name: String = "Sink $id",
        finalizer: suspend (Comm) -> Comm
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
        condition: (suspend (Comm) -> Boolean)? = null,
        transformer: (suspend (Comm) -> Comm)? = null,
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
        condition: suspend (Comm) -> Boolean,
        thenNode: String,
        elseNode: String? = null
    ) {
        val conditionId = "condition_${System.currentTimeMillis()}"
        condition(conditionId, condition = condition)
        
        edge(conditionId, thenNode, condition = condition)
        elseNode?.let { 
            edge(conditionId, it, condition = { comm -> !condition(comm) })
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
        input: Comm
    ): Comm {
        val context = WorkflowContext(workflow.id, workflow.startNode)
        return executeNode(workflow, input, context)
    }
    
    /**
     * Execute specific node in workflow
     */
    private suspend fun executeNode(
        workflow: WorkflowDefinition,
        comm: Comm,
        context: WorkflowContext
    ): Comm {
        val currentNode = workflow.nodes.find { it.id == context.currentNode }
            ?: return comm.reply(
                content = "Node not found: ${context.currentNode}",
                from = "workflow-executor",
                type = CommType.ERROR
            )
        
        // Check if already visited (cycle prevention)
        if (context.visitedNodes.contains(currentNode.id)) {
            return comm.reply(
                content = "Cycle detected at node: ${currentNode.id}",
                from = "workflow-executor",
                type = CommType.ERROR
            )
        }
        
        context.visitedNodes.add(currentNode.id)
        
        // Execute node
        val startTime = System.currentTimeMillis()
        val result = try {
            currentNode.processor(comm)
        } catch (e: Exception) {
            comm.reply(
                content = "Node execution failed: ${e.message}",
                from = "workflow-executor",
                type = CommType.ERROR,
                data = mapOf(
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
                input = comm,
                output = result,
                executionTime = executionTime
            )
        )
        
        // Check if end node
        if (workflow.endNodes.contains(currentNode.id)) {
            return result.copy(
                data = result.data + mapOf(
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
                data = result.data + mapOf(
                    "workflow_end" to "no_valid_path",
                    "current_node" to currentNode.id
                )
            )
        }
        
        // Transform message if needed
        val transformedComm = nextEdge.transformer?.invoke(result) ?: result
        
        // Continue to next node
        val nextContext = context.copy(currentNode = nextEdge.to)
        return executeNode(workflow, transformedComm, nextContext)
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
        processor = { comm -> processComm(comm) }
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
        processor = { comm ->
            val parameters = comm.data.toMap()
            val result = execute(parameters)
            comm.reply(
                content = result.result ?: result.error ?: "Tool execution completed",
                from = "tool-$id",
                data = result.metadata + mapOf("tool_success" to result.success.toString())
            )
        }
    )
} 