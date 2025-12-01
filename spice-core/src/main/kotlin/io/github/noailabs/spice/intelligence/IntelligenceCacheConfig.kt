package io.github.noailabs.spice.intelligence

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Intelligence Layer 캐시 설정 (v2)
 *
 * ## 2-Level Cache 아키텍처
 * ```
 * [Request] → [L1 Cache (Caffeine)] → [L2 Cache (Redis)] → [Compute]
 * ```
 *
 * ## 캐시 키 스키마
 * ```
 * {layer}:{tenantId}:{workflowId}:{locale}:{hash(normalizedUtterance)}
 * ```
 * - layer: "semantic" | "nano" | "big" | "policy"
 * - locale: "ko" | "en" | etc. (optional)
 *
 * ## Tenant/Workflow Override
 * ```kotlin
 * val config = IntelligenceCacheConfig.DEFAULT
 *     .withTenantOverride("premium-tenant", l1Ttl = 1.hours)
 *     .withWorkflowOverride("high-traffic-flow", l1Ttl = 2.hours, l1MaxSize = 2000)
 * ```
 *
 * Override 우선순위: `workflowOverride > tenantOverride > default`
 *
 * ## Thread-Safety & Hot Reload
 * - 이 data class는 **immutable**이므로 thread-safe
 * - copy()로 새 인스턴스 생성 시 기존 참조에 영향 없음
 * - **Hot Reload 미지원**: 설정 변경 시 애플리케이션 재시작 필요
 * - 런타임 TTL 변경 필요 시 외부 설정 소스 폴링 또는 캐시 무효화 API 사용
 *
 * @property enabled 캐시 활성화 여부
 * @property l1Enabled L1 (In-Memory) 캐시 활성화
 * @property l1Ttl L1 캐시 TTL
 * @property l1MaxSize L1 캐시 최대 엔트리 수
 * @property l2Enabled L2 (Redis) 캐시 활성화
 * @property l2Ttl L2 캐시 TTL
 * @property l2KeyPrefix L2 캐시 키 접두사
 * @property cacheNulls null 결과 캐싱 여부
 * @property nullTtl null 결과 캐시 TTL (짧게 설정 권장)
 * @property includeLocale 캐시 키에 locale 포함 여부
 * @property tenantOverrides 테넌트별 설정 오버라이드
 * @property workflowOverrides 워크플로우별 설정 오버라이드
 *
 * @since 2.0.0
 */
data class IntelligenceCacheConfig(
    // 전역 활성화
    val enabled: Boolean = true,

    // L1 (In-Memory: Caffeine)
    val l1Enabled: Boolean = true,
    val l1Ttl: Duration = 30.minutes,
    val l1MaxSize: Int = 1000,

    // L2 (Distributed: Redis)
    val l2Enabled: Boolean = true,
    val l2Ttl: Duration = 2.hours,
    val l2KeyPrefix: String = "spice:intel:",

    // Null 캐싱
    val cacheNulls: Boolean = true,
    val nullTtl: Duration = 5.minutes,

    // Clarify(AMBIGUOUS) 결과 캐싱 (동일 발화 반복 호출 방지)
    val cacheAmbiguous: Boolean = true,
    val ambiguousTtl: Duration = 2.minutes,  // 짧은 TTL

    // 캐시 키 옵션
    val includeLocale: Boolean = true,

    // 테넌트/워크플로우 오버라이드
    val tenantOverrides: Map<String, CacheOverride> = emptyMap(),
    val workflowOverrides: Map<String, CacheOverride> = emptyMap()
) {
    /**
     * 실제 적용될 설정 조회 (오버라이드 적용)
     *
     * 우선순위: workflowOverride > tenantOverride > default
     */
    fun resolveFor(tenantId: String?, workflowId: String?): ResolvedCacheConfig {
        val workflowOverride = workflowId?.let { workflowOverrides[it] }
        val tenantOverride = tenantId?.let { tenantOverrides[it] }

        return ResolvedCacheConfig(
            l1Ttl = workflowOverride?.l1Ttl ?: tenantOverride?.l1Ttl ?: l1Ttl,
            l1MaxSize = workflowOverride?.l1MaxSize ?: tenantOverride?.l1MaxSize ?: l1MaxSize,
            l2Ttl = workflowOverride?.l2Ttl ?: tenantOverride?.l2Ttl ?: l2Ttl,
            enabled = workflowOverride?.enabled ?: tenantOverride?.enabled ?: enabled
        )
    }

    /**
     * 테넌트 오버라이드 추가
     */
    fun withTenantOverride(
        tenantId: String,
        l1Ttl: Duration? = null,
        l1MaxSize: Int? = null,
        l2Ttl: Duration? = null,
        enabled: Boolean? = null
    ): IntelligenceCacheConfig = copy(
        tenantOverrides = tenantOverrides + (tenantId to CacheOverride(l1Ttl, l1MaxSize, l2Ttl, enabled))
    )

    /**
     * 워크플로우 오버라이드 추가
     */
    fun withWorkflowOverride(
        workflowId: String,
        l1Ttl: Duration? = null,
        l1MaxSize: Int? = null,
        l2Ttl: Duration? = null,
        enabled: Boolean? = null
    ): IntelligenceCacheConfig = copy(
        workflowOverrides = workflowOverrides + (workflowId to CacheOverride(l1Ttl, l1MaxSize, l2Ttl, enabled))
    )

    /**
     * L1 전용 모드 (Redis 없이)
     */
    fun l1Only(): IntelligenceCacheConfig = copy(l2Enabled = false)

    /**
     * L2 전용 모드 (In-Memory 없이)
     */
    fun l2Only(): IntelligenceCacheConfig = copy(l1Enabled = false)

    /**
     * 캐시 비활성화
     */
    fun disabled(): IntelligenceCacheConfig = copy(enabled = false)

    companion object {
        /**
         * 기본 설정
         */
        val DEFAULT = IntelligenceCacheConfig()

        /**
         * 고성능 설정 (큰 L1, 긴 TTL)
         */
        val HIGH_PERFORMANCE = IntelligenceCacheConfig(
            l1Ttl = 1.hours,
            l1MaxSize = 5000,
            l2Ttl = 4.hours
        )

        /**
         * 메모리 절약 설정 (작은 L1, 짧은 TTL)
         */
        val LOW_MEMORY = IntelligenceCacheConfig(
            l1Ttl = 10.minutes,
            l1MaxSize = 200,
            l2Ttl = 1.hours
        )

        /**
         * 테스트 설정 (캐시 비활성화)
         */
        val TEST = IntelligenceCacheConfig(enabled = false)

        /**
         * L1 전용 설정 (Redis 없이)
         */
        val L1_ONLY = IntelligenceCacheConfig(l2Enabled = false)
    }
}

/**
 * 캐시 오버라이드 설정
 *
 * null 값은 부모 설정을 상속.
 *
 * @since 2.0.0
 */
data class CacheOverride(
    val l1Ttl: Duration? = null,
    val l1MaxSize: Int? = null,
    val l2Ttl: Duration? = null,
    val enabled: Boolean? = null
)

/**
 * 해결된(resolved) 캐시 설정
 *
 * 오버라이드가 적용된 최종 설정.
 *
 * @since 2.0.0
 */
data class ResolvedCacheConfig(
    val l1Ttl: Duration,
    val l1MaxSize: Int,
    val l2Ttl: Duration,
    val enabled: Boolean
)

/**
 * 캐시 키 빌더
 *
 * ## 키 스키마
 * ```
 * {prefix}{layer}:{tenantId}:{workflowId}:{locale}:{hash}
 * ```
 *
 * ## 정규화 정책
 * 1. 소문자 변환 (lowercase)
 * 2. 앞뒤 공백 제거 (trim)
 * 3. 연속 공백 → 단일 공백
 * 4. 숫자 보존 (정규화 안 함)
 * 5. 특수문자 보존 (정규화 안 함)
 *
 * ## 해시 충돌 대비
 * - 해시 충돌 시 normalized utterance 로깅 필요
 * - buildWithMetadata() 사용하여 원본 정보 보존
 *
 * ## Thread-Safety
 * - 이 객체는 stateless이므로 thread-safe
 * - 반환된 CacheKeyResult는 immutable
 *
 * @since 2.0.0
 */
object CacheKeyBuilder {

    /**
     * 캐시 키 생성
     *
     * @param layer 캐시 레이어 (semantic, nano, big, policy)
     * @param tenantId 테넌트 ID
     * @param workflowId 워크플로우 ID (nullable)
     * @param utterance 발화 (정규화됨)
     * @param locale 로케일 (nullable)
     * @param prefix 키 접두사
     */
    fun build(
        layer: CacheLayer,
        tenantId: String,
        workflowId: String?,
        utterance: String,
        locale: String? = null,
        prefix: String = "spice:intel:"
    ): String {
        val normalized = normalize(utterance)
        val hash = normalized.hashCode().toUInt().toString(16)
        val wf = workflowId ?: "_"
        val loc = locale ?: "_"
        return "$prefix${layer.key}:$tenantId:$wf:$loc:$hash"
    }

    /**
     * 캐시 키 생성 (메타데이터 포함)
     *
     * 해시 충돌 대비 로깅용 원본 정보 포함.
     *
     * @return CacheKeyResult (key + normalized + original)
     */
    fun buildWithMetadata(
        layer: CacheLayer,
        tenantId: String,
        workflowId: String?,
        utterance: String,
        locale: String? = null,
        prefix: String = "spice:intel:"
    ): CacheKeyResult {
        val normalized = normalize(utterance)
        val hash = normalized.hashCode().toUInt().toString(16)
        val wf = workflowId ?: "_"
        val loc = locale ?: "_"
        val key = "$prefix${layer.key}:$tenantId:$wf:$loc:$hash"

        return CacheKeyResult(
            key = key,
            normalized = normalized,
            original = utterance,
            hash = hash,
            layer = layer
        )
    }

    /**
     * 발화 정규화
     *
     * ## 정규화 규칙
     * 1. lowercase: "Hello" → "hello"
     * 2. trim: " hello " → "hello"
     * 3. 연속 공백: "hello  world" → "hello world"
     * 4. 숫자 보존: "order 123" → "order 123"
     * 5. 특수문자 보존: "hello!" → "hello!"
     */
    fun normalize(utterance: String): String {
        return utterance
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}

/**
 * 캐시 키 결과 (메타데이터 포함)
 *
 * 해시 충돌 대비 로깅용.
 *
 * @property key 생성된 캐시 키
 * @property normalized 정규화된 발화 (로깅용)
 * @property original 원본 발화 (디버깅용)
 * @property hash 해시 값
 * @property layer 캐시 레이어
 *
 * @since 2.0.0
 */
data class CacheKeyResult(
    val key: String,
    val normalized: String,
    val original: String,
    val hash: String,
    val layer: CacheLayer
)

/**
 * 캐시 레이어
 *
 * @since 2.0.0
 */
enum class CacheLayer(val key: String) {
    /** Semantic 매칭 결과 */
    SEMANTIC("sem"),
    /** Nano LLM 검증 결과 */
    NANO("nano"),
    /** Big LLM (One-Shot Reasoner) 결과 */
    BIG("big"),
    /** Policy-RAG 검색 결과 */
    POLICY("pol")
}

/**
 * 레이어별 캐시 활성화 판단 (Cache + FeatureFlags 결합)
 *
 * ## safeDisable() 동작
 * FeatureFlags.safeDisable() 호출 시:
 * - enableNanoLayer = false
 * - enableBigLayer = false
 * - enablePolicyRag = false
 *
 * → NANO, BIG, POLICY 레이어 캐시 lookup/put 자동 스킵
 * → 불필요한 캐시 오염 방지
 *
 * ## 사용 예시
 * ```kotlin
 * val decision = LayerCacheDecision.shouldCache(
 *     layer = CacheLayer.NANO,
 *     cacheConfig = config.cacheConfig,
 *     featureFlags = config.featureFlags,
 *     tenantId = "tenant-1",
 *     workflowId = "workflow-1"
 * )
 * if (decision.enabled) {
 *     cache.put(key, value, decision.ttl)
 * }
 * ```
 *
 * @since 2.0.0
 */
object LayerCacheDecision {

    /**
     * 특정 레이어에 대해 캐시 활성화 여부 판단
     *
     * ## 판단 로직
     * 1. cacheConfig.enabled == false → 비활성
     * 2. featureFlags.enabled == false → 비활성
     * 3. 레이어별 featureFlag 비활성 → 비활성
     *    - SEMANTIC: 항상 활성 (Fast Layer)
     *    - NANO: featureFlags.enableNanoLayer
     *    - BIG: featureFlags.enableBigLayer
     *    - POLICY: featureFlags.enablePolicyRag
     * 4. tenant/workflow override 적용
     *
     * @param layer 캐시 레이어
     * @param cacheConfig 캐시 설정
     * @param featureFlags Feature Flags
     * @param tenantId 테넌트 ID
     * @param workflowId 워크플로우 ID
     * @return 캐시 활성화 여부 및 TTL
     */
    fun shouldCache(
        layer: CacheLayer,
        cacheConfig: IntelligenceCacheConfig,
        featureFlags: IntelligenceFeatureFlags,
        tenantId: String,
        workflowId: String? = null
    ): CacheDecisionResult {
        // 1. 전역 캐시 비활성화
        if (!cacheConfig.enabled) {
            return CacheDecisionResult.disabled("Cache globally disabled")
        }

        // 2. 전역 Intelligence 비활성화
        if (!featureFlags.enabled) {
            return CacheDecisionResult.disabled("Intelligence globally disabled")
        }

        // 3. 레이어별 Feature Flag 체크
        val layerEnabled = when (layer) {
            CacheLayer.SEMANTIC -> true  // Fast Layer는 항상 활성
            CacheLayer.NANO -> featureFlags.enableNanoLayer
            CacheLayer.BIG -> featureFlags.enableBigLayer
            CacheLayer.POLICY -> featureFlags.enablePolicyRag
        }

        if (!layerEnabled) {
            return CacheDecisionResult.disabled("Layer ${layer.name} disabled by feature flag")
        }

        // 4. Tenant/Workflow 레벨 체크
        if (!featureFlags.isEnabledFor(tenantId, workflowId)) {
            return CacheDecisionResult.disabled("Disabled for tenant=$tenantId, workflow=$workflowId")
        }

        // 5. Override 적용
        val resolved = cacheConfig.resolveFor(tenantId, workflowId)
        if (!resolved.enabled) {
            return CacheDecisionResult.disabled("Cache disabled by tenant/workflow override")
        }

        return CacheDecisionResult.enabled(resolved.l1Ttl)
    }

    /**
     * Stage3 (Big LLM) 호출 가능 여부
     *
     * safeDisable() 모드에서 Stage3 완전 차단 확인용.
     */
    fun canInvokeStage3(featureFlags: IntelligenceFeatureFlags, tenantId: String, workflowId: String?): Boolean {
        if (!featureFlags.enabled) return false
        if (!featureFlags.enableBigLayer) return false
        return featureFlags.isEnabledFor(tenantId, workflowId)
    }

    /**
     * Nano 호출 가능 여부
     */
    fun canInvokeNano(featureFlags: IntelligenceFeatureFlags, tenantId: String, workflowId: String?): Boolean {
        if (!featureFlags.enabled) return false
        if (!featureFlags.enableNanoLayer) return false
        return featureFlags.isEnabledFor(tenantId, workflowId)
    }
}

/**
 * 캐시 활성화 판단 결과
 *
 * @property enabled 캐시 활성화 여부
 * @property ttl 적용할 TTL (활성화 시)
 * @property reason 비활성화 사유 (비활성화 시)
 *
 * @since 2.0.0
 */
data class CacheDecisionResult(
    val enabled: Boolean,
    val ttl: kotlin.time.Duration?,
    val reason: String?
) {
    companion object {
        fun enabled(ttl: kotlin.time.Duration) = CacheDecisionResult(true, ttl, null)
        fun disabled(reason: String) = CacheDecisionResult(false, null, reason)
    }
}
