package samples.basic

import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.psi.*
import kotlinx.coroutines.runBlocking

/**
 * 🧠 Mnemo PSI Integration Sample
 * 
 * Shows how to use PSI to save Spice structures to mnemo
 * for context management and vibe coding prevention.
 */
fun main() = runBlocking {
    println("=== Mnemo PSI Integration Sample ===\n")
    
    // Mock mnemo client (replace with actual MCP mnemo client)
    val mnemo = MockMnemoClient()
    
    // 1. Create agents with potential vibe coding issues
    val dataAnalyzer = buildAgent {
        id = "data-analyzer"
        name = "Data Analysis Agent"
        description = "Analyzes data and provides insights"
        
        tool("sentiment-analysis")
        tool("keyword-extraction")
        
        vectorStore {
            provider = "qdrant"
            collection = "analytics"
        }
        
        handle { comm ->
            comm.reply("Analysis complete!", id)
        }
    }
    
    val textAnalyzer = buildAgent {
        id = "text-analyzer"  
        name = "Text Analyzer"  // Similar name!
        description = "Analyzes text content"
        
        tool("sentiment-analysis")  // Duplicate tool!
        tool("summarization")
        
        handle { comm ->
            comm.reply("Text analyzed!", id)
        }
    }
    
    // 2. Save agent structures to mnemo
    println("Saving agent structures to mnemo...")
    MnemoIntegration.run {
        dataAnalyzer.saveToMnemo(mnemo)
        textAnalyzer.saveToMnemo(mnemo)
    }
    
    // 3. Create and execute a flow
    val analysisFlow = buildFlow {
        name = "Document Analysis Flow"
        description = "Analyzes documents using multiple agents"
        
        step("extract", "data-analyzer")
        step("summarize", "text-analyzer")
    }
    
    // 4. Save flow execution
    println("\nExecuting flow and saving to mnemo...")
    val input = comm("Analyze this document about AI ethics")
    val output = comm("Analysis complete: AI ethics considerations found")
    
    MnemoIntegration.saveFlowExecution(
        flow = analysisFlow,
        input = input,
        output = output,
        mnemo = mnemo
    )
    
    // 5. Save context injection pattern
    println("\nSaving context injection pattern...")
    MnemoIntegration.saveContextInjection(
        agentId = "data-analyzer",
        injectionPoint = "beforeAnalysis",
        context = mapOf(
            "source" to "vector-search",
            "query" to "AI ethics guidelines",
            "limit" to 5,
            "threshold" to 0.8
        ),
        mnemo = mnemo
    )
    
    // 6. Detect vibe coding patterns
    println("\nDetecting vibe coding patterns...")
    val patterns = MnemoIntegration.detectVibeCoding(
        agents = listOf(dataAnalyzer, textAnalyzer),
        mnemo = mnemo
    )
    
    println("Found ${patterns.size} vibe coding patterns:")
    patterns.forEach { pattern ->
        println("- [${pattern.severity}] ${pattern.type}: ${pattern.description}")
    }
    
    // 7. Query saved structures from mnemo
    println("\n\nQuerying mnemo for saved structures:")
    println("=".repeat(50))
    
    // Search for agent structures
    val agentMemories = mnemo.search("agent structure", listOf("code_pattern"))
    println("\nAgent structures in mnemo: ${agentMemories.size}")
    agentMemories.forEach { memory ->
        println("- ${memory.key}: ${memory.tags.joinToString()}")
    }
    
    // Search for flow executions
    val flowMemories = mnemo.search("flow execution", listOf("fact"))
    println("\nFlow executions in mnemo: ${flowMemories.size}")
    
    // Search for context patterns
    val contextMemories = mnemo.search("context injection", listOf("skill"))
    println("\nContext injection patterns: ${contextMemories.size}")
    
    println("\n✅ Mnemo PSI integration complete!")
}

/**
 * Mock implementation of MnemoClient for demonstration
 */
class MockMnemoClient : MnemoClient {
    private val memories = mutableListOf<Memory>()
    
    override fun remember(
        key: String,
        content: String,
        memory_type: String,
        tags: List<String>
    ) {
        memories.add(Memory(key, content, memory_type, tags))
        println("💾 Saved to mnemo: $key (${memory_type}) [${tags.joinToString()}]")
    }
    
    override fun recall(key: String): String? {
        return memories.find { it.key == key }?.content
    }
    
    override fun search(
        query: String,
        memory_types: List<String>?,
        limit: Int
    ): List<Memory> {
        return memories
            .filter { memory ->
                (memory_types == null || memory.memory_type in memory_types) &&
                (memory.key.contains(query, ignoreCase = true) || 
                 memory.tags.any { it.contains(query, ignoreCase = true) })
            }
            .take(limit)
    }
}