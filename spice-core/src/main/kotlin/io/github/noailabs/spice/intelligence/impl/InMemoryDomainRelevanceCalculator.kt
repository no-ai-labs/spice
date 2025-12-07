package io.github.noailabs.spice.intelligence.impl

import io.github.noailabs.spice.intelligence.spi.DomainRelevanceCalculator
import io.github.noailabs.spice.intelligence.spi.DomainRelevanceContext
import io.github.noailabs.spice.intelligence.spi.DomainRelevanceResult
import io.github.noailabs.spice.intelligence.spi.DomainRelevanceSource

/**
 * InMemory Domain Relevance Calculator (테스트용)
 *
 * 휴리스틱 기반의 간단한 도메인 관련성 계산.
 * 실제 구현에서는 Policy-RAG 또는 분류기 사용.
 *
 * ## 휴리스틱 규칙
 * - workflowId 있으면 기본 0.7
 * - Policy hint 매칭 시 가산점
 * - 특정 키워드 포함 시 가감점
 *
 * @since 2.0.0
 */
class InMemoryDomainRelevanceCalculator(
    private val baseScore: Double = 0.7,
    private val offDomainKeywords: Set<String> = DEFAULT_OFF_DOMAIN_KEYWORDS
) : DomainRelevanceCalculator {

    override val sourceType: DomainRelevanceSource = DomainRelevanceSource.HEURISTIC

    override suspend fun calculate(
        utterance: String,
        context: DomainRelevanceContext
    ): DomainRelevanceResult {
        var score = if (context.workflowId != null) baseScore else 0.5
        val reasons = mutableListOf<String>()

        // 1. Off-domain 키워드 체크
        val normalizedUtterance = utterance.lowercase()
        val offDomainMatches = offDomainKeywords.count { normalizedUtterance.contains(it) }
        if (offDomainMatches > 0) {
            score -= (offDomainMatches * 0.2)
            reasons.add("off-domain keywords: $offDomainMatches")
        }

        // 2. Policy hint 매칭 보너스
        val policyMatchCount = context.policyHints.size
        if (policyMatchCount > 0) {
            val policyBonus = (policyMatchCount * 0.1).coerceAtMost(0.2)
            score += policyBonus
            reasons.add("policy hints: $policyMatchCount (+$policyBonus)")
        }

        // 3. Semantic score 참고 (약간만)
        context.semanticScore?.let { semanticScore ->
            if (semanticScore > 0.8) {
                score += 0.1
                reasons.add("high semantic score (+0.1)")
            }
        }

        // 점수 범위 제한
        score = score.coerceIn(0.0, 1.0)

        return DomainRelevanceResult(
            score = score,
            source = DomainRelevanceSource.HEURISTIC,
            reasoning = reasons.joinToString(", ").ifEmpty { "default heuristic" },
            policyMatchCount = policyMatchCount
        )
    }

    companion object {
        /**
         * 기본 Off-domain 키워드 (한국어)
         */
        val DEFAULT_OFF_DOMAIN_KEYWORDS = setOf(
            "날씨", "weather",
            "농담", "joke",
            "게임", "game",
            "주식", "stock",
            "정치", "politics"
        )

        /**
         * 항상 높은 점수를 반환하는 인스턴스 (테스트용)
         */
        fun alwaysInDomain() = object : DomainRelevanceCalculator {
            override val sourceType = DomainRelevanceSource.HEURISTIC
            override suspend fun calculate(utterance: String, context: DomainRelevanceContext) =
                DomainRelevanceResult.heuristic(0.9, "always in-domain (test)")
        }

        /**
         * 항상 낮은 점수를 반환하는 인스턴스 (테스트용)
         */
        fun alwaysOffDomain() = object : DomainRelevanceCalculator {
            override val sourceType = DomainRelevanceSource.HEURISTIC
            override suspend fun calculate(utterance: String, context: DomainRelevanceContext) =
                DomainRelevanceResult.heuristic(0.1, "always off-domain (test)")
        }

        /**
         * Policy-RAG 기반 (stub)
         */
        fun policyBased() = object : DomainRelevanceCalculator {
            override val sourceType = DomainRelevanceSource.POLICY_RAG
            override suspend fun calculate(utterance: String, context: DomainRelevanceContext): DomainRelevanceResult {
                val policyCount = context.policyHints.size
                val score = if (policyCount > 0) {
                    val avgScore = context.policyHints.map { it.score }.average()
                    (0.5 + avgScore * 0.5).coerceIn(0.0, 1.0)
                } else {
                    0.3 // 정책 매칭 없으면 낮은 점수
                }
                return DomainRelevanceResult.fromPolicyRAG(
                    score = score,
                    policyMatchCount = policyCount,
                    reasoning = "policy-based: $policyCount policies matched"
                )
            }
        }
    }
}
