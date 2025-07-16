package io.github.spice.samples

import io.github.spice.Message
import io.github.spice.ToolResult
import io.github.spice.dsl.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * 🎭 Scenario Runner
 * 
 * DSL 샘플들을 체계적으로 실행하고 관리하는 시나리오 실행기.
 * PlaygroundMain.kt에서 분리되어 더 나은 구조화와 재사용성을 제공합니다.
 */
class ScenarioRunner {
    
    private val scenarios = mutableMapOf<String, Scenario>()
    private var currentScenario: String? = null
    
    init {
        registerDefaultScenarios()
    }
    
    /**
     * 시나리오 실행
     */
    suspend fun runScenario(scenarioName: String): ScenarioResult {
        val scenario = scenarios[scenarioName.lowercase()]
            ?: return ScenarioResult.error("Scenario not found: $scenarioName")
        
        currentScenario = scenarioName
        println("🎬 Running scenario: ${scenario.name}")
        println("📝 Description: ${scenario.description}")
        println("⏱️ Estimated time: ${scenario.estimatedTimeMs}ms")
        println("${"─".repeat(50)}")
        
        val startTime = System.currentTimeMillis()
        
        return try {
            scenario.executor()
            val duration = System.currentTimeMillis() - startTime
            println("${"─".repeat(50)}")
            println("✅ Scenario '${scenario.name}' completed in ${duration}ms")
            ScenarioResult.success(scenario.name, duration)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("${"─".repeat(50)}")
            println("❌ Scenario '${scenario.name}' failed after ${duration}ms: ${e.message}")
            ScenarioResult.error("Execution failed: ${e.message}", duration)
        }
    }
    
    /**
     * 모든 시나리오 실행
     */
    suspend fun runAllScenarios(): List<ScenarioResult> {
        println("🚀 Running all scenarios...")
        val results = mutableListOf<ScenarioResult>()
        
        scenarios.keys.forEach { scenarioName ->
            val result = runScenario(scenarioName)
            results.add(result)
            delay(500) // 시나리오 간 간격
        }
        
        println("\n📊 Summary:")
        val successful = results.count { it.success }
        val failed = results.size - successful
        val totalTime = results.sumOf { it.durationMs }
        
        println("  ✅ Successful: $successful")
        println("  ❌ Failed: $failed")
        println("  ⏱️ Total time: ${totalTime}ms")
        
        return results
    }
    
    /**
     * 시나리오 목록 출력
     */
    fun listScenarios() {
        println("📋 Available Scenarios:")
        scenarios.values.sortedBy { it.name }.forEach { scenario ->
            val status = if (scenario.name.lowercase() == currentScenario?.lowercase()) "▶️ " else "  "
            println("$status• ${scenario.name}")
            println("    ${scenario.description}")
            println("    Estimated: ${scenario.estimatedTimeMs}ms")
            println()
        }
    }
    
    /**
     * 커스텀 시나리오 등록
     */
    fun registerScenario(scenario: Scenario) {
        scenarios[scenario.name.lowercase()] = scenario
        println("✅ Registered scenario: ${scenario.name}")
    }
    
    /**
     * 기본 시나리오들 등록
     */
    private fun registerDefaultScenarios() {
        // Basic DSL 시나리오
        registerScenario(Scenario(
            name = "basic-agents",
            description = "Core DSL Agent creation and basic functionality",
            estimatedTimeMs = 2000
        ) {
            runBasicAgentsDemo()
        })
        
        registerScenario(Scenario(
            name = "basic-tools",
            description = "Core DSL Tool creation and execution",
            estimatedTimeMs = 1500
        ) {
            runBasicToolsDemo()
        })
        
        registerScenario(Scenario(
            name = "flow-orchestration",
            description = "Flow creation and agent orchestration",
            estimatedTimeMs = 3000
        ) {
            runFlowOrchestrationDemo()
        })
        
        registerScenario(Scenario(
            name = "experimental-features",
            description = "Advanced experimental DSL capabilities",
            estimatedTimeMs = 2500
        ) {
            runExperimentalFeaturesDemo()
        })
        
        registerScenario(Scenario(
            name = "template-usage",
            description = "Template functions and quick scaffolding",
            estimatedTimeMs = 2000
        ) {
            runTemplateUsageDemo()
        })
        
        registerScenario(Scenario(
            name = "complete-workflow",
            description = "End-to-end customer service workflow",
            estimatedTimeMs = 4000
        ) {
            runCompleteWorkflowDemo()
        })
        
        registerScenario(Scenario(
            name = "debug-mode",
            description = "Debug mode features and logging",
            estimatedTimeMs = 1000
        ) {
            runDebugModeDemo()
        })
        
        registerScenario(Scenario(
            name = "sample-loading",
            description = "DSL sample loading and management",
            estimatedTimeMs = 1500
        ) {
            runSampleLoadingDemo()
        })
    }
    
    // =====================================
    // SCENARIO IMPLEMENTATIONS
    // =====================================
    
    private suspend fun runBasicAgentsDemo() {
        println("🤖 Creating basic agents...")
        
        val agent = defaultAgent("Demo Agent", alias = "demo-basic-agent")
        AgentRegistry.register(agent)
        
        val message = Message(content = "Hello from scenario runner!", sender = "runner")
        val response = agent.processMessage(message)
        
        println("✅ Agent created and tested")
        println("   Response: ${response.content}")
    }
    
    private suspend fun runBasicToolsDemo() {
        println("🔧 Creating basic tools...")
        
        val calculator = calculatorTool(alias = "demo-calculator")
        ToolRegistry.register(calculator)
        
        val result = calculator.execute(mapOf(
            "operation" to "multiply",
            "a" to 7,
            "b" to 6
        ))
        
        println("✅ Tool created and tested")
        println("   Result: ${result.result}")
    }
    
    private suspend fun runFlowOrchestrationDemo() {
        println("🔄 Setting up flow orchestration...")
        
        // 간단한 에이전트들 생성
        val processor = defaultAgent("Processor", alias = "flow-processor")
        val analyzer = defaultAgent("Analyzer", alias = "flow-analyzer")
        
        AgentRegistry.register(processor)
        AgentRegistry.register(analyzer)
        
        // 플로우 생성
        val flow = pipelineFlow("Demo Pipeline", listOf("flow-processor", "flow-analyzer"))
        
        val message = Message(content = "Flow test data", sender = "runner")
        val result = flow.execute(message)
        
        println("✅ Flow orchestration tested")
        println("   Final result: ${result.content}")
    }
    
    private suspend fun runExperimentalFeaturesDemo() {
        println("🧪 Testing experimental features...")
        
        // Mock experimental feature
        println("   • Conditional routing: ✅")
        println("   • Reactive processing: ✅ (simulated)")
        println("   • Type-safe messaging: ✅ (conceptual)")
        
        println("✅ Experimental features demonstrated")
    }
    
    private suspend fun runTemplateUsageDemo() {
        println("📋 Demonstrating template usage...")
        
        val echo = loadSample("echo") {
            agentId = "demo-echo"
            registerComponents = true
        }
        
        echo.printSummary()
        
        println("✅ Template loading demonstrated")
    }
    
    private suspend fun runCompleteWorkflowDemo() {
        println("🎯 Running complete workflow...")
        
        val customerService = createCustomerServiceTemplate()
        customerService.registerAll()
        
        val customerMessage = Message(
            content = "I'm frustrated with the service!",
            sender = "customer",
            metadata = mapOf("customer" to "demo@email.com")
        )
        
        val result = customerService.flow.execute(customerMessage)
        
        println("✅ Complete workflow executed")
        println("   Service response: ${result.content.take(100)}...")
        println("   Sentiment: ${result.metadata["sentiment"]}")
    }
    
    private suspend fun runDebugModeDemo() {
        println("🐛 Testing debug mode...")
        
        val debugAgent = buildAgent {
            id = "debug-demo-agent"
            name = "Debug Demo Agent"
            description = "Agent for debug demonstration"
            
            debugMode(enabled = true, prefix = "[DEMO-DEBUG]")
            
            handle { message ->
                delay(50) // 시뮬레이트 처리 시간
                Message(
                    content = "Debug demo: ${message.content}",
                    sender = id,
                    receiver = message.sender
                )
            }
        }
        
        val message = Message(content = "Debug test", sender = "runner")
        val response = debugAgent.processMessage(message)
        
        println("✅ Debug mode demonstrated")
    }
    
    private suspend fun runSampleLoadingDemo() {
        println("📦 Testing sample loading...")
        
        printAvailableSamples()
        
        val chatbot = loadSample("chatbot") {
            agentId = "demo-chatbot"
            registerComponents = false
        }
        
        chatbot.printSummary()
        
        println("✅ Sample loading demonstrated")
    }
}

/**
 * 시나리오 정의
 */
data class Scenario(
    val name: String,
    val description: String,
    val estimatedTimeMs: Long,
    val executor: suspend () -> Unit
)

/**
 * 시나리오 실행 결과
 */
data class ScenarioResult(
    val scenarioName: String,
    val success: Boolean,
    val durationMs: Long,
    val errorMessage: String? = null
) {
    companion object {
        fun success(scenarioName: String, durationMs: Long): ScenarioResult {
            return ScenarioResult(scenarioName, true, durationMs)
        }
        
        fun error(errorMessage: String, durationMs: Long = 0): ScenarioResult {
            return ScenarioResult("unknown", false, durationMs, errorMessage)
        }
    }
}

/**
 * 배치 실행 유틸리티
 */
class BatchRunner(private val scenarioRunner: ScenarioRunner) {
    
    /**
     * 특정 카테고리의 시나리오들 실행
     */
    suspend fun runCategory(category: String): List<ScenarioResult> {
        val categoryScenarios = when (category.lowercase()) {
            "basic" -> listOf("basic-agents", "basic-tools")
            "advanced" -> listOf("flow-orchestration", "experimental-features")
            "templates" -> listOf("template-usage", "sample-loading")
            "complete" -> listOf("complete-workflow", "debug-mode")
            else -> throw IllegalArgumentException("Unknown category: $category")
        }
        
        println("🎯 Running $category scenarios...")
        val results = mutableListOf<ScenarioResult>()
        
        categoryScenarios.forEach { scenarioName ->
            val result = scenarioRunner.runScenario(scenarioName)
            results.add(result)
            delay(300)
        }
        
        return results
    }
    
    /**
     * 성능 벤치마크 실행
     */
    suspend fun runBenchmark(): BenchmarkResult {
        println("⏱️ Running performance benchmark...")
        
        val results = mutableListOf<ScenarioResult>()
        val iterations = 3
        
        repeat(iterations) { iteration ->
            println("🔄 Benchmark iteration ${iteration + 1}/$iterations")
            val iterationResults = scenarioRunner.runAllScenarios()
            results.addAll(iterationResults)
            delay(1000)
        }
        
        val avgDuration = results.map { it.durationMs }.average()
        val successRate = results.count { it.success }.toDouble() / results.size * 100
        
        return BenchmarkResult(
            iterations = iterations,
            totalTests = results.size,
            averageDurationMs = avgDuration,
            successRate = successRate,
            results = results
        )
    }
}

/**
 * 벤치마크 결과
 */
data class BenchmarkResult(
    val iterations: Int,
    val totalTests: Int,
    val averageDurationMs: Double,
    val successRate: Double,
    val results: List<ScenarioResult>
) {
    fun printSummary() {
        println("📊 Benchmark Results:")
        println("  • Iterations: $iterations")
        println("  • Total tests: $totalTests")
        println("  • Average duration: ${"%.1f".format(averageDurationMs)}ms")
        println("  • Success rate: ${"%.1f".format(successRate)}%")
    }
} 