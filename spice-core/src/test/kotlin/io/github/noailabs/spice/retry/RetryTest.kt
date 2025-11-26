package io.github.noailabs.spice.retry

import io.github.noailabs.spice.SimpleTool
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.RetryHint
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.tool.ToolInvocationContext
import io.github.noailabs.spice.tool.ToolLifecycleListener
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive tests for retry functionality.
 *
 * Tests cover:
 * - RetryClassifier: Error classification
 * - ExecutionRetryPolicy: Backoff calculation
 * - RetrySupervisor: Retry orchestration
 * - GraphRunner integration: Graph-level override, disableRetry()
 */
class RetryTest {

    @Nested
    inner class RetryClassifierTest {

        private val classifier = RetryClassifier.DEFAULT

        @Test
        fun `classifies RetryableError as retryable`() {
            val error = SpiceError.RetryableError(
                message = "Service unavailable",
                statusCode = 503
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 5xx as retryable`() {
            val error = SpiceError.NetworkError(
                message = "Internal Server Error",
                statusCode = 500
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 503 as retryable`() {
            val error = SpiceError.NetworkError(
                message = "Service Unavailable",
                statusCode = 503
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 408 (Request Timeout) as retryable`() {
            val error = SpiceError.NetworkError(
                message = "Request Timeout",
                statusCode = 408
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 429 (Rate Limited) as retryable`() {
            val error = SpiceError.NetworkError(
                message = "Too Many Requests",
                statusCode = 429
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
        }

        @Test
        fun `classifies RateLimitError as retryable with suggested delay`() {
            val error = SpiceError.RateLimitError(
                message = "Rate limited",
                retryAfterMs = 5000
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
            assertEquals(5000L, result.suggestedDelayMs)
        }

        @Test
        fun `classifies TimeoutError as retryable`() {
            val error = SpiceError.TimeoutError(
                message = "Operation timed out",
                timeoutMs = 30000
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
        }

        @Test
        fun `classifies ToolError without statusCode as non-retryable by default`() {
            val error = SpiceError.ToolError(
                message = "Tool failed",
                toolName = "my-tool"
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `classifies ToolError with retryable statusCode as retryable`() {
            val error = SpiceError.ToolError(
                message = "Tool failed",
                toolName = "my-tool",
                context = mapOf("statusCode" to 503)
            )
            val result = classifier.classify(error)
            assertTrue(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 4xx (client error) as non-retryable`() {
            val error = SpiceError.NetworkError(
                message = "Bad Request",
                statusCode = 400
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 404 as non-retryable`() {
            val error = SpiceError.NetworkError(
                message = "Not Found",
                statusCode = 404
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 401 (Unauthorized) as non-retryable`() {
            val error = SpiceError.NetworkError(
                message = "Unauthorized",
                statusCode = 401
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `classifies NetworkError 403 (Forbidden) as non-retryable`() {
            val error = SpiceError.NetworkError(
                message = "Forbidden",
                statusCode = 403
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `classifies ValidationError as non-retryable`() {
            val error = SpiceError.ValidationError(
                message = "Invalid input",
                field = "email"
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `classifies AuthenticationError as non-retryable`() {
            val error = SpiceError.AuthenticationError(
                message = "Invalid credentials"
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `classifies ConfigurationError as non-retryable`() {
            val error = SpiceError.ConfigurationError(
                message = "Invalid configuration"
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `respects RetryHint skipRetry flag`() {
            val error = SpiceError.RetryableError(
                message = "Skip this retry",
                retryHint = RetryHint(skipRetry = true)
            )
            val result = classifier.classify(error)
            assertFalse(result.shouldRetry)
        }

        @Test
        fun `NEVER_RETRY classifier rejects all errors`() {
            val classifier = RetryClassifier.NEVER_RETRY

            val errors = listOf(
                SpiceError.RetryableError("test"),
                SpiceError.NetworkError("test", 500),
                SpiceError.RateLimitError("test"),
                SpiceError.TimeoutError("test")
            )

            errors.forEach { error ->
                assertFalse(classifier.classify(error).shouldRetry)
            }
        }
    }

    @Nested
    inner class ExecutionRetryPolicyTest {

        @Test
        fun `DEFAULT policy has expected values`() {
            val policy = ExecutionRetryPolicy.DEFAULT

            assertEquals(3, policy.maxAttempts)
            assertEquals(200.milliseconds, policy.initialDelay)
            assertEquals(10.seconds, policy.maxDelay)
            assertEquals(2.0, policy.backoffMultiplier)
            assertEquals(0.1, policy.jitterFactor)
        }

        @Test
        fun `NO_RETRY policy has one attempt only`() {
            val policy = ExecutionRetryPolicy.NO_RETRY

            // NO_RETRY means 1 total attempt (execute once, no retries)
            assertEquals(1, policy.maxAttempts)
            assertFalse(policy.isRetryEnabled())  // isRetryEnabled checks maxAttempts > 1
        }

        @Test
        fun `shouldRetry returns true within maxAttempts`() {
            val policy = ExecutionRetryPolicy(maxAttempts = 3)

            // shouldRetry checks if current attempt is allowed (currentAttempt <= maxAttempts)
            assertTrue(policy.shouldRetry(1))
            assertTrue(policy.shouldRetry(2))
            assertTrue(policy.shouldRetry(3))
            assertFalse(policy.shouldRetry(4))
        }

        @Test
        fun `hasMoreRetries returns false for NO_RETRY policy after first attempt`() {
            val policy = ExecutionRetryPolicy.NO_RETRY

            // First attempt is allowed
            assertTrue(policy.shouldRetry(1))
            // But no more retries after first attempt
            assertFalse(policy.hasMoreRetries(1))
        }

        @Test
        fun `calculateDelay respects retryAfterHint priority`() {
            val policy = ExecutionRetryPolicy(
                initialDelay = 200.milliseconds,
                maxDelay = 10.seconds,
                jitterFactor = 0.0 // Disable jitter for predictable test
            )

            // retryAfterHint should be used instead of calculated delay
            val delay = policy.calculateDelay(1, retryAfterHint = 5.seconds)
            assertEquals(5.seconds, delay)
        }

        @Test
        fun `calculateDelay caps retryAfterHint at maxDelay`() {
            val policy = ExecutionRetryPolicy(
                maxDelay = 10.seconds,
                jitterFactor = 0.0
            )

            val delay = policy.calculateDelay(1, retryAfterHint = 60.seconds)
            assertEquals(10.seconds, delay)
        }

        @Test
        fun `calculateDelay uses exponential backoff`() {
            val policy = ExecutionRetryPolicy(
                initialDelay = 200.milliseconds,
                backoffMultiplier = 2.0,
                maxDelay = 10.seconds,
                jitterFactor = 0.0 // Disable jitter for predictable test
            )

            assertEquals(200.milliseconds, policy.calculateDelay(1))
            assertEquals(400.milliseconds, policy.calculateDelay(2))
            assertEquals(800.milliseconds, policy.calculateDelay(3))
            assertEquals(1600.milliseconds, policy.calculateDelay(4))
        }

        @Test
        fun `calculateDelay caps at maxDelay`() {
            val policy = ExecutionRetryPolicy(
                initialDelay = 1.seconds,
                backoffMultiplier = 10.0,
                maxDelay = 5.seconds,
                jitterFactor = 0.0
            )

            // Attempt 1: 1s, Attempt 2: 10s -> capped to 5s
            assertEquals(1.seconds, policy.calculateDelay(1))
            assertEquals(5.seconds, policy.calculateDelay(2))
            assertEquals(5.seconds, policy.calculateDelay(3))
        }

        @Test
        fun `delaySequence generates correct sequence`() {
            val policy = ExecutionRetryPolicy(
                maxAttempts = 3,
                initialDelay = 100.milliseconds,
                backoffMultiplier = 2.0,
                maxDelay = 10.seconds,
                jitterFactor = 0.0
            )

            val delays = policy.delaySequence().toList()

            assertEquals(3, delays.size)
            assertEquals(100.milliseconds, delays[0])
            assertEquals(200.milliseconds, delays[1])
            assertEquals(400.milliseconds, delays[2])
        }
    }

    @Nested
    inner class RetrySupervisorTest {

        @Test
        fun `succeeds on first attempt without retry`() = runTest {
            val supervisor = RetrySupervisor.default()
            var attemptCount = 0

            val result = supervisor.executeWithRetry(
                message = SpiceMessage.create("test", "user"),
                nodeId = "test-node",
                policy = ExecutionRetryPolicy.DEFAULT
            ) { _, attempt ->
                attemptCount = attempt
                SpiceResult.success("success")
            }

            assertTrue(result is RetryResult.Success)
            assertEquals("success", (result as RetryResult.Success).value)
            assertEquals(1, attemptCount)
        }

        @Test
        fun `retries on retryable error and succeeds`() = runTest {
            val supervisor = RetrySupervisor.default()
            var attemptCount = 0

            val result = supervisor.executeWithRetry(
                message = SpiceMessage.create("test", "user"),
                nodeId = "test-node",
                policy = ExecutionRetryPolicy(
                    maxAttempts = 3,
                    initialDelay = 10.milliseconds,
                    jitterFactor = 0.0
                )
            ) { _, attempt ->
                attemptCount = attempt
                if (attempt < 2) {
                    SpiceResult.failure(SpiceError.NetworkError("Temporary error", 503))
                } else {
                    SpiceResult.success("success after retry")
                }
            }

            assertTrue(result is RetryResult.Success)
            assertEquals("success after retry", (result as RetryResult.Success).value)
            assertEquals(2, attemptCount)
        }

        @Test
        fun `exhausts retries and returns ExecutionError`() = runTest {
            val supervisor = RetrySupervisor.default()
            var attemptCount = 0

            val result = supervisor.executeWithRetry(
                message = SpiceMessage.create("test", "user"),
                nodeId = "test-node",
                policy = ExecutionRetryPolicy(
                    maxAttempts = 3,  // 3 total attempts (1 initial + 2 retries)
                    initialDelay = 10.milliseconds,
                    jitterFactor = 0.0
                )
            ) { _, attempt ->
                attemptCount = attempt
                SpiceResult.failure<String>(SpiceError.NetworkError("Always fails", 500))
            }

            assertTrue(result is RetryResult.Exhausted)
            val exhausted = result as RetryResult.Exhausted
            // maxAttempts=3 means 3 total attempts (1 initial + 2 retries)
            assertEquals(3, attemptCount)
            assertTrue(exhausted.finalError is SpiceError.ExecutionError)
            assertTrue(exhausted.finalError.message.contains("Retry exhausted after 3 attempts"))
            assertEquals(3, exhausted.context.attemptNumber)
        }

        @Test
        fun `does not retry non-retryable errors`() = runTest {
            val supervisor = RetrySupervisor.default()
            var attemptCount = 0

            val result = supervisor.executeWithRetry(
                message = SpiceMessage.create("test", "user"),
                nodeId = "test-node",
                policy = ExecutionRetryPolicy.DEFAULT
            ) { _, attempt ->
                attemptCount = attempt
                SpiceResult.failure<String>(SpiceError.ValidationError("Bad input", "field"))
            }

            assertTrue(result is RetryResult.NotRetryable)
            assertEquals(1, attemptCount) // Only one attempt
        }

        @Test
        fun `noRetry supervisor does not retry`() = runTest {
            val supervisor = RetrySupervisor.noRetry()
            var attemptCount = 0

            val result = supervisor.executeWithRetry(
                message = SpiceMessage.create("test", "user"),
                nodeId = "test-node"
            ) { _, attempt ->
                attemptCount = attempt
                SpiceResult.failure<String>(SpiceError.NetworkError("Error", 500))
            }

            assertTrue(result is RetryResult.NotRetryable)
            assertEquals(1, attemptCount)
        }

        @Test
        fun `ExecutionError contains retry metadata`() = runTest {
            val supervisor = RetrySupervisor.default()

            val result = supervisor.executeWithRetry(
                message = SpiceMessage.create("test", "user"),
                nodeId = "test-node",
                policy = ExecutionRetryPolicy(
                    maxAttempts = 3,  // 3 total attempts (1 initial + 2 retries)
                    initialDelay = 10.milliseconds,
                    jitterFactor = 0.0
                )
            ) { _, _ ->
                SpiceResult.failure<String>(SpiceError.NetworkError("Server error", 500))
            }

            assertTrue(result is RetryResult.Exhausted)
            val error = (result as RetryResult.Exhausted).finalError as SpiceError.ExecutionError

            assertEquals(true, error.context["retriesExhausted"])
            // maxAttempts=3 means 3 total attempts (1 initial + 2 retries)
            assertEquals(3, error.context["totalAttempts"])
            assertNotNull(error.context["totalRetryDelayMs"])
            assertNotNull(error.context["elapsedMs"])
            assertEquals(500, error.context["lastStatusCode"])
        }
    }

    @Nested
    inner class GraphRunnerRetryIntegrationTest {

        @Test
        fun `graph-level retryPolicy overrides runner default`() = runTest {
            var attemptCount = 0
            val attemptNumbers = mutableListOf<Int>()

            val failingTool = SimpleTool(
                name = "failing_tool",
                description = "Always fails with retryable error"
            ) { _ ->
                attemptCount++
                // Throw SocketException which is converted to RetryableError
                throw java.net.SocketException("Connection reset")
            }

            val listener = object : ToolLifecycleListener {
                override suspend fun onInvoke(context: ToolInvocationContext) {
                    attemptNumbers.add(context.attemptNumber)
                }
                override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {}
                override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {}
                override suspend fun onComplete(context: ToolInvocationContext) {}
            }

            // Graph with custom retry policy: 3 total attempts (1 initial + 2 retries)
            val testGraph = graph("retry-test") {
                retryPolicy(ExecutionRetryPolicy(
                    maxAttempts = 3,  // 3 total attempts
                    initialDelay = 10.milliseconds,
                    jitterFactor = 0.0
                ))
                toolLifecycleListeners(listener)
                tool("tool1", failingTool)
            }

            // Runner with different default: 5 total attempts
            val runner = DefaultGraphRunner(
                retrySupervisor = RetrySupervisor.default(),
                defaultRetryPolicy = ExecutionRetryPolicy(
                    maxAttempts = 5,
                    initialDelay = 10.milliseconds
                )
            )

            val message = SpiceMessage.create("test", "user")
            val result = runner.execute(testGraph, message)

            assertTrue(result is SpiceResult.Failure)
            // Should use graph's 3 total attempts, not runner's 5 total attempts
            assertEquals(3, attemptCount)
            assertEquals(listOf(1, 2, 3), attemptNumbers)
        }

        @Test
        fun `disableRetry causes single attempt only`() = runTest {
            var attemptCount = 0

            val failingTool = SimpleTool(
                name = "failing_tool",
                description = "Always fails with retryable error"
            ) { _ ->
                attemptCount++
                // Throw SocketException which would normally be retried
                throw java.net.SocketException("Connection reset")
            }

            // Graph with retry disabled
            val testGraph = graph("no-retry-test") {
                disableRetry()
                tool("tool1", failingTool)
            }

            // Runner with retry enabled by default (5 total attempts)
            val runner = DefaultGraphRunner(
                retrySupervisor = RetrySupervisor.default(),
                defaultRetryPolicy = ExecutionRetryPolicy(maxAttempts = 5)
            )

            val message = SpiceMessage.create("test", "user")
            val result = runner.execute(testGraph, message)

            assertTrue(result is SpiceResult.Failure)
            // Should only attempt once due to disableRetry() overriding runner's retry
            assertEquals(1, attemptCount)
        }

        @Test
        fun `enableRetry activates retry for graph`() = runTest {
            var attemptCount = 0

            val failingTool = SimpleTool(
                name = "failing_tool",
                description = "Fails twice then succeeds"
            ) { _ ->
                attemptCount++
                if (attemptCount < 3) {
                    // Throw SocketException which is converted to RetryableError
                    throw java.net.SocketException("Connection reset")
                }
                ToolResult.success("success")
            }

            // Graph with retry enabled (default policy: 3 retries = 4 total)
            val testGraph = graph("enable-retry-test") {
                enableRetry()
                tool("tool1", failingTool)
            }

            // Runner without supervisor (retry disabled by default)
            val runner = DefaultGraphRunner()

            val message = SpiceMessage.create("test", "user")
            val result = runner.execute(testGraph, message)

            assertTrue(result is SpiceResult.Success)
            assertEquals(3, attemptCount) // Succeeded on 3rd attempt
        }

        @Test
        fun `retry preserves original message across attempts`() = runTest {
            val receivedContents = mutableListOf<String>()

            val checkingTool = SimpleTool(
                name = "checking_tool",
                description = "Records received messages"
            ) { params ->
                val content = params["content"]?.toString() ?: ""
                receivedContents.add(content)
                if (receivedContents.size < 2) {
                    // Throw retryable exception
                    throw java.net.SocketException("Connection reset")
                }
                ToolResult.success("done")
            }

            val testGraph = graph("message-test") {
                enableRetry()
                tool("tool1", checkingTool) { msg ->
                    mapOf("content" to msg.content, "id" to msg.id)
                }
            }

            val runner = DefaultGraphRunner(
                retrySupervisor = RetrySupervisor.default(),
                defaultRetryPolicy = ExecutionRetryPolicy(
                    maxAttempts = 3,
                    initialDelay = 10.milliseconds
                )
            )

            val originalMessage = SpiceMessage.create("original content", "user")
            runner.execute(testGraph, originalMessage)

            // All attempts should receive same original content
            assertTrue(receivedContents.all { it == "original content" })
        }

        @Test
        fun `attemptNumber is passed to ToolInvocationContext`() = runTest {
            val capturedAttemptNumbers = mutableListOf<Int>()

            val listener = object : ToolLifecycleListener {
                override suspend fun onInvoke(context: ToolInvocationContext) {
                    capturedAttemptNumbers.add(context.attemptNumber)
                }
                override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {}
                override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {}
                override suspend fun onComplete(context: ToolInvocationContext) {}
            }

            var attemptCount = 0
            val failingTool = SimpleTool(
                name = "failing_tool",
                description = "Fails twice then succeeds"
            ) { _ ->
                attemptCount++
                if (attemptCount < 3) {
                    // Throw retryable exception
                    throw java.net.SocketException("Connection reset")
                }
                ToolResult.success("ok")
            }

            val testGraph = graph("attempt-number-test") {
                retryPolicy(ExecutionRetryPolicy(
                    maxAttempts = 3,  // 3 retries = enough to succeed on 3rd attempt
                    initialDelay = 10.milliseconds
                ))
                toolLifecycleListeners(listener)
                tool("tool1", failingTool)
            }

            val runner = DefaultGraphRunner(
                retrySupervisor = RetrySupervisor.default()
            )

            val message = SpiceMessage.create("test", "user")
            runner.execute(testGraph, message)

            assertEquals(listOf(1, 2, 3), capturedAttemptNumbers)
        }
    }

    @Nested
    inner class SpiceErrorFromExceptionTest {

        @Test
        fun `fromException returns RetryableError for SocketException`() {
            val exception = SocketException("Connection reset")
            val error = SpiceError.fromException(exception)

            assertTrue(error is SpiceError.RetryableError)
            assertTrue(error.message.contains("Connection reset"))
        }

        @Test
        fun `fromException returns RetryableError for SocketTimeoutException`() {
            val exception = SocketTimeoutException("Read timed out")
            val error = SpiceError.fromException(exception)

            assertTrue(error is SpiceError.RetryableError)
        }

        @Test
        fun `fromException returns RetryableError for UnknownHostException`() {
            val exception = UnknownHostException("api.example.com")
            val error = SpiceError.fromException(exception)

            assertTrue(error is SpiceError.RetryableError)
        }

        @Test
        fun `retryableError factory creates correct error`() {
            val error = SpiceError.retryableError(
                message = "Service unavailable",
                statusCode = 503,
                errorCode = "SERVICE_UNAVAILABLE"
            )

            assertTrue(error is SpiceError.RetryableError)
            assertEquals("Service unavailable", error.message)
            assertEquals(503, (error as SpiceError.RetryableError).statusCode)
            assertEquals("SERVICE_UNAVAILABLE", error.errorCode)
        }

        @Test
        fun `retryableHttpError factory creates error with retryAfter`() {
            val error = SpiceError.retryableHttpError(
                statusCode = 429,
                message = "Rate limited",
                retryAfterSeconds = 5
            )

            assertTrue(error is SpiceError.RetryableError)
            assertEquals(429, (error as SpiceError.RetryableError).statusCode)
            assertEquals(5000L, error.retryHint?.retryAfterMs) // 5 seconds = 5000ms
        }

        @Test
        fun `isRetryable companion method works correctly`() {
            assertTrue(SpiceError.isRetryable(SpiceError.NetworkError("error", 500)))
            assertTrue(SpiceError.isRetryable(SpiceError.NetworkError("error", 503)))
            assertTrue(SpiceError.isRetryable(SpiceError.NetworkError("error", 429)))
            assertTrue(SpiceError.isRetryable(SpiceError.NetworkError("error", 408)))
            assertTrue(SpiceError.isRetryable(SpiceError.TimeoutError("timeout")))
            assertTrue(SpiceError.isRetryable(SpiceError.RateLimitError("rate limited")))
            assertTrue(SpiceError.isRetryable(SpiceError.RetryableError("retryable")))

            assertFalse(SpiceError.isRetryable(SpiceError.NetworkError("error", 400)))
            assertFalse(SpiceError.isRetryable(SpiceError.NetworkError("error", 404)))
            assertFalse(SpiceError.isRetryable(SpiceError.ValidationError("validation")))
            assertFalse(SpiceError.isRetryable(SpiceError.AuthenticationError("auth")))
        }
    }

    @Nested
    inner class RetryContextTest {

        @Test
        fun `initial context has correct values`() {
            val context = RetryContext.initial("node-1", "tenant-1")

            assertEquals(1, context.attemptNumber)
            assertEquals("node-1", context.nodeId)
            assertEquals("tenant-1", context.tenantId)
            assertEquals(0, context.retryCount())
            assertTrue(context.isFirstAttempt())
        }

        @Test
        fun `recordAttempt increments attempt number`() {
            val initial = RetryContext.initial("node-1", null)
            val error = SpiceError.NetworkError("error", 500)

            val afterFirst = initial.recordAttempt(error, 100.milliseconds)

            assertEquals(2, afterFirst.attemptNumber)
            assertEquals(error, afterFirst.lastError)
            assertEquals(500, afterFirst.lastStatusCode)
            assertEquals(1, afterFirst.retryCount())
            assertFalse(afterFirst.isFirstAttempt())
        }

        @Test
        fun `context tracks error history`() {
            var context = RetryContext.initial("node-1", null)

            val errors = listOf(
                SpiceError.NetworkError("error1", 500),
                SpiceError.NetworkError("error2", 503),
                SpiceError.NetworkError("error3", 502)
            )

            errors.forEach { error ->
                context = context.recordAttempt(error, 100.milliseconds)
            }

            assertEquals(4, context.attemptNumber)
            assertEquals(3, context.errors.size)
            assertEquals(errors, context.errors)
        }

        @Test
        fun `context accumulates total retry delay`() {
            var context = RetryContext.initial("node-1", null)
            val error = SpiceError.NetworkError("error", 500)

            context = context.recordAttempt(error, 100.milliseconds)
            context = context.recordAttempt(error, 200.milliseconds)
            context = context.recordAttempt(error, 400.milliseconds)

            assertEquals(700.milliseconds, context.totalRetryDelay)
        }
    }
}
