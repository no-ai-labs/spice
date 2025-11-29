package io.github.noailabs.spice.retrieval.spi

import io.github.noailabs.spice.retrieval.Candidate

/**
 * 도메인 객체 → Candidate 변환 어댑터 SPI
 *
 * Spice 범용 인터페이스. 도메인별 구현체가 kai-core, dalba-core 등에 위치.
 * 이를 통해 Spice는 Kai 전용이 아닌 범용 AI Routing Engine으로 동작.
 *
 * ## 구현 예시 (예약)
 * ```kotlin
 * class ReservationCandidateAdapter : CandidateTextAdapter<Reservation> {
 *     override val domain = "stayfolio:reservation"
 *
 *     override fun toEmbeddableText(item: Reservation): String =
 *         "${item.placeName} 숙소 ${item.checkIn} 체크인 ${item.guestCount}명"
 *
 *     override fun extractSlots(item: Reservation): Map<String, Any?> = mapOf(
 *         "place_name" to item.placeName,
 *         "check_in" to item.checkIn,
 *         "guest_name" to item.guestName
 *     )
 *
 *     override fun toCandidate(item: Reservation, score: Double?) = Candidate(
 *         id = item.id,
 *         displayLabel = item.placeName,
 *         description = "${item.checkIn} | ${item.guestName}",
 *         score = score,
 *         slots = extractSlots(item),
 *         payload = item
 *     )
 * }
 * ```
 *
 * @param T 도메인 객체 타입
 *
 * @since 1.1.0
 */
interface CandidateTextAdapter<T> {

    /**
     * 도메인 식별자 (메트릭/로깅용)
     *
     * 형식: `{tenant}:{entity}` 또는 `{entity}`
     * 예: "stayfolio:reservation", "reservation", "option"
     */
    val domain: String

    /**
     * 도메인 객체 → 임베딩용 텍스트 변환
     *
     * 시멘틱 매칭에 사용될 텍스트를 생성합니다.
     * 검색/매칭에 중요한 정보만 포함하세요.
     *
     * @param item 도메인 객체
     * @return 임베딩용 텍스트
     */
    fun toEmbeddableText(item: T): String

    /**
     * 도메인 객체 → Candidate 래퍼 변환
     *
     * @param item 도메인 객체
     * @param score 유사도 점수 (선택)
     * @return Candidate 래퍼
     */
    fun toCandidate(item: T, score: Double? = null): Candidate<T>

    /**
     * 슬롯 추출 (Clarification 템플릿용)
     *
     * 템플릿에서 참조할 수 있는 키-값 쌍을 추출합니다.
     * 예: {{place_name}}, {{check_in}} 등
     *
     * @param item 도메인 객체
     * @return 슬롯 맵
     */
    fun extractSlots(item: T): Map<String, Any?>

    /**
     * 도메인 객체 ID 추출
     *
     * @param item 도메인 객체
     * @return 고유 식별자
     */
    fun extractId(item: T): String = toCandidate(item).id

    /**
     * 여러 객체를 Candidate 리스트로 변환
     *
     * @param items 도메인 객체 리스트
     * @return Candidate 리스트
     */
    fun toCandidates(items: List<T>): List<Candidate<T>> =
        items.map { toCandidate(it) }

    /**
     * 여러 객체를 점수와 함께 Candidate 리스트로 변환
     *
     * @param itemsWithScores (도메인 객체, 점수) 쌍 리스트
     * @return Candidate 리스트
     */
    fun toCandidatesWithScores(itemsWithScores: List<Pair<T, Double>>): List<Candidate<T>> =
        itemsWithScores.map { (item, score) -> toCandidate(item, score) }
}

/**
 * 복합 어댑터 (여러 어댑터 조합)
 *
 * 여러 도메인 어댑터를 하나로 묶어서 사용할 때 유용합니다.
 */
interface CompositeCandidateTextAdapter {

    /**
     * 지원하는 도메인 목록
     */
    val supportedDomains: List<String>

    /**
     * 특정 도메인의 어댑터 조회
     *
     * @param domain 도메인 식별자
     * @return 해당 도메인의 어댑터, 없으면 null
     */
    fun <T> getAdapter(domain: String): CandidateTextAdapter<T>?

    /**
     * 어댑터 등록
     *
     * @param adapter 등록할 어댑터
     */
    fun <T> registerAdapter(adapter: CandidateTextAdapter<T>)
}

/**
 * 기본 복합 어댑터 구현
 */
class DefaultCompositeCandidateTextAdapter : CompositeCandidateTextAdapter {

    private val adapters = mutableMapOf<String, CandidateTextAdapter<*>>()

    override val supportedDomains: List<String>
        get() = adapters.keys.toList()

    @Suppress("UNCHECKED_CAST")
    override fun <T> getAdapter(domain: String): CandidateTextAdapter<T>? =
        adapters[domain] as? CandidateTextAdapter<T>

    override fun <T> registerAdapter(adapter: CandidateTextAdapter<T>) {
        adapters[adapter.domain] = adapter
    }
}

/**
 * 어댑터 팩토리
 */
object CandidateTextAdapters {

    /**
     * 람다 기반 간단한 어댑터 생성
     *
     * @param domain 도메인 식별자
     * @param toText 임베딩 텍스트 변환 함수
     * @param toCandidate Candidate 변환 함수
     * @param extractSlots 슬롯 추출 함수
     */
    fun <T> create(
        domain: String,
        toText: (T) -> String,
        toCandidate: (T, Double?) -> Candidate<T>,
        extractSlots: (T) -> Map<String, Any?> = { emptyMap() }
    ): CandidateTextAdapter<T> = object : CandidateTextAdapter<T> {
        override val domain: String = domain
        override fun toEmbeddableText(item: T): String = toText(item)
        override fun toCandidate(item: T, score: Double?): Candidate<T> = toCandidate(item, score)
        override fun extractSlots(item: T): Map<String, Any?> = extractSlots(item)
    }

    /**
     * 문자열 ID 기반 간단한 어댑터 생성
     *
     * 도메인 객체가 단순 데이터 클래스일 때 사용.
     *
     * @param domain 도메인 식별자
     * @param idExtractor ID 추출 함수
     * @param labelExtractor 라벨 추출 함수
     * @param descriptionExtractor 설명 추출 함수 (선택)
     * @param slotExtractor 슬롯 추출 함수 (선택)
     */
    fun <T> simple(
        domain: String,
        idExtractor: (T) -> String,
        labelExtractor: (T) -> String,
        descriptionExtractor: ((T) -> String?)? = null,
        slotExtractor: ((T) -> Map<String, Any?>)? = null
    ): CandidateTextAdapter<T> = object : CandidateTextAdapter<T> {
        override val domain: String = domain

        override fun toEmbeddableText(item: T): String = labelExtractor(item)

        override fun toCandidate(item: T, score: Double?): Candidate<T> = Candidate(
            id = idExtractor(item),
            displayLabel = labelExtractor(item),
            description = descriptionExtractor?.invoke(item),
            score = score,
            slots = extractSlots(item),
            payload = item
        )

        override fun extractSlots(item: T): Map<String, Any?> =
            slotExtractor?.invoke(item) ?: emptyMap()
    }

    /**
     * 맵 기반 어댑터 생성
     *
     * 도메인 객체가 Map<String, Any?>일 때 사용.
     *
     * @param domain 도메인 식별자
     * @param idKey ID 키
     * @param labelKey 라벨 키
     * @param descriptionKey 설명 키 (선택)
     * @param slotKeys 슬롯으로 추출할 키들
     */
    fun forMap(
        domain: String,
        idKey: String,
        labelKey: String,
        descriptionKey: String? = null,
        slotKeys: List<String> = emptyList()
    ): CandidateTextAdapter<Map<String, Any?>> = object : CandidateTextAdapter<Map<String, Any?>> {
        override val domain: String = domain

        override fun toEmbeddableText(item: Map<String, Any?>): String =
            item[labelKey]?.toString() ?: ""

        override fun toCandidate(item: Map<String, Any?>, score: Double?): Candidate<Map<String, Any?>> = Candidate(
            id = item[idKey]?.toString() ?: "",
            displayLabel = item[labelKey]?.toString() ?: "",
            description = descriptionKey?.let { item[it]?.toString() },
            score = score,
            slots = extractSlots(item),
            payload = item
        )

        override fun extractSlots(item: Map<String, Any?>): Map<String, Any?> =
            if (slotKeys.isEmpty()) item
            else slotKeys.associateWith { item[it] }
    }
}
