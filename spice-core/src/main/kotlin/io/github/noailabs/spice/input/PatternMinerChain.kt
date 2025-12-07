package io.github.noailabs.spice.input

import io.github.noailabs.spice.input.spi.PartialResult
import io.github.noailabs.spice.input.spi.PatternMiner

/**
 * 패턴 마이너 체인
 *
 * 등록된 Miner들을 우선순위 순으로 실행하여 입력을 정규화.
 * 높은 신뢰도 결과가 나오면 조기 종료.
 *
 * ## 실행 흐름
 * 1. 적용 가능한 Miner 필터링 (supportedTypes 확인)
 * 2. 우선순위 순으로 정렬
 * 3. 순차 실행, 결과 수집
 * 4. 높은 신뢰도 (>=0.95) 시 조기 종료
 *
 * ## 사용 예시
 * ```kotlin
 * val chain = PatternMinerChain(
 *     miners = listOf(
 *         numberPatternMiner,   // priority: 10
 *         driftDetectionMiner,  // priority: 5
 *         enumSimilarityMiner   // priority: 30
 *     )
 * )
 *
 * val results = chain.process(input, spec, context)
 * // → 여러 Miner 결과 (ResultFusionLayer로 전달)
 * ```
 *
 * @property miners 등록된 Miner 목록
 * @property earlyExitThreshold 조기 종료 신뢰도 임계값 (기본 0.95)
 *
 * @since 1.7.0
 */
class PatternMinerChain(
    private val miners: List<PatternMiner>,
    private val earlyExitThreshold: Double = 0.95
) {
    companion object {
        /**
         * 빈 체인 (테스트용)
         */
        val EMPTY = PatternMinerChain(emptyList())
    }

    /**
     * 등록된 Miner 수
     */
    val size: Int get() = miners.size

    /**
     * 패턴 마이닝 실행
     *
     * @param input 원본 입력 텍스트
     * @param spec 필드 명세
     * @param context 정규화 컨텍스트
     * @return Miner 결과 목록 (비어있을 수 있음)
     */
    suspend fun process(
        input: String,
        spec: InputFieldSpec,
        context: NormalizationContext
    ): List<PartialResult> {
        // 1. 적용 가능한 Miner 필터링 + 우선순위 정렬
        val applicableMiners = miners
            .filter { it.supports(spec) }
            .sortedBy { it.priority }

        if (applicableMiners.isEmpty()) {
            return emptyList()
        }

        // 2. 순차 실행
        val results = mutableListOf<PartialResult>()

        for (miner in applicableMiners) {
            val result = try {
                miner.mine(input, spec, context)
            } catch (e: Exception) {
                // Miner 오류는 무시하고 다음으로
                null
            }

            if (result != null) {
                results.add(result)

                // 3. 높은 신뢰도 시 조기 종료
                if (result.confidence >= earlyExitThreshold) {
                    break
                }

                // 4. 비정상 라우팅 시 조기 종료 (drift, escalate 등)
                if (result.routingSignal != RoutingSignal.NORMAL) {
                    break
                }
            }
        }

        return results
    }

    /**
     * 단일 결과만 반환 (가장 높은 신뢰도)
     */
    suspend fun processSingle(
        input: String,
        spec: InputFieldSpec,
        context: NormalizationContext
    ): PartialResult? {
        return process(input, spec, context).maxByOrNull { it.confidence }
    }

    /**
     * 비정상 라우팅 결과만 반환 (drift, escalate 등)
     */
    suspend fun processForRouting(
        input: String,
        spec: InputFieldSpec,
        context: NormalizationContext
    ): PartialResult? {
        return process(input, spec, context)
            .find { it.routingSignal != RoutingSignal.NORMAL }
    }

    /**
     * Miner 추가 (새 체인 반환)
     */
    fun withMiner(miner: PatternMiner): PatternMinerChain {
        return PatternMinerChain(miners + miner, earlyExitThreshold)
    }

    /**
     * 조기 종료 임계값 변경 (새 체인 반환)
     */
    fun withEarlyExitThreshold(threshold: Double): PatternMinerChain {
        return PatternMinerChain(miners, threshold)
    }

    /**
     * 특정 타입용 Miner만 필터링
     */
    fun forType(fieldType: FieldType): PatternMinerChain {
        val filtered = miners.filter { fieldType in it.supportedTypes }
        return PatternMinerChain(filtered, earlyExitThreshold)
    }
}

/**
 * PatternMinerChain 빌더
 *
 * 체인 생성을 위한 DSL 지원.
 *
 * @since 1.7.0
 */
class PatternMinerChainBuilder {
    private val miners = mutableListOf<PatternMiner>()
    private var earlyExitThreshold = 0.95

    /**
     * Miner 추가
     */
    fun miner(miner: PatternMiner): PatternMinerChainBuilder {
        miners.add(miner)
        return this
    }

    /**
     * 여러 Miner 추가
     */
    fun miners(vararg miners: PatternMiner): PatternMinerChainBuilder {
        this.miners.addAll(miners)
        return this
    }

    /**
     * 조기 종료 임계값 설정
     */
    fun earlyExitThreshold(threshold: Double): PatternMinerChainBuilder {
        this.earlyExitThreshold = threshold
        return this
    }

    /**
     * 체인 빌드
     */
    fun build(): PatternMinerChain {
        return PatternMinerChain(miners.toList(), earlyExitThreshold)
    }
}

/**
 * PatternMinerChain DSL 시작점
 */
fun patternMinerChain(block: PatternMinerChainBuilder.() -> Unit): PatternMinerChain {
    return PatternMinerChainBuilder().apply(block).build()
}
