package io.github.noailabs.spice.intelligence

/**
 * Intelligence Layer Feature Flags (v2)
 *
 * ## Opt-in 전략
 * Intelligence Layer는 `.withIntelligence()` 호출로 opt-in.
 * 기본 동작은 변경 없이 기존 DECISION 노드 로직 유지.
 *
 * ## 스코프
 * - Global: 전체 시스템 레벨
 * - Tenant: 테넌트별 활성화/비활성화
 * - Workflow: 워크플로우별 활성화/비활성화
 *
 * ## 우선순위 (Priority Order)
 * 평가 순서 및 우선순위:
 * ```
 * 1. enabled == false         → 무조건 비활성 (최우선)
 * 2. tenantBlacklist 포함      → 비활성 (blacklist wins)
 * 3. workflowBlacklist 포함    → 비활성 (blacklist wins)
 * 4. tenantWhitelist 미포함    → 비활성 (whitelist > rollout)
 * 5. workflowWhitelist 미포함  → 비활성 (whitelist > rollout)
 * 6. rolloutPercentage 미달    → 비활성 (rollout은 마지막)
 * 7. → 활성
 * ```
 *
 * **핵심**: `blacklist > whitelist > rollout`
 *
 * whitelist가 설정되면 rollout%와 무관하게 whitelist에 없으면 비활성.
 * whitelist와 rollout을 동시에 사용하면 whitelist 통과 후 rollout 적용.
 *
 * ## 안전한 Disable 경로
 * ```kotlin
 * // 런타임 disable (graceful degradation)
 * featureFlags.copy(enabled = false)
 * // → Fast Layer만 동작, Nano/Big LLM 스킵
 * // → DECISION 노드에 canonical 그대로 전달
 * ```
 *
 * ## 롤아웃 전략
 * ```kotlin
 * // 단계적 롤아웃
 * val flags = IntelligenceFeatureFlags.DEFAULT
 *     .withRolloutPercentage(10)  // 10% 트래픽만 적용
 *     .withTenantWhitelist(setOf("beta-tenant"))
 * // → beta-tenant만 10% 롤아웃 적용
 * ```
 *
 * ## Thread-Safety & Hot Reload
 * - 이 data class는 **immutable**이므로 thread-safe
 * - copy()로 새 인스턴스 생성 시 기존 참조에 영향 없음
 * - **Hot Reload 미지원**: 설정 변경 시 애플리케이션 재시작 필요
 * - 런타임 변경이 필요하면 외부 설정 소스(Redis, DB) 폴링 권장
 *
 * @property enabled Intelligence Layer 전역 활성화
 * @property enableFastLayer Fast Layer (Embedding) 활성화
 * @property enableNanoLayer Nano LLM 검증 활성화
 * @property enableBigLayer Big LLM (One-Shot Reasoner) 활성화
 * @property enablePolicyRag Policy-RAG 활성화
 * @property enableIntentShift Intent Shift 감지 활성화
 * @property enableWorkflowSwitch 워크플로우 전환 활성화
 * @property enableLoopGuard Loop Guard 활성화
 * @property enableFrustrationDetection 불만 감지 활성화
 * @property rolloutPercentage 롤아웃 비율 (0-100)
 * @property tenantWhitelist 활성화된 테넌트 화이트리스트 (null이면 전체)
 * @property tenantBlacklist 비활성화된 테넌트 블랙리스트
 * @property workflowWhitelist 활성화된 워크플로우 화이트리스트 (null이면 전체)
 * @property workflowBlacklist 비활성화된 워크플로우 블랙리스트
 * @property experimentFlags 실험 플래그
 *
 * @since 2.0.0
 */
data class IntelligenceFeatureFlags(
    // 전역 활성화
    val enabled: Boolean = true,

    // 레이어별 활성화
    val enableFastLayer: Boolean = true,
    val enableNanoLayer: Boolean = true,
    val enableBigLayer: Boolean = true,
    val enablePolicyRag: Boolean = true,

    // 기능별 활성화
    val enableIntentShift: Boolean = true,
    val enableWorkflowSwitch: Boolean = true,
    val enableLoopGuard: Boolean = true,
    val enableFrustrationDetection: Boolean = true,

    // 롤아웃 설정
    val rolloutPercentage: Int = 100,

    // 화이트/블랙 리스트
    val tenantWhitelist: Set<String>? = null,
    val tenantBlacklist: Set<String> = emptySet(),
    val workflowWhitelist: Set<String>? = null,
    val workflowBlacklist: Set<String> = emptySet(),

    // 실험 플래그
    val experimentFlags: Map<String, Boolean> = emptyMap()
) {
    init {
        require(rolloutPercentage in 0..100) { "rolloutPercentage must be 0-100" }
    }

    /**
     * 특정 테넌트/워크플로우에서 활성화 여부 확인
     *
     * 검사 순서:
     * 1. enabled == false → 비활성
     * 2. tenantBlacklist 포함 → 비활성
     * 3. workflowBlacklist 포함 → 비활성
     * 4. tenantWhitelist != null && 미포함 → 비활성
     * 5. workflowWhitelist != null && 미포함 → 비활성
     * 6. rolloutPercentage 체크
     * 7. 활성
     */
    fun isEnabledFor(
        tenantId: String,
        workflowId: String? = null,
        sessionHash: Int? = null
    ): Boolean {
        // 전역 비활성화
        if (!enabled) return false

        // 블랙리스트 체크
        if (tenantId in tenantBlacklist) return false
        if (workflowId != null && workflowId in workflowBlacklist) return false

        // 화이트리스트 체크
        if (tenantWhitelist != null && tenantId !in tenantWhitelist) return false
        if (workflowWhitelist != null && workflowId != null && workflowId !in workflowWhitelist) return false

        // 롤아웃 비율 체크
        if (rolloutPercentage < 100) {
            val hash = sessionHash ?: (tenantId + (workflowId ?: "")).hashCode()
            val bucket = (hash.toUInt() % 100u).toInt()
            if (bucket >= rolloutPercentage) return false
        }

        return true
    }

    /**
     * 특정 레이어 활성화 여부
     */
    fun isLayerEnabled(layer: IntelligenceLayer): Boolean = when (layer) {
        IntelligenceLayer.FAST -> enableFastLayer
        IntelligenceLayer.NANO -> enableNanoLayer
        IntelligenceLayer.BIG -> enableBigLayer
        IntelligenceLayer.POLICY_RAG -> enablePolicyRag
    }

    /**
     * 특정 기능 활성화 여부
     */
    fun isFeatureEnabled(feature: IntelligenceFeature): Boolean = when (feature) {
        IntelligenceFeature.INTENT_SHIFT -> enableIntentShift
        IntelligenceFeature.WORKFLOW_SWITCH -> enableWorkflowSwitch
        IntelligenceFeature.LOOP_GUARD -> enableLoopGuard
        IntelligenceFeature.FRUSTRATION_DETECTION -> enableFrustrationDetection
    }

    /**
     * 실험 플래그 조회
     */
    fun isExperimentEnabled(experimentId: String, default: Boolean = false): Boolean {
        return experimentFlags[experimentId] ?: default
    }

    // Fluent Builder Methods

    /**
     * 롤아웃 비율 설정
     */
    fun withRolloutPercentage(percentage: Int): IntelligenceFeatureFlags =
        copy(rolloutPercentage = percentage)

    /**
     * 테넌트 화이트리스트 설정
     */
    fun withTenantWhitelist(tenants: Set<String>): IntelligenceFeatureFlags =
        copy(tenantWhitelist = tenants)

    /**
     * 테넌트 블랙리스트 추가
     */
    fun withTenantBlacklist(tenants: Set<String>): IntelligenceFeatureFlags =
        copy(tenantBlacklist = tenantBlacklist + tenants)

    /**
     * 워크플로우 화이트리스트 설정
     */
    fun withWorkflowWhitelist(workflows: Set<String>): IntelligenceFeatureFlags =
        copy(workflowWhitelist = workflows)

    /**
     * 워크플로우 블랙리스트 추가
     */
    fun withWorkflowBlacklist(workflows: Set<String>): IntelligenceFeatureFlags =
        copy(workflowBlacklist = workflowBlacklist + workflows)

    /**
     * 실험 플래그 추가
     */
    fun withExperiment(experimentId: String, enabled: Boolean): IntelligenceFeatureFlags =
        copy(experimentFlags = experimentFlags + (experimentId to enabled))

    /**
     * 레이어 비활성화
     */
    fun disableLayer(layer: IntelligenceLayer): IntelligenceFeatureFlags = when (layer) {
        IntelligenceLayer.FAST -> copy(enableFastLayer = false)
        IntelligenceLayer.NANO -> copy(enableNanoLayer = false)
        IntelligenceLayer.BIG -> copy(enableBigLayer = false)
        IntelligenceLayer.POLICY_RAG -> copy(enablePolicyRag = false)
    }

    /**
     * 기능 비활성화
     */
    fun disableFeature(feature: IntelligenceFeature): IntelligenceFeatureFlags = when (feature) {
        IntelligenceFeature.INTENT_SHIFT -> copy(enableIntentShift = false)
        IntelligenceFeature.WORKFLOW_SWITCH -> copy(enableWorkflowSwitch = false)
        IntelligenceFeature.LOOP_GUARD -> copy(enableLoopGuard = false)
        IntelligenceFeature.FRUSTRATION_DETECTION -> copy(enableFrustrationDetection = false)
    }

    /**
     * 안전한 비활성화 (graceful degradation)
     *
     * Fast Layer만 유지, LLM 레이어 비활성화.
     * DECISION 노드에 canonical 그대로 전달.
     */
    fun safeDisable(): IntelligenceFeatureFlags = copy(
        enableNanoLayer = false,
        enableBigLayer = false,
        enablePolicyRag = false
    )

    companion object {
        /**
         * 기본 설정 (모든 기능 활성화)
         */
        val DEFAULT = IntelligenceFeatureFlags()

        /**
         * 최소 설정 (Fast Layer만)
         */
        val MINIMAL = IntelligenceFeatureFlags(
            enableNanoLayer = false,
            enableBigLayer = false,
            enablePolicyRag = false
        )

        /**
         * 테스트 설정 (전체 비활성화)
         */
        val TEST = IntelligenceFeatureFlags(enabled = false)

        /**
         * 베타 설정 (10% 롤아웃)
         */
        val BETA = IntelligenceFeatureFlags(rolloutPercentage = 10)

        /**
         * 보수적 설정 (Intent Shift/Workflow Switch 비활성화)
         */
        val CONSERVATIVE = IntelligenceFeatureFlags(
            enableIntentShift = false,
            enableWorkflowSwitch = false
        )
    }
}

/**
 * Intelligence Layer 레이어
 *
 * @since 2.0.0
 */
enum class IntelligenceLayer {
    /** Fast Layer (Embedding) */
    FAST,
    /** Nano LLM 검증 */
    NANO,
    /** Big LLM (One-Shot Reasoner) */
    BIG,
    /** Policy-RAG */
    POLICY_RAG
}

/**
 * Intelligence Layer 기능
 *
 * @since 2.0.0
 */
enum class IntelligenceFeature {
    /** Intent Shift 감지 */
    INTENT_SHIFT,
    /** 워크플로우 전환 */
    WORKFLOW_SWITCH,
    /** Loop Guard */
    LOOP_GUARD,
    /** 불만 감지 */
    FRUSTRATION_DETECTION
}
