package io.github.noailabs.spice.samples.flow

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.middleware.*
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive Graph Middleware Sample
 *
 * This sample demonstrates:
 * 1. Built-in Middleware (LoggingMiddleware, MetricsMiddleware)
 * 2. Advanced Patterns (Conditional, Auth, Caching, Rate Limiting, Composition, Timeout)
 * 3. Real-world Use Cases (Multi-tenant security, Cost optimization)
 * 4. Error Handling with ErrorAction
 */

// =====================================
// ADVANCED MIDDLEWARE PATTERNS
// =====================================

/**
 * Pattern 1: Conditional Middleware - Wraps another middleware with a condition
 */
class ConditionalMiddleware(
    private val condition: (NodeRequest) -> Boolean,
    private val wrapped: Middleware
) : Middleware {
    override suspend fun onNode(
        request: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        return if (condition(request)) {
            wrapped.onNode(request, next)
        } else {
            next(request)
        }
    }
}

/**
 * Pattern 2: Authentication & Authorization Middleware
 */
class AuthMiddleware : Middleware {
    override suspend fun onNode(
        request: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val tenantId = request.context.context.tenantId
        val userId = request.context.context.userId

        // Validate tenant & user
        if (tenantId.isNullOrBlank() || userId.isNullOrBlank()) {
            return SpiceResult.failure(
                SpiceError.ValidationError(
                    message = "Missing tenantId or userId in context",
                    validationErrors = mapOf(
                        "tenantId" to tenantId,
                        "userId" to userId
                    )
                )
            )
        }

        // Check permissions (simplified example)
        val hasPermission = checkPermission(tenantId, userId, request.nodeId)
        if (!hasPermission) {
            return SpiceResult.failure(
                SpiceError.AuthenticationError(
                    message = "User $userId does not have permission for node ${request.nodeId}"
                )
            )
        }

        return next(request)
    }

    private fun checkPermission(tenantId: String, userId: String, nodeId: String): Boolean {
        // In production, query permission store
        // For demo, allow all except special nodes
        return !nodeId.startsWith("admin-")
    }
}

/**
 * Pattern 3: Caching Middleware - Caches node results
 */
class CachingMiddleware(
    private val ttlMillis: Long = 60_000 // 1 minute default
) : Middleware {
    private data class CacheEntry(val result: NodeResult, val timestamp: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun onNode(
        request: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val cacheKey = "${request.nodeId}:${request.input.hashCode()}"
        val now = System.currentTimeMillis()

        // Check cache
        cache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < ttlMillis) {
                println("🎯 Cache hit for node ${request.nodeId}")
                return SpiceResult.success(entry.result)
            } else {
                cache.remove(cacheKey)
            }
        }

        // Execute and cache
        return next(request).onSuccess { response ->
            cache[cacheKey] = CacheEntry(response, now)
            println("💾 Cached result for node ${request.nodeId}")
        }
    }

    fun clearCache() = cache.clear()
}

/**
 * Pattern 4: Rate Limiting Middleware
 */
class RateLimitingMiddleware(
    private val maxRequestsPerMinute: Int = 60
) : Middleware {
    private data class RateLimitState(
        val count: AtomicInteger = AtomicInteger(0),
        var windowStart: Long = System.currentTimeMillis()
    )

    private val rateLimits = ConcurrentHashMap<String, RateLimitState>()

    override suspend fun onNode(
        request: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val tenantId = request.context.context.tenantId ?: "default"
        val now = System.currentTimeMillis()

        val state = rateLimits.computeIfAbsent(tenantId) { RateLimitState() }

        synchronized(state) {
            // Reset window if expired
            if (now - state.windowStart > 60_000) {
                state.count.set(0)
                state.windowStart = now
            }

            // Check limit
            if (state.count.get() >= maxRequestsPerMinute) {
                return SpiceResult.failure(
                    SpiceError.RateLimitError(
                        message = "Rate limit exceeded for tenant $tenantId",
                        retryAfter = 60_000 - (now - state.windowStart)
                    )
                )
            }

            state.count.incrementAndGet()
        }

        return next(request)
    }
}

/**
 * Pattern 5: Timeout Middleware
 */
class TimeoutMiddleware(
    private val timeoutMillis: Long = 30_000 // 30 seconds default
) : Middleware {
    override suspend fun onNode(
        request: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        return try {
            withTimeout(timeoutMillis) {
                next(request)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            SpiceResult.failure(
                SpiceError.TimeoutError(
                    message = "Node ${request.nodeId} execution timeout after ${timeoutMillis}ms",
                    timeoutDuration = timeoutMillis
                )
            )
        }
    }
}

/**
 * Pattern 6: Composition - Chain multiple middleware
 */
fun composeMiddleware(vararg middleware: Middleware): Middleware {
    return object : Middleware {
        override suspend fun onNode(
            request: NodeRequest,
            next: suspend (NodeRequest) -> SpiceResult<NodeResult>
        ): SpiceResult<NodeResult> {
            var current = next
            for (i in middleware.indices.reversed()) {
                val m = middleware[i]
                val captured = current
                current = { req -> m.onNode(req, captured) }
            }
            return current(request)
        }
    }
}

// =====================================
// CUSTOM MIDDLEWARE EXAMPLES
// =====================================

/**
 * Cost Tracking Middleware - Tracks token usage and costs
 */
class CostTrackingMiddleware : Middleware {
    private val costsByTenant = ConcurrentHashMap<String, Double>()
    private val tokensByNode = ConcurrentHashMap<String, Long>()

    override suspend fun onNode(
        request: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val tenantId = request.context.context.tenantId ?: "default"
        val startTime = System.currentTimeMillis()

        return next(request).map { response ->
            // Extract token usage from metadata (simulated)
            val tokensUsed = response.metadata["tokens_used"]?.toString()?.toLongOrNull() ?: 0L
            val costPerToken = 0.00001 // $0.01 per 1000 tokens
            val cost = tokensUsed * costPerToken

            // Accumulate
            tokensByNode.merge(request.nodeId, tokensUsed, Long::plus)
            costsByTenant.merge(tenantId, cost, Double::plus)

            val duration = System.currentTimeMillis() - startTime
            println("💰 Cost: ${request.nodeId} → \$${String.format("%.6f", cost)} " +
                    "(${tokensUsed} tokens, ${duration}ms)")

            response
        }
    }

    fun getCostByTenant(tenantId: String): Double = costsByTenant[tenantId] ?: 0.0
    fun getTokensByNode(nodeId: String): Long = tokensByNode[nodeId] ?: 0L
}

/**
 * Audit Logging Middleware - Records all node executions
 */
class AuditMiddleware : Middleware {
    data class AuditLog(
        val timestamp: Instant,
        val tenantId: String?,
        val userId: String?,
        val nodeId: String,
        val success: Boolean,
        val errorMessage: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    )

    private val auditLogs = mutableListOf<AuditLog>()

    override suspend fun onNode(
        request: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val result = next(request)

        val log = AuditLog(
            timestamp = Instant.now(),
            tenantId = request.context.context.tenantId,
            userId = request.context.context.userId,
            nodeId = request.nodeId,
            success = result.isSuccess,
            errorMessage = result.errorOrNull()?.message,
            metadata = mapOf(
                "input" to request.input?.toString()
            )
        )

        synchronized(auditLogs) {
            auditLogs.add(log)
        }

        return result
    }

    fun getAuditLogs(): List<AuditLog> = synchronized(auditLogs) { auditLogs.toList() }
}

// =====================================
// TEST AGENTS
// =====================================

/**
 * Simple calculator agent for testing
 */
class CalculatorAgent : Agent {
    override val id = "calculator"
    override val name = "Calculator Agent"
    override val description = "Performs calculations"
    override val capabilities = listOf("calculate")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val result = when (comm.content) {
            "slow" -> {
                delay(2000) // Simulate slow operation
                "100"
            }
            "error" -> return SpiceResult.failure(SpiceError.AgentError("Intentional error", null))
            else -> {
                val nums = comm.content.split("+").map { it.trim().toIntOrNull() ?: 0 }
                nums.sum().toString()
            }
        }

        return SpiceResult.success(
            comm.reply("Result: $result", id)
                .withData(mapOf(
                    "tokens_used" to 150L, // Simulated
                    "model" to "gpt-4"
                ))
        )
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
    override fun isReady() = true
}

/**
 * Data processor agent
 */
class ProcessorAgent : Agent {
    override val id = "processor"
    override val name = "Processor Agent"
    override val description = "Processes data"
    override val capabilities = listOf("process")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply("Processed: ${comm.content}", id)
                .withData(mapOf(
                    "tokens_used" to 200L,
                    "processing_type" to "standard"
                ))
        )
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
    override fun isReady() = true
}

// =====================================
// MAIN EXAMPLES
// =====================================

fun main() = runBlocking {
    println("=== Graph Middleware Examples ===\n")

    val calculator = CalculatorAgent()
    val processor = ProcessorAgent()

    // Example 1: Basic Middleware (Logging + Metrics)
    println("--- Example 1: Basic Middleware ---")
    basicMiddlewareExample(calculator, processor)

    println("\n--- Example 2: Authentication Middleware ---")
    authenticationExample(calculator)

    println("\n--- Example 3: Caching Middleware ---")
    cachingExample(calculator)

    println("\n--- Example 4: Rate Limiting ---")
    rateLimitingExample(calculator)

    println("\n--- Example 5: Timeout Middleware ---")
    timeoutExample(calculator)

    println("\n--- Example 6: Middleware Composition ---")
    compositionExample(calculator, processor)

    println("\n--- Example 7: Production Stack ---")
    productionStackExample(calculator, processor)

    println("\n✅ All middleware examples completed!")
}

/**
 * Example 1: Basic logging and metrics
 */
suspend fun basicMiddlewareExample(calculator: CalculatorAgent, processor: ProcessorAgent) {
    val graph = graph("basic-middleware") {
        agent("calc", calculator)
        agent("process", processor)
        output("result") { ctx -> ctx.state["process"] }

        edge("calc", "process")
    }

    val middleware = listOf(
        LoggingMiddleware(),
        MetricsMiddleware()
    )

    val runner = DefaultGraphRunner()
    runner.execute(graph, mapOf("input" to "10+20"), middleware = middleware)
}

/**
 * Example 2: Authentication & Authorization
 */
suspend fun authenticationExample(calculator: CalculatorAgent) {
    val graph = graph("auth-demo") {
        agent("calc", calculator)
        output("result") { ctx -> ctx.state["calc"] }
    }

    val authMiddleware = AuthMiddleware()
    val runner = DefaultGraphRunner()

    // Test 1: Missing credentials
    println("Test 1: Missing credentials")
    val result1 = runner.execute(
        graph,
        mapOf("input" to "5+5"),
        middleware = listOf(authMiddleware)
    )
    if (result1.isFailure) {
        println("❌ Expected failure: ${result1.errorOrNull()?.message}")
    }

    // Test 2: Valid credentials
    println("\nTest 2: Valid credentials")
    val contextWithAuth = io.github.noailabs.spice.ExecutionContext.of(
        mapOf(
            "tenantId" to "tenant-123",
            "userId" to "user-456"
        )
    )
    kotlinx.coroutines.withContext(contextWithAuth) {
        val result2 = runner.execute(
            graph,
            mapOf("input" to "5+5"),
            middleware = listOf(authMiddleware)
        )
        if (result2.isSuccess) {
            println("✅ Authenticated execution succeeded")
        }
    }
}

/**
 * Example 3: Caching for expensive operations
 */
suspend fun cachingExample(calculator: CalculatorAgent) {
    val graph = graph("cache-demo") {
        agent("calc", calculator)
        output("result") { ctx -> ctx.state["calc"] }
    }

    val cachingMiddleware = CachingMiddleware(ttlMillis = 5000)
    val runner = DefaultGraphRunner()

    println("First execution (cache miss):")
    runner.execute(graph, mapOf("input" to "100+200"), middleware = listOf(cachingMiddleware))

    println("\nSecond execution (cache hit):")
    runner.execute(graph, mapOf("input" to "100+200"), middleware = listOf(cachingMiddleware))

    println("\nDifferent input (cache miss):")
    runner.execute(graph, mapOf("input" to "50+75"), middleware = listOf(cachingMiddleware))
}

/**
 * Example 4: Rate limiting
 */
suspend fun rateLimitingExample(calculator: CalculatorAgent) {
    val graph = graph("rate-limit-demo") {
        agent("calc", calculator)
        output("result") { ctx -> ctx.state["calc"] }
    }

    val rateLimiter = RateLimitingMiddleware(maxRequestsPerMinute = 3)
    val runner = DefaultGraphRunner()

    val context = io.github.noailabs.spice.ExecutionContext.of(
        mapOf("tenantId" to "tenant-123")
    )

    kotlinx.coroutines.withContext(context) {
        repeat(5) { i ->
            println("Request ${i + 1}:")
            val result = runner.execute(
                graph,
                mapOf("input" to "${i}+${i}"),
                middleware = listOf(rateLimiter)
            )
            if (result.isFailure) {
                println("❌ Rate limit exceeded: ${result.errorOrNull()?.message}")
            } else {
                println("✅ Request succeeded")
            }
        }
    }
}

/**
 * Example 5: Timeout protection
 */
suspend fun timeoutExample(calculator: CalculatorAgent) {
    val graph = graph("timeout-demo") {
        agent("calc", calculator)
        output("result") { ctx -> ctx.state["calc"] }
    }

    val timeoutMiddleware = TimeoutMiddleware(timeoutMillis = 1000) // 1 second
    val runner = DefaultGraphRunner()

    println("Fast operation (should succeed):")
    val result1 = runner.execute(
        graph,
        mapOf("input" to "1+1"),
        middleware = listOf(timeoutMiddleware)
    )
    println(if (result1.isSuccess) "✅ Completed" else "❌ Failed")

    println("\nSlow operation (should timeout):")
    val result2 = runner.execute(
        graph,
        mapOf("input" to "slow"),
        middleware = listOf(timeoutMiddleware)
    )
    if (result2.isFailure) {
        println("❌ Expected timeout: ${result2.errorOrNull()?.message}")
    }
}

/**
 * Example 6: Middleware composition
 */
suspend fun compositionExample(calculator: CalculatorAgent, processor: ProcessorAgent) {
    val graph = graph("composition-demo") {
        agent("calc", calculator)
        agent("process", processor)
        output("result") { ctx -> ctx.state["process"] }

        edge("calc", "process")
    }

    val composed = composeMiddleware(
        LoggingMiddleware(),
        TimeoutMiddleware(timeoutMillis = 5000),
        MetricsMiddleware()
    )

    val runner = DefaultGraphRunner()
    runner.execute(graph, mapOf("input" to "7+8"), middleware = listOf(composed))
}

/**
 * Example 7: Production-ready middleware stack
 */
suspend fun productionStackExample(calculator: CalculatorAgent, processor: ProcessorAgent) {
    val graph = graph("production-demo") {
        agent("calc", calculator)
        agent("process", processor)
        output("result") { ctx -> ctx.state["process"] }

        edge("calc", "process")
    }

    val auditMiddleware = AuditMiddleware()
    val costMiddleware = CostTrackingMiddleware()

    val productionStack = listOf(
        LoggingMiddleware(),
        auditMiddleware,
        AuthMiddleware(),
        RateLimitingMiddleware(maxRequestsPerMinute = 100),
        costMiddleware,
        TimeoutMiddleware(timeoutMillis = 30_000),
        MetricsMiddleware()
    )

    val runner = DefaultGraphRunner()

    val context = io.github.noailabs.spice.ExecutionContext.of(
        mapOf(
            "tenantId" to "tenant-prod",
            "userId" to "user-prod"
        )
    )

    kotlinx.coroutines.withContext(context) {
        val result = runner.execute(
            graph,
            mapOf("input" to "42+58"),
            middleware = productionStack
        )

        if (result.isSuccess) {
            println("\n📊 Production Metrics:")
            println("  Total cost for tenant-prod: \$${String.format("%.6f", costMiddleware.getCostByTenant("tenant-prod"))}")
            println("  Tokens used by calc node: ${costMiddleware.getTokensByNode("calc")}")
            println("  Audit logs recorded: ${auditMiddleware.getAuditLogs().size}")
            println("\n  Audit Details:")
            auditMiddleware.getAuditLogs().forEach { log ->
                println("    • ${log.timestamp} - ${log.nodeId} - ${if (log.success) "✅" else "❌"}")
            }
        }
    }
}
