package io.github.noailabs.spice.springboot.statemachine.transformer

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Injects distributed tracing context into messages.
 *
 * Creates trace and span IDs for tracking execution across nodes and subgraphs.
 * Essential for debugging super-graph BFS execution in kai-core.
 *
 * **Trace Hierarchy:**
 * ```
 * traceId (entire workflow)
 *   ├─ spanId: graph-execution (main graph)
 *   │   ├─ spanId: node-agent1 (node execution)
 *   │   ├─ spanId: node-tool1 (node execution)
 *   │   └─ spanId: subgraph-login (subgraph)
 *   │       ├─ spanId: node-check-session
 *   │       └─ spanId: node-load-profile
 *   └─ spanId: graph-completion
 * ```
 *
 * **Example - kai-core integration:**
 * ```kotlin
 * @Bean
 * fun tracingTransformer(): MessageTransformer {
 *     return TracingTransformer(
 *         sendToJaeger = { traceId, spanId, operation, duration ->
 *             jaegerClient.sendSpan(traceId, spanId, operation, duration)
 *         }
 *     )
 * }
 * ```
 *
 * **Accessing trace data:**
 * ```kotlin
 * val traceId = message.getMetadata<String>("traceId")
 * val spanId = message.getMetadata<String>("spanId")
 * val parentSpanId = message.getMetadata<String>("parentSpanId")
 * ```
 *
 * @param sendToJaeger Optional callback to send spans to Jaeger/Zipkin
 */
class TracingTransformer(
    private val sendToJaeger: ((String, String, String, Long) -> Unit)? = null
) : MessageTransformer {

    private val logger = LoggerFactory.getLogger(TracingTransformer::class.java)

    /**
     * Tracing is non-critical - failures shouldn't stop graph execution.
     */
    override val continueOnFailure: Boolean = true

    // Thread-local storage for span timing
    private val spanStartTimes = ThreadLocal<MutableMap<String, Long>>()

    override suspend fun beforeExecution(
        graph: Graph,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        // Create or propagate traceId
        val traceId = message.getMetadata<String>("traceId") ?: UUID.randomUUID().toString()
        val spanId = UUID.randomUUID().toString()

        logger.debug("Starting trace for graph ${graph.id}: traceId=$traceId, spanId=$spanId")

        // Record span start time
        getSpanTimes()[spanId] = System.currentTimeMillis()

        return SpiceResult.success(
            message.withMetadata(
                mapOf(
                    "traceId" to traceId,
                    "spanId" to spanId,
                    "graphId" to graph.id,
                    "spanOperation" to "graph:${graph.id}"
                )
            )
        )
    }

    override suspend fun beforeNode(
        graph: Graph,
        nodeId: String,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val traceId = message.getMetadata<String>("traceId") ?: return SpiceResult.success(message)
        val parentSpanId = message.getMetadata<String>("spanId")
        val nodeSpanId = UUID.randomUUID().toString()

        logger.debug("Starting node span: traceId=$traceId, nodeId=$nodeId, spanId=$nodeSpanId")

        // Record node span start time
        getSpanTimes()[nodeSpanId] = System.currentTimeMillis()

        return SpiceResult.success(
            message.withMetadata(
                mapOf(
                    "spanId" to nodeSpanId,
                    "parentSpanId" to (parentSpanId ?: ""),
                    "nodeId" to nodeId,
                    "spanOperation" to "node:$nodeId"
                )
            )
        )
    }

    override suspend fun afterNode(
        graph: Graph,
        nodeId: String,
        input: SpiceMessage,
        output: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val traceId = input.getMetadata<String>("traceId") ?: return SpiceResult.success(output)
        val spanId = input.getMetadata<String>("spanId") ?: return SpiceResult.success(output)
        val parentSpanId = input.getMetadata<String>("parentSpanId")

        // Calculate duration
        val startTime = getSpanTimes()[spanId]
        val duration = if (startTime != null) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }

        logger.debug(
            "Completed node span: traceId=$traceId, nodeId=$nodeId, spanId=$spanId, duration=${duration}ms"
        )

        // Send to Jaeger if configured
        sendToJaeger?.invoke(traceId, spanId, "node:$nodeId", duration)

        // Clean up span time
        getSpanTimes().remove(spanId)

        // Restore parent span ID
        return SpiceResult.success(
            output.withMetadata(
                mapOf(
                    "spanId" to (parentSpanId ?: spanId),
                    "lastNodeDuration" to duration
                )
            )
        )
    }

    override suspend fun afterExecution(
        graph: Graph,
        input: SpiceMessage,
        output: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val traceId = input.getMetadata<String>("traceId") ?: return SpiceResult.success(output)
        val spanId = input.getMetadata<String>("spanId") ?: return SpiceResult.success(output)

        // Calculate total graph duration
        val startTime = getSpanTimes()[spanId]
        val duration = if (startTime != null) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }

        logger.info(
            "Completed graph execution: traceId=$traceId, graphId=${graph.id}, duration=${duration}ms, state=${output.state}"
        )

        // Send final span to Jaeger
        sendToJaeger?.invoke(traceId, spanId, "graph:${graph.id}", duration)

        // Clean up
        getSpanTimes().remove(spanId)

        return SpiceResult.success(
            output.withMetadata(
                mapOf(
                    "totalDuration" to duration,
                    "traceCompleted" to true
                )
            )
        )
    }

    private fun getSpanTimes(): MutableMap<String, Long> {
        var map = spanStartTimes.get()
        if (map == null) {
            map = mutableMapOf()
            spanStartTimes.set(map)
        }
        return map
    }

    companion object {
        /**
         * Creates a TracingTransformer with console logging only (no external tracing).
         */
        fun consoleOnly(): TracingTransformer {
            return TracingTransformer()
        }

        /**
         * Creates a TracingTransformer that logs traces to SLF4J.
         */
        fun withLogging(): TracingTransformer {
            val logger = LoggerFactory.getLogger("SpiceTracing")
            return TracingTransformer { traceId, spanId, operation, duration ->
                logger.info("Trace: traceId=$traceId, spanId=$spanId, operation=$operation, duration=${duration}ms")
            }
        }
    }
}
