package io.github.noailabs.spice.intelligence.impl

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.CacheLayer
import io.github.noailabs.spice.intelligence.CompositeDecision
import io.github.noailabs.spice.intelligence.DecisionSource
import io.github.noailabs.spice.intelligence.GatingDecision
import io.github.noailabs.spice.intelligence.IntelligenceConfig
import io.github.noailabs.spice.intelligence.RoutingSignal
import io.github.noailabs.spice.intelligence.spi.IntelligenceCache
import io.github.noailabs.spice.intelligence.spi.IntelligenceCacheKey
import io.github.noailabs.spice.intelligence.spi.IntelligenceContext
import io.github.noailabs.spice.intelligence.spi.IntelligenceResolver
import io.github.noailabs.spice.intelligence.spi.ScoredCandidate
import io.github.noailabs.spice.intelligence.spi.SemanticMatchResult
import io.github.noailabs.spice.routing.spi.SemanticMatcher
import kotlinx.datetime.Clock

/**
 * Default Intelligence Resolver (Fast Layer Only)
 *
 * SemanticMatcher 기반의 Fast Path 구현.
 * Nano/Big LLM은 Phase 3에서 추가.
 *
 * ## 파이프라인
 * ```
 * [Cache Check] → [Semantic Matching] → [Gating] → [Result]
 * ```
 *
 * ## Gating 로직 (오메가 수정)
 * embedding score 단독 사용 금지!
 * - semanticScore + domainRelevance + gap 조합 판단
 *
 * @property semanticMatcher 시멘틱 매처
 * @property cache Intelligence 캐시
 * @property config 설정
 *
 * @since 2.0.0
 */
class DefaultIntelligenceResolver(
    private val semanticMatcher: SemanticMatcher,
    private val cache: IntelligenceCache,
    private val config: IntelligenceConfig = IntelligenceConfig.DEFAULT
) : IntelligenceResolver {

    override suspend fun resolve(
        utterance: String,
        context: IntelligenceContext
    ): SpiceResult<CompositeDecision> {
        val startTime = Clock.System.now()

        // 1. 캐시 체크
        if (config.cacheConfig.enabled) {
            val cacheKey = buildCacheKey(utterance, context)
            cache.get(cacheKey)?.let { cached ->
                return SpiceResult.success(cached.copy(
                    decisionSource = DecisionSource.EMBEDDING,
                    reasoning = "Cache hit: ${cached.reasoning}"
                ))
            }
        }

        // 2. 라우팅 옵션이 없으면 Pass-through
        if (context.routingOptions.isEmpty()) {
            return SpiceResult.success(
                CompositeDecision.passThrough("No routing options available")
            )
        }

        // 3. Semantic Matching
        val matchResult = performSemanticMatch(utterance, context)

        // 4. Gating 판단
        val gatingDecision = gate(
            semanticScore = matchResult.topScore,
            secondScore = matchResult.secondScore,
            domainRelevance = matchResult.domainRelevance
        )

        // 5. 결과 생성
        val latencyMs = Clock.System.now().toEpochMilliseconds() - startTime.toEpochMilliseconds()
        val decision = buildDecision(matchResult, gatingDecision, latencyMs)

        // 6. 캐시 저장 (Fast Path만)
        if (config.cacheConfig.enabled && gatingDecision == GatingDecision.FAST_PATH) {
            val cacheKey = buildCacheKey(utterance, context)
            cache.put(cacheKey, decision, config.cacheConfig.l1Ttl)
        }

        return SpiceResult.success(decision)
    }

    override fun gate(
        semanticScore: Double,
        secondScore: Double,
        domainRelevance: Double
    ): GatingDecision = config.gate(semanticScore, secondScore, domainRelevance)

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

        // SemanticMatcher 호출
        val scores = semanticMatcher.match(utterance, options)

        // 결과 정렬 및 추출
        val scoredCandidates = context.routingOptions.mapIndexed { index, option ->
            ScoredCandidate(
                canonical = option.canonical,
                score = scores.getOrElse(index) { 0.0 },
                label = option.description
            )
        }.sortedByDescending { it.score }

        val topCandidate = scoredCandidates.firstOrNull()
        val secondScore = scoredCandidates.getOrNull(1)?.score ?: 0.0

        // domainRelevance 계산
        // 현재는 topScore를 사용하지만, 실제로는 별도 모델/로직 필요
        // TODO: Phase 3에서 Policy-RAG 컨텍스트 기반 domainRelevance 계산
        val domainRelevance = calculateDomainRelevance(utterance, context, topCandidate?.score ?: 0.0)

        return SemanticMatchResult(
            topScore = topCandidate?.score ?: 0.0,
            secondScore = secondScore,
            topCanonical = topCandidate?.canonical,
            candidates = scoredCandidates,
            optionCount = options.size,
            domainRelevance = domainRelevance
        )
    }

    /**
     * 도메인 관련성 계산
     *
     * ## 오메가 지침
     * embedding score와 별개로 계산!
     * 현재는 간단한 휴리스틱, Phase 3에서 Policy-RAG 기반으로 개선
     */
    private fun calculateDomainRelevance(
        utterance: String,
        context: IntelligenceContext,
        topScore: Double
    ): Double {
        // 현재 구현: 워크플로우가 지정되어 있으면 0.7 기본값
        // 실제로는 Policy-RAG / 도메인 분류기 필요
        val baseRelevance = if (context.workflowId != null) 0.7 else 0.5

        // topScore가 높으면 도메인 관련성도 약간 높게
        // 하지만 topScore 단독으로 결정하지 않음!
        val scoreBonus = (topScore * 0.2).coerceAtMost(0.2)

        return (baseRelevance + scoreBonus).coerceAtMost(1.0)
    }

    /**
     * 결과 생성
     */
    private fun buildDecision(
        matchResult: SemanticMatchResult,
        gatingDecision: GatingDecision,
        latencyMs: Long
    ): CompositeDecision {
        return when (gatingDecision) {
            GatingDecision.FAST_PATH -> CompositeDecision(
                domainRelevance = matchResult.domainRelevance,
                aiCanonical = matchResult.topCanonical,
                confidence = matchResult.topScore,
                reasoning = "Fast path: score=${matchResult.topScore}, gap=${matchResult.gap}, domain=${matchResult.domainRelevance}",
                decisionSource = DecisionSource.EMBEDDING,
                routingSignal = RoutingSignal.NORMAL,
                latencyMs = latencyMs
            )

            GatingDecision.NANO_VALIDATION -> CompositeDecision(
                domainRelevance = matchResult.domainRelevance,
                aiCanonical = matchResult.topCanonical,  // 후보로 제공
                confidence = matchResult.topScore,
                reasoning = "Nano validation required: score=${matchResult.topScore}, gap=${matchResult.gap}",
                decisionSource = DecisionSource.EMBEDDING,
                routingSignal = RoutingSignal.DELEGATE_TO_LLM,  // Nano에게 위임
                latencyMs = latencyMs
            )

            GatingDecision.BIG_LLM -> CompositeDecision(
                domainRelevance = matchResult.domainRelevance,
                aiCanonical = null,  // LLM이 결정
                confidence = matchResult.topScore,
                reasoning = "Big LLM required: low confidence or complex case",
                decisionSource = DecisionSource.EMBEDDING,
                routingSignal = RoutingSignal.DELEGATE_TO_LLM,
                latencyMs = latencyMs
            )
        }
    }

    /**
     * 캐시 키 생성 (레이어 + 테넌트 포함)
     */
    private fun buildCacheKey(
        utterance: String,
        context: IntelligenceContext
    ): IntelligenceCacheKey {
        return IntelligenceCacheKey.from(
            layer = CacheLayer.SEMANTIC,
            tenantId = context.tenantId,
            utterance = utterance,
            prevNodes = context.prevNodes,
            workflowId = context.workflowId
        )
    }

    companion object {
        /**
         * 테스트용 더미 인스턴스
         */
        fun dummy(config: IntelligenceConfig = IntelligenceConfig.TEST): DefaultIntelligenceResolver {
            val dummyMatcher = object : SemanticMatcher {
                override val id: String = "dummy"
                override suspend fun match(utterance: String, options: List<String>): List<Double> {
                    // 첫 번째 옵션에 0.9 부여
                    return options.mapIndexed { index, _ -> if (index == 0) 0.9 else 0.3 }
                }
            }
            return DefaultIntelligenceResolver(
                semanticMatcher = dummyMatcher,
                cache = InMemoryIntelligenceCache.NO_OP,
                config = config
            )
        }

        /**
         * InMemory 구현체로 생성 (테스트/개발용)
         */
        fun inMemory(
            semanticMatcher: SemanticMatcher,
            config: IntelligenceConfig = IntelligenceConfig.DEFAULT
        ): DefaultIntelligenceResolver {
            return DefaultIntelligenceResolver(
                semanticMatcher = semanticMatcher,
                cache = InMemoryIntelligenceCache(),
                config = config
            )
        }
    }
}
