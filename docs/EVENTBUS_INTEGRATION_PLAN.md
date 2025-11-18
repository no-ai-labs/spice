# EventBus Integration Plan (1.0.0-alpha-5)

## 목적

Week 1에서 구현한 새로운 EventBus 아키텍처를 기존 Spice Framework 코드와 통합하는 계획을 정의합니다.

## 현재 상태 (Week 1 완료)

### ✅ 완료된 컴포넌트

1. **Core EventBus Interfaces**
   - `EventBus` - 통합 이벤트 버스 인터페이스 (SchemaRegistry 강제)
   - `EventChannel<T>` - 타입 안전 채널 (version 필수)
   - `EventEnvelope` - 스키마 버전닝 래퍼
   - `TypedEvent<T>` - 타입 안전 이벤트 래퍼

2. **Schema Management**
   - `SchemaRegistry` - 스키마 버전 관리
   - `DefaultSchemaRegistry` - ConcurrentHashMap 기반 구현
   - Semantic versioning (MAJOR.MINOR.PATCH)

3. **Standard Events**
   - `GraphLifecycleEvent` (Started, Completed, Failed, Paused)
   - `NodeLifecycleEvent` (Started, Completed, Failed)
   - `SystemEvent` (Error, Warning, Info)
   - `StandardChannels` (GRAPH_LIFECYCLE, NODE_LIFECYCLE, TOOL_CALLS, SYSTEM_EVENTS)

4. **Dead Letter Queue**
   - `InMemoryDeadLetterQueue` - per-channel partitioning, eviction hooks
   - `RedisDeadLetterQueue` - Lua script atomic trimming

5. **Observability**
   - `EventBusStats` - 통합 메트릭
   - `EventFilter` - 타입 안전 필터링

### ❌ 아직 구현 안 된 컴포넌트

1. **EventBus Implementations** (Week 2)
   - `InMemoryEventBus`
   - `RedisStreamsEventBus`
   - `KafkaEventBus`

2. **기존 코드 통합** (Week 3)
   - GraphRunner 이벤트 발행
   - ToolCall 이벤트 마이그레이션

---

## 통합 전략

### Phase 1: EventBus 구현 (Week 2)

#### 1.1 InMemoryEventBus

**구현 요구사항:**
- SchemaRegistry를 constructor로 받아서 `channel()` 호출 시 스키마 검증
- 채널 생성 시 `schemaRegistry.isRegistered(type, version)` 확인 → 없으면 `IllegalStateException`
- `publish()` 시 SchemaRegistry의 serializer를 사용해 자동 직렬화
- `subscribe()` 시 SchemaRegistry의 serializer를 사용해 자동 역직렬화

**예시 코드:**
```kotlin
class InMemoryEventBus(
    override val schemaRegistry: SchemaRegistry,
    private val deadLetterQueue: DeadLetterQueue = InMemoryDeadLetterQueue()
) : EventBus {

    private val channels = ConcurrentHashMap<String, MutableSharedFlow<EventEnvelope>>()

    override fun <T : Any> channel(
        name: String,
        type: KClass<T>,
        version: String,
        config: ChannelConfig
    ): EventChannel<T> {
        // 1. Validate schema is registered
        require(schemaRegistry.isRegistered(type, version)) {
            "Schema not registered: ${type.qualifiedName}:$version. " +
            "Register it first: schemaRegistry.register(${type.simpleName}::class, \"$version\", serializer)"
        }

        // 2. Get or create channel flow
        channels.getOrPut(name) {
            MutableSharedFlow(
                replay = if (config.enableHistory) config.historySize else 0,
                extraBufferCapacity = 1000
            )
        }

        return EventChannel(name, type, version, config)
    }

    override suspend fun <T : Any> publish(
        channel: EventChannel<T>,
        event: T,
        metadata: EventMetadata
    ): SpiceResult<String> {
        return try {
            // 1. Get serializer from registry (ENFORCED!)
            val serializer = schemaRegistry.getSerializer(channel.type, channel.version)
                ?: return SpiceResult.failure(
                    SpiceError.validationError("No serializer found for ${channel.schemaKey}")
                )

            // 2. Serialize event
            val payload = Json.encodeToString(serializer, event)

            // 3. Create envelope
            val envelope = EventEnvelope(
                channelName = channel.name,
                eventType = channel.type.qualifiedName!!,
                schemaVersion = channel.version,
                payload = payload,
                metadata = metadata
            )

            // 4. Publish to flow
            val flow = channels[channel.name]
                ?: return SpiceResult.failure(
                    SpiceError.validationError("Channel not found: ${channel.name}")
                )

            flow.emit(envelope)

            SpiceResult.success(envelope.id)
        } catch (e: Exception) {
            SpiceResult.failure(SpiceError.fromException(e))
        }
    }

    override fun <T : Any> subscribe(
        channel: EventChannel<T>,
        filter: EventFilter<T>
    ): Flow<TypedEvent<T>> {
        val flow = channels[channel.name] ?: return emptyFlow()

        return flow
            .mapNotNull { envelope ->
                try {
                    // 1. Get serializer
                    val serializer = schemaRegistry.getSerializer(channel.type, channel.version)
                        ?: throw IllegalStateException("No serializer for ${channel.schemaKey}")

                    // 2. Deserialize
                    val event = Json.decodeFromString(serializer, envelope.payload)

                    // 3. Wrap in TypedEvent
                    TypedEvent(
                        id = envelope.id,
                        event = event,
                        envelope = envelope,
                        receivedAt = Clock.System.now()
                    )
                } catch (e: Exception) {
                    // Send to DLQ
                    runBlocking {
                        deadLetterQueue.send(
                            envelope,
                            "Deserialization failed: ${e.message}",
                            e
                        )
                    }
                    null // Filter out failed events
                }
            }
            .filter { filter.matches(it) }
    }
}
```

#### 1.2 RedisStreamsEventBus

**Redis Streams 사용 이유:**
- Consumer groups for distributed processing
- Persistent history with configurable retention
- Built-in message ID ordering

**구현 요구사항:**
- Consumer group 자동 생성: `XGROUP CREATE channel-name consumer-group-id`
- `XADD` for publish, `XREADGROUP` for subscribe
- Dead letter queue for failed deserialization

#### 1.3 KafkaEventBus

**Kafka 사용 이유:**
- Production-grade message streaming
- High throughput, fault-tolerant
- Best for multi-datacenter deployments

**구현 요구사항:**
- Topic per channel
- Partitioning by correlationId for ordering
- Schema Registry integration

---

### Phase 2: 기존 코드 통합 (Week 3)

#### 2.1 GraphRunner 이벤트 발행

**위치:** `spice-core/src/main/kotlin/io/github/noailabs/spice/graph/DefaultGraphRunner.kt`

**변경사항:**

```kotlin
class DefaultGraphRunner(
    private val eventBus: EventBus  // NEW: Inject EventBus
) : GraphRunner {

    suspend fun execute(
        graph: Graph,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val runId = UUID.randomUUID().toString()
        val graphId = graph.id

        // ✅ Emit Started event
        eventBus.publish(
            StandardChannels.GRAPH_LIFECYCLE,
            GraphLifecycleEvent.Started(
                graphId = graphId,
                runId = runId,
                message = message,
                metadata = mapOf("source" to "graph-runner")
            ),
            EventMetadata(source = "graph-runner")
        )

        val startTime = Clock.System.now()

        try {
            // ... existing execution logic ...

            val result = runGraph(graph, message)

            // ✅ Emit Completed event
            eventBus.publish(
                StandardChannels.GRAPH_LIFECYCLE,
                GraphLifecycleEvent.Completed(
                    graphId = graphId,
                    runId = runId,
                    result = result,
                    durationMs = (Clock.System.now() - startTime).inWholeMilliseconds,
                    nodesExecuted = executedNodes.size
                ),
                EventMetadata(source = "graph-runner")
            )

            return SpiceResult.success(result)
        } catch (e: Exception) {
            // ✅ Emit Failed event
            eventBus.publish(
                StandardChannels.GRAPH_LIFECYCLE,
                GraphLifecycleEvent.Failed(
                    graphId = graphId,
                    runId = runId,
                    error = SpiceError.fromException(e),
                    durationMs = (Clock.System.now() - startTime).inWholeMilliseconds,
                    failedNodeId = currentNodeId
                ),
                EventMetadata(source = "graph-runner")
            )

            return SpiceResult.failure(SpiceError.fromException(e))
        }
    }

    // ✅ Node execution도 이벤트 발행
    private suspend fun executeNode(
        node: Node,
        message: SpiceMessage,
        graphId: String,
        runId: String
    ): SpiceResult<SpiceMessage> {
        eventBus.publish(
            StandardChannels.NODE_LIFECYCLE,
            NodeLifecycleEvent.Started(
                graphId = graphId,
                nodeId = node.id,
                runId = runId,
                message = message,
                nodeType = node::class.simpleName ?: "Unknown"
            )
        )

        val startTime = Clock.System.now()
        val result = node.run(message)

        result.onSuccess { resultMessage ->
            eventBus.publish(
                StandardChannels.NODE_LIFECYCLE,
                NodeLifecycleEvent.Completed(
                    graphId = graphId,
                    nodeId = node.id,
                    runId = runId,
                    result = resultMessage,
                    durationMs = (Clock.System.now() - startTime).inWholeMilliseconds,
                    nodeType = node::class.simpleName ?: "Unknown"
                )
            )
        }.onFailure { error ->
            eventBus.publish(
                StandardChannels.NODE_LIFECYCLE,
                NodeLifecycleEvent.Failed(
                    graphId = graphId,
                    nodeId = node.id,
                    runId = runId,
                    error = error,
                    durationMs = (Clock.System.now() - startTime).inWholeMilliseconds,
                    nodeType = node::class.simpleName ?: "Unknown"
                )
            )
        }

        return result
    }
}
```

#### 2.2 ToolCall 이벤트 마이그레이션

**현재 상태:**
- `spice-core/src/main/kotlin/io/github/noailabs/spice/event/ToolCallEventBus.kt` (OLD)
- `spice-core/src/main/kotlin/io/github/noailabs/spice/event/ToolCallEvent.kt` (KEEP)

**변경사항:**

1. **ToolCallEventBus 제거** - `StandardChannels.TOOL_CALLS` 사용
2. **ToolCallEvent 유지** - 이미 잘 설계됨
3. **발행 코드 업데이트:**

```kotlin
// ❌ OLD
toolCallEventBus.emit(ToolCallEvent.Emitted(...))

// ✅ NEW
eventBus.publish(
    StandardChannels.TOOL_CALLS,
    ToolCallEvent.Emitted(...),
    EventMetadata(source = "tool-executor")
)
```

#### 2.3 Spring Boot Auto-Configuration

**위치:** `spice-springboot/src/main/kotlin/io/github/noailabs/spice/springboot/config/SpiceAutoConfiguration.kt`

**변경사항:**

```kotlin
@Configuration
@EnableConfigurationProperties(SpiceFrameworkProperties::class)
class SpiceAutoConfiguration(
    private val properties: SpiceFrameworkProperties
) {

    @Bean
    fun schemaRegistry(): SchemaRegistry {
        return DefaultSchemaRegistry().apply {
            // Register standard event schemas
            register(
                GraphLifecycleEvent::class,
                "1.0.0",
                GraphLifecycleEvent.serializer()
            )
            register(
                NodeLifecycleEvent::class,
                "1.0.0",
                NodeLifecycleEvent.serializer()
            )
            register(
                ToolCallEvent::class,
                "1.0.0",
                ToolCallEvent.serializer()
            )
            register(
                SystemEvent::class,
                "1.0.0",
                SystemEvent.serializer()
            )
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun eventBus(
        schemaRegistry: SchemaRegistry
    ): EventBus {
        return when (properties.eventBus.type) {
            EventBusType.IN_MEMORY -> InMemoryEventBus(schemaRegistry)
            EventBusType.REDIS -> {
                val jedisPool = JedisPool(properties.eventBus.redis.host)
                RedisStreamsEventBus(schemaRegistry, jedisPool)
            }
            EventBusType.KAFKA -> {
                val producerProps = Properties().apply {
                    put("bootstrap.servers", properties.eventBus.kafka.bootstrapServers)
                }
                KafkaEventBus(schemaRegistry, producerProps)
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun deadLetterQueue(): DeadLetterQueue {
        return when (properties.eventBus.dlq.type) {
            DLQType.IN_MEMORY -> InMemoryDeadLetterQueue(
                maxSize = properties.eventBus.dlq.maxSize,
                maxSizePerChannel = properties.eventBus.dlq.maxSizePerChannel
            )
            DLQType.REDIS -> {
                val jedisPool = JedisPool(properties.eventBus.redis.host)
                RedisDeadLetterQueue(
                    jedisPool = jedisPool,
                    maxSize = properties.eventBus.dlq.maxSize,
                    maxSizePerChannel = properties.eventBus.dlq.maxSizePerChannel,
                    ttl = properties.eventBus.dlq.ttl
                )
            }
        }
    }

    @Bean
    fun graphRunner(
        eventBus: EventBus  // ✅ Inject EventBus
    ): GraphRunner {
        return DefaultGraphRunner(eventBus)
    }
}
```

**application.yml 설정:**

```yaml
spice:
  event-bus:
    type: redis  # in_memory, redis, kafka
    redis:
      host: localhost
      port: 6379
    kafka:
      bootstrap-servers: localhost:9092
    dlq:
      type: redis  # in_memory, redis
      max-size: 10000
      max-size-per-channel: 1000
      ttl: 7d
```

---

## 삭제 대상 파일 (Clean Break)

Week 3에서 아래 파일들을 삭제합니다:

```bash
# Old EventBus (legacy)
spice-core/src/main/kotlin/io/github/noailabs/spice/eventbus/EventBus.kt.old
spice-core/src/main/kotlin/io/github/noailabs/spice/eventbus/InMemoryEventBus.kt.old
spice-core/src/main/kotlin/io/github/noailabs/spice/eventbus/RedisEventBus.kt.old
spice-core/src/main/kotlin/io/github/noailabs/spice/eventbus/KafkaEventBus.kt.old

# Old ToolCallEventBus (to be replaced)
spice-core/src/main/kotlin/io/github/noailabs/spice/event/ToolCallEventBus.kt
spice-core/src/main/kotlin/io/github/noailabs/spice/event/InMemoryToolCallEventBus.kt
spice-core/src/main/kotlin/io/github/noailabs/spice/event/RedisToolCallEventBus.kt
spice-core/src/main/kotlin/io/github/noailabs/spice/event/KafkaToolCallEventBus.kt
```

---

## 테스트 전략 (Week 3 Day 3)

### Unit Tests

1. **DefaultSchemaRegistry**
   - Version matching (same major = compatible)
   - Schema registration/lookup
   - Migration logic

2. **EventFilter**
   - Combinator logic (And, Or, Not)
   - Predicate filtering
   - Metadata filtering

3. **InMemoryDeadLetterQueue**
   - Per-channel eviction
   - Global eviction
   - Metrics (totalEvicted)

4. **RedisDeadLetterQueue**
   - Lua script trimming
   - TTL behavior
   - Atomic operations

### Integration Tests

1. **InMemoryEventBus + SchemaRegistry**
   - Schema enforcement
   - Automatic serialization/deserialization
   - Dead letter queue on schema mismatch

2. **RedisStreamsEventBus**
   - Consumer groups
   - Message ordering
   - Failover behavior

3. **GraphRunner + EventBus**
   - Lifecycle events published correctly
   - Event metadata propagation
   - Error events on failures

---

## 마일스톤

- **Week 2 (현재)**: EventBus 구현 (InMemory, Redis, Kafka)
- **Week 3 Day 1-2**: GraphRunner, Spring Boot 통합
- **Week 3 Day 3**: 테스트 작성 (85% coverage)
- **Week 3 Day 4**: 문서 및 마이그레이션 가이드
- **Week 3 Day 5**: 성능 테스트 및 최종 검증

---

## 성공 기준

1. ✅ SchemaRegistry가 코드 레벨에서 강제됨 (문서만이 아님)
2. ✅ Redis DLQ trimming이 Lua script로 atomic하게 처리됨
3. ✅ 기존 GraphRunner가 새 EventBus로 이벤트 발행
4. ✅ ToolCallEvent가 StandardChannels.TOOL_CALLS로 마이그레이션
5. ✅ Spring Boot Auto-Configuration으로 설정 간소화
6. ✅ 85% 테스트 커버리지 달성
7. ✅ 성능 테스트 통과 (Redis DLQ trimming 병목 없음)

---

**작성일**: 2025-01-16
**버전**: 1.0.0-alpha-5
**작성자**: Spice Framework Team
