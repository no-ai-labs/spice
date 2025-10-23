package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement

/**
 * 🎯 Agent 실행 컨텍스트
 *
 * Agent가 실행되는 환경과 필요한 리소스를 제공.
 * CoroutineContext Element로 구현되어 코루틴 간 자동 전파됨.
 *
 * **Immutable**: 모든 수정 작업은 새로운 AgentContext 인스턴스를 반환
 *
 * @since 0.4.0 - AbstractCoroutineContextElement로 전환 (Breaking Change)
 */
data class AgentContext(
    private val data: Map<String, Any> = emptyMap()
) : AbstractCoroutineContextElement(AgentContext) {

    companion object Key : CoroutineContext.Key<AgentContext> {
        /**
         * 빈 컨텍스트 생성
         */
        fun empty() = AgentContext()

        /**
         * 초기 데이터로 생성
         */
        fun of(vararg pairs: Pair<String, Any>) = AgentContext(mapOf(*pairs))

        /**
         * Map으로부터 생성
         */
        fun from(map: Map<String, Any>) = AgentContext(map)
    }

    /**
     * 값 조회
     */
    operator fun get(key: String): Any? = data[key]

    /**
     * 타입 안전한 값 조회
     */
    fun <T> getAs(key: String): T? = data[key] as? T

    /**
     * 값 존재 여부 확인
     */
    fun has(key: String): Boolean = data.containsKey(key)

    /**
     * 모든 키 조회
     */
    fun keys(): Set<String> = data.keys

    /**
     * 빌더 스타일로 값 추가 (새 인스턴스 반환)
     */
    fun with(key: String, value: Any): AgentContext {
        return AgentContext(data + (key to value))
    }

    /**
     * 여러 값을 한번에 추가 (새 인스턴스 반환)
     */
    fun withAll(vararg pairs: Pair<String, Any>): AgentContext {
        return AgentContext(data + pairs)
    }

    /**
     * Map으로 변환
     */
    fun toMap(): Map<String, Any> = data

    // =========================================
    // Type-safe Accessors for Common Keys
    // =========================================

    /**
     * Tenant ID (멀티테넌트 환경에서 사용)
     */
    val tenantId: String?
        get() = get(ContextKeys.TENANT_ID)?.toString()

    /**
     * User ID
     */
    val userId: String?
        get() = get(ContextKeys.USER_ID)?.toString()

    /**
     * Session ID
     */
    val sessionId: String?
        get() = get(ContextKeys.SESSION_ID)?.toString()

    /**
     * Correlation ID (분산 추적용)
     */
    val correlationId: String?
        get() = get(ContextKeys.CORRELATION_ID)?.toString()

    /**
     * Request ID
     */
    val requestId: String?
        get() = get(ContextKeys.REQUEST_ID)?.toString()

    /**
     * Trace ID (OpenTelemetry 등)
     */
    val traceId: String?
        get() = get(ContextKeys.TRACE_ID)?.toString()

    /**
     * Locale (언어/지역 설정)
     */
    val locale: String?
        get() = get(ContextKeys.LOCALE)?.toString()

    /**
     * Timezone
     */
    val timezone: String?
        get() = get(ContextKeys.TIMEZONE)?.toString()

    override fun toString(): String = "AgentContext($data)"
}

/**
 * 일반적으로 사용되는 컨텍스트 키들 (선택적 사용)
 */
object ContextKeys {
    const val USER_ID = "userId"
    const val SESSION_ID = "sessionId"
    const val TENANT_ID = "tenantId"
    const val LOCALE = "locale"
    const val TIMEZONE = "timezone"
    const val CORRELATION_ID = "correlationId"
    const val REQUEST_ID = "requestId"
    const val TRACE_ID = "traceId"
    const val FEATURES = "features"
    const val PERMISSIONS = "permissions"
    const val METADATA = "metadata"
}

/**
 * 🏃 Agent 실행 환경
 */
interface AgentRuntime {
    val context: AgentContext
    val scope: CoroutineScope
    
    /**
     * 다른 Agent 호출
     */
    suspend fun callAgent(agentId: String, comm: Comm): SpiceResult<Comm>
    
    /**
     * 이벤트 발행
     */
    suspend fun publishEvent(event: AgentEvent)
    
    /**
     * 상태 저장
     */
    suspend fun saveState(key: String, value: Any)
    
    /**
     * 상태 조회
     */
    suspend fun getState(key: String): Any?
    
    /**
     * 로깅
     */
    fun log(level: LogLevel, message: String, data: Map<String, Any> = emptyMap())
}

/**
 * 로그 레벨
 */
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Agent 이벤트
 */
data class AgentEvent(
    val type: String,
    val agentId: String,
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 🎛️ Agent 설정
 */
data class AgentConfig(
    val maxConcurrentRequests: Int = 10,
    val requestTimeoutMs: Long = 30_000,
    val retryPolicy: RetryPolicy = RetryPolicy.default(),
    val rateLimiting: RateLimiting? = null,
    val monitoring: MonitoringConfig = MonitoringConfig()
)

/**
 * 재시도 정책
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val backoffMultiplier: Double = 2.0
) {
    companion object {
        fun default() = RetryPolicy()
        fun noRetry() = RetryPolicy(maxRetries = 0)
    }
}

/**
 * Rate Limiting 설정
 */
data class RateLimiting(
    val maxRequestsPerMinute: Int,
    val maxBurstSize: Int = maxRequestsPerMinute
)

/**
 * 모니터링 설정
 */
data class MonitoringConfig(
    val enableMetrics: Boolean = true,
    val enableTracing: Boolean = true,
    val enableLogging: Boolean = true,
    val logSlowRequests: Boolean = true,
    val slowRequestThresholdMs: Long = 5000
)

/**
 * 🏭 기본 AgentRuntime 구현
 */
open class DefaultAgentRuntime(
    override val context: AgentContext,
    private val orchestrator: AgentOrchestrator? = null
) : AgentRuntime {
    
    private val executorService = Executors.newFixedThreadPool(10)
    override val scope = CoroutineScope(
        SupervisorJob() + executorService.asCoroutineDispatcher()
    )
    
    private val stateStore = mutableMapOf<String, Any>()
    
    override suspend fun callAgent(agentId: String, comm: Comm): SpiceResult<Comm> {
        return if (orchestrator != null) {
            orchestrator.routeComm(comm.copy(to = agentId))
        } else {
            SpiceResult.success(comm.error("No orchestrator available", "runtime"))
        }
    }
    
    override suspend fun publishEvent(event: AgentEvent) {
        log(LogLevel.DEBUG, "Event published: ${event.type}", event.data)
    }
    
    override suspend fun saveState(key: String, value: Any) {
        stateStore[key] = value
    }
    
    override suspend fun getState(key: String): Any? {
        return stateStore[key]
    }
    
    override fun log(level: LogLevel, message: String, data: Map<String, Any>) {
        println("[${level.name}] $message ${if (data.isNotEmpty()) "- $data" else ""}")
    }
    
    fun shutdown() {
        executorService.shutdown()
    }
}

/**
 * Agent Orchestrator 인터페이스 (순환 참조 방지)
 */
interface AgentOrchestrator {
    suspend fun routeComm(comm: Comm): SpiceResult<Comm>
}