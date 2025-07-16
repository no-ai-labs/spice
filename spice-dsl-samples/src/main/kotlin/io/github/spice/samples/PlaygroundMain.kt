package io.github.spice.samples

import kotlinx.coroutines.runBlocking

/**
 * 🌶️ Spice DSL Playground
 * 
 * 간소화된 플레이그라운드 메인 클래스. 
 * 복잡한 시나리오 실행 로직은 ScenarioRunner로 분리되었습니다.
 * 
 * Usage:
 * ./gradlew :spice-dsl-samples:run --args="basic"
 * ./gradlew :spice-dsl-samples:run --args="all"
 * ./gradlew :spice-dsl-samples:run --args="list"
 * ./gradlew :spice-dsl-samples:run --args="scenario basic-agents"
 * ./gradlew :spice-dsl-samples:run --args="benchmark"
 */
fun main(args: Array<String>) = runBlocking {
    val scenarioRunner = ScenarioRunner()
    val batchRunner = BatchRunner(scenarioRunner)
    
    println("🌶️ Welcome to Spice DSL Playground!")
    println("=" .repeat(50))
    
    when {
        args.isEmpty() || args[0] == "help" -> {
            printUsageHelp()
        }
        
        args[0] == "list" -> {
            scenarioRunner.listScenarios()
        }
        
        args[0] == "all" -> {
            val results = scenarioRunner.runAllScenarios()
            printExecutionSummary(results)
        }
        
        args[0] == "benchmark" -> {
            val benchmark = batchRunner.runBenchmark()
            benchmark.printSummary()
        }
        
        args[0] == "scenario" && args.size > 1 -> {
            val scenarioName = args[1]
            val result = scenarioRunner.runScenario(scenarioName)
            println("\n${if (result.success) "✅" else "❌"} Result: ${result.scenarioName}")
            if (result.errorMessage != null) {
                println("Error: ${result.errorMessage}")
            }
        }
        
        args[0] in listOf("basic", "advanced", "templates", "complete") -> {
            val results = batchRunner.runCategory(args[0])
            printCategorySummary(args[0], results)
        }
        
        // 백워드 호환성을 위한 기존 카테고리 지원
        args[0] == "experimental" -> {
            println("🧪 Running experimental features...")
            val result = scenarioRunner.runScenario("experimental-features")
            println("${if (result.success) "✅" else "❌"} Experimental demo completed")
        }
        
        args[0] == "flow" -> {
            println("🔄 Running flow examples...")
            val result = scenarioRunner.runScenario("flow-orchestration")
            println("${if (result.success) "✅" else "❌"} Flow demo completed")
        }
        
        else -> {
            println("❌ Unknown command: ${args[0]}")
            printUsageHelp()
        }
    }
    
    println("\n🎯 For more details, visit: https://github.com/spice-framework/spice-dsl-samples")
    println("📚 Documentation: Run with 'help' for usage information")
}

/**
 * 사용법 도움말 출력
 */
private fun printUsageHelp() {
    println("""
    |🎮 Spice DSL Playground Commands:
    |
    |📋 Information:
    |  help              Show this help message
    |  list              List all available scenarios
    |
    |🚀 Quick Start:
    |  basic             Run basic DSL examples (agents, tools)
    |  advanced          Run advanced features (flows, experimental)
    |  templates         Run template and scaffolding examples
    |  complete          Run complete workflows and debug features
    |
    |🎯 Specific Execution:
    |  all               Run all scenarios sequentially
    |  scenario <name>   Run a specific scenario by name
    |
    |⏱️ Performance:
    |  benchmark         Run performance benchmark (3 iterations)
    |
    |🔧 Legacy Support:
    |  experimental      Run experimental features demo
    |  flow              Run flow orchestration demo
    |
    |💡 Examples:
    |  ./gradlew :spice-dsl-samples:run --args="basic"
    |  ./gradlew :spice-dsl-samples:run --args="scenario debug-mode"
    |  ./gradlew :spice-dsl-samples:run --args="benchmark"
    |
    |📊 Categories:
    |  • basic: Core DSL features (agents, tools)
    |  • advanced: Complex features (flows, experimental)
    |  • templates: Scaffolding and templates
    |  • complete: End-to-end workflows
    """.trimMargin())
}

/**
 * 실행 결과 요약 출력
 */
private fun printExecutionSummary(results: List<ScenarioResult>) {
    println("\n" + "=".repeat(50))
    println("📊 Execution Summary")
    println("=".repeat(50))
    
    val successful = results.count { it.success }
    val failed = results.size - successful
    val totalTime = results.sumOf { it.durationMs }
    val avgTime = if (results.isNotEmpty()) totalTime / results.size else 0
    
    println("Results:")
    println("  ✅ Successful: $successful")
    println("  ❌ Failed: $failed")
    println("  📈 Success Rate: ${"%.1f".format(successful.toDouble() / results.size * 100)}%")
    println()
    println("Performance:")
    println("  ⏱️ Total Time: ${totalTime}ms")
    println("  📊 Average Time: ${avgTime}ms")
    println("  🚀 Fastest: ${results.minByOrNull { it.durationMs }?.durationMs ?: 0}ms")
    println("  🐌 Slowest: ${results.maxByOrNull { it.durationMs }?.durationMs ?: 0}ms")
    
    if (failed > 0) {
        println("\n❌ Failed Scenarios:")
        results.filter { !it.success }.forEach { result ->
            println("  • ${result.scenarioName}: ${result.errorMessage}")
        }
    }
    
    println("\n🎯 All scenarios completed!")
}

/**
 * 카테고리별 실행 결과 요약
 */
private fun printCategorySummary(category: String, results: List<ScenarioResult>) {
    println("\n" + "=".repeat(50))
    println("📊 Category '$category' Summary")
    println("=".repeat(50))
    
    val successful = results.count { it.success }
    val totalTime = results.sumOf { it.durationMs }
    
    println("Category Results:")
    println("  🎯 Category: ${category.uppercase()}")
    println("  ✅ Successful: $successful/${results.size}")
    println("  ⏱️ Total Time: ${totalTime}ms")
    
    results.forEach { result ->
        val status = if (result.success) "✅" else "❌"
        val time = "${result.durationMs}ms"
        println("  $status ${result.scenarioName.padEnd(20)} ($time)")
    }
    
    val successRate = successful.toDouble() / results.size * 100
    when {
        successRate == 100.0 -> println("\n🎉 Perfect! All scenarios in '$category' passed!")
        successRate >= 80.0 -> println("\n👍 Great! Most scenarios in '$category' passed!")
        else -> println("\n⚠️ Some scenarios in '$category' need attention.")
    }
}

/**
 * 개발자 정보 출력
 */
private fun printDeveloperInfo() {
    println("""
    |🔧 Developer Information:
    |
    |Project Structure:
    |  spice-core/               Core DSL implementation
    |  spice-dsl-samples/        This playground project
    |  └── samples/
    |      ├── basic/            Basic DSL examples
    |      ├── flow/             Flow orchestration examples
    |      ├── experimental/     Advanced features
    |      └── templates/        Template usage examples
    |
    |Key Components:
    |  • ScenarioRunner         Manages and executes test scenarios
    |  • BatchRunner            Handles category and benchmark execution
    |  • DSLTemplates           Provides scaffolding templates
    |  • DSLSummary             Auto-generates documentation
    |
    |Framework Architecture:
    |  Agent > Flow > Tool       Clean 3-tier hierarchy
    |  Registry System          Centralized component management
    |  Template System          Rapid prototyping support
    |  Debug Mode               Development assistance
    |
    |Next Steps:
    |  1. Explore basic examples: run --args="basic"
    |  2. Try template system: run --args="templates"
    |  3. Test complete workflows: run --args="complete"
    |  4. Run performance tests: run --args="benchmark"
    """.trimMargin())
} 