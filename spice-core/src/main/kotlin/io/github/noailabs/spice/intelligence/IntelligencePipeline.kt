package io.github.noailabs.spice.intelligence

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.spi.*
import io.github.noailabs.spice.routing.spi.SemanticMatcher
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

/**
 * Intelligence Pipeline (v2 Core Orchestrator)
 *
 * Fast → Nano → Big LLM 파이프라인을 오케스트레이션하는 핵심 클래스.
 *
 * ## 파이프라인 흐름
 * ```
 * [1. Cache Check]
 *      ↓ miss
 * [2. Parallel: Semantic + Policy-RAG + DomainRelevance]
 *      ↓
 * [3. Gating Decision]
 *   ├─ FAST_PATH → [4a. Return]
 *   ├─ NANO_VALIDATION → [4b. Nano] → Override/Clarify/Delegate
 *   └─ BIG_LLM → [4c. OneShotReasoner]
 *      ↓
 * [5. Cache Put + Return]
 * ```
 *
 * ## Feature Flag 연동
 * - safeDisable() 시 Nano/Big/Policy-RAG 스킵
 * - 레이어별 개별 비활성화 지원
 * - tenant/workflow 레벨 롤아웃 지원
 *
 * ## Fallback 전략
 * - Nano 실패 → Big LLM (enableBigLayer=true 시)
 * - Big LLM 실패 → Fast Layer 결과 + CLARIFY
 * - Policy-RAG 실패 → emptyList()로 진행
 *
 * @property semanticMatcher 시멘틱 매처 (Fast Layer)
 * @property domainRelevanceCalculator 도메인 관련성 계산기
 * @property nanoValidator Nano LLM 검증기 (nullable)
 * @property oneShotReasoner Big LLM Reasoner (nullable)
 * @property policyStore Policy-RAG 스토어 (nullable)
 * @property cache Intelligence 캐시
 * @property config 설정
 *
 * @since 2.0.0
 */
class IntelligencePipeline(
    private val semanticMatcher: SemanticMatcher,
    private val domainRelevanceCalculator: DomainRelevanceCalculator,
    private val nanoValidator: NanoValidator? = null,
    private val oneShotReasoner: OneShotReasoner? = null,
    private val policyStore: PolicyStore? = null,
    private val cache: IntelligenceCache,
    private val config: IntelligenceConfig = IntelligenceConfig.DEFAULT,
    private val metricsRecorder: PipelineMetricsRecorder = PipelineMetricsRecorder.NO_OP
) {
    private val cacheConfig get() = config.cacheConfig
    private val featureFlags get() = config.featureFlags

    /**
     * Intelligence 파이프라인 실행
     *
     * @param utterance 사용자 발화
     * @param context 판단 컨텍스트
     * @return 파이프라인 결과
     */
    suspend fun execute(
        utterance: String,
        context: IntelligenceContext
    ): SpiceResult<PipelineResult> {
        val startTime = Clock.System.now()
        val tenantId = context.tenantId
        val workflowId = context.workflowId

        // 0. Feature Flag 체크
        if (!featureFlags.isEnabledFor(tenantId, workflowId)) {
            return SpiceResult.success(
                PipelineResult.passThrough(
                    reason = "Intelligence disabled for tenant=$tenantId, workflow=$workflowId",
                    latencyMs = 0
                )
            )
        }

        // 1. Cache Check (SEMANTIC 레이어)
        val cacheDecision = LayerCacheDecision.shouldCache(
            layer = CacheLayer.SEMANTIC,
            cacheConfig = cacheConfig,
            featureFlags = featureFlags,
            tenantId = tenantId,
            workflowId = workflowId
        )

        if (cacheDecision.enabled) {
            val cacheStartTime = Clock.System.now()
            val cacheKey = buildCacheKey(CacheLayer.SEMANTIC, utterance, context)
            val cached = cache.get(cacheKey)
            val cacheLatencyMs = Clock.System.now().toEpochMilliseconds() - cacheStartTime.toEpochMilliseconds()

            metricsRecorder.recordCacheAccess(CacheAccessEvent(
                tenantId = tenantId,
                workflowId = workflowId,
                layer = CacheLayer.SEMANTIC,
                hit = cached != null,
                latencyMs = cacheLatencyMs
            ))

            if (cached != null) {
                val latencyMs = Clock.System.now().toEpochMilliseconds() - startTime.toEpochMilliseconds()
                return SpiceResult.success(
                    PipelineResult.fromCache(cached, latencyMs, CacheLayer.SEMANTIC)
                )
            }
        }

        // 2. Parallel: Semantic + Policy-RAG + DomainRelevance
        val parallelResult = executeParallelStage(utterance, context)

        // 3. Gating Decision
        val gatingDecision = config.gate(
            semanticScore = parallelResult.semanticResult.topScore,
            secondScore = parallelResult.semanticResult.secondScore,
            domainRelevance = parallelResult.domainRelevance.score
        )

        // Gating 메트릭 기록
        metricsRecorder.recordGating(GatingEvent(
            tenantId = tenantId,
            workflowId = workflowId,
            decision = gatingDecision,
            topScore = parallelResult.semanticResult.topScore,
            domainRelevance = parallelResult.domainRelevance.score,
            gap = parallelResult.semanticResult.gap
        ))

        // 4. 경로 분기
        val decision = when (gatingDecision) {
            GatingDecision.FAST_PATH -> executeFastPath(utterance, parallelResult, context)
            GatingDecision.NANO_VALIDATION -> executeNanoPath(utterance, parallelResult, context)
            GatingDecision.BIG_LLM -> executeBigLLMPath(utterance, parallelResult, context)
        }

        // 5. 결과 반환
        val latencyMs = Clock.System.now().toEpochMilliseconds() - startTime.toEpochMilliseconds()

        // 최종 결과 메트릭 기록
        metricsRecorder.recordPipelineResult(PipelineResultEvent(
            tenantId = tenantId,
            workflowId = workflowId,
            routingSignal = decision.routingSignal,
            decisionSource = decision.decisionSource,
            confidence = decision.confidence,
            cacheHit = false,
            cacheLayer = null,
            totalLatencyMs = latencyMs
        ))

        return SpiceResult.success(
            PipelineResult(
                decision = decision,
                gatingDecision = gatingDecision,
                semanticResult = parallelResult.semanticResult,
                domainRelevance = parallelResult.domainRelevance,
                policyHints = parallelResult.policyHints,
                cacheHit = false,
                cacheLayer = null,
                latencyMs = latencyMs
            )
        )
    }

    /**
     * Stage 2: 병렬 실행 (Semantic + Policy-RAG + DomainRelevance)
     */
    private suspend fun executeParallelStage(
        utterance: String,
        context: IntelligenceContext
    ): ParallelStageResult = coroutineScope {
        val tenantId = context.tenantId
        val workflowId = context.workflowId

        // Semantic Matching (필수)
        val semanticDeferred = async {
            performSemanticMatch(utterance, context)
        }

        // Policy-RAG (optional, feature flag 체크)
        val policyDeferred = async {
            if (policyStore != null && featureFlags.enablePolicyRag) {
                fetchPolicyHints(utterance, workflowId)
            } else {
                emptyList()
            }
        }

        // DomainRelevance (필수, but with fallback)
        val domainDeferred = async {
            calculateDomainRelevance(utterance, context)
        }

        val semanticResult = semanticDeferred.await()
        val policyHints = policyDeferred.await()
        val domainRelevance = domainDeferred.await()

        ParallelStageResult(
            semanticResult = semanticResult,
            policyHints = policyHints,
            domainRelevance = domainRelevance
        )
    }

    /**
     * Fast Path 실행 (Gating: FAST_PATH)
     */
    private suspend fun executeFastPath(
        utterance: String,
        parallelResult: ParallelStageResult,
        context: IntelligenceContext
    ): CompositeDecision {
        val semantic = parallelResult.semanticResult

        val decision = CompositeDecision(
            domainRelevance = parallelResult.domainRelevance.score,
            aiCanonical = semantic.topCanonical,
            confidence = semantic.topScore,
            reasoning = "Fast path: score=${semantic.topScore}, gap=${semantic.gap}, domain=${parallelResult.domainRelevance.score}",
            decisionSource = DecisionSource.EMBEDDING,
            routingSignal = RoutingSignal.NORMAL,
            latencyMs = 0
        )

        // Cache 저장 (입력 utterance로 키 생성)
        saveToCacheIfEnabled(CacheLayer.SEMANTIC, utterance, context, decision)

        return decision
    }

    /**
     * Nano Path 실행 (Gating: NANO_VALIDATION)
     */
    private suspend fun executeNanoPath(
        utterance: String,
        parallelResult: ParallelStageResult,
        context: IntelligenceContext
    ): CompositeDecision {
        val tenantId = context.tenantId
        val workflowId = context.workflowId

        // Nano 비활성화 or 없음 → Big LLM으로 위임
        if (nanoValidator == null || !LayerCacheDecision.canInvokeNano(featureFlags, tenantId, workflowId)) {
            return executeBigLLMPath(utterance, parallelResult, context)
        }

        // Nano 검증 요청 생성
        val nanoRequest = NanoValidationRequest(
            utterance = utterance,
            suggestedCanonical = parallelResult.semanticResult.topCanonical,
            suggestedConfidence = parallelResult.semanticResult.topScore,
            candidates = parallelResult.semanticResult.candidates,
            gap = parallelResult.semanticResult.gap,
            policyHints = parallelResult.policyHints,
            workflowId = workflowId
        )

        // Nano 호출 (타임아웃 적용)
        val nanoResult = try {
            withTimeout(config.nanoTimeoutMs) {
                nanoValidator.validate(nanoRequest).getOrNull()
            }
        } catch (e: TimeoutCancellationException) {
            NanoValidationResult.fallback("Nano timeout after ${config.nanoTimeoutMs}ms")
        } catch (e: Exception) {
            NanoValidationResult.fallback("Nano exception: ${e.message}")
        }

        if (nanoResult == null) {
            return executeBigLLMPath(utterance, parallelResult, context)
        }

        // Nano 결과에 따른 분기
        return when (nanoResult.status) {
            NanoValidationStatus.OVERRIDE -> {
                val decision = CompositeDecision(
                    domainRelevance = parallelResult.domainRelevance.score,
                    aiCanonical = nanoResult.canonical,
                    confidence = nanoResult.confidence,
                    reasoning = "Nano override: ${nanoResult.reasoning}",
                    decisionSource = DecisionSource.NANO,
                    routingSignal = RoutingSignal.NORMAL,
                    latencyMs = nanoResult.latencyMs
                )
                saveToCacheIfEnabled(CacheLayer.NANO, utterance, context, decision)
                decision
            }

            NanoValidationStatus.CLARIFY -> {
                val decision = CompositeDecision(
                    domainRelevance = parallelResult.domainRelevance.score,
                    aiCanonical = null,
                    confidence = nanoResult.confidence,
                    reasoning = "Nano clarify: ${nanoResult.reasoning}",
                    decisionSource = DecisionSource.NANO,
                    routingSignal = RoutingSignal.AMBIGUOUS,
                    clarificationRequest = nanoResult.clarificationRequest,
                    latencyMs = nanoResult.latencyMs
                )
                // CLARIFY는 짧은 TTL로 캐시 (동일 발화 반복 방지)
                if (cacheConfig.cacheAmbiguous) {
                    saveToCacheWithTtl(CacheLayer.NANO, utterance, context, decision, cacheConfig.ambiguousTtl)
                }
                decision
            }

            NanoValidationStatus.DELEGATE, NanoValidationStatus.FALLBACK -> {
                // Big LLM으로 escalation
                executeBigLLMPath(utterance, parallelResult, context, nanoResult)
            }
        }
    }

    /**
     * Big LLM Path 실행 (Gating: BIG_LLM or Nano Delegate)
     */
    private suspend fun executeBigLLMPath(
        utterance: String,
        parallelResult: ParallelStageResult,
        context: IntelligenceContext,
        nanoResult: NanoValidationResult? = null
    ): CompositeDecision {
        val tenantId = context.tenantId
        val workflowId = context.workflowId

        // Big LLM 비활성화 or 없음 → Fallback
        if (oneShotReasoner == null || !LayerCacheDecision.canInvokeStage3(featureFlags, tenantId, workflowId)) {
            return createFallbackDecision(parallelResult, nanoResult, "Big LLM disabled")
        }

        // OneShotRequest 생성
        val request = OneShotRequest.from(
            utterance = utterance,
            context = context,
            semanticResult = parallelResult.semanticResult,
            policyHints = parallelResult.policyHints,
            availableWorkflows = context.availableWorkflows
        )

        // Big LLM 호출
        val reasonerResult = try {
            oneShotReasoner.reason(request).getOrNull()
        } catch (e: Exception) {
            null
        }

        if (reasonerResult == null) {
            return createFallbackDecision(parallelResult, nanoResult, "Big LLM failed")
        }

        // 캐시 저장 (성공 시, 입력 utterance로 키 생성)
        if (reasonerResult.routingSignal == RoutingSignal.NORMAL) {
            saveToCacheIfEnabled(CacheLayer.BIG, utterance, context, reasonerResult)
        }

        return reasonerResult
    }

    /**
     * Fallback Decision 생성 (모든 LLM 실패 시)
     *
     * ## FallbackConfig 적용
     * - confidence = originalScore * confidenceMultiplier
     * - confidence < ambiguousThreshold → AMBIGUOUS
     * - confidence >= ambiguousThreshold → NORMAL
     */
    private fun createFallbackDecision(
        parallelResult: ParallelStageResult,
        nanoResult: NanoValidationResult?,
        reason: String
    ): CompositeDecision {
        val semantic = parallelResult.semanticResult
        val fallbackResult = config.fallbackConfig.calculateFallback(semantic.topScore)

        return CompositeDecision(
            domainRelevance = parallelResult.domainRelevance.score,
            aiCanonical = semantic.topCanonical,
            confidence = fallbackResult.confidence,
            reasoning = "Fallback: $reason. confidence=${fallbackResult.originalScore}→${fallbackResult.confidence} (×${fallbackResult.multiplierApplied})",
            decisionSource = DecisionSource.EMBEDDING,
            routingSignal = fallbackResult.toRoutingSignal(),
            latencyMs = nanoResult?.latencyMs ?: 0
        )
    }

    /**
     * Semantic Matching 수행
     */
    private suspend fun performSemanticMatch(
        utterance: String,
        context: IntelligenceContext
    ): SemanticMatchResult {
        val options = context.optionTexts
        if (options.isEmpty()) {
            return SemanticMatchResult.EMPTY
        }

        val scores = semanticMatcher.match(utterance, options)

        val scoredCandidates = context.routingOptions.mapIndexed { index, option ->
            ScoredCandidate(
                canonical = option.canonical,
                score = scores.getOrElse(index) { 0.0 },
                label = option.description
            )
        }.sortedByDescending { it.score }

        val topCandidate = scoredCandidates.firstOrNull()
        val secondScore = scoredCandidates.getOrNull(1)?.score ?: 0.0

        return SemanticMatchResult(
            topScore = topCandidate?.score ?: 0.0,
            secondScore = secondScore,
            topCanonical = topCandidate?.canonical,
            candidates = scoredCandidates,
            optionCount = options.size,
            domainRelevance = 0.0,  // 별도 계산
            domainRelevanceSource = DomainRelevanceSource.HEURISTIC
        )
    }

    /**
     * Policy-RAG 검색 (timeout/degrade 적용)
     *
     * config.policyRagTimeoutMs 사용 (기본 2000ms)
     */
    private suspend fun fetchPolicyHints(
        query: String,
        workflowId: String?
    ): List<PolicyHint> {
        if (policyStore == null) return emptyList()

        return try {
            withTimeout(config.policyRagTimeoutMs) {
                policyStore.search(query, workflowId, config.policyTopK)
                    .getOrNull() ?: emptyList()
            }
        } catch (e: TimeoutCancellationException) {
            // 타임아웃 → 빈 리스트 (degrade)
            emptyList()
        } catch (e: Exception) {
            // 오류 → 빈 리스트 (degrade)
            emptyList()
        }
    }

    /**
     * Domain Relevance 계산 (fallback 적용)
     */
    private suspend fun calculateDomainRelevance(
        utterance: String,
        context: IntelligenceContext
    ): DomainRelevanceResult {
        return try {
            withTimeout(DOMAIN_RELEVANCE_TIMEOUT_MS) {
                domainRelevanceCalculator.calculate(
                    utterance,
                    DomainRelevanceContext(
                        workflowId = context.workflowId,
                        tenantId = context.tenantId
                    )
                )
            }
        } catch (e: Exception) {
            DomainRelevanceCalculator.fallback("Exception: ${e.message}")
        }
    }

    /**
     * 캐시 저장 (조건부)
     *
     * @param layer 캐시 레이어
     * @param utterance 원본 입력 발화 (캐시 키 생성에 사용, 조회와 일치해야 함)
     * @param context Intelligence 컨텍스트
     * @param decision 저장할 결정
     */
    private suspend fun saveToCacheIfEnabled(
        layer: CacheLayer,
        utterance: String,
        context: IntelligenceContext,
        decision: CompositeDecision
    ) {
        val cacheDecision = LayerCacheDecision.shouldCache(
            layer = layer,
            cacheConfig = cacheConfig,
            featureFlags = featureFlags,
            tenantId = context.tenantId,
            workflowId = context.workflowId
        )

        if (cacheDecision.enabled && cacheDecision.ttl != null) {
            val key = buildCacheKey(layer, utterance, context)
            cache.put(key, decision, cacheDecision.ttl)
        }
    }

    /**
     * 캐시 저장 (명시적 TTL)
     *
     * @param layer 캐시 레이어
     * @param utterance 원본 입력 발화 (캐시 키 생성에 사용, 조회와 일치해야 함)
     * @param context Intelligence 컨텍스트
     * @param decision 저장할 결정
     * @param ttl 유효 기간
     */
    private suspend fun saveToCacheWithTtl(
        layer: CacheLayer,
        utterance: String,
        context: IntelligenceContext,
        decision: CompositeDecision,
        ttl: kotlin.time.Duration
    ) {
        val cacheDecision = LayerCacheDecision.shouldCache(
            layer = layer,
            cacheConfig = cacheConfig,
            featureFlags = featureFlags,
            tenantId = context.tenantId,
            workflowId = context.workflowId
        )

        if (cacheDecision.enabled) {
            val key = buildCacheKey(layer, utterance, context)
            cache.put(key, decision, ttl)
        }
    }

    /**
     * 캐시 키 생성 (레이어 + 테넌트 포함)
     */
    private fun buildCacheKey(
        layer: CacheLayer,
        utterance: String,
        context: IntelligenceContext
    ): IntelligenceCacheKey {
        return IntelligenceCacheKey.from(
            layer = layer,
            tenantId = context.tenantId,
            utterance = utterance,
            prevNodes = context.prevNodes,
            workflowId = context.workflowId
        )
    }

    companion object {
        /** Domain Relevance 계산 타임아웃 (ms) */
        const val DOMAIN_RELEVANCE_TIMEOUT_MS = 1000L
    }
}

/**
 * 병렬 스테이지 결과
 */
private data class ParallelStageResult(
    val semanticResult: SemanticMatchResult,
    val policyHints: List<PolicyHint>,
    val domainRelevance: DomainRelevanceResult
)

/**
 * 파이프라인 실행 결과
 *
 * @property decision 최종 결정
 * @property gatingDecision Gating 단계 결정
 * @property semanticResult Semantic 매칭 결과
 * @property domainRelevance 도메인 관련성 결과
 * @property policyHints Policy-RAG 힌트
 * @property cacheHit 캐시 히트 여부
 * @property cacheLayer 캐시 히트 레이어 (히트 시)
 * @property latencyMs 총 처리 시간
 *
 * @since 2.0.0
 */
data class PipelineResult(
    val decision: CompositeDecision,
    val gatingDecision: GatingDecision,
    val semanticResult: SemanticMatchResult,
    val domainRelevance: DomainRelevanceResult,
    val policyHints: List<PolicyHint>,
    val cacheHit: Boolean,
    val cacheLayer: CacheLayer?,
    val latencyMs: Long
) {
    companion object {
        /**
         * Pass-through 결과 (Intelligence 비활성화 시)
         */
        fun passThrough(reason: String, latencyMs: Long) = PipelineResult(
            decision = CompositeDecision.passThrough(reason),
            gatingDecision = GatingDecision.FAST_PATH,
            semanticResult = SemanticMatchResult.EMPTY,
            domainRelevance = DomainRelevanceResult.heuristic(0.5, "Pass-through"),
            policyHints = emptyList(),
            cacheHit = false,
            cacheLayer = null,
            latencyMs = latencyMs
        )

        /**
         * 캐시 히트 결과
         */
        fun fromCache(
            decision: CompositeDecision,
            latencyMs: Long,
            layer: CacheLayer
        ) = PipelineResult(
            decision = decision.copy(reasoning = "Cache hit (${layer.name}): ${decision.reasoning}"),
            gatingDecision = GatingDecision.FAST_PATH,
            semanticResult = SemanticMatchResult.EMPTY,
            domainRelevance = DomainRelevanceResult.heuristic(decision.domainRelevance, "Cached"),
            policyHints = emptyList(),
            cacheHit = true,
            cacheLayer = layer,
            latencyMs = latencyMs
        )
    }
}
