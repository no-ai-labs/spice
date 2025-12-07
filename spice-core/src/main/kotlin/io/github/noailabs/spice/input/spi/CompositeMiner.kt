package io.github.noailabs.spice.input.spi

import io.github.noailabs.spice.input.FieldType
import io.github.noailabs.spice.input.InputFieldSpec
import io.github.noailabs.spice.input.NormalizationContext

/**
 * 복합 패턴 마이너
 *
 * 여러 서브마이너를 포함하는 복합 마이너.
 * PatternMinerChain에서는 하나의 Miner로 취급.
 *
 * ## 사용 예시
 * ```kotlin
 * // 날짜 관련 서브마이너들을 묶는 복합 마이너
 * class DateCompositeMiner(
 *     relativeDateMiner: RelativeDateMiner,
 *     koreanDateMiner: KoreanDateMiner,
 *     rangeDateMiner: RangeDateMiner
 * ) : CompositeMiner {
 *     override val id = "date-composite"
 *     override val supportedTypes = setOf(FieldType.DATE, FieldType.DATETIME)
 *     override val priority = 20
 *
 *     override val subMiners = listOf(
 *         relativeDateMiner,  // "내일", "다음주" 먼저
 *         koreanDateMiner,    // "12월 15일"
 *         rangeDateMiner      // "12/1~12/3"
 *     )
 * }
 * ```
 *
 * ## 확장성
 * 도메인별로 자유롭게 서브마이너 확장 가능:
 * ```kotlin
 * class GuestCountCompositeMiner : CompositeMiner {
 *     override val subMiners = listOf(
 *         AdultChildParser(),
 *         KoreanQuantityParser()
 *     )
 * }
 * ```
 *
 * @since 1.7.0
 */
interface CompositeMiner : PatternMiner {
    /**
     * 서브마이너 목록
     * 순서대로 실행되며, 첫 번째 매칭 결과 반환
     */
    val subMiners: List<PatternMiner>

    /**
     * 기본 구현: 서브마이너들을 순차 실행
     *
     * 우선순위 순으로 정렬 후 첫 번째 결과 반환.
     * 모든 서브마이너가 null이면 null 반환.
     */
    override suspend fun mine(
        input: String,
        spec: InputFieldSpec,
        context: NormalizationContext
    ): PartialResult? {
        // 서브마이너를 우선순위 순으로 정렬
        val sortedMiners = subMiners
            .filter { it.supports(spec) }
            .sortedBy { it.priority }

        // 순차 실행, 첫 번째 결과 반환
        for (miner in sortedMiners) {
            val result = miner.mine(input, spec, context)
            if (result != null) {
                return result.copy(
                    reasoning = "${result.reasoning ?: ""} (via ${miner.id})"
                )
            }
        }

        return null
    }

    /**
     * 서브마이너 중 해당 스펙을 지원하는 것이 있는지 확인
     */
    override fun supports(spec: InputFieldSpec): Boolean {
        return spec.fieldType in supportedTypes ||
                subMiners.any { it.supports(spec) }
    }
}

/**
 * 기본 CompositeMiner 구현
 *
 * 서브마이너 목록만 제공하면 되는 간단한 복합 마이너.
 *
 * @property id 마이너 ID
 * @property supportedTypes 지원 타입
 * @property priority 우선순위
 * @property subMiners 서브마이너 목록
 */
abstract class BaseCompositeMiner(
    override val id: String,
    override val supportedTypes: Set<FieldType>,
    override val priority: Int,
    override val subMiners: List<PatternMiner>
) : CompositeMiner
