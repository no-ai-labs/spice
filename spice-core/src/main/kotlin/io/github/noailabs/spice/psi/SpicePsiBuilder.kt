package io.github.noailabs.spice.psi

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.model.*
import io.github.noailabs.spice.swarm.SwarmAgentBuilder
import io.github.noailabs.spice.swarm.CoordinatorType

/**
 * 🌲 PSI Converter for Spice Framework DSL
 *
 * Translates DSL constructs like Agent, Tool, Flow into a traversable PSI tree.
 * This creates LLM-friendly representations of Spice components.
 */
object SpicePsiBuilder {
    
    // ===== Agent Conversions =====
    
    /**
     * Convert CoreAgentBuilder to PSI
     */
    fun CoreAgentBuilder.toPsi(): PsiNode {
        val root = psiNode(PsiTypes.AGENT) {
            prop("id", id)
            prop("name", name)
            prop("description", description)
            
            // Add metadata
            if (metadata.isNotEmpty()) {
                meta("buildMetadata", metadata)
            }
        }
        
        // Get all tools from the builder
        val allTools = getAllAgentTools()
        if (allTools.isNotEmpty()) {
            val toolsNode = psiNode(PsiTypes.TOOLS)
            allTools.forEach { tool ->
                toolsNode.add(tool.toPsi())
            }
            root.add(toolsNode)
        }
        
        // Vector stores
        val allVectorStores = getAllVectorStores()
        if (allVectorStores.isNotEmpty()) {
            val vsNode = psiNode(PsiTypes.VECTOR_STORES)
            allVectorStores.forEach { (name, config) ->
                vsNode.add(config.toPsi(name))
            }
            root.add(vsNode)
        }
        
        // Handler function (represented as metadata)
        root.add(psiNode(PsiTypes.HANDLER) {
            prop("type", "message")
            meta("hasImplementation", true)
        })
        
        return root
    }
    
    /**
     * Convert Agent interface to PSI
     */
    fun Agent.toPsi(): PsiNode = psiNode(PsiTypes.AGENT) {
        prop("id", id)
        prop("name", name)
        prop("description", description)
        prop("capabilities", capabilities)
        prop("ready", isReady())
        
        // Add tools if CoreAgent
        (this@toPsi as? CoreAgent)?.let { coreAgent ->
            val tools = coreAgent.getTools()
            if (tools.isNotEmpty()) {
                val toolsNode = psiNode(PsiTypes.TOOLS)
                tools.forEach { tool ->
                    toolsNode.add(tool.toPsi())
                }
                add(toolsNode)
            }
        }
        
        // Add SwarmAgent specific info
        (this@toPsi as? io.github.noailabs.spice.swarm.SwarmAgent)?.let { swarm ->
            prop("type", "swarm")
            meta("swarmCapabilities", swarm.capabilities.filter { it.startsWith("swarm-") })
        }
    }
    
    // ===== Tool Conversions =====
    
    /**
     * Convert Tool to PSI
     */
    fun Tool.toPsi(): PsiNode = psiNode(PsiTypes.TOOL) {
        prop("name", name)
        prop("description", description)
        
        // Schema
        add(schema.toPsi())
        
        // Add metadata if available
        (this@toPsi as? ToolWrapperEx)?.let { wrapper ->
            prop("source", wrapper.source)
            if (wrapper.tags.isNotEmpty()) {
                prop("tags", wrapper.tags)
            }
            if (wrapper.metadata.isNotEmpty()) {
                meta("toolMetadata", wrapper.metadata)
            }
        }
    }
    
    /**
     * Convert InlineTool to PSI
     */
    fun InlineTool.toPsi(): PsiNode = psiNode(PsiTypes.TOOL) {
        prop("name", name)
        prop("description", description)
        prop("source", "inline")
        
        // Schema
        add(schema.toPsi())
        
        // Handler indicator
        add(psiNode(PsiTypes.HANDLER) {
            prop("type", "inline")
            meta("hasImplementation", true)
        })
    }
    
    /**
     * Convert AgentTool to PSI
     */
    fun AgentTool.toPsi(): PsiNode = psiNode(PsiTypes.TOOL) {
        prop("name", name)
        prop("description", description)
        prop("implementationType", implementationType)
        
        if (tags.isNotEmpty()) {
            prop("tags", tags)
        }
        
        if (metadata.isNotEmpty()) {
            meta("toolMetadata", metadata)
        }
        
        if (implementationDetails.isNotEmpty()) {
            meta("implementationDetails", implementationDetails)
        }
        
        // Parameters
        val schemaNode = psiNode(PsiTypes.SCHEMA)
        parameters.forEach { (paramName, paramSchema) ->
            schemaNode.add(paramSchema.toPsi(paramName))
        }
        add(schemaNode)
    }
    
    // ===== Schema Conversions =====
    
    /**
     * Convert ToolSchema to PSI
     */
    fun ToolSchema.toPsi(): PsiNode = psiNode(PsiTypes.SCHEMA) {
        prop("name", name)
        prop("description", description)
        
        parameters.forEach { (paramName, paramSchema) ->
            add(paramSchema.toPsi(paramName))
        }
    }
    
    /**
     * Convert ParameterSchema to PSI
     */
    fun ParameterSchema.toPsi(name: String): PsiNode = psiNode(PsiTypes.PARAMETER) {
        prop("name", name)
        prop("type", type)
        prop("description", description)
        prop("required", required)
        default?.let { prop("default", it) }
    }
    
    // ===== VectorStore Conversions =====
    
    /**
     * Convert VectorStoreConfig to PSI
     */
    fun VectorStoreConfig.toPsi(name: String): PsiNode = psiNode("VectorStore") {
        prop("name", name)
        prop("provider", provider)
        prop("host", host)
        prop("port", port)
        prop("collection", collection)
        prop("vectorSize", vectorSize)
        
        // Don't expose API key
        if (apiKey != null) {
            prop("hasApiKey", true)
        }
        
        if (config.isNotEmpty()) {
            meta("config", config)
        }
    }
    
    // ===== Flow Conversions =====

    /**
     * Convert MultiAgentFlow to PSI
     */
    fun MultiAgentFlow.toPsi(): PsiNode = psiNode(PsiTypes.FLOW) {
        prop("id", id)
        prop("type", "MultiAgentFlow")
        prop("stepCount", getStepCount())

        // Add steps information
        add(psiNode("FlowSteps") {
            meta("stepCount", getStepCount())
            getSteps().forEachIndexed { index, step ->
                add(psiNode("Step") {
                    prop("index", index)
                    prop("agentId", step.agent.id)
                    prop("hasCondition", step.condition != null)
                })
            }
        })
    }
    
    // ===== Swarm Conversions =====
    
    /**
     * Convert SwarmAgentBuilder to PSI
     */
    fun SwarmAgentBuilder.toPsi(): PsiNode = psiNode(PsiTypes.SWARM) {
        prop("id", id)
        prop("name", name)
        prop("description", description)
        
        // Since private fields can't be accessed, indicate swarm configuration exists
        add(psiNode(PsiTypes.CONFIG) {
            meta("hasConfiguration", true)
            meta("builderType", "SwarmAgentBuilder")
        })
        
        // Members info
        add(psiNode(PsiTypes.MEMBERS) {
            meta("hasMemberAgents", true)
        })
    }
    
    // ===== Persona Conversions =====
    
    /**
     * Convert AgentPersona to PSI
     */
    fun AgentPersona.toPsi(): PsiNode = psiNode(PsiTypes.PERSONA) {
        prop("name", name)
        prop("personalityType", personalityType.name)
        prop("communicationStyle", communicationStyle.name)
        prop("traits", traits.map { it.name })
        
        // Behavior modifiers
        if (behaviorModifiers.isNotEmpty()) {
            meta("behaviorModifiers", behaviorModifiers)
        }
    }
    
    // ===== Utility Functions =====
    
    /**
     * Create a PSI tree from a complete agent configuration
     */
    fun buildCompletePsi(block: CompletePsiBuilder.() -> Unit): PsiNode {
        val builder = CompletePsiBuilder()
        builder.block()
        return builder.build()
    }
}

/**
 * Builder for complete PSI trees
 */
class CompletePsiBuilder {
    private val agents = mutableListOf<PsiNode>()
    private val tools = mutableListOf<PsiNode>()
    private val flows = mutableListOf<PsiNode>()
    private val configs = mutableMapOf<String, Any>()
    
    fun agent(agent: Agent) {
        agents.add(SpicePsiBuilder.run { agent.toPsi() })
    }
    
    fun tool(tool: Tool) {
        tools.add(SpicePsiBuilder.run { tool.toPsi() })
    }
    
    fun flow(flow: MultiAgentFlow) {
        flows.add(SpicePsiBuilder.run { flow.toPsi() })
    }
    
    fun config(key: String, value: Any) {
        configs[key] = value
    }
    
    fun build(): PsiNode = psiNode("SpiceApplication") {
        if (agents.isNotEmpty()) {
            add(psiNode("Agents") {
                addAll(agents)
            })
        }
        
        if (tools.isNotEmpty()) {
            add(psiNode("Tools") {
                addAll(tools)
            })
        }
        
        if (flows.isNotEmpty()) {
            add(psiNode("Flows") {
                addAll(flows)
            })
        }
        
        if (configs.isNotEmpty()) {
            add(psiNode("Configuration") {
                configs.forEach { (k, v) ->
                    prop(k, v)
                }
            })
        }
    }
}