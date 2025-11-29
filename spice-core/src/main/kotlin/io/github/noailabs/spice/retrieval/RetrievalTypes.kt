package io.github.noailabs.spice.retrieval

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 검색 결과 이유
 *
 * 후보 검색 결과의 품질을 나타냅니다.
 *
 * @since 1.1.0
 */
@Serializable
enum class RetrievalReason {
    /**
     * 정확히 1건 매칭
     * - 유사도 >= 0.85
     * - 격차 >= 0.15 (다음 후보와 차이)
     * - 자동 선택 가능
     */
    EXACT,

    /**
     * 높은 확률 매칭
     * - 유사도 >= 0.70
     * - 자동 선택 가능하나 확인 권장
     */
    FUZZY,

    /**
     * 모호함 (Clarification 필요)
     * - 유사도 >= 0.60
     * - 격차 < 0.10 (후보 간 차이 작음)
     * - HITL 질문 필요
     */
    AMBIGUOUS,

    /**
     * 매칭 없음
     * - 유사도 < 0.50
     * - 또는 후보가 0건
     * - 직접 입력 요청 또는 다른 워크플로우
     */
    NONE
}

/**
 * 검색 결과
 *
 * @param T 후보 페이로드 타입
 * @property candidates 검색된 후보 리스트 (점수 내림차순 정렬 권장)
 * @property reason 검색 결과 이유
 * @property query 원본 검색 쿼리
 * @property metadata 추가 메타데이터 (검색 시간, 총 개수 등)
 *
 * @since 1.1.0
 */
@Serializable
data class RetrievalResult<T>(
    val candidates: List<Candidate<T>>,
    val reason: RetrievalReason,
    val query: String,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 후보 개수
     */
    val count: Int
        get() = candidates.size

    /**
     * 후보가 있는지 여부
     */
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    /**
     * 단일 후보인지 여부
     */
    val isSingle: Boolean
        get() = candidates.size == 1

    /**
     * 다중 후보인지 여부
     */
    val isMultiple: Boolean
        get() = candidates.size > 1

    /**
     * 자동 선택 가능 여부 (EXACT 또는 단일 FUZZY)
     */
    val canAutoSelect: Boolean
        get() = reason == RetrievalReason.EXACT ||
                (reason == RetrievalReason.FUZZY && candidates.size == 1)

    /**
     * Clarification 필요 여부
     */
    val needsClarification: Boolean
        get() = reason == RetrievalReason.AMBIGUOUS || (reason == RetrievalReason.FUZZY && candidates.size > 1)

    /**
     * 최상위 후보 (점수 기준)
     */
    val topCandidate: Candidate<T>?
        get() = candidates.maxByOrNull { it.score ?: 0.0 }

    /**
     * 최상위 점수
     */
    val topScore: Double?
        get() = topCandidate?.score

    /**
     * 상위 N개 후보
     */
    fun topN(n: Int): List<Candidate<T>> = candidates
        .sortedByDescending { it.score ?: 0.0 }
        .take(n)

    /**
     * 임계값 이상 후보만 필터링
     */
    fun aboveThreshold(threshold: Double): RetrievalResult<T> = copy(
        candidates = candidates.filter { (it.score ?: 0.0) >= threshold }
    )

    /**
     * 후보 ID 리스트
     */
    fun candidateIds(): List<String> = candidates.map { it.id }

    /**
     * 후보 라벨 리스트
     */
    fun candidateLabels(): List<String> = candidates.map { it.displayLabel }

    companion object {
        /**
         * 빈 결과 생성
         */
        fun <T> empty(query: String): RetrievalResult<T> = RetrievalResult(
            candidates = emptyList(),
            reason = RetrievalReason.NONE,
            query = query
        )

        /**
         * 단일 EXACT 결과 생성
         */
        fun <T> exact(candidate: Candidate<T>, query: String): RetrievalResult<T> = RetrievalResult(
            candidates = listOf(candidate),
            reason = RetrievalReason.EXACT,
            query = query
        )

        /**
         * 모호한 결과 생성
         */
        fun <T> ambiguous(
            candidates: List<Candidate<T>>,
            query: String
        ): RetrievalResult<T> = RetrievalResult(
            candidates = candidates,
            reason = RetrievalReason.AMBIGUOUS,
            query = query
        )

        /**
         * 후보 리스트에서 RetrievalReason 자동 결정
         *
         * @param candidates 후보 리스트
         * @param query 검색 쿼리
         * @param exactThreshold EXACT 판정 임계값 (기본: 0.85)
         * @param fuzzyThreshold FUZZY 판정 임계값 (기본: 0.70)
         * @param gapThreshold 격차 임계값 (기본: 0.15)
         */
        fun <T> fromCandidates(
            candidates: List<Candidate<T>>,
            query: String,
            exactThreshold: Double = 0.85,
            fuzzyThreshold: Double = 0.70,
            gapThreshold: Double = 0.15
        ): RetrievalResult<T> {
            if (candidates.isEmpty()) {
                return empty(query)
            }

            val sorted = candidates.sortedByDescending { it.score ?: 0.0 }
            val topScore = sorted.first().score ?: 0.0
            val secondScore = sorted.getOrNull(1)?.score ?: 0.0
            val gap = topScore - secondScore

            val reason = when {
                // 단일 후보 + 높은 점수
                sorted.size == 1 && topScore >= fuzzyThreshold -> RetrievalReason.EXACT

                // 복수 후보지만 확실한 1위
                topScore >= exactThreshold && gap >= gapThreshold -> RetrievalReason.EXACT

                // 높은 점수지만 격차 부족
                topScore >= fuzzyThreshold && gap < gapThreshold -> RetrievalReason.AMBIGUOUS

                // 중간 점수
                topScore >= fuzzyThreshold -> RetrievalReason.FUZZY

                // 낮은 점수
                topScore >= 0.50 -> RetrievalReason.AMBIGUOUS

                // 매칭 없음
                else -> RetrievalReason.NONE
            }

            return RetrievalResult(
                candidates = sorted,
                reason = reason,
                query = query,
                metadata = mapOf(
                    "topScore" to topScore,
                    "gap" to gap,
                    "candidateCount" to candidates.size
                )
            )
        }
    }
}

/**
 * 검색 요청
 *
 * @property query 검색 쿼리
 * @property domain 검색 도메인 (e.g., "reservation", "option")
 * @property tenantId 테넌트 ID
 * @property userId 사용자 ID
 * @property limit 최대 결과 개수
 * @property threshold 최소 유사도 임계값
 * @property filters 추가 필터 조건
 * @property metadata 추가 메타데이터
 *
 * @since 1.1.0
 */
@Serializable
data class RetrievalRequest(
    val query: String,
    val domain: String,
    val tenantId: String? = null,
    val userId: String? = null,
    val limit: Int = 10,
    val threshold: Double = 0.50,
    val filters: Map<String, @Contextual Any> = emptyMap(),
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 필터 추가 (불변성 유지)
     */
    fun withFilter(key: String, value: Any): RetrievalRequest =
        copy(filters = filters + (key to value))

    /**
     * 메타데이터 추가 (불변성 유지)
     */
    fun withMetadata(key: String, value: Any): RetrievalRequest =
        copy(metadata = metadata + (key to value))

    companion object {
        /**
         * 간단한 검색 요청 생성
         */
        fun simple(query: String, domain: String): RetrievalRequest =
            RetrievalRequest(query = query, domain = domain)
    }
}

/**
 * 검색 통계
 *
 * 검색 성능 및 품질 모니터링용.
 *
 * @property totalCandidates 전체 후보 수
 * @property filteredCandidates 필터 후 후보 수
 * @property retrievalTimeMs 검색 소요 시간 (밀리초)
 * @property embeddingTimeMs 임베딩 소요 시간 (밀리초)
 * @property matchingTimeMs 매칭 소요 시간 (밀리초)
 * @property cacheHit 캐시 히트 여부
 */
@Serializable
data class RetrievalStats(
    val totalCandidates: Int,
    val filteredCandidates: Int,
    val retrievalTimeMs: Long,
    val embeddingTimeMs: Long? = null,
    val matchingTimeMs: Long? = null,
    val cacheHit: Boolean = false
) {
    /**
     * 총 처리 시간 (밀리초)
     */
    val totalTimeMs: Long
        get() = retrievalTimeMs + (embeddingTimeMs ?: 0) + (matchingTimeMs ?: 0)

    /**
     * 필터링 비율
     */
    val filterRatio: Double
        get() = if (totalCandidates > 0) {
            filteredCandidates.toDouble() / totalCandidates
        } else 0.0
}
