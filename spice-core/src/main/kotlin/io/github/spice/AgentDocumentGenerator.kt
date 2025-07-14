package io.github.spice

import java.io.File

/**
 * 📝 AgentDocumentGenerator - Agent documentation generator
 * 
 * Documents information about Agents registered in AgentEngine in markdown format.
 * Generates comprehensive documentation including basic information, capabilities, tools, and usage examples for each Agent.
 */
class AgentDocumentGenerator {
    
    /**
     * Generate markdown document based on AgentEngine status
     */
    fun generateAgentDocumentation(engine: AgentEngine): String {
        val markdown = StringBuilder()
        
        // Document header
        markdown.appendLine("# 🤖 Spice Agent Documentation")
        markdown.appendLine()
        markdown.appendLine("This document contains information about all Agents registered in the Spice AgentEngine.")
        markdown.appendLine("Generated at: ${java.time.LocalDateTime.now()}")
        markdown.appendLine()
        
        // Agent overview
        val agents = engine.getAllAgents()
        markdown.appendLine("## 📊 Agent Overview")
        markdown.appendLine()
        markdown.appendLine("- **Total Agents**: ${agents.size}")
        markdown.appendLine("- **Active Agents**: ${agents.count { it.isReady() }}")
        markdown.appendLine("- **Agent Types**: ${agents.map { it.javaClass.simpleName }.distinct().size}")
        markdown.appendLine()
        
        // Individual Agent documentation
        agents.forEach { agent ->
            markdown.appendLine(generateAgentSection(agent))
        }
        
        return markdown.toString()
    }
    
    /**
     * Generate detailed documentation for specific Agent
     */
    private fun generateAgentSection(agent: Agent): String {
        val section = StringBuilder()
        
        section.appendLine("## 🔧 ${agent.name}")
        section.appendLine()
        
        // Basic information table
        section.appendLine("### Basic Information")
        section.appendLine()
        section.appendLine("| Property | Value |")
        section.appendLine("|----------|-------|")
        section.appendLine("| **ID** | `${agent.id}` |")
        section.appendLine("| **Name** | ${agent.name} |")
        section.appendLine("| **Type** | ${agent.javaClass.simpleName} |")
        section.appendLine("| **Description** | ${agent.description} |")
        section.appendLine("| **Status** | ${if (agent.isReady()) "✅ Ready" else "❌ Not Ready"} |")
        section.appendLine()
        
        // Capabilities list
        section.appendLine("### 🎯 Capabilities")
        section.appendLine()
        if (agent.capabilities.isNotEmpty()) {
            agent.capabilities.forEach { capability ->
                section.appendLine("- ${capability.replace("_", " ").replaceFirstChar { it.uppercase() }}")
            }
        } else {
            section.appendLine("- No specific capabilities defined")
        }
        section.appendLine()
        
        // Usage recommendations
        section.appendLine("### 💡 Usage Recommendations")
        section.appendLine()
        generateUsageRecommendations(agent, section)
        section.appendLine()
        
        // Message type compatibility
        section.appendLine("### 📨 Message Type Compatibility")
        section.appendLine()
        val supportedTypes = getSupportedMessageTypes(agent)
        supportedTypes.forEach { type ->
            section.appendLine("- ✅ `${type}`")
        }
        section.appendLine()
        
        // Tools (if any)
        val tools = agent.getTools()
        if (tools.isNotEmpty()) {
            section.appendLine("### 🛠️ Available Tools")
            section.appendLine()
            tools.forEach { tool ->
                section.appendLine("- **${tool.name}**: ${tool.description}")
            }
            section.appendLine()
        }
        
        // Usage examples
        section.appendLine("### 📋 Usage Examples")
        section.appendLine()
        generateUsageExamples(agent, section)
        
        section.appendLine("---")
        section.appendLine()
        
        return section.toString()
    }
    
    /**
     * Generate usage recommendations based on Agent type
     */
    private fun generateUsageRecommendations(agent: Agent, section: StringBuilder) {
        when (agent.javaClass.simpleName) {
            "PromptAgent" -> {
                section.appendLine("- User prompt processing")
                section.appendLine("- Text generation and transformation")
                section.appendLine("- Interactive conversation")
            }
            "ActionAgent" -> {
                section.appendLine("- Data collection and organization")
                section.appendLine("- File processing")
                section.appendLine("- Data transformation tasks")
            }
            "ResultAgent" -> {
                section.appendLine("- Result formatting and visualization")
                section.appendLine("- Report generation")
                section.appendLine("- Final result processing")
            }
            "AgentNode" -> {
                section.appendLine("- Conditional logic processing")
                section.appendLine("- Workflow branching")
                section.appendLine("- Decision automation")
            }
            "MultiAgentFlow" -> {
                section.appendLine("- Multiple flow merging")
                section.appendLine("- Result integration")
                section.appendLine("- Synchronization tasks")
            }
            else -> {
                section.appendLine("- Custom logic execution")
                section.appendLine("- Specialized task processing")
                section.appendLine("- Flexible message processing")
            }
        }
        
        // Capability-based recommendations
        agent.capabilities.forEach { capability ->
            when (capability) {
                "text_processing" -> section.appendLine("- Text analysis and processing")
                "api_calls" -> section.appendLine("- External API integration")
                "data_analysis" -> section.appendLine("- Data analysis tasks")
                "file_handling" -> section.appendLine("- File input/output processing")
                "message_routing" -> section.appendLine("- Message routing and forwarding")
                else -> section.appendLine("- ${capability.replace("_", " ").replaceFirstChar { it.uppercase() }}")
            }
        }
        
        if (agent.capabilities.isEmpty()) {
            section.appendLine("- General message processing")
            section.appendLine("- Basic Agent functionality")
        }
    }
    
    /**
     * Get supported message types for Agent
     */
    private fun getSupportedMessageTypes(agent: Agent): List<String> {
        // Create test messages for each type
        val testMessages = listOf(
            Message("test", "test", MessageType.TEXT),
            Message("test", "test", MessageType.PROMPT),
            Message("test", "test", MessageType.SYSTEM),
            Message("test", "test", MessageType.ACTION),
            Message("test", "test", MessageType.RESULT),
            Message("test", "test", MessageType.ERROR),
            Message("test", "test", MessageType.TOOL_CALL),
            Message("test", "test", MessageType.TOOL_RESULT)
        )
        
        return testMessages.filter { agent.canHandle(it) }.map { it.type.name }
    }
    
    /**
     * Generate usage examples
     */
    private fun generateUsageExamples(agent: Agent, section: StringBuilder) {
        section.appendLine("```kotlin")
        section.appendLine("// Create message")
        section.appendLine("val message = Message(")
        section.appendLine("    sender = \"user\",")
        section.appendLine("    content = \"Your request here\",")
        section.appendLine("    type = MessageType.TEXT")
        section.appendLine(")")
        section.appendLine()
        section.appendLine("// Send to agent")
        section.appendLine("val response = agent.receive(message)")
        section.appendLine()
        section.appendLine("// Process response")
        section.appendLine("println(\"Response: \${response.content}\")")
        section.appendLine("```")
        section.appendLine()
        
        // Add specific examples based on agent type
        when (agent.javaClass.simpleName) {
            "PromptAgent" -> {
                section.appendLine("#### Prompt Processing Example")
                section.appendLine("```kotlin")
                section.appendLine("val promptMessage = Message(")
                section.appendLine("    sender = \"user\",")
                section.appendLine("    content = \"Analyze this data and provide insights\",")
                section.appendLine("    type = MessageType.PROMPT")
                section.appendLine(")")
                section.appendLine("val result = agent.receive(promptMessage)")
                section.appendLine("```")
            }
            "ActionAgent" -> {
                section.appendLine("#### Action Execution Example")
                section.appendLine("```kotlin")
                section.appendLine("val actionMessage = Message(")
                section.appendLine("    sender = \"system\",")
                section.appendLine("    content = \"Execute data processing pipeline\",")
                section.appendLine("    type = MessageType.ACTION")
                section.appendLine(")")
                section.appendLine("val result = agent.receive(actionMessage)")
                section.appendLine("```")
            }
        }
        
        // Remove last comma for proper processing
        if (section.isNotEmpty() && section.length > 2) {
            section.setLength(section.length - 2) // Remove last comma and newline
        }
    }
    
    /**
     * Generate tool documentation
     */
    private fun generateToolDocumentation(tool: Tool): String {
        val toolDoc = StringBuilder()
        
        toolDoc.appendLine("#### 🔧 ${tool.name}")
        toolDoc.appendLine()
        toolDoc.appendLine("**Description**: ${tool.description}")
        toolDoc.appendLine()
        
        // Tool parameters
        val schema = tool.getSchema()
        if (schema.isNotEmpty()) {
            toolDoc.appendLine("**Parameters**:")
            toolDoc.appendLine()
            schema.forEach { (param, description) ->
                toolDoc.appendLine("- `$param`: $description")
            }
            toolDoc.appendLine()
        }
        
        // Usage example
        toolDoc.appendLine("**Usage Example**:")
        toolDoc.appendLine("```kotlin")
        toolDoc.appendLine("val parameters = mapOf(")
        schema.keys.take(3).forEach { param ->
            toolDoc.appendLine("    \"$param\" to \"example_value\",")
        }
        if (schema.isNotEmpty()) {
            toolDoc.setLength(toolDoc.length - 2) // Remove last comma
            toolDoc.appendLine()
        }
        toolDoc.appendLine(")")
        toolDoc.appendLine("val result = tool.execute(parameters)")
        toolDoc.appendLine("```")
        toolDoc.appendLine()
        
        return toolDoc.toString()
    }
    
    /**
     * Generate Agent flow diagram
     */
    private fun generateFlowDiagram(agents: List<Agent>): String {
        val diagram = StringBuilder()
        
        diagram.appendLine("```mermaid")
        diagram.appendLine("graph TD")
        
        agents.forEachIndexed { index, agent ->
            val nodeId = "A$index"
            val nodeLabel = "${agent.name}\\n(${agent.javaClass.simpleName})"
            diagram.appendLine("    $nodeId[\"$nodeLabel\"]")
            
            // Add connections based on message flow
            if (index < agents.size - 1) {
                diagram.appendLine("    $nodeId --> A${index + 1}")
            }
        }
        
        diagram.appendLine("```")
        
        return diagram.toString()
    }
}

/**
 * AgentEngine extension function - Easy document generation
 */
fun AgentEngine.generateDocumentation(): String {
    val generator = AgentDocumentGenerator()
    return generator.generateAgentDocumentation(this)
}

/**
 * Agent extension function - Individual Agent document generation
 */
fun Agent.generateDocumentation(): String {
    val generator = AgentDocumentGenerator()
    return generator.generateAgentSection(this)
}

/**
 * Save document to file
 */
fun String.saveToFile(filePath: String) {
    File(filePath).writeText(this)
}

/**
 * Save individual Agent document to file
 */
fun Agent.saveDocumentationToFile(filePath: String) {
    val documentation = this.generateDocumentation()
    documentation.saveToFile(filePath)
} 