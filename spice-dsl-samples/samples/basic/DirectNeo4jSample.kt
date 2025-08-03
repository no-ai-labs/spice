package samples.basic

import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.neo4j.*
import io.github.noailabs.spice.swarm.*
import kotlinx.serialization.json.*

/**
 * 📊 Direct DSL to Neo4j Conversion Sample
 * 
 * Shows the more efficient direct conversion from DSL to Neo4j
 * without the intermediate PSI step.
 */
fun main() {
    println("--- Direct DSL to Neo4j Conversion ---\n")
    
    // 1. Create a complex agent with tools and vector stores
    val mlEngineer = buildAgent {
        id = "ml-engineer"
        name = "ML Engineer Assistant"
        description = "Helps with machine learning tasks"
        
        tool("train-model") {
            description("Train a machine learning model")
            parameters {
                string("algorithm", "ML algorithm to use")
                string("dataset", "Dataset path")
                map("hyperparameters", "Model hyperparameters")
            }
            execute { params ->
                println("Training ${params["algorithm"]} on ${params["dataset"]}")
                success("Model trained with accuracy: 0.92")
            }
        }
        
        tool("evaluate-model") {
            description("Evaluate model performance")
            parameters {
                string("model_path", "Path to the model")
                string("test_data", "Test dataset path")
            }
            execute { params ->
                success("Model evaluation complete")
            }
        }
        
        vectorStore("ml-papers") {
            provider = "pinecone"
            index = "machine-learning-papers"
            dimension = 1536
        }
        
        vectorStore("model-results") {
            provider = "qdrant"
            collection = "experiment-results"
            host = "localhost"
            port = 6333
        }
        
        handle { comm ->
            comm.reply("Processing ML task: ${comm.content}")
        }
    }
    
    // 2. Direct conversion to Neo4j (no PSI!)
    println("🚀 Direct DSL → Neo4j conversion:")
    val mlGraph = mlEngineer.toNeo4jGraph()
    
    println("\nGraph Statistics:")
    println("- Nodes: ${mlGraph.nodes.size}")
    println("- Relationships: ${mlGraph.relationships.size}")
    
    // Show nodes
    println("\n📍 Nodes:")
    mlGraph.nodes.forEach { node ->
        println("${node.id} [${node.labels.joinToString(", ")}]")
    }
    
    // Show relationships
    println("\n🔗 Relationships:")
    mlGraph.relationships.forEach { rel ->
        println("${rel.fromId} -[${rel.type}]-> ${rel.toId}")
    }
    
    // 3. Create a flow
    val mlPipeline = buildFlow {
        id = "ml-pipeline"
        name = "ML Training Pipeline"
        description = "Complete ML workflow"
        
        step("prepare-data", "ml-engineer")
        step("train", "ml-engineer")
        step("evaluate", "ml-engineer")
        step("deploy", "ml-engineer")
    }
    
    println("\n\n--- Flow to Neo4j Conversion ---")
    val flowGraph = mlPipeline.toNeo4jGraph()
    
    println("\nFlow Graph:")
    println("- Nodes: ${flowGraph.nodes.size}")
    println("- Relationships: ${flowGraph.relationships.size}")
    
    // Show flow relationships
    println("\n🔄 Flow Structure:")
    flowGraph.relationships
        .filter { it.type in listOf("CONTAINS_STEP", "NEXT_STEP", "CALLS_AGENT") }
        .sortedBy { 
            (it.properties["order"] as? Int) ?: 
            (it.properties["sequence"] as? Int) ?: 
            999 
        }
        .forEach { rel ->
            val props = if (rel.properties.isNotEmpty()) " ${rel.properties}" else ""
            println("${rel.fromId} -[${rel.type}$props]-> ${rel.toId}")
        }
    
    // 4. Create a swarm
    val dataScientist = buildAgent {
        id = "data-scientist"
        name = "Data Scientist"
        description = "Analyzes data and provides insights"
        
        tool("analyze-data") {
            description("Perform statistical analysis")
            parameters {
                string("dataset", "Dataset to analyze")
            }
            execute { success("Analysis complete") }
        }
        
        handle { it.reply("Analyzing...") }
    }
    
    val mlSwarm = SwarmAgent(
        id = "ml-swarm",
        name = "ML Research Swarm",
        description = "Collaborative ML research team",
        members = listOf(mlEngineer.build(), dataScientist.build()),
        capabilities = emptySet()
    )
    
    println("\n\n--- Swarm to Neo4j Conversion ---")
    val swarmGraph = mlSwarm.toNeo4jGraph()
    
    println("\nSwarm Graph:")
    println("- Nodes: ${swarmGraph.nodes.size}")
    println("- Relationships: ${swarmGraph.relationships.size}")
    
    // 5. Build complete application graph
    println("\n\n--- Complete Application Graph ---")
    val appGraph = SpiceToNeo4j.buildApplicationGraph(
        agents = listOf(mlEngineer.build(), dataScientist.build()),
        flows = emptyList(), // Note: would need flow instances
        swarms = listOf(mlSwarm)
    )
    
    println("\nApplication Graph:")
    println("- Total Nodes: ${appGraph.nodes.size}")
    println("- Total Relationships: ${appGraph.relationships.size}")
    
    // 6. Convert to mnemo format
    println("\n\n--- Mnemo-ready JSON ---")
    val mnemoJson = SpiceToNeo4j.run { appGraph.toMnemoFormat() }
    val prettyJson = Json { prettyPrint = true }.encodeToString(
        JsonObject.serializer(),
        mnemoJson
    )
    
    println("First 500 chars of JSON:")
    println(prettyJson.take(500) + "...")
    
    // 7. Show efficiency comparison
    println("\n\n--- Efficiency Comparison ---")
    println("Previous approach: DSL → PSI → Neo4j (2 conversions)")
    println("New approach: DSL → Neo4j (1 conversion)")
    println("Benefits:")
    println("- 🚀 Faster conversion")
    println("- 💾 Less memory usage") 
    println("- 🎯 More direct mapping")
    println("- 🔍 Easier to debug")
    
    // 8. Example Neo4j queries
    println("\n\n--- Example Neo4j Queries ---")
    println("""
    // Find all tools for ML Engineer
    MATCH (a:Agent {id: 'ml-engineer'})-[:HAS_TOOL]->(t:Tool)
    RETURN t.name, t.description
    
    // Find flow execution path
    MATCH p=(f:Flow {id: 'ml-pipeline'})-[:CONTAINS_STEP|NEXT_STEP*]->(s:FlowStep)
    RETURN p ORDER BY s.order
    
    // Find swarm members and their tools
    MATCH (sw:SwarmAgent)-[:HAS_MEMBER]->(a:Agent)-[:HAS_TOOL]->(t:Tool)
    RETURN sw.name, a.name, collect(t.name) as tools
    
    // Find all vector stores in the system
    MATCH (vs:VectorStore)
    RETURN vs.name, vs.provider, vs.collection
    """.trimIndent())
    
    println("\n--- Direct Conversion Complete! ---")
}