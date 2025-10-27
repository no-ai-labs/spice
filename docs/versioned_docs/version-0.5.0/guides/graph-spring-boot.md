---
sidebar_position: 2
---

# Spring Boot Integration Guide

Production-ready guide for integrating Spice Graph System with Spring Boot.

## Project Setup

### Dependencies

**build.gradle.kts:**

```kotlin
dependencies {
    // Spice
    implementation("io.github.noailabs:spice-core:0.5.0")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Jackson for serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
```

## Project Structure

```
src/main/kotlin/com/example/app/
├── config/
│   ├── GraphConfig.kt              # Graph bean definitions
│   ├── CheckpointConfig.kt         # Checkpoint store setup
│   └── MiddlewareConfig.kt         # Custom middleware
├── graph/
│   ├── nodes/
│   │   ├── CustomValidationNode.kt
│   │   └── CustomTransformNode.kt
│   ├── middleware/
│   │   ├── RequestLoggingMiddleware.kt
│   │   └── MetricsMiddleware.kt
│   └── store/
│       └── RedisCheckpointStore.kt
├── agents/
│   ├── IntentAnalysisAgent.kt
│   ├── ResponseGeneratorAgent.kt
│   └── HandoffCoordinatorAgent.kt
├── service/
│   ├── WorkflowService.kt          # Main service
│   └── SessionService.kt           # Session management
├── controller/
│   ├── WorkflowController.kt       # REST endpoints
│   └── WebSocketController.kt      # WebSocket for HITL
└── model/
    ├── WorkflowRequest.kt
    ├── WorkflowResponse.kt
    └── HumanInteractionDTO.kt
```

## Configuration

### 1. Graph Configuration

```kotlin
package com.example.app.config

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.middleware.LoggingMiddleware
import io.github.noailabs.spice.graph.middleware.MetricsMiddleware
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class GraphConfig {

    @Bean("customerSupportGraph")
    fun customerSupportGraph(
        @Qualifier("intentAgent") intentAgent: Agent,
        @Qualifier("responseAgent") responseAgent: Agent,
        loggingMiddleware: LoggingMiddleware,
        metricsMiddleware: MetricsMiddleware
    ): Graph {
        return graph("customer-support") {
            // Middleware stack
            middleware(loggingMiddleware)
            middleware(metricsMiddleware)

            // Workflow steps
            agent("intent-analyzer", intentAgent)
            agent("response-generator", responseAgent)

            // Conditional routing
            edge("intent-analyzer", "response-generator") { result ->
                // Auto-handle if confidence is high
                val intent = result.data as? Intent
                intent?.confidence ?: 0.0 > 0.8
            }

            edge("intent-analyzer", "human-handoff") { result ->
                // Manual handling if confidence is low
                val intent = result.data as? Intent
                intent?.confidence ?: 0.0 <= 0.8
            }

            // Human-in-the-loop
            humanNode(
                id = "human-handoff",
                prompt = "Please review and provide response",
                options = listOf(
                    HumanOption("approve", "Approve AI Response"),
                    HumanOption("custom", "Provide Custom Response")
                ),
                timeout = Duration.ofHours(4),
                validator = { response ->
                    response.selectedOption in listOf("approve", "custom")
                }
            )

            edge("human-handoff", "response-generator") { result ->
                // Continue to response generation after human input
                true
            }

            output("final-response") { ctx ->
                ctx.state["response-generator"]
            }
        }
    }

    @Bean("approvalWorkflowGraph")
    fun approvalWorkflowGraph(
        @Qualifier("draftAgent") draftAgent: Agent,
        @Qualifier("publishAgent") publishAgent: Agent,
        loggingMiddleware: LoggingMiddleware
    ): Graph {
        return graph("approval-workflow") {
            middleware(loggingMiddleware)

            agent("draft-creator", draftAgent)

            humanNode(
                id = "approval-review",
                prompt = "Please review the draft",
                options = listOf(
                    HumanOption("approve", "Approve"),
                    HumanOption("reject", "Reject"),
                    HumanOption("revise", "Request Revision")
                ),
                timeout = Duration.ofDays(1)
            )

            agent("publisher", publishAgent)
            agent("revisor", revisionAgent)

            edge("draft-creator", "approval-review")

            edge("approval-review", "publisher") { result ->
                (result.data as? HumanResponse)?.selectedOption == "approve"
            }

            edge("approval-review", "revisor") { result ->
                (result.data as? HumanResponse)?.selectedOption == "revise"
            }

            edge("revisor", "draft-creator")  // Loop back for revision

            output("result") { ctx ->
                when {
                    ctx.state.containsKey("publisher") -> ctx.state["publisher"]
                    else -> null
                }
            }
        }
    }

    @Bean
    fun graphRunner(): DefaultGraphRunner {
        return DefaultGraphRunner()
    }
}
```

### 2. Checkpoint Store Configuration

#### In-Memory (Development)

```kotlin
package com.example.app.config

import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("dev")
class DevCheckpointConfig {

    @Bean
    fun checkpointStore(): CheckpointStore {
        return InMemoryCheckpointStore()
    }
}
```

#### Redis (Production)

```kotlin
package com.example.app.config

import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import com.example.app.graph.store.RedisCheckpointStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.RedisTemplate

@Configuration
@Profile("prod")
class ProdCheckpointConfig {

    @Bean
    fun checkpointStore(
        redisTemplate: RedisTemplate<String, String>
    ): CheckpointStore {
        return RedisCheckpointStore(redisTemplate)
    }
}
```

**Redis Checkpoint Store Implementation:**

```kotlin
package com.example.app.graph.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedisCheckpointStore(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : CheckpointStore {

    private val keyPrefix = "spice:checkpoint:"
    private val ttl = 7L // 7 days

    override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> {
        return SpiceResult.catching {
            val key = "$keyPrefix${checkpoint.id}"
            val json = objectMapper.writeValueAsString(checkpoint)
            redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.DAYS)

            // Add to run index
            val runKey = "${keyPrefix}run:${checkpoint.runId}"
            redisTemplate.opsForSet().add(runKey, checkpoint.id)
            redisTemplate.expire(runKey, ttl, TimeUnit.DAYS)

            // Add to graph index
            val graphKey = "${keyPrefix}graph:${checkpoint.graphId}"
            redisTemplate.opsForSet().add(graphKey, checkpoint.id)
            redisTemplate.expire(graphKey, ttl, TimeUnit.DAYS)

            checkpoint.id
        }
    }

    override suspend fun load(checkpointId: String): SpiceResult<Checkpoint> {
        return SpiceResult.catching {
            val key = "$keyPrefix$checkpointId"
            val json = redisTemplate.opsForValue().get(key)
                ?: throw NoSuchElementException("Checkpoint not found: $checkpointId")

            objectMapper.readValue<Checkpoint>(json)
        }
    }

    override suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>> {
        return SpiceResult.catching {
            val runKey = "${keyPrefix}run:$runId"
            val checkpointIds = redisTemplate.opsForSet().members(runKey) ?: emptySet()

            checkpointIds.mapNotNull { id ->
                load(id).getOrNull()
            }.sortedByDescending { it.timestamp }
        }
    }

    override suspend fun listByGraph(graphId: String): SpiceResult<List<Checkpoint>> {
        return SpiceResult.catching {
            val graphKey = "${keyPrefix}graph:$graphId"
            val checkpointIds = redisTemplate.opsForSet().members(graphKey) ?: emptySet()

            checkpointIds.mapNotNull { id ->
                load(id).getOrNull()
            }.sortedByDescending { it.timestamp }
        }
    }

    override suspend fun delete(checkpointId: String): SpiceResult<Unit> {
        return SpiceResult.catching {
            val key = "$keyPrefix$checkpointId"
            redisTemplate.delete(key)
            Unit
        }
    }

    override suspend fun deleteByRun(runId: String): SpiceResult<Unit> {
        return SpiceResult.catching {
            val runKey = "${keyPrefix}run:$runId"
            val checkpointIds = redisTemplate.opsForSet().members(runKey) ?: emptySet()

            checkpointIds.forEach { id ->
                delete(id)
            }

            redisTemplate.delete(runKey)
            Unit
        }
    }
}
```

### 3. Custom Middleware

```kotlin
package com.example.app.graph.middleware

import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.middleware.RunContext
import io.github.noailabs.spice.graph.middleware.NodeRequest
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.error.SpiceResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RequestLoggingMiddleware : Middleware {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        logger.info(
            "Starting graph execution: graphId={}, runId={}, tenant={}",
            ctx.graphId,
            ctx.runId,
            ctx.agentContext?.tenantId
        )
        next()
    }

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val startTime = System.currentTimeMillis()

        logger.debug(
            "Executing node: nodeId={}, runId={}",
            req.nodeId,
            req.context.runId
        )

        val result = next(req)

        val duration = System.currentTimeMillis() - startTime
        when (result) {
            is SpiceResult.Success -> {
                logger.debug(
                    "Node succeeded: nodeId={}, duration={}ms",
                    req.nodeId,
                    duration
                )
            }
            is SpiceResult.Failure -> {
                logger.error(
                    "Node failed: nodeId={}, duration={}ms, error={}",
                    req.nodeId,
                    duration,
                    result.error.message
                )
            }
        }

        return result
    }

    override suspend fun onFinish(report: io.github.noailabs.spice.graph.runner.RunReport) {
        logger.info(
            "Graph execution finished: graphId={}, status={}, duration={}ms, nodes={}",
            report.graphId,
            report.status,
            report.duration.toMillis(),
            report.nodeReports.size
        )
    }
}
```

## Service Layer

### Workflow Service

```kotlin
package com.example.app.service

import com.example.app.model.*
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.checkpoint.CheckpointConfig
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.nodes.HumanInteraction
import io.github.noailabs.spice.graph.nodes.HumanResponse
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class WorkflowService(
    @Qualifier("customerSupportGraph") private val supportGraph: Graph,
    private val graphRunner: DefaultGraphRunner,
    private val checkpointStore: CheckpointStore,
    private val sessionService: SessionService
) {

    suspend fun processCustomerRequest(
        request: WorkflowRequest
    ): WorkflowResponse {
        val input = mapOf(
            "input" to Comm(
                content = request.message,
                sender = "user",
                context = request.agentContext
            ),
            "sessionId" to request.sessionId,
            "userId" to request.userId
        )

        val report = graphRunner.runWithCheckpoint(
            graph = supportGraph,
            input = input,
            store = checkpointStore,
            config = CheckpointConfig(
                saveEveryNNodes = 3,
                saveOnError = true,
                maxCheckpointsPerRun = 20
            )
        ).getOrThrow()

        return when (report.status) {
            RunStatus.SUCCESS -> {
                val result = report.result as Comm
                WorkflowResponse(
                    status = "completed",
                    message = result.content,
                    processingTime = report.duration.toMillis()
                )
            }

            RunStatus.PAUSED -> {
                val interaction = report.result as HumanInteraction
                sessionService.saveCheckpoint(request.sessionId, report.checkpointId!!)

                WorkflowResponse(
                    status = "pending_human_input",
                    message = "Awaiting human review",
                    humanInteraction = HumanInteractionDTO(
                        nodeId = interaction.nodeId,
                        prompt = interaction.prompt,
                        options = interaction.options.map { option ->
                            OptionDTO(option.id, option.label, option.description)
                        },
                        expiresAt = interaction.expiresAt
                    ),
                    checkpointId = report.checkpointId
                )
            }

            RunStatus.FAILED -> {
                WorkflowResponse(
                    status = "failed",
                    message = "Workflow failed: ${report.error?.message}",
                    error = report.error?.message
                )
            }

            else -> {
                WorkflowResponse(
                    status = "unknown",
                    message = "Unexpected status: ${report.status}"
                )
            }
        }
    }

    suspend fun resumeWorkflow(
        checkpointId: String,
        humanResponse: HumanResponse
    ): WorkflowResponse {
        val report = graphRunner.resumeWithHumanResponse(
            graph = supportGraph,
            checkpointId = checkpointId,
            response = humanResponse,
            store = checkpointStore
        ).getOrThrow()

        return when (report.status) {
            RunStatus.SUCCESS -> {
                val result = report.result as Comm
                WorkflowResponse(
                    status = "completed",
                    message = result.content,
                    processingTime = report.duration.toMillis()
                )
            }
            else -> {
                WorkflowResponse(
                    status = "failed",
                    message = "Resume failed: ${report.error?.message}"
                )
            }
        }
    }

    suspend fun getPendingInteractions(
        checkpointId: String
    ): List<HumanInteraction> {
        return graphRunner.getPendingInteractions(
            checkpointId = checkpointId,
            store = checkpointStore
        ).getOrDefault(emptyList())
    }
}
```

### Session Service

```kotlin
package com.example.app.service

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class SessionService(
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val sessionKeyPrefix = "session:"
    private val ttl = 24L // 24 hours

    fun saveCheckpoint(sessionId: String, checkpointId: String) {
        val key = "$sessionKeyPrefix$sessionId:checkpoint"
        redisTemplate.opsForValue().set(key, checkpointId, ttl, TimeUnit.HOURS)
    }

    fun getCheckpoint(sessionId: String): String? {
        val key = "$sessionKeyPrefix$sessionId:checkpoint"
        return redisTemplate.opsForValue().get(key)
    }

    fun clearCheckpoint(sessionId: String) {
        val key = "$sessionKeyPrefix$sessionId:checkpoint"
        redisTemplate.delete(key)
    }
}
```

## REST Controller

```kotlin
package com.example.app.controller

import com.example.app.model.*
import com.example.app.service.WorkflowService
import com.example.app.service.SessionService
import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.graph.nodes.HumanResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/workflows")
class WorkflowController(
    private val workflowService: WorkflowService,
    private val sessionService: SessionService
) {

    @PostMapping("/support/execute")
    suspend fun executeSupport(
        @RequestBody request: SupportTicketRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-Tenant-Id", required = false) tenantId: String?
    ): ResponseEntity<WorkflowResponse> {
        val sessionId = UUID.randomUUID().toString()

        val workflowRequest = WorkflowRequest(
            sessionId = sessionId,
            userId = userId ?: "anonymous",
            message = request.message,
            agentContext = AgentContext.of(
                "userId" to (userId ?: "anonymous"),
                "tenantId" to (tenantId ?: "default"),
                "sessionId" to sessionId
            )
        )

        val response = workflowService.processCustomerRequest(workflowRequest)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/support/resume")
    suspend fun resumeSupport(
        @RequestBody request: HumanResponseRequest
    ): ResponseEntity<WorkflowResponse> {
        val humanResponse = HumanResponse(
            nodeId = request.nodeId,
            selectedOption = request.selectedOption,
            text = request.text,
            metadata = request.metadata
        )

        val response = workflowService.resumeWorkflow(
            checkpointId = request.checkpointId,
            humanResponse = humanResponse
        )

        // Clear checkpoint after successful resume
        if (response.status == "completed") {
            request.sessionId?.let { sessionService.clearCheckpoint(it) }
        }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/support/pending/{checkpointId}")
    suspend fun getPendingInteractions(
        @PathVariable checkpointId: String
    ): ResponseEntity<List<HumanInteractionDTO>> {
        val interactions = workflowService.getPendingInteractions(checkpointId)

        val dtos = interactions.map { interaction ->
            HumanInteractionDTO(
                nodeId = interaction.nodeId,
                prompt = interaction.prompt,
                options = interaction.options.map { option ->
                    OptionDTO(option.id, option.label, option.description)
                },
                expiresAt = interaction.expiresAt
            )
        }

        return ResponseEntity.ok(dtos)
    }
}
```

## DTOs / Models

```kotlin
package com.example.app.model

import io.github.noailabs.spice.AgentContext

data class WorkflowRequest(
    val sessionId: String,
    val userId: String,
    val message: String,
    val agentContext: AgentContext? = null
)

data class WorkflowResponse(
    val status: String,  // completed, pending_human_input, failed
    val message: String,
    val processingTime: Long? = null,
    val humanInteraction: HumanInteractionDTO? = null,
    val checkpointId: String? = null,
    val error: String? = null
)

data class SupportTicketRequest(
    val message: String,
    val category: String? = null,
    val priority: String? = null
)

data class HumanResponseRequest(
    val checkpointId: String,
    val nodeId: String,
    val selectedOption: String? = null,
    val text: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val sessionId: String? = null
)

data class HumanInteractionDTO(
    val nodeId: String,
    val prompt: String,
    val options: List<OptionDTO>,
    val expiresAt: String? = null
)

data class OptionDTO(
    val id: String,
    val label: String,
    val description: String? = null
)
```

## WebSocket for Real-Time HITL

```kotlin
package com.example.app.controller

import com.example.app.service.WorkflowService
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.noailabs.spice.graph.nodes.HumanResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class HumanInteractionWebSocketHandler(
    private val workflowService: WorkflowService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val checkpointId = extractCheckpointId(session)
        checkpointId?.let {
            sessions[it] = session
            // Send pending interactions
            sendPendingInteractions(it, session)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = objectMapper.readValue(message.payload, HumanResponsePayload::class.java)

        val humanResponse = HumanResponse(
            nodeId = payload.nodeId,
            selectedOption = payload.selectedOption,
            text = payload.text
        )

        // Resume workflow asynchronously
        kotlinx.coroutines.GlobalScope.launch {
            val result = workflowService.resumeWorkflow(
                checkpointId = payload.checkpointId,
                humanResponse = humanResponse
            )

            val response = objectMapper.writeValueAsString(result)
            session.sendMessage(TextMessage(response))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.values.remove(session)
    }

    private fun extractCheckpointId(session: WebSocketSession): String? {
        return session.uri?.query?.substringAfter("checkpointId=")
    }

    private fun sendPendingInteractions(checkpointId: String, session: WebSocketSession) {
        kotlinx.coroutines.GlobalScope.launch {
            val interactions = workflowService.getPendingInteractions(checkpointId)
            val json = objectMapper.writeValueAsString(interactions)
            session.sendMessage(TextMessage(json))
        }
    }
}

data class HumanResponsePayload(
    val checkpointId: String,
    val nodeId: String,
    val selectedOption: String? = null,
    val text: String? = null
)
```

## Testing

```kotlin
package com.example.app

import com.example.app.config.GraphConfig
import com.example.app.service.WorkflowService
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class WorkflowServiceTest {

    @Autowired
    lateinit var workflowService: WorkflowService

    @Test
    fun `test customer support workflow`() = runTest {
        val request = WorkflowRequest(
            sessionId = "test-session",
            userId = "test-user",
            message = "I need help with my account"
        )

        val response = workflowService.processCustomerRequest(request)

        assertEquals("completed", response.status)
    }
}
```

## Production Considerations

### 1. Error Handling

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                message = e.message ?: "Internal error",
                timestamp = System.currentTimeMillis()
            ))
    }
}

data class ErrorResponse(
    val message: String,
    val timestamp: Long
)
```

### 2. Metrics & Monitoring

```kotlin
@Component
class MetricsMiddleware(
    private val meterRegistry: MeterRegistry
) : Middleware {

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val timer = Timer.start(meterRegistry)
        val result = next(req)

        timer.stop(Timer.builder("graph.node.duration")
            .tag("nodeId", req.nodeId)
            .tag("status", if (result.isSuccess) "success" else "failure")
            .register(meterRegistry))

        return result
    }
}
```

### 3. Rate Limiting

```kotlin
@Component
class RateLimitingMiddleware(
    private val rateLimiter: RateLimiter
) : Middleware {

    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val tenantId = ctx.agentContext?.tenantId ?: "default"

        if (!rateLimiter.tryAcquire(tenantId)) {
            throw RateLimitExceededException("Rate limit exceeded for tenant: $tenantId")
        }

        next()
    }
}
```

## Next Steps

- **[Design Patterns](./graph-patterns.md)** - Best practices
- **[Troubleshooting](./troubleshooting.md)** - Common issues
- **[API Reference](../api/graph.md)** - Complete API docs

## Example Application

Check out the complete example application on GitHub: [spice-examples/spring-boot-graph](https://github.com/no-ai-labs/spice-examples)
