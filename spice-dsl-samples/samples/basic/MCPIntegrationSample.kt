package samples.basic

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.mcp.*
import kotlinx.coroutines.runBlocking

/**
 * 🔌 MCP Integration Sample
 * 
 * Demonstrates how Spice acts as an MCP client to send
 * PSI structures and patterns to mnemo (MCP server).
 */
fun main() = runBlocking {
    println("--- Spice MCP Client Demo ---\n")
    
    // 1. Initialize MCP adapter for mnemo
    val mnemoAdapter = MnemoMCPAdapter(
        serverUrl = "http://localhost:8080",  // mnemo MCP server
        apiKey = System.getenv("MNEMO_API_KEY")  // optional
    )
    
    // 2. Create agents
    val dataAnalyst = buildAgent {
        id = "data-analyst"
        name = "Data Analysis Agent"
        description = "Analyzes data and generates insights"
        
        tool("query-database") {
            description("Query database for data")
            parameters {
                string("query", "SQL query to execute")
                boolean("cached", "Use cached results", default = true)
            }
            execute { params ->
                val query = params["query"] as String
                println("Executing query: $query")
                success("Query executed: 42 rows returned")
            }
        }
        
        tool("generate-chart") {
            description("Generate visualization chart")
            parameters {
                string("type", "Chart type (bar, line, pie)")
                list("data", "Data points for the chart")
            }
            execute { params ->
                val type = params["type"] as String
                println("Generating $type chart...")
                success("Chart generated successfully")
            }
        }
        
        vectorStore("insights-db") {
            provider = "qdrant"
            collection = "data_insights"
        }
        
        handle { comm ->
            comm.reply("Analyzing data: ${comm.content}")
        }
    }
    
    val reportGenerator = buildAgent {
        id = "report-generator"
        name = "Report Generation Agent"
        description = "Generates reports based on analysis"
        
        tool("format-report") {
            description("Format analysis into report")
            parameters {
                string("format", "Output format (pdf, html, markdown)")
                map("sections", "Report sections")
            }
            execute { params ->
                val format = params["format"] as String
                println("Formatting report as $format")
                success("Report formatted")
            }
        }
        
        handle { comm ->
            comm.reply("Generating report for: ${comm.content}")
        }
    }
    
    // 3. Save agents PSI to mnemo
    println("📤 Saving agent structures to mnemo...")
    try {
        dataAnalyst.savePSIToMnemo(mnemoAdapter)
        println("✅ Saved data-analyst PSI")
        
        reportGenerator.savePSIToMnemo(mnemoAdapter)
        println("✅ Saved report-generator PSI")
    } catch (e: Exception) {
        println("❌ Error saving to mnemo: ${e.message}")
    }
    
    // 4. Create and execute a flow
    val analysisFlow = buildFlow {
        id = "data-analysis-flow"
        name = "Complete Data Analysis"
        description = "Analyze data and generate report"
        
        step("analyze", "data-analyst")
        step("report", "report-generator")
    }
    
    // 5. Simulate flow execution and save to mnemo
    println("\n🔄 Executing flow...")
    val input = Comm(
        content = "Analyze Q4 sales data", 
        from = "user"
    )
    
    // Simulate execution
    val startTime = System.currentTimeMillis()
    Thread.sleep(100)  // Simulate processing
    
    val output = Comm(
        content = "Analysis complete. Report generated with 5 key insights.",
        from = "data-analysis-flow"
    )
    val duration = System.currentTimeMillis() - startTime
    
    println("📤 Saving flow execution to mnemo...")
    try {
        mnemoAdapter.saveFlowExecution(
            flowId = analysisFlow.id,
            input = input,
            output = output,
            duration = duration
        )
        println("✅ Flow execution saved")
    } catch (e: Exception) {
        println("❌ Error saving flow: ${e.message}")
    }
    
    // 6. Check for vibe coding patterns
    println("\n🔍 Checking for vibe coding patterns...")
    val allAgents = listOf(dataAnalyst, reportGenerator)
    try {
        val issues = allAgents.checkVibeCoding(mnemoAdapter)
        if (issues.isEmpty()) {
            println("✅ No vibe coding issues detected")
        } else {
            println("⚠️  Vibe coding issues found:")
            issues.forEach { println("   - $it") }
        }
    } catch (e: Exception) {
        println("❌ Error checking patterns: ${e.message}")
    }
    
    // 7. Find similar agents
    println("\n🔎 Finding similar agents to data-analyst...")
    try {
        val similar = mnemoAdapter.findSimilarAgents(dataAnalyst, limit = 3)
        if (similar.isNotEmpty()) {
            println("Found ${similar.size} similar agents in mnemo")
            similar.forEach { agent ->
                println("   - ${agent["key"]}: ${agent["content"]?.toString()?.take(50)}...")
            }
        } else {
            println("No similar agents found")
        }
    } catch (e: Exception) {
        println("❌ Error searching: ${e.message}")
    }
    
    // 8. Get tool recommendations
    println("\n💡 Getting tool recommendations for 'ML model training agent'...")
    try {
        val recommendations = mnemoAdapter.getRecommendedTools("ML model training agent")
        if (recommendations.isNotEmpty()) {
            println("Recommended tools:")
            recommendations.forEach { println("   - $it") }
        } else {
            println("No recommendations available")
        }
    } catch (e: Exception) {
        println("❌ Error getting recommendations: ${e.message}")
    }
    
    // 9. Save context injection pattern
    println("\n💉 Saving successful context injection pattern...")
    try {
        mnemoAdapter.saveContextPattern(
            agentId = "data-analyst",
            context = mapOf(
                "database" to "analytics_db",
                "cache_enabled" to true,
                "max_rows" to 10000
            ),
            effectiveness = 0.92
        )
        println("✅ Context pattern saved")
    } catch (e: Exception) {
        println("❌ Error saving context: ${e.message}")
    }
    
    // Clean up
    mnemoAdapter.close()
    println("\n--- MCP Integration Complete ---")
}