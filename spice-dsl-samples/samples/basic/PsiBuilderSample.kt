package samples.basic

import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.psi.*
import io.github.noailabs.spice.agents.*
import kotlinx.coroutines.runBlocking

/**
 * 🌲 PSI Builder Sample
 * 
 * Demonstrates how to convert DSL constructs to PSI trees
 * for analysis, storage, and LLM interaction.
 */
fun main() = runBlocking {
    println("=== PSI Builder Sample ===\n")
    
    // 1. Create a complex agent with tools and vector store
    val researchAgent = buildAgent {
        id = "research-agent"
        name = "Research Assistant"
        description = "Analyzes documents and provides insights"
        
        capabilities {
            add("document-analysis")
            add("summarization")
            add("fact-checking")
        }
        
        // Add global tool reference
        tool("web-search")
        
        // Add inline tool
        tool {
            name = "word-counter"
            description = "Counts words in text"
            parameter("text", "string", "Text to analyze", required = true)
            
            execute { params ->
                val text = params["text"] as? String ?: ""
                val wordCount = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                ToolResult.success("Word count: $wordCount")
            }
        }
        
        // Configure vector store
        vectorStore {
            provider = "qdrant"
            host = "localhost"
            port = 6333
            collection = "research-docs"
            vectorSize = 384
        }
        
        handle { comm ->
            comm.reply("Research agent ready to analyze!", id)
        }
    }
    
    // 2. Convert to PSI
    println("Converting agent to PSI tree...\n")
    val psi = SpicePsiBuilder.run { 
        researchAgent.builder.toPsi()
    }
    
    // 3. Display PSI tree structure
    println("PSI Tree Structure:")
    println(psi.toString())
    println()
    
    // 4. Analyze PSI structure
    println("PSI Analysis:")
    val tools = psi.findByType(PsiTypes.TOOL_REF)
    println("- Found ${tools.size} tool references")
    
    val vectorStores = psi.findByType("VectorStore")
    println("- Found ${vectorStores.size} vector stores")
    
    val handlers = psi.findByType(PsiTypes.HANDLER)
    println("- Found ${handlers.size} handlers")
    println()
    
    // 5. Serialize to JSON
    println("PSI as JSON:")
    val json = PsiSerializer.run { psi.toJson() }
    println(json.toString())
    println()
    
    // 6. Convert to mnemo format
    println("PSI for mnemo storage:")
    val mnemoFormat = PsiSerializer.run { psi.toMnemoFormat() }
    println("Mnemo format (${mnemoFormat.length} chars): ${mnemoFormat.take(100)}...")
    println()
    
    // 7. Generate Mermaid diagram
    println("PSI as Mermaid diagram:")
    val mermaid = PsiSerializer.run { psi.toMermaid() }
    println(mermaid)
    println()
    
    // 8. LLM-friendly format
    println("PSI for LLM analysis:")
    val llmFormat = PsiSerializer.run { psi.toLLMFormat() }
    println(llmFormat)
    println()
    
    // 9. Create a swarm agent PSI
    println("=== Swarm Agent PSI ===\n")
    
    val analyzerAgent = ModernGPTAgent(
        id = "analyzer",
        name = "Analyzer",
        apiKey = "test-key"
    )
    
    val summarizerAgent = ModernClaudeAgent(
        id = "summarizer",
        name = "Summarizer",
        apiKey = "test-key"
    )
    
    val swarmBuilder = SwarmAgentBuilder().apply {
        id = "research-swarm"
        name = "Research Swarm"
        description = "Collaborative research team"
        
        member(analyzerAgent)
        member(summarizerAgent)
        
        coordinatorType = CoordinatorType.SMART
        
        config {
            debugEnabled = true
            consensusThreshold = 0.7
        }
    }
    
    val swarmPsi = SpicePsiBuilder.run { swarmBuilder.toPsi() }
    println("Swarm PSI Structure:")
    println(swarmPsi.toString())
    println()
    
    // 10. Build complete application PSI
    println("=== Complete Application PSI ===\n")
    
    val appPsi = SpicePsiBuilder.buildCompletePsi {
        agent(researchAgent)
        agent(analyzerAgent)
        agent(summarizerAgent)
        
        config("app.name", "Research System")
        config("app.version", "1.0.0")
    }
    
    println("Application PSI:")
    println(appPsi.toLLMFormat())
    
    // 11. Round-trip test
    println("\n=== Round-trip Test ===")
    val serialized = PsiSerializer.run { psi.toMnemoFormat() }
    val deserialized = PsiSerializer.fromMnemoFormat(serialized)
    
    println("Original type: ${psi.type}")
    println("Restored type: ${deserialized.type}")
    println("Props match: ${psi.props == deserialized.props}")
    println("Children count match: ${psi.children.size == deserialized.children.size}")
    
    println("\n✅ PSI Builder demonstration complete!")
}

// Extension function for LLM format
private fun PsiNode.toLLMFormat(): String = PsiSerializer.run { 
    this@toLLMFormat.toLLMFormat() 
}