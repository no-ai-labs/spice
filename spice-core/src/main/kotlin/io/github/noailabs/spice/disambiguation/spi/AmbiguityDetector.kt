package io.github.noailabs.spice.disambiguation.spi

import io.github.noailabs.spice.disambiguation.AmbiguityResult
import io.github.noailabs.spice.disambiguation.DisambiguationConfig
import io.github.noailabs.spice.retrieval.Candidate

/**
 * 모호성 감지 SPI
 *
 * 검색 결과에서 모호성을 감지합니다.
 * 도메인별 구현체가 kai-core 등에서 제공됩니다.
 *
 * ## 구현 예시
 * ```kotlin
 * class ReservationAmbiguityDetector : AmbiguityDetector<Reservation> {
 *     override val domain = "reservation"
 *
 *     override suspend fun detect(
 *         candidates: List<Candidate<Reservation>>,
 *         config: DisambiguationConfig
 *     ): AmbiguityDetectionResult {
 *         if (candidates.isEmpty()) {
 *             return AmbiguityDetectionResult.none()
 *         }
 *         if (candidates.size == 1) {
 *             return AmbiguityDetectionResult.single(candidates.first())
 *         }
 *
 *         val sorted = candidates.sortedByDescending { it.score ?: 0.0 }
 *         val topScore = sorted.first().score ?: 0.0
 *         val secondScore = sorted.getOrNull(1)?.score ?: 0.0
 *         val gap = topScore - secondScore
 *
 *         return if (topScore >= config.exactThreshold && gap >= config.gapThreshold) {
 *             AmbiguityDetectionResult.single(sorted.first())
 *         } else {
 *             AmbiguityDetectionResult.ambiguous(sorted)
 *         }
 *     }
 * }
 * ```
 *
 * @param T 후보 페이로드 타입
 *
 * @since 1.1.0
 */
interface AmbiguityDetector<T> {

    /**
     * 도메인 식별자
     */
    val domain: String

    /**
     * 모호성 감지
     *
     * @param candidates 검색된 후보 리스트
     * @param config 모호성 해소 설정
     * @return 감지 결과
     */
    suspend fun detect(
        candidates: List<Candidate<T>>,
        config: DisambiguationConfig
    ): AmbiguityDetectionResult<T>

    /**
     * 기본 구현: 점수 기반 모호성 감지
     */
    suspend fun detectByScore(
        candidates: List<Candidate<T>>,
        config: DisambiguationConfig
    ): AmbiguityDetectionResult<T> {
        if (candidates.isEmpty()) {
            return AmbiguityDetectionResult.none()
        }

        val sorted = candidates.sortedByDescending { it.score ?: 0.0 }

        if (sorted.size == 1) {
            val score = sorted.first().score ?: 0.0
            return if (score >= config.fuzzyThreshold) {
                AmbiguityDetectionResult.single(sorted.first())
            } else {
                AmbiguityDetectionResult.none()
            }
        }

        val topScore = sorted.first().score ?: 0.0
        val secondScore = sorted[1].score ?: 0.0
        val gap = topScore - secondScore

        val result = config.determineAmbiguity(topScore, secondScore, sorted.size)

        return when (result) {
            AmbiguityResult.SINGLE -> AmbiguityDetectionResult.single(sorted.first())
            AmbiguityResult.AMBIGUOUS -> AmbiguityDetectionResult.ambiguous(sorted, gap)
            AmbiguityResult.NONE -> AmbiguityDetectionResult.none()
        }
    }
}

/**
 * 모호성 감지 결과
 *
 * @param T 후보 페이로드 타입
 * @property result 모호성 결과
 * @property selectedCandidate 선택된 후보 (SINGLE일 때)
 * @property ambiguousCandidates 모호한 후보들 (AMBIGUOUS일 때)
 * @property gap 1위-2위 점수 격차
 * @property metadata 추가 메타데이터
 */
data class AmbiguityDetectionResult<T>(
    val result: AmbiguityResult,
    val selectedCandidate: Candidate<T>? = null,
    val ambiguousCandidates: List<Candidate<T>> = emptyList(),
    val gap: Double? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * 단건 확정인지 여부
     */
    val isSingle: Boolean
        get() = result == AmbiguityResult.SINGLE

    /**
     * 모호한지 여부
     */
    val isAmbiguous: Boolean
        get() = result == AmbiguityResult.AMBIGUOUS

    /**
     * 매칭 없음인지 여부
     */
    val isNone: Boolean
        get() = result == AmbiguityResult.NONE

    /**
     * Clarification이 필요한지 여부
     */
    val needsClarification: Boolean
        get() = isAmbiguous

    /**
     * 자동 선택 가능한지 여부
     */
    val canAutoSelect: Boolean
        get() = isSingle && selectedCandidate != null

    /**
     * 모호한 후보 개수
     */
    val ambiguousCount: Int
        get() = ambiguousCandidates.size

    companion object {
        /**
         * 단건 확정 결과 생성
         */
        fun <T> single(candidate: Candidate<T>): AmbiguityDetectionResult<T> =
            AmbiguityDetectionResult(
                result = AmbiguityResult.SINGLE,
                selectedCandidate = candidate
            )

        /**
         * 모호함 결과 생성
         */
        fun <T> ambiguous(
            candidates: List<Candidate<T>>,
            gap: Double? = null
        ): AmbiguityDetectionResult<T> =
            AmbiguityDetectionResult(
                result = AmbiguityResult.AMBIGUOUS,
                ambiguousCandidates = candidates,
                gap = gap
            )

        /**
         * 매칭 없음 결과 생성
         */
        fun <T> none(): AmbiguityDetectionResult<T> =
            AmbiguityDetectionResult(result = AmbiguityResult.NONE)
    }
}

/**
 * 기본 모호성 감지기 (점수 기반)
 *
 * 도메인 특화 로직 없이 점수만으로 판단.
 */
class DefaultAmbiguityDetector<T>(
    override val domain: String = "default"
) : AmbiguityDetector<T> {

    override suspend fun detect(
        candidates: List<Candidate<T>>,
        config: DisambiguationConfig
    ): AmbiguityDetectionResult<T> = detectByScore(candidates, config)
}

/**
 * 복합 모호성 감지기
 *
 * 점수 + 추가 조건을 모두 확인.
 *
 * @param T 후보 페이로드 타입
 * @param domain 도메인 식별자
 * @param additionalCheck 추가 검증 함수 (true면 SINGLE, false면 AMBIGUOUS)
 */
class CompositeAmbiguityDetector<T>(
    override val domain: String,
    private val additionalCheck: suspend (List<Candidate<T>>, Candidate<T>) -> Boolean = { _, _ -> true }
) : AmbiguityDetector<T> {

    override suspend fun detect(
        candidates: List<Candidate<T>>,
        config: DisambiguationConfig
    ): AmbiguityDetectionResult<T> {
        val scoreResult = detectByScore(candidates, config)

        // SINGLE 결과에 추가 검증
        if (scoreResult.isSingle && scoreResult.selectedCandidate != null) {
            val passesAdditionalCheck = additionalCheck(candidates, scoreResult.selectedCandidate)
            if (!passesAdditionalCheck) {
                return AmbiguityDetectionResult.ambiguous(
                    candidates.sortedByDescending { it.score ?: 0.0 },
                    scoreResult.gap
                )
            }
        }

        return scoreResult
    }
}
