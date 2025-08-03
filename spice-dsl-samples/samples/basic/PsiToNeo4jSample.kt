package samples.basic

import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.psi.*
import kotlinx.serialization.json.*

/**
 * 📊 PSI to Neo4j Graph Conversion Sample
 * 
 * Shows how Spice converts PSI trees to Neo4j-friendly format
 * so mnemo can directly store them without LLM interpretation.
 */
fun main() {
    println("--- PSI to Neo4j Graph Conversion ---\n")
    
    // 1. Create a sample agent
    val aiResearcher = buildAgent {
        id = "ai-researcher"
        name = "AI Research Assistant"
        description = "Researches AI papers and trends"
        
        tool("search-arxiv") {
            description("Search AI papers on arXiv")
            parameters {
                string("query", "Search query")
                integer("max_results", "Maximum results", default = 10)
            }
            execute { params ->
                println("Searching arXiv: ${params["query"]}")
                success("Found 5 relevant papers")
            }
        }
        
        tool("summarize-paper") {
            description("Summarize a research paper")
            parameters {
                string("paper_id", "Paper identifier")
                boolean("include_citations", "Include citations", default = false)
            }
            execute { params ->
                println("Summarizing paper: ${params["paper_id"]}")
                success("Summary generated")
            }
        }
        
        vectorStore("research-papers") {
            provider = "pinecone"
            apiKey = "test-key"
            index = "ai-papers"
            dimension = 1536
        }
        
        handle { comm ->
            comm.reply("Researching: ${comm.content}")
        }
    }
    
    // 2. Convert to PSI
    println("📋 Converting Agent to PSI...")
    val psi = SpicePsiBuilder.run { aiResearcher.toPsi() }
    
    // Pretty print PSI structure
    println("\nPSI Tree Structure:")
    println(psi)
    
    // 3. Convert PSI to Neo4j Graph
    println("\n📊 Converting PSI to Neo4j Graph...")
    val neo4jGraph = psi.toNeo4jGraph()
    
    // Display graph statistics
    println("\nGraph Statistics:")
    println("- Nodes: ${neo4jGraph.nodes.size}")
    println("- Relationships: ${neo4jGraph.relationships.size}")
    
    // 4. Show Neo4j nodes
    println("\n🔵 Neo4j Nodes:")
    neo4jGraph.nodes.forEach { node ->
        println("\nNode: ${node.id}")
        println("  Labels: ${node.labels.joinToString(", ")}")
        println("  Properties:")
        node.properties.forEach { (key, value) ->
            if (!key.startsWith("_")) {  // Skip internal props
                println("    $key: $value")
            }
        }
    }
    
    // 5. Show Neo4j relationships
    println("\n🔗 Neo4j Relationships:")
    neo4jGraph.relationships.forEach { rel ->
        println("${rel.fromId} -[${rel.type}]-> ${rel.toId}")
        if (rel.properties.isNotEmpty()) {
            println("  Properties: ${rel.properties}")
        }
    }
    
    // 6. Convert to mnemo format (JSON)
    println("\n📄 Mnemo-ready JSON format:")
    val mnemoJson = neo4jGraph.toMnemoFormat()
    val prettyJson = Json { prettyPrint = true }.encodeToString(
        JsonObject.serializer(), 
        mnemoJson
    )
    
    // Show first part of JSON
    println(prettyJson.take(500) + "...\n")
    
    // 7. Create a flow and convert it too
    val researchFlow = buildFlow {
        id = "ai-research-flow"
        name = "AI Research Workflow"
        description = "Complete AI research workflow"
        
        step("search", "ai-researcher")
        step("analyze", "ai-researcher")
        step("summarize", "ai-researcher")
    }
    
    println("📋 Converting Flow to Neo4j Graph...")
    val flowPsi = SpicePsiBuilder.run { researchFlow.toPsi() }
    val flowGraph = flowPsi.toNeo4jGraph()
    
    println("\nFlow Graph Statistics:")
    println("- Nodes: ${flowGraph.nodes.size}")
    println("- Relationships: ${flowGraph.relationships.size}")
    
    // Show flow relationships
    println("\nFlow Relationships:")
    flowGraph.relationships
        .filter { it.type.contains("STEP") || it.type.contains("AGENT") }
        .forEach { rel ->
            println("${rel.fromId} -[${rel.type}]-> ${rel.toId}")
        }
    
    // 8. Demonstrate the graph query potential
    println("\n🔍 Example Neo4j Queries (that mnemo can run):")
    println("""
    // Find all tools for an agent
    MATCH (a:Agent {id: 'ai-researcher'})-[:HAS_TOOL]->(t:Tool)
    RETURN t.name, t.description
    
    // Find agents that use vector stores
    MATCH (a:Agent)-[:USES_VECTORSTORE]->(vs:VectorStore)
    RETURN a.name, vs.provider, vs.collection
    
    // Find flow execution path
    MATCH (f:Flow {id: 'ai-research-flow'})-[:CONTAINS_STEP]->(s:FlowStep)
    MATCH (s)-[:CALLS_AGENT]->(a:Agent)
    RETURN s.name, a.id ORDER BY s.order
    """.trimIndent())
    
    println("\n--- Conversion Complete ---")
}