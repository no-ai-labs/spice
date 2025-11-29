package io.github.noailabs.spice.retrieval

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 범용 후보 래퍼
 *
 * 도메인 객체를 감싸서 시멘틱 매칭/UI 표시에 필요한 정보를 제공합니다.
 * Spice 범용 인터페이스로, Reservation, Option, Product 등 다양한 도메인에 적용 가능.
 *
 * ## 사용 예시
 * ```kotlin
 * // 예약 후보
 * val candidate = Candidate(
 *     id = "rsv-123",
 *     displayLabel = "스테이폴리오 제주점",
 *     description = "2024-01-15 | 홍길동",
 *     score = 0.87,
 *     slots = mapOf(
 *         "place_name" to "스테이폴리오 제주점",
 *         "check_in" to "2024-01-15",
 *         "guest_name" to "홍길동"
 *     ),
 *     payload = reservation
 * )
 * ```
 *
 * @param T 원본 도메인 객체 타입
 * @property id 고유 식별자
 * @property displayLabel UI 표시용 라벨
 * @property description 부가 설명 (선택)
 * @property score 유사도 점수 (0.0~1.0, 선택)
 * @property slots 템플릿 참조용 슬롯 (Clarification 메시지 생성용)
 * @property payload 원본 도메인 객체
 * @property metadata 추가 메타데이터
 *
 * @since 1.1.0
 */
@Serializable
data class Candidate<T>(
    val id: String,
    val displayLabel: String,
    val description: String? = null,
    val score: Double? = null,
    val slots: Map<String, @Contextual Any?> = emptyMap(),
    @Contextual
    val payload: T,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 점수가 설정되었는지 여부
     */
    val hasScore: Boolean
        get() = score != null

    /**
     * 높은 신뢰도 여부 (score >= 0.85)
     */
    val isHighConfidence: Boolean
        get() = score != null && score >= 0.85

    /**
     * 중간 신뢰도 여부 (0.65 <= score < 0.85)
     */
    val isMediumConfidence: Boolean
        get() = score != null && score in 0.65..<0.85

    /**
     * 낮은 신뢰도 여부 (score < 0.65)
     */
    val isLowConfidence: Boolean
        get() = score != null && score < 0.65

    /**
     * 특정 슬롯 값 조회 (타입 안전)
     */
    @Suppress("UNCHECKED_CAST")
    fun <V> getSlot(key: String): V? = slots[key] as? V

    /**
     * 슬롯 값 조회 (기본값 포함)
     */
    @Suppress("UNCHECKED_CAST")
    fun <V> getSlotOrDefault(key: String, default: V): V =
        (slots[key] as? V) ?: default

    /**
     * 점수 업데이트 (불변성 유지)
     */
    fun withScore(newScore: Double): Candidate<T> = copy(score = newScore)

    /**
     * 슬롯 추가 (불변성 유지)
     */
    fun withSlot(key: String, value: Any?): Candidate<T> =
        copy(slots = slots + (key to value))

    /**
     * 슬롯 병합 (불변성 유지)
     */
    fun withSlots(newSlots: Map<String, Any?>): Candidate<T> =
        copy(slots = slots + newSlots)

    /**
     * 메타데이터 추가 (불변성 유지)
     */
    fun withMetadata(key: String, value: Any): Candidate<T> =
        copy(metadata = metadata + (key to value))

    /**
     * HITL Option으로 변환
     */
    fun toHitlOptionLabel(): String = buildString {
        append(displayLabel)
        if (description != null) {
            append(" | ")
            append(description)
        }
    }

    companion object {
        /**
         * 간단한 후보 생성
         */
        fun <T> simple(
            id: String,
            displayLabel: String,
            payload: T
        ): Candidate<T> = Candidate(
            id = id,
            displayLabel = displayLabel,
            payload = payload
        )

        /**
         * 점수 포함 후보 생성
         */
        fun <T> withScore(
            id: String,
            displayLabel: String,
            score: Double,
            payload: T
        ): Candidate<T> = Candidate(
            id = id,
            displayLabel = displayLabel,
            score = score,
            payload = payload
        )

        /**
         * 점수 기준 정렬 (내림차순)
         */
        fun <T> List<Candidate<T>>.sortedByScoreDesc(): List<Candidate<T>> =
            sortedByDescending { it.score ?: 0.0 }

        /**
         * 상위 N개 추출
         */
        fun <T> List<Candidate<T>>.topN(n: Int): List<Candidate<T>> =
            sortedByScoreDesc().take(n)

        /**
         * 임계값 이상만 필터링
         */
        fun <T> List<Candidate<T>>.aboveThreshold(threshold: Double): List<Candidate<T>> =
            filter { (it.score ?: 0.0) >= threshold }
    }
}

/**
 * 후보 비교기
 */
object CandidateComparators {
    /**
     * 점수 기준 내림차순 비교
     */
    fun <T> byScoreDesc(): Comparator<Candidate<T>> =
        compareByDescending { it.score ?: 0.0 }

    /**
     * ID 기준 오름차순 비교
     */
    fun <T> byIdAsc(): Comparator<Candidate<T>> =
        compareBy { it.id }

    /**
     * 라벨 기준 오름차순 비교
     */
    fun <T> byLabelAsc(): Comparator<Candidate<T>> =
        compareBy { it.displayLabel }
}

/**
 * 후보 슬롯 키 상수
 *
 * 도메인별로 표준 슬롯 키를 정의하여 일관성 유지.
 */
object CandidateSlotKeys {
    // 공통
    const val ID = "id"
    const val NAME = "name"
    const val TITLE = "title"
    const val DESCRIPTION = "description"

    // 예약 관련
    const val BOOKING_ID = "booking_id"
    const val PLACE_NAME = "place_name"
    const val CHECK_IN = "check_in"
    const val CHECK_OUT = "check_out"
    const val GUEST_NAME = "guest_name"
    const val GUEST_COUNT = "guest_count"
    const val ROOM_TYPE = "room_type"

    // 옵션 관련
    const val OPTION_ID = "option_id"
    const val OPTION_NAME = "option_name"
    const val UNIT_PRICE = "unit_price"
    const val QUANTITY = "quantity"
    const val TOTAL_PRICE = "total_price"

    // 상품 관련
    const val PRODUCT_ID = "product_id"
    const val PRODUCT_NAME = "product_name"
    const val CATEGORY = "category"
    const val PRICE = "price"
}
