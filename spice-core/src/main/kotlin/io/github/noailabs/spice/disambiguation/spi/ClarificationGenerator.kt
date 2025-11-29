package io.github.noailabs.spice.disambiguation.spi

import io.github.noailabs.spice.disambiguation.DisambiguationConfig
import io.github.noailabs.spice.intelligence.ClarificationOption
import io.github.noailabs.spice.intelligence.ClarificationRequest
import io.github.noailabs.spice.intelligence.ClarificationStrategy
import io.github.noailabs.spice.retrieval.Candidate
import kotlinx.serialization.Serializable

/**
 * Clarification 생성 SPI
 *
 * 모호한 후보들로부터 사용자에게 보여줄 Clarification 요청을 생성합니다.
 * 도메인별 구현체가 kai-core 등에서 제공됩니다.
 *
 * ## 구현 예시
 * ```kotlin
 * class ReservationClarificationGenerator : ClarificationGenerator<Reservation> {
 *     override val domain = "reservation"
 *
 *     override suspend fun generate(
 *         candidates: List<Candidate<Reservation>>,
 *         context: ClarificationContext
 *     ): ClarificationRequest {
 *         val options = candidates.map { candidate ->
 *             val reservation = candidate.payload
 *             ClarificationOption(
 *                 id = candidate.id,
 *                 label = reservation.placeName,
 *                 description = "${reservation.checkIn} | ${reservation.guestName}"
 *             )
 *         }
 *
 *         return ClarificationRequest(
 *             prompt = "어떤 예약을 말씀하시는 건가요?",
 *             options = options,
 *             strategy = ClarificationStrategy.SELECTION,
 *             metadata = mapOf("domain" to domain)
 *         )
 *     }
 * }
 * ```
 *
 * @param T 후보 페이로드 타입
 *
 * @since 1.1.0
 */
interface ClarificationGenerator<T> {

    /**
     * 도메인 식별자
     */
    val domain: String

    /**
     * Clarification 요청 생성
     *
     * @param candidates 모호한 후보 리스트
     * @param context Clarification 컨텍스트
     * @return Clarification 요청
     */
    suspend fun generate(
        candidates: List<Candidate<T>>,
        context: ClarificationContext
    ): ClarificationRequest

    /**
     * 후보 수에 따른 최적 전략 결정
     *
     * @param candidateCount 후보 수
     * @param config 설정
     * @return 권장 전략
     */
    fun determineStrategy(
        candidateCount: Int,
        config: DisambiguationConfig
    ): ClarificationStrategy {
        return when {
            candidateCount == 0 -> ClarificationStrategy.LLM
            candidateCount <= config.selectionMaxOptions -> ClarificationStrategy.TEMPLATE
            else -> ClarificationStrategy.HYBRID
        }
    }

    /**
     * 기본 프롬프트 템플릿 적용
     *
     * @param template 템플릿 문자열 (예: "{{count}}개의 {{domain}}이 있습니다")
     * @param variables 변수 맵
     * @return 치환된 문자열
     */
    fun applyTemplate(template: String, variables: Map<String, Any>): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value.toString())
        }
        return result
    }
}

/**
 * Clarification 컨텍스트
 *
 * @property sessionId 세션 ID
 * @property userQuery 사용자 원본 쿼리
 * @property attemptCount 현재까지 Clarification 시도 횟수
 * @property previousResponses 이전 사용자 응답들
 * @property config 설정
 * @property metadata 추가 메타데이터
 */
@Serializable
data class ClarificationContext(
    val sessionId: String,
    val userQuery: String,
    val attemptCount: Int = 0,
    val previousResponses: List<String> = emptyList(),
    val config: DisambiguationConfig = DisambiguationConfig.DEFAULT,
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * 첫 번째 시도인지 여부
     */
    val isFirstAttempt: Boolean
        get() = attemptCount == 0

    /**
     * 재시도인지 여부
     */
    val isRetry: Boolean
        get() = attemptCount > 0

    /**
     * 마지막 시도인지 여부
     */
    val isLastAttempt: Boolean
        get() = attemptCount >= config.maxClarifyAttempts - 1

    /**
     * 남은 시도 횟수
     */
    val remainingAttempts: Int
        get() = maxOf(0, config.maxClarifyAttempts - attemptCount)

    /**
     * 메타데이터 추가
     */
    fun withMetadata(key: String, value: String): ClarificationContext =
        copy(metadata = metadata + (key to value))

    /**
     * 이전 응답 추가
     */
    fun withPreviousResponse(response: String): ClarificationContext =
        copy(
            previousResponses = previousResponses + response,
            attemptCount = attemptCount + 1
        )

    companion object {
        /**
         * 새 컨텍스트 생성
         */
        fun create(
            sessionId: String,
            userQuery: String,
            config: DisambiguationConfig = DisambiguationConfig.DEFAULT
        ): ClarificationContext = ClarificationContext(
            sessionId = sessionId,
            userQuery = userQuery,
            config = config
        )
    }
}

/**
 * 기본 Clarification 생성기
 *
 * 도메인 특화 로직 없이 범용 템플릿 사용.
 */
class DefaultClarificationGenerator<T>(
    override val domain: String = "default",
    private val promptTemplate: String = DEFAULT_PROMPT_TEMPLATE,
    private val optionTemplate: ClarificationOptionTemplate<T> = DefaultOptionTemplate()
) : ClarificationGenerator<T> {

    override suspend fun generate(
        candidates: List<Candidate<T>>,
        context: ClarificationContext
    ): ClarificationRequest {
        val strategy = determineStrategy(candidates.size, context.config)

        val options = candidates.map { candidate ->
            optionTemplate.toOption(candidate)
        }

        val prompt = buildPrompt(candidates.size, context, strategy)

        return ClarificationRequest(
            question = prompt,
            options = options,
            strategy = strategy,
            metadata = mapOf(
                "domain" to domain,
                "attemptCount" to context.attemptCount,
                "candidateCount" to candidates.size
            )
        )
    }

    private fun buildPrompt(
        candidateCount: Int,
        context: ClarificationContext,
        strategy: ClarificationStrategy
    ): String {
        val basePrompt = applyTemplate(promptTemplate, mapOf(
            "count" to candidateCount,
            "domain" to domain,
            "query" to context.userQuery
        ))

        // 재시도 시 추가 안내
        return if (context.isRetry) {
            "$basePrompt 조금 더 구체적으로 말씀해주세요."
        } else {
            basePrompt
        }
    }

    companion object {
        const val DEFAULT_PROMPT_TEMPLATE = "{{count}}개의 항목이 검색되었습니다. 어떤 것을 선택하시겠어요?"
    }
}

/**
 * Clarification 옵션 템플릿
 *
 * Candidate를 ClarificationOption으로 변환하는 전략.
 */
interface ClarificationOptionTemplate<T> {
    /**
     * Candidate → ClarificationOption 변환
     */
    fun toOption(candidate: Candidate<T>): ClarificationOption
}

/**
 * 기본 옵션 템플릿
 *
 * Candidate의 displayLabel과 description을 그대로 사용.
 */
class DefaultOptionTemplate<T> : ClarificationOptionTemplate<T> {
    override fun toOption(candidate: Candidate<T>): ClarificationOption =
        ClarificationOption(
            id = candidate.id,
            label = candidate.displayLabel,
            description = candidate.description
        )
}

/**
 * 슬롯 기반 옵션 템플릿
 *
 * Candidate의 slots를 사용하여 라벨/설명 생성.
 *
 * @param labelSlots 라벨에 포함할 슬롯 키들
 * @param descriptionSlots 설명에 포함할 슬롯 키들
 * @param labelSeparator 라벨 슬롯 구분자
 * @param descriptionSeparator 설명 슬롯 구분자
 */
class SlotBasedOptionTemplate<T>(
    private val labelSlots: List<String>,
    private val descriptionSlots: List<String> = emptyList(),
    private val labelSeparator: String = " ",
    private val descriptionSeparator: String = " | "
) : ClarificationOptionTemplate<T> {

    override fun toOption(candidate: Candidate<T>): ClarificationOption {
        val label = labelSlots
            .mapNotNull { candidate.slots[it]?.toString() }
            .joinToString(labelSeparator)
            .ifEmpty { candidate.displayLabel }

        val description = if (descriptionSlots.isNotEmpty()) {
            descriptionSlots
                .mapNotNull { candidate.slots[it]?.toString() }
                .joinToString(descriptionSeparator)
                .ifEmpty { null }
        } else {
            candidate.description
        }

        return ClarificationOption(
            id = candidate.id,
            label = label,
            description = description
        )
    }
}

/**
 * 복합 Clarification 생성기
 *
 * 여러 도메인의 생성기를 조합.
 */
interface CompositeClarificationGenerator {

    /**
     * 지원하는 도메인 목록
     */
    val supportedDomains: List<String>

    /**
     * 특정 도메인의 생성기 조회
     */
    fun <T> getGenerator(domain: String): ClarificationGenerator<T>?

    /**
     * 생성기 등록
     */
    fun <T> registerGenerator(generator: ClarificationGenerator<T>)
}

/**
 * 기본 복합 생성기 구현
 */
class DefaultCompositeClarificationGenerator : CompositeClarificationGenerator {

    private val generators = mutableMapOf<String, ClarificationGenerator<*>>()

    override val supportedDomains: List<String>
        get() = generators.keys.toList()

    @Suppress("UNCHECKED_CAST")
    override fun <T> getGenerator(domain: String): ClarificationGenerator<T>? =
        generators[domain] as? ClarificationGenerator<T>

    override fun <T> registerGenerator(generator: ClarificationGenerator<T>) {
        generators[generator.domain] = generator
    }
}

/**
 * Clarification 생성기 팩토리
 */
object ClarificationGenerators {

    /**
     * 람다 기반 간단한 생성기 생성
     *
     * @param domain 도메인 식별자
     * @param promptBuilder 프롬프트 생성 함수
     * @param optionBuilder 옵션 생성 함수
     */
    fun <T> create(
        domain: String,
        promptBuilder: (List<Candidate<T>>, ClarificationContext) -> String,
        optionBuilder: (Candidate<T>) -> ClarificationOption = { candidate ->
            ClarificationOption(
                id = candidate.id,
                label = candidate.displayLabel,
                description = candidate.description
            )
        }
    ): ClarificationGenerator<T> = object : ClarificationGenerator<T> {
        override val domain: String = domain

        override suspend fun generate(
            candidates: List<Candidate<T>>,
            context: ClarificationContext
        ): ClarificationRequest {
            val strategy = determineStrategy(candidates.size, context.config)

            return ClarificationRequest(
                question = promptBuilder(candidates, context),
                options = candidates.map(optionBuilder),
                strategy = strategy,
                metadata = mapOf("domain" to domain)
            )
        }
    }

    /**
     * 템플릿 기반 생성기 생성
     *
     * @param domain 도메인 식별자
     * @param promptTemplate 프롬프트 템플릿
     * @param labelSlots 라벨 슬롯 키들
     * @param descriptionSlots 설명 슬롯 키들
     */
    fun <T> fromTemplate(
        domain: String,
        promptTemplate: String,
        labelSlots: List<String> = emptyList(),
        descriptionSlots: List<String> = emptyList()
    ): ClarificationGenerator<T> {
        val optionTemplate = if (labelSlots.isNotEmpty()) {
            SlotBasedOptionTemplate<T>(labelSlots, descriptionSlots)
        } else {
            DefaultOptionTemplate()
        }

        return DefaultClarificationGenerator(
            domain = domain,
            promptTemplate = promptTemplate,
            optionTemplate = optionTemplate
        )
    }

    /**
     * 도메인별 표준 프롬프트 템플릿
     */
    object Templates {
        const val RESERVATION = "{{count}}개의 예약이 검색되었습니다. 어떤 예약을 말씀하시는 건가요?"
        const val OPTION = "{{count}}개의 옵션이 있습니다. 어떤 옵션을 선택하시겠어요?"
        const val PRODUCT = "{{count}}개의 상품이 검색되었습니다. 어떤 상품을 원하시나요?"
        const val PLACE = "{{count}}개의 장소가 검색되었습니다. 어디를 말씀하시는 건가요?"
        const val GENERIC = "{{count}}개의 항목이 검색되었습니다. 어떤 것을 선택하시겠어요?"
    }
}
