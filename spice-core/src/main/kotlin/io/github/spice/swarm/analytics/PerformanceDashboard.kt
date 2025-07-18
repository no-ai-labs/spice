package io.github.spice.swarm.analytics

import io.github.spice.*
import io.github.spice.swarm.*
import io.github.spice.swarm.scoring.*
import io.github.spice.swarm.optimization.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * 📊 Real-time Performance Analytics Dashboard
 * 
 * Revolutionary performance monitoring and visualization system that provides:
 * - Live agent performance metrics
 * - Strategy effectiveness tracking
 * - System health monitoring
 * - Predictive insights
 * - Interactive performance reports
 */

// =====================================
// PERFORMANCE DASHBOARD
// =====================================

/**
 * 📈 Real-time Performance Dashboard
 */
class PerformanceDashboard(
    private val performanceTracker: AgentPerformanceTracker,
    private val strategyOptimizer: DynamicStrategyOptimizer,
    private val config: DashboardConfig = DashboardConfig()
) {
    
    private val metricsCollector = MetricsCollector()
    private val visualizer = PerformanceVisualizer()
    
    /**
     * 🎯 Generate comprehensive performance report
     */
    suspend fun generatePerformanceReport(
        swarmId: String,
        timeRange: TimeRange = TimeRange.LAST_HOUR
    ): PerformanceReport {
        
        if (config.debugEnabled) {
            println("[DASHBOARD] Generating performance report for $swarmId")
        }
        
        // Collect real-time metrics
        val realtimeMetrics = metricsCollector.collectRealtimeMetrics(swarmId)
        
        // Analyze agent performance
        val agentAnalytics = analyzeAgentPerformance(swarmId, timeRange)
        
        // Analyze strategy effectiveness
        val strategyAnalytics = analyzeStrategyEffectiveness(swarmId, timeRange)
        
        // Generate system health status
        val systemHealth = generateSystemHealthStatus(realtimeMetrics, agentAnalytics)
        
        // Create performance insights
        val insights = generatePerformanceInsights(agentAnalytics, strategyAnalytics, systemHealth)
        
        // Generate visualizations
        val visualizations = generateVisualizations(agentAnalytics, strategyAnalytics, realtimeMetrics)
        
        return PerformanceReport(
            swarmId = swarmId,
            timeRange = timeRange,
            realtimeMetrics = realtimeMetrics,
            agentAnalytics = agentAnalytics,
            strategyAnalytics = strategyAnalytics,
            systemHealth = systemHealth,
            insights = insights,
            visualizations = visualizations,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 📊 Generate live dashboard display
     */
    fun generateLiveDashboard(swarmId: String): String {
        val report = runBlocking { generatePerformanceReport(swarmId) }
        
        return visualizer.renderDashboard(report)
    }
    
    /**
     * 🎯 Analyze agent performance metrics
     */
    private fun analyzeAgentPerformance(
        swarmId: String,
        timeRange: TimeRange
    ): AgentPerformanceAnalytics {
        
        // Mock data for demonstration - in real implementation, fetch from tracker
        val agentMetrics = listOf(
            AgentMetrics(
                agentId = "researcher",
                tasksCompleted = 24,
                successRate = 0.91,
                averageResponseTime = 850.0,
                averageScore = 0.87,
                trend = TrendDirection.IMPROVING,
                currentLoad = 0.65,
                specializations = listOf("analysis", "research")
            ),
            AgentMetrics(
                agentId = "analyst",
                tasksCompleted = 18,
                successRate = 0.89,
                averageResponseTime = 950.0,
                averageScore = 0.84,
                trend = TrendDirection.STABLE,
                currentLoad = 0.72,
                specializations = listOf("data-analysis", "statistics")
            ),
            AgentMetrics(
                agentId = "strategist",
                tasksCompleted = 15,
                successRate = 0.93,
                averageResponseTime = 1200.0,
                averageScore = 0.91,
                trend = TrendDirection.IMPROVING,
                currentLoad = 0.58,
                specializations = listOf("strategy", "planning")
            )
        )
        
        // Calculate aggregate metrics
        val totalTasks = agentMetrics.sumOf { it.tasksCompleted }
        val averageSuccessRate = agentMetrics.map { it.successRate }.average()
        val averageResponseTime = agentMetrics.map { it.averageResponseTime }.average()
        val systemLoad = agentMetrics.map { it.currentLoad }.average()
        
        // Identify top performers
        val topPerformers = agentMetrics.sortedByDescending { it.averageScore }.take(3)
        val improvingAgents = agentMetrics.filter { it.trend == TrendDirection.IMPROVING }
        val overloadedAgents = agentMetrics.filter { it.currentLoad > 0.8 }
        
        return AgentPerformanceAnalytics(
            agentMetrics = agentMetrics,
            totalTasks = totalTasks,
            averageSuccessRate = averageSuccessRate,
            averageResponseTime = averageResponseTime,
            systemLoad = systemLoad,
            topPerformers = topPerformers,
            improvingAgents = improvingAgents,
            overloadedAgents = overloadedAgents
        )
    }
    
    /**
     * 📈 Analyze strategy effectiveness
     */
    private fun analyzeStrategyEffectiveness(
        swarmId: String,
        timeRange: TimeRange
    ): StrategyAnalytics {
        
        // Mock strategy execution data
        val strategyResults = mapOf(
            SwarmStrategyType.PARALLEL to StrategyMetrics(
                executionCount = 12,
                successRate = 0.92,
                averageExecutionTime = 2300.0,
                averageQualityScore = 0.85,
                averageAgentUtilization = 0.78
            ),
            SwarmStrategyType.CONSENSUS to StrategyMetrics(
                executionCount = 8,
                successRate = 0.88,
                averageExecutionTime = 4200.0,
                averageQualityScore = 0.91,
                averageAgentUtilization = 0.82
            ),
            SwarmStrategyType.SEQUENTIAL to StrategyMetrics(
                executionCount = 6,
                successRate = 0.83,
                averageExecutionTime = 3800.0,
                averageQualityScore = 0.80,
                averageAgentUtilization = 0.65
            )
        )
        
        // Calculate effectiveness scores
        val effectivenessScores = strategyResults.mapValues { (strategy, metrics) ->
            calculateEffectivenessScore(metrics)
        }
        
        // Find best performing strategy
        val mostEffectiveStrategy = effectivenessScores.maxByOrNull { it.value }?.key
        val leastEffectiveStrategy = effectivenessScores.minByOrNull { it.value }?.key
        
        return StrategyAnalytics(
            strategyResults = strategyResults,
            effectivenessScores = effectivenessScores,
            mostEffectiveStrategy = mostEffectiveStrategy,
            leastEffectiveStrategy = leastEffectiveStrategy,
            totalExecutions = strategyResults.values.sumOf { it.executionCount }
        )
    }
    
    /**
     * 🏥 Generate system health status
     */
    private fun generateSystemHealthStatus(
        realtimeMetrics: RealtimeMetrics,
        agentAnalytics: AgentPerformanceAnalytics
    ): SystemHealth {
        
        val healthScore = calculateSystemHealthScore(realtimeMetrics, agentAnalytics)
        
        val status = when {
            healthScore >= 0.9 -> HealthStatus.EXCELLENT
            healthScore >= 0.8 -> HealthStatus.GOOD
            healthScore >= 0.7 -> HealthStatus.FAIR
            healthScore >= 0.6 -> HealthStatus.POOR
            else -> HealthStatus.CRITICAL
        }
        
        val healthFactors = mutableListOf<HealthFactor>()
        
        // Analyze different health aspects
        if (agentAnalytics.averageSuccessRate < 0.8) {
            healthFactors.add(HealthFactor("Low Success Rate", HealthImpact.HIGH, "Agent success rate below threshold"))
        }
        
        if (agentAnalytics.systemLoad > 0.8) {
            healthFactors.add(HealthFactor("High System Load", HealthImpact.MEDIUM, "System load exceeds 80%"))
        }
        
        if (agentAnalytics.overloadedAgents.isNotEmpty()) {
            healthFactors.add(HealthFactor("Overloaded Agents", HealthImpact.MEDIUM, "${agentAnalytics.overloadedAgents.size} agents overloaded"))
        }
        
        if (realtimeMetrics.activeOperations > 20) {
            healthFactors.add(HealthFactor("High Operation Count", HealthImpact.LOW, "Many concurrent operations"))
        }
        
        return SystemHealth(
            overallScore = healthScore,
            status = status,
            healthFactors = healthFactors,
            recommendations = generateHealthRecommendations(healthFactors),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 💡 Generate performance insights
     */
    private fun generatePerformanceInsights(
        agentAnalytics: AgentPerformanceAnalytics,
        strategyAnalytics: StrategyAnalytics,
        systemHealth: SystemHealth
    ): List<PerformanceInsight> {
        
        val insights = mutableListOf<PerformanceInsight>()
        
        // Agent performance insights
        if (agentAnalytics.improvingAgents.size >= 2) {
            insights.add(PerformanceInsight(
                type = InsightType.POSITIVE_TREND,
                title = "Agent Performance Improving",
                description = "${agentAnalytics.improvingAgents.size} agents showing improvement trends",
                impact = InsightImpact.MEDIUM,
                actionable = true,
                recommendation = "Continue current optimization strategies"
            ))
        }
        
        // Strategy insights
        strategyAnalytics.mostEffectiveStrategy?.let { strategy ->
            insights.add(PerformanceInsight(
                type = InsightType.OPTIMIZATION_OPPORTUNITY,
                title = "Optimal Strategy Identified",
                description = "$strategy strategy showing highest effectiveness",
                impact = InsightImpact.HIGH,
                actionable = true,
                recommendation = "Increase usage of $strategy strategy for similar tasks"
            ))
        }
        
        // Load balancing insights
        if (agentAnalytics.systemLoad > 0.8) {
            insights.add(PerformanceInsight(
                type = InsightType.PERFORMANCE_ISSUE,
                title = "High System Load Detected",
                description = "System load at ${String.format("%.1f", agentAnalytics.systemLoad * 100)}%",
                impact = InsightImpact.HIGH,
                actionable = true,
                recommendation = "Consider load balancing or scaling up agent capacity"
            ))
        }
        
        // Quality insights
        val avgQuality = strategyAnalytics.strategyResults.values.map { it.averageQualityScore }.average()
        if (avgQuality > 0.9) {
            insights.add(PerformanceInsight(
                type = InsightType.QUALITY_EXCELLENCE,
                title = "Exceptional Quality Metrics",
                description = "Average quality score: ${String.format("%.2f", avgQuality)}",
                impact = InsightImpact.MEDIUM,
                actionable = false,
                recommendation = "Maintain current quality standards"
            ))
        }
        
        return insights
    }
    
    /**
     * 🎨 Generate visualizations
     */
    private fun generateVisualizations(
        agentAnalytics: AgentPerformanceAnalytics,
        strategyAnalytics: StrategyAnalytics,
        realtimeMetrics: RealtimeMetrics
    ): DashboardVisualizations {
        
        return DashboardVisualizations(
            agentPerformanceChart = visualizer.createAgentPerformanceChart(agentAnalytics),
            strategyEffectivenessChart = visualizer.createStrategyChart(strategyAnalytics),
            systemLoadGraph = visualizer.createSystemLoadGraph(realtimeMetrics),
            successRateTrend = visualizer.createSuccessRateTrend(agentAnalytics),
            responseTimeDistribution = visualizer.createResponseTimeDistribution(agentAnalytics)
        )
    }
    
    // === HELPER METHODS ===
    
    private fun calculateEffectivenessScore(metrics: StrategyMetrics): Double {
        return (metrics.successRate * 0.4 +
                metrics.averageQualityScore * 0.3 +
                (1.0 - metrics.averageExecutionTime / 5000.0) * 0.2 +
                metrics.averageAgentUtilization * 0.1).coerceIn(0.0, 1.0)
    }
    
    private fun calculateSystemHealthScore(
        realtimeMetrics: RealtimeMetrics,
        agentAnalytics: AgentPerformanceAnalytics
    ): Double {
        var score = 1.0
        
        // Deduct for low success rate
        score -= max(0.0, (0.9 - agentAnalytics.averageSuccessRate) * 2.0)
        
        // Deduct for high system load
        score -= max(0.0, (agentAnalytics.systemLoad - 0.8) * 1.5)
        
        // Deduct for overloaded agents
        score -= agentAnalytics.overloadedAgents.size * 0.1
        
        // Deduct for failed operations
        if (realtimeMetrics.failedOperations > 0) {
            score -= realtimeMetrics.failedOperations * 0.05
        }
        
        return score.coerceIn(0.0, 1.0)
    }
    
    private fun generateHealthRecommendations(healthFactors: List<HealthFactor>): List<String> {
        val recommendations = mutableListOf<String>()
        
        healthFactors.forEach { factor ->
            when (factor.impact) {
                HealthImpact.HIGH -> recommendations.add("URGENT: ${factor.description}")
                HealthImpact.MEDIUM -> recommendations.add("Monitor: ${factor.description}")
                HealthImpact.LOW -> recommendations.add("Note: ${factor.description}")
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("System operating optimally")
        }
        
        return recommendations
    }
}

// =====================================
// METRICS COLLECTOR
// =====================================

/**
 * 📊 Real-time Metrics Collector
 */
class MetricsCollector {
    
    fun collectRealtimeMetrics(swarmId: String): RealtimeMetrics {
        // Mock real-time data - in real implementation, collect from live system
        return RealtimeMetrics(
            swarmId = swarmId,
            activeOperations = 7,
            completedOperations = 156,
            failedOperations = 3,
            averageResponseTime = 1200.0,
            throughputPerMinute = 12.5,
            memoryUsage = 0.68,
            cpuUsage = 0.72,
            networkLatency = 45.0,
            timestamp = System.currentTimeMillis()
        )
    }
}

// =====================================
// PERFORMANCE VISUALIZER
// =====================================

/**
 * 🎨 Performance Visualizer
 */
class PerformanceVisualizer {
    
    fun renderDashboard(report: PerformanceReport): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val currentTime = Instant.ofEpochMilli(report.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
        
        return """
        ╔══════════════════════════════════════════════════════════════════════════════════════╗
        ║                          🚀 SwarmAgent Performance Dashboard                          ║
        ║                                    $currentTime                                    ║
        ╠══════════════════════════════════════════════════════════════════════════════════════╣
        ║ 📊 SYSTEM OVERVIEW                                                                   ║
        ║ • Swarm ID: ${report.swarmId.padEnd(20)} • Health: ${renderHealthStatus(report.systemHealth.status)}               ║
        ║ • Active Operations: ${report.realtimeMetrics.activeOperations.toString().padEnd(10)} • System Load: ${String.format("%.1f%%", report.agentAnalytics.systemLoad * 100).padEnd(8)} ║
        ║ • Success Rate: ${String.format("%.1f%%", report.agentAnalytics.averageSuccessRate * 100).padEnd(12)} • Avg Response: ${String.format("%.0fms", report.agentAnalytics.averageResponseTime).padEnd(8)} ║
        ╠══════════════════════════════════════════════════════════════════════════════════════╣
        ║ 🤖 AGENT PERFORMANCE                                                                 ║
        ${renderAgentMetrics(report.agentAnalytics)}
        ╠══════════════════════════════════════════════════════════════════════════════════════╣
        ║ 🎯 STRATEGY EFFECTIVENESS                                                            ║
        ${renderStrategyMetrics(report.strategyAnalytics)}
        ╠══════════════════════════════════════════════════════════════════════════════════════╣
        ║ 💡 INSIGHTS & RECOMMENDATIONS                                                        ║
        ${renderInsights(report.insights)}
        ╚══════════════════════════════════════════════════════════════════════════════════════╝
        """.trimIndent()
    }
    
    fun createAgentPerformanceChart(analytics: AgentPerformanceAnalytics): String {
        return analytics.agentMetrics.joinToString("\n") { agent ->
            val scoreBar = "█".repeat((agent.averageScore * 20).toInt()) + 
                          "░".repeat(20 - (agent.averageScore * 20).toInt())
            "║ ${agent.agentId.padEnd(12)} [$scoreBar] ${String.format("%.2f", agent.averageScore)} ${renderTrend(agent.trend)} ║"
        }
    }
    
    fun createStrategyChart(analytics: StrategyAnalytics): String {
        return analytics.effectivenessScores.entries.joinToString("\n") { (strategy, score) ->
            val scoreBar = "█".repeat((score * 15).toInt()) + 
                          "░".repeat(15 - (score * 15).toInt())
            "║ ${strategy.name.padEnd(12)} [$scoreBar] ${String.format("%.2f", score)}                    ║"
        }
    }
    
    fun createSystemLoadGraph(metrics: RealtimeMetrics): String = "System Load Visualization"
    fun createSuccessRateTrend(analytics: AgentPerformanceAnalytics): String = "Success Rate Trend"
    fun createResponseTimeDistribution(analytics: AgentPerformanceAnalytics): String = "Response Time Distribution"
    
    private fun renderHealthStatus(status: HealthStatus): String {
        return when (status) {
            HealthStatus.EXCELLENT -> "🟢 EXCELLENT"
            HealthStatus.GOOD -> "🟡 GOOD     "
            HealthStatus.FAIR -> "🟠 FAIR     "
            HealthStatus.POOR -> "🔴 POOR     "
            HealthStatus.CRITICAL -> "💀 CRITICAL "
        }
    }
    
    private fun renderAgentMetrics(analytics: AgentPerformanceAnalytics): String {
        return analytics.agentMetrics.joinToString("\n") { agent ->
            val loadBar = "█".repeat((agent.currentLoad * 10).toInt()) + 
                         "░".repeat(10 - (agent.currentLoad * 10).toInt())
            "║ ${agent.agentId.padEnd(12)} | Load: [$loadBar] | Success: ${String.format("%.1f%%", agent.successRate * 100).padEnd(6)} | ${renderTrend(agent.trend)} ║"
        }
    }
    
    private fun renderStrategyMetrics(analytics: StrategyAnalytics): String {
        return analytics.strategyResults.entries.take(3).joinToString("\n") { (strategy, metrics) ->
            "║ ${strategy.name.padEnd(12)} | Executions: ${metrics.executionCount.toString().padEnd(3)} | Success: ${String.format("%.1f%%", metrics.successRate * 100).padEnd(6)} | Quality: ${String.format("%.2f", metrics.averageQualityScore)} ║"
        }
    }
    
    private fun renderInsights(insights: List<PerformanceInsight>): String {
        return insights.take(3).joinToString("\n") { insight ->
            val icon = when (insight.type) {
                InsightType.POSITIVE_TREND -> "📈"
                InsightType.OPTIMIZATION_OPPORTUNITY -> "⚡"
                InsightType.PERFORMANCE_ISSUE -> "⚠️"
                InsightType.QUALITY_EXCELLENCE -> "🏆"
            }
            "║ $icon ${insight.title.padEnd(76)} ║"
        }
    }
    
    private fun renderTrend(trend: TrendDirection): String {
        return when (trend) {
            TrendDirection.IMPROVING -> "📈"
            TrendDirection.DECLINING -> "📉"
            TrendDirection.STABLE -> "➡️"
        }
    }
}

// =====================================
// DATA STRUCTURES
// =====================================

/**
 * 📊 Performance Report
 */
data class PerformanceReport(
    val swarmId: String,
    val timeRange: TimeRange,
    val realtimeMetrics: RealtimeMetrics,
    val agentAnalytics: AgentPerformanceAnalytics,
    val strategyAnalytics: StrategyAnalytics,
    val systemHealth: SystemHealth,
    val insights: List<PerformanceInsight>,
    val visualizations: DashboardVisualizations,
    val timestamp: Long
)

/**
 * ⏰ Time Range
 */
enum class TimeRange {
    LAST_HOUR, LAST_6_HOURS, LAST_24_HOURS, LAST_WEEK
}

/**
 * 📈 Real-time Metrics
 */
data class RealtimeMetrics(
    val swarmId: String,
    val activeOperations: Int,
    val completedOperations: Int,
    val failedOperations: Int,
    val averageResponseTime: Double,
    val throughputPerMinute: Double,
    val memoryUsage: Double,
    val cpuUsage: Double,
    val networkLatency: Double,
    val timestamp: Long
)

/**
 * 🤖 Agent Performance Analytics
 */
data class AgentPerformanceAnalytics(
    val agentMetrics: List<AgentMetrics>,
    val totalTasks: Int,
    val averageSuccessRate: Double,
    val averageResponseTime: Double,
    val systemLoad: Double,
    val topPerformers: List<AgentMetrics>,
    val improvingAgents: List<AgentMetrics>,
    val overloadedAgents: List<AgentMetrics>
)

/**
 * 📊 Agent Metrics
 */
data class AgentMetrics(
    val agentId: String,
    val tasksCompleted: Int,
    val successRate: Double,
    val averageResponseTime: Double,
    val averageScore: Double,
    val trend: TrendDirection,
    val currentLoad: Double,
    val specializations: List<String>
)

/**
 * 🎯 Strategy Analytics
 */
data class StrategyAnalytics(
    val strategyResults: Map<SwarmStrategyType, StrategyMetrics>,
    val effectivenessScores: Map<SwarmStrategyType, Double>,
    val mostEffectiveStrategy: SwarmStrategyType?,
    val leastEffectiveStrategy: SwarmStrategyType?,
    val totalExecutions: Int
)

/**
 * 📈 Strategy Metrics
 */
data class StrategyMetrics(
    val executionCount: Int,
    val successRate: Double,
    val averageExecutionTime: Double,
    val averageQualityScore: Double,
    val averageAgentUtilization: Double
)

/**
 * 🏥 System Health
 */
data class SystemHealth(
    val overallScore: Double,
    val status: HealthStatus,
    val healthFactors: List<HealthFactor>,
    val recommendations: List<String>,
    val timestamp: Long
)

/**
 * 💡 Performance Insight
 */
data class PerformanceInsight(
    val type: InsightType,
    val title: String,
    val description: String,
    val impact: InsightImpact,
    val actionable: Boolean,
    val recommendation: String
)

/**
 * 🎨 Dashboard Visualizations
 */
data class DashboardVisualizations(
    val agentPerformanceChart: String,
    val strategyEffectivenessChart: String,
    val systemLoadGraph: String,
    val successRateTrend: String,
    val responseTimeDistribution: String
)

/**
 * 🏥 Health Factor
 */
data class HealthFactor(
    val name: String,
    val impact: HealthImpact,
    val description: String
)

/**
 * ⚙️ Dashboard Configuration
 */
data class DashboardConfig(
    val debugEnabled: Boolean = false,
    val refreshInterval: Long = 5000,
    val retentionPeriod: Long = 24 * 60 * 60 * 1000 // 24 hours
)

// =====================================
// ENUMS
// =====================================

enum class TrendDirection { IMPROVING, DECLINING, STABLE }
enum class HealthStatus { EXCELLENT, GOOD, FAIR, POOR, CRITICAL }
enum class HealthImpact { HIGH, MEDIUM, LOW }
enum class InsightType { POSITIVE_TREND, OPTIMIZATION_OPPORTUNITY, PERFORMANCE_ISSUE, QUALITY_EXCELLENCE }
enum class InsightImpact { HIGH, MEDIUM, LOW } 