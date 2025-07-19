package io.github.spice.samples

import io.github.spice.*
import io.github.spice.*
import io.github.spice.dsl.*

/**
 * 🧠 VectorStore Ultimate DSL Sample
 * 
 * Demonstrates the revolutionary 4-Level Management System:
 * Level 1: Agent tools
 * Level 2: Global tools  
 * Level 3: Inline tools
 * Level 4: VectorStore management
 */

/**
 * 🚀 RAG Agent with VectorStore DSL
 */
fun createRAGAgent(): Agent {
    return buildAgent {
        name = "RAG Knowledge Agent"
        description = "Retrieval-Augmented Generation agent with vector search"
        debugMode(enabled = true, prefix = "[RAG]")
        
        // Level 4: VectorStore DSL configuration
        vectorStore("knowledge-base") {
            provider = "qdrant"
            connection("localhost", 6333)
            apiKey = "your-api-key"
            collection = "documents"
            vectorSize = 1536
        }
        
        vectorStore("memory", "qdrant://localhost:6333?apiKey=memory-key&collection=conversations")
        
        // Level 3: Inline RAG tools
        tool("contextual-answer", autoRegister = true) {
            description("Generate contextual answers using retrieved knowledge")
            parameter("question", "string", "User question", required = true)
            parameter("context", "string", "Retrieved context", required = false)
            
            execute(fun (params: Map<String, Any>): ToolResult {
                val question = params["question"] as String
                val context = params["context"] as? String ?: ""
                
                val answer = if (context.isNotEmpty()) {
                    "Based on the knowledge: $context\n\nAnswer: Let me help you with '$question'..."
                } else {
                    "I'll search for relevant information about: $question"
                }
                
                return ToolResult.success(answer)
            })
        }
        
        // Level 2: Global tools
        globalTools("text-processor", "datetime")
        
        handle { comm ->
            println("[RAG] Processing RAG query: ${comm.content}")
            
            // Step 1: Search knowledge base (mock for demo)
            val searchResults = "Knowledge result: AI concepts, machine learning basics"
            println("[RAG] Search results: $searchResults")
            
            // Step 2: Search conversation memory (mock for demo)
            val memoryResults = "Previous: User asked about AI yesterday"
            println("[RAG] Memory results: $memoryResults")
            
            // Step 3: Generate contextual answer (mock for demo)
            val answer = "I can help you with AI questions based on my knowledge base"
            
            comm.reply(
                content = "🧠 RAG Response: $answer",
                from = id
            )
        }
    }
}

/**
 * 🎯 Multi-VectorStore Agent
 */
fun createMultiStoreAgent(): Agent {
    return buildAgent {
        name = "Multi-Store Research Agent"
        description = "Agent with multiple specialized vector stores"
        debugMode(enabled = true, prefix = "[RESEARCH]")
        
        // Multiple vector stores for different domains
        vectorStore("papers") {
            provider = "qdrant"
            connection("research-db", 6333)
            collection = "academic-papers"
            vectorSize = 1536
        }
        
        vectorStore("code") {
            provider = "qdrant"
            connection("code-db", 6334)
            collection = "code-snippets"
            vectorSize = 768
        }
        
        vectorStore("docs") {
            provider = "qdrant"
            connection("docs-db", 6335)
            collection = "documentation"
            vectorSize = 384
        }
        
        tool("comprehensive-search") {
            description("Search across all knowledge stores")
            parameter("query", "string", "Search query")
            parameter("domains", "array", "Domains to search", required = false)
            
            execute(fun (params: Map<String, Any>): ToolResult {
                val query = params["query"] as String
                val results = mutableListOf<String>()
                
                // Mock search results for demo
                results.add("Papers: Found 3 academic papers about '$query'")
                results.add("Code: Found 5 code snippets related to '$query'")
                results.add("Docs: Found 2 documentation pages for '$query'")
                
                return ToolResult.success(results.joinToString("\n\n"))
            })
        }
        
        handle { comm ->
            println("[RESEARCH] Multi-store research query: ${comm.content}")
            
            // Mock comprehensive search for demo
            val comprehensiveResults = "Research completed across all stores"
            
            comm.reply(
                content = "📚 Research Results:\n$comprehensiveResults",
                from = id
            )
        }
    }
}

/**
 * 🔥 Quick Vector Agent (Connection String Style)
 */
fun createQuickVectorAgent(): Agent {
    return buildAgent {
        name = "Quick Vector Agent"
        description = "Rapid setup with connection strings"
        
        // Super quick setup with connection strings
        vectorStore("main", "qdrant://localhost:6333?apiKey=quick-key")
        vectorStore("backup", "qdrant://backup-server:6333")
        
        handle { comm ->
            // Mock search for demo
            val mainResults = "Quick vector search result for: ${comm.content}"
            comm.reply(
                content = "Quick search result: $mainResults",
                from = id
            )
        }
    }
}

/**
 * 🧪 VectorStore DSL Test Suite
 */
fun runVectorStoreDSLTests() {
    println("🧠 VectorStore Ultimate DSL Tests")
    println("==================================")
    
    // Test 1: RAG Agent Creation
    println("🚀 Test 1: RAG Agent with VectorStore DSL")
    val ragAgent = createRAGAgent()
    println("✅ Created RAG agent: ${ragAgent.name}")
    println("   - ID: ${ragAgent.id}")
    println("   - Tools: ${ragAgent.getTools().map { it.name }}")
    println("   - VectorStores: ${ragAgent.getVectorStores().keys}")
    
    // Demonstrate accessing VectorStore instances
    val knowledgeStore = ragAgent.getVectorStore("knowledge-base")
    println("   - Knowledge base store: ${if (knowledgeStore != null) "Available" else "Not found"}")
    
    // Also available from registry
    val memoryStore = VectorStoreRegistry.get("memory")
    println("   - Memory store from registry: ${if (memoryStore != null) "Available" else "Not found"}")
    println()
    
    // Test 2: Multi-Store Agent
    println("🎯 Test 2: Multi-VectorStore Agent")
    val multiAgent = createMultiStoreAgent()
    println("✅ Created multi-store agent: ${multiAgent.name}")
    println("   - ID: ${multiAgent.id}")
    println("   - Tools: ${multiAgent.getTools().map { it.name }}")
    println()
    
    // Test 3: Quick Vector Agent
    println("🔥 Test 3: Quick Vector Agent (Connection Strings)")
    val quickAgent = createQuickVectorAgent()
    println("✅ Created quick agent: ${quickAgent.name}")
    println("   - ID: ${quickAgent.id}")
    println("   - Tools: ${quickAgent.getTools().map { it.name }}")
    println()
    
    println("🎉 All VectorStore DSL tests completed successfully!")
}

/**
 * 📝 VectorStore DSL Usage Examples
 */
fun printVectorStoreDSLExamples() {
    println("""
🧠 VectorStore Ultimate DSL Examples
====================================

 1. Full Configuration DSL:
 - Configure provider, host, port, apiKey
 - Set collection and vector dimensions
 - Auto-generates search tools
 
 2. Quick Connection String:
 - Use connection strings like "qdrant://host:port?params"
 - Rapid setup for prototyping
 
 3. Multiple Vector Stores:
 - Configure multiple stores for different domains
 - Each gets its own auto-generated search tool
 
 4. 4-Level Management System:
 - Level 1: Agent tools
 - Level 2: Global tools
 - Level 3: Inline tools 
 - Level 4: VectorStore management

🚀 Revolutionary Features:
- Auto-generated search tools for each vector store
- Connection string parsing (qdrant://host:port?params)
- Multiple provider support (qdrant, pinecone, weaviate)
- Seamless integration with runTool() sugar DSL
- Debug mode with detailed logging
- Thread-safe vector store management

""".trimIndent())
}

/**
 * 🎯 Main Demo Function
 */
fun main() {
    println("🧠 VectorStore Ultimate DSL Demo")
    println("=================================")
    
    runVectorStoreDSLTests()
    printVectorStoreDSLExamples()
} 