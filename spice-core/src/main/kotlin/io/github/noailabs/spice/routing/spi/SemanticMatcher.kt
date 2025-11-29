package io.github.noailabs.spice.routing.spi

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.serialization.Serializable

/**
 * 시멘틱 매칭 SPI
 *
 * 사용자 발화와 옵션들 간의 시멘틱 유사도를 계산합니다.
 * 구현체: Embedding 기반, 키워드 기반 등
 *
 * ## 구현 예시 (Embedding 기반)
 * ```kotlin
 * class EmbeddingSemanticMatcher(
 *     private val embeddingClient: EmbeddingClient
 * ) : SemanticMatcher {
 *     override suspend fun match(utterance: String, options: List<String>): List<Double> {
 *         val allTexts = listOf(utterance) + options
 *         val embeddings = embeddingClient.embedBatch(allTexts)
 *         val userVector = embeddings[0]
 *         return embeddings.drop(1).map { VectorUtils.cosine(userVector, it) }
 *     }
 * }
 * ```
 *
 * @since 1.1.0
 */
interface SemanticMatcher {

    /**
     * 매처 식별자
     */
    val id: String
        get() = this::class.simpleName ?: "unknown"

    /**
     * 발화와 옵션들 간 시멘틱 유사도 계산
     *
     * @param utterance 사용자 발화
     * @param options 비교할 옵션 텍스트들
     * @return 각 옵션의 유사도 점수 리스트 (0.0~1.0, 옵션 순서와 동일)
     */
    suspend fun match(
        utterance: String,
        options: List<String>
    ): List<Double>

    /**
     * 최적 매칭 찾기
     *
     * @param utterance 사용자 발화
     * @param options 비교할 옵션 텍스트들
     * @param threshold 최소 매칭 임계값 (기본: 0.65)
     * @return 최적 매칭 인덱스 (0-based), 임계값 미달 시 null
     */
    suspend fun resolveBestMatch(
        utterance: String,
        options: List<String>,
        threshold: Double = 0.65
    ): Int? {
        if (options.isEmpty()) return null

        val scores = match(utterance, options)
        val maxScore = scores.maxOrNull() ?: return null
        val maxIndex = scores.indexOf(maxScore)

        return if (maxScore >= threshold) maxIndex else null
    }

    /**
     * 상세 매칭 결과 + 설명 (Explanation Mode)
     *
     * 디버깅/A/B 테스트/신뢰도 검증에 활용
     * - 각 옵션별 점수
     * - 선택/미선택 이유
     * - 토큰 레벨 분석 (선택 사항)
     *
     * @param utterance 사용자 발화
     * @param options 비교할 옵션 텍스트들
     * @param threshold 최소 매칭 임계값
     * @return 상세 매칭 결과
     */
    suspend fun matchWithExplanation(
        utterance: String,
        options: List<String>,
        threshold: Double = 0.65
    ): SemanticMatchExplanation {
        if (options.isEmpty()) {
            return SemanticMatchExplanation(
                utterance = utterance,
                options = options,
                scores = emptyList(),
                bestIndex = null,
                bestScore = null,
                gap = null,
                threshold = threshold,
                reason = MatchExplanationReason.NO_OPTIONS
            )
        }

        val scores = match(utterance, options)
        val sortedScores = scores.sortedDescending()
        val bestScore = sortedScores.first()
        val bestIndex = scores.indexOf(bestScore)
        val secondScore = sortedScores.getOrNull(1) ?: 0.0
        val gap = bestScore - secondScore

        val reason = when {
            bestScore < threshold -> MatchExplanationReason.BELOW_THRESHOLD
            gap < 0.10 && options.size > 1 -> MatchExplanationReason.AMBIGUOUS_GAP
            bestScore >= 0.85 && gap >= 0.15 -> MatchExplanationReason.EXACT_MATCH
            else -> MatchExplanationReason.FUZZY_MATCH
        }

        return SemanticMatchExplanation(
            utterance = utterance,
            options = options,
            scores = scores,
            bestIndex = if (bestScore >= threshold) bestIndex else null,
            bestScore = bestScore,
            gap = gap,
            threshold = threshold,
            reason = reason
        )
    }
}

/**
 * 시멘틱 매칭 상세 결과 (Explanation Mode)
 *
 * @property utterance 원본 발화
 * @property options 후보 옵션들
 * @property scores 각 옵션별 점수
 * @property bestIndex 최적 매칭 인덱스 (null if below threshold)
 * @property bestScore 최적 점수
 * @property gap top-second 격차
 * @property threshold 사용된 임계값
 * @property reason 선택/미선택 이유
 * @property tokenAnalysis 토큰 레벨 분석 (선택 사항)
 */
@Serializable
data class SemanticMatchExplanation(
    val utterance: String,
    val options: List<String>,
    val scores: List<Double>,
    val bestIndex: Int?,
    val bestScore: Double?,
    val gap: Double?,
    val threshold: Double,
    val reason: MatchExplanationReason,
    val tokenAnalysis: List<TokenMatchInfo>? = null
) {
    /**
     * 매칭 성공 여부
     */
    val isMatched: Boolean
        get() = bestIndex != null

    /**
     * 매칭이 확실한지 여부
     */
    val isCertain: Boolean
        get() = reason == MatchExplanationReason.EXACT_MATCH

    /**
     * 모호한 매칭인지 여부
     */
    val isAmbiguous: Boolean
        get() = reason == MatchExplanationReason.AMBIGUOUS_GAP

    /**
     * 최적 매칭된 옵션 텍스트
     */
    val bestOption: String?
        get() = bestIndex?.let { options.getOrNull(it) }

    /**
     * 상위 N개 옵션 인덱스와 점수
     */
    fun topN(n: Int): List<Pair<Int, Double>> {
        return scores.mapIndexed { index, score -> index to score }
            .sortedByDescending { it.second }
            .take(n)
    }

    /**
     * 로그용 요약 문자열
     */
    fun toLogSummary(): String = buildString {
        append("SemanticMatch[")
        append("utterance=\"$utterance\", ")
        append("reason=$reason, ")
        append("bestScore=${"%.3f".format(bestScore)}, ")
        append("gap=${"%.3f".format(gap)}, ")
        append("threshold=$threshold")
        if (bestIndex != null) {
            append(", matched=\"${options.getOrNull(bestIndex)}\"")
        }
        append("]")
    }
}

/**
 * 매칭 설명 이유
 */
@Serializable
enum class MatchExplanationReason {
    /** 정확 매칭 (threshold 초과 + 격차 충분) */
    EXACT_MATCH,

    /** 퍼지 매칭 (threshold 초과) */
    FUZZY_MATCH,

    /** 임계값 미달 */
    BELOW_THRESHOLD,

    /** 격차 부족 (1위/2위 차이 작음) */
    AMBIGUOUS_GAP,

    /** 옵션 없음 */
    NO_OPTIONS,

    /** 임베딩 오류 */
    EMBEDDING_ERROR
}

/**
 * 토큰 레벨 매칭 분석 (선택 사항)
 *
 * @property token 발화 내 토큰
 * @property matchedOptionIndex 매칭된 옵션 인덱스
 * @property contribution 점수 기여도 (0.0~1.0)
 */
@Serializable
data class TokenMatchInfo(
    val token: String,
    val matchedOptionIndex: Int?,
    val contribution: Double
)

/**
 * 시멘틱 매칭 컨텍스트
 *
 * 매칭 시 추가 정보를 제공하기 위한 컨텍스트.
 *
 * @property locale 언어/지역 (ko, en, ja 등)
 * @property domain 도메인 (reservation, option 등)
 * @property tenantId 테넌트 ID
 * @property userId 사용자 ID
 * @property metadata 추가 메타데이터
 */
@Serializable
data class SemanticMatchContext(
    val locale: String = "ko",
    val domain: String? = null,
    val tenantId: String? = null,
    val userId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 확장 시멘틱 매처 (컨텍스트 지원)
 *
 * 기본 SemanticMatcher를 확장하여 컨텍스트 기반 매칭 지원.
 */
interface ContextualSemanticMatcher : SemanticMatcher {

    /**
     * 컨텍스트 기반 시멘틱 매칭
     *
     * @param utterance 사용자 발화
     * @param options 비교할 옵션 텍스트들
     * @param context 매칭 컨텍스트
     * @return 각 옵션의 유사도 점수 리스트
     */
    suspend fun matchWithContext(
        utterance: String,
        options: List<String>,
        context: SemanticMatchContext
    ): List<Double>

    /**
     * 기본 match는 기본 컨텍스트로 호출
     */
    override suspend fun match(utterance: String, options: List<String>): List<Double> =
        matchWithContext(utterance, options, SemanticMatchContext())
}

/**
 * SpiceMessage 기반 시멘틱 매처
 *
 * SpiceMessage의 메타데이터를 활용한 매칭.
 */
interface MessageAwareSemanticMatcher : SemanticMatcher {

    /**
     * SpiceMessage 기반 시멘틱 매칭
     *
     * @param message SpiceMessage (content, metadata 활용)
     * @param options 비교할 옵션 텍스트들
     * @return 각 옵션의 유사도 점수 리스트
     */
    suspend fun matchMessage(
        message: SpiceMessage,
        options: List<String>
    ): List<Double>

    /**
     * SpiceMessage 기반 최적 매칭
     */
    suspend fun resolveBestMatchFromMessage(
        message: SpiceMessage,
        options: List<String>,
        threshold: Double = 0.65
    ): Int? {
        if (options.isEmpty()) return null

        val scores = matchMessage(message, options)
        val maxScore = scores.maxOrNull() ?: return null
        val maxIndex = scores.indexOf(maxScore)

        return if (maxScore >= threshold) maxIndex else null
    }
}

/**
 * 시멘틱 매처 팩토리
 */
object SemanticMatchers {

    /**
     * 항상 동일한 점수를 반환하는 더미 매처 (테스트용)
     */
    fun dummy(scores: List<Double>): SemanticMatcher = object : SemanticMatcher {
        override val id: String = "dummy"
        override suspend fun match(utterance: String, options: List<String>): List<Double> {
            return if (options.size == scores.size) scores
            else options.map { 0.5 }
        }
    }

    /**
     * 첫 번째 옵션에 높은 점수를 부여하는 매처 (테스트용)
     */
    fun firstOption(): SemanticMatcher = object : SemanticMatcher {
        override val id: String = "first-option"
        override suspend fun match(utterance: String, options: List<String>): List<Double> {
            return options.mapIndexed { index, _ -> if (index == 0) 0.9 else 0.3 }
        }
    }

    /**
     * 키워드 기반 매처 (Fallback용)
     *
     * @param keywords 옵션별 키워드 맵
     */
    fun keyword(keywords: Map<Int, List<String>>): SemanticMatcher = object : SemanticMatcher {
        override val id: String = "keyword"
        override suspend fun match(utterance: String, options: List<String>): List<Double> {
            val normalized = utterance.lowercase()
            return options.mapIndexed { index, _ ->
                val optionKeywords = keywords[index] ?: emptyList()
                val matchCount = optionKeywords.count { normalized.contains(it.lowercase()) }
                if (optionKeywords.isEmpty()) 0.5
                else matchCount.toDouble() / optionKeywords.size
            }
        }
    }
}
