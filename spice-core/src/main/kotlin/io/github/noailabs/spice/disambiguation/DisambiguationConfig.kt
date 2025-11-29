package io.github.noailabs.spice.disambiguation

import kotlinx.serialization.Serializable

/**
 * 모호성 해소 설정
 *
 * Hot-Reload 지원을 위해 SpiceConfigProvider와 연동.
 * 실서비스에서 재시작 없이 threshold 튜닝 가능.
 *
 * ## 임계값 의미
 * - exactThreshold (0.85): 정확 매칭 - 자동 선택
 * - fuzzyThreshold (0.70): 퍼지 매칭 - 확인 권장
 * - ambiguousThreshold (0.60): 모호함 - Clarification 필요
 * - gapThreshold (0.15): 1위-2위 격차 - 이하면 모호
 *
 * ## 사용 예시
 * ```kotlin
 * // 기본 설정
 * val config = DisambiguationConfig.DEFAULT
 *
 * // 엄격한 설정 (더 많은 Clarification)
 * val strictConfig = DisambiguationConfig.STRICT
 *
 * // 느슨한 설정 (자동 선택 우선)
 * val lenientConfig = DisambiguationConfig.LENIENT
 * ```
 *
 * @property exactThreshold EXACT 매칭 임계값 (기본: 0.85)
 * @property fuzzyThreshold FUZZY 매칭 임계값 (기본: 0.70)
 * @property ambiguousThreshold AMBIGUOUS 임계값 (기본: 0.60)
 * @property gapThreshold top-second 격차 임계값 (기본: 0.15)
 * @property maxClarifyAttempts 최대 Clarification 루프 횟수 (기본: 2)
 * @property selectionMaxOptions selection 최대 옵션 수 (초과 시 free-text) (기본: 5)
 * @property clarificationTimeoutMs Clarification 응답 타임아웃 (밀리초) (기본: 300000 = 5분)
 * @property enableLoopGuard LoopGuard 활성화 여부 (기본: true)
 * @property frustrationPatterns 사용자 불만 감지 패턴
 *
 * @since 1.1.0
 */
@Serializable
data class DisambiguationConfig(
    val exactThreshold: Double = 0.85,
    val fuzzyThreshold: Double = 0.70,
    val ambiguousThreshold: Double = 0.60,
    val gapThreshold: Double = 0.15,
    val maxClarifyAttempts: Int = 2,
    val selectionMaxOptions: Int = 5,
    val clarificationTimeoutMs: Long = 300_000L,
    val enableLoopGuard: Boolean = true,
    val frustrationPatterns: List<String> = DEFAULT_FRUSTRATION_PATTERNS
) {
    /**
     * 모호성 결과 결정
     *
     * @param topScore 최고 점수
     * @param secondScore 두 번째 점수
     * @param candidateCount 후보 수
     * @return 모호성 결과
     */
    fun determineAmbiguity(
        topScore: Double,
        secondScore: Double,
        candidateCount: Int
    ): AmbiguityResult {
        if (candidateCount == 0) return AmbiguityResult.NONE
        if (candidateCount == 1 && topScore >= fuzzyThreshold) return AmbiguityResult.SINGLE

        val gap = topScore - secondScore

        return when {
            topScore >= exactThreshold && gap >= gapThreshold -> AmbiguityResult.SINGLE
            topScore >= ambiguousThreshold && gap < gapThreshold -> AmbiguityResult.AMBIGUOUS
            topScore >= fuzzyThreshold -> AmbiguityResult.SINGLE
            topScore >= 0.50 -> AmbiguityResult.AMBIGUOUS
            else -> AmbiguityResult.NONE
        }
    }

    /**
     * Clarification 시도 가능 여부
     *
     * @param currentAttempts 현재까지 시도 횟수
     * @return 시도 가능 여부
     */
    fun canAttemptClarification(currentAttempts: Int): Boolean =
        currentAttempts < maxClarifyAttempts

    /**
     * Selection UI 사용 가능 여부
     *
     * @param optionCount 옵션 수
     * @return true면 selection, false면 free-text
     */
    fun useSelectionUI(optionCount: Int): Boolean =
        optionCount in 1..selectionMaxOptions

    /**
     * 사용자 불만 감지
     *
     * @param text 사용자 입력 텍스트
     * @return 불만 감지 여부와 감지된 패턴
     */
    fun detectFrustration(text: String): Pair<Boolean, List<String>> {
        val normalized = text.lowercase()
        val detected = frustrationPatterns.filter { normalized.contains(it.lowercase()) }
        return (detected.isNotEmpty()) to detected
    }

    companion object {
        const val CONFIG_KEY = "spice.disambiguation"

        /**
         * 기본 불만 감지 패턴 (한국어)
         */
        val DEFAULT_FRUSTRATION_PATTERNS = listOf(
            "그냥", "됐어", "몰라", "아무거나", "빨리",
            "알아서", "상관없", "귀찮", "그만", "싫어",
            "안돼", "못해", "짜증", "답답"
        )

        /**
         * 기본 설정
         */
        val DEFAULT = DisambiguationConfig()

        /**
         * 엄격한 설정 (더 많은 Clarification)
         *
         * - 높은 임계값
         * - 더 넓은 격차 요구
         * - 적은 최대 시도
         */
        val STRICT = DisambiguationConfig(
            exactThreshold = 0.90,
            fuzzyThreshold = 0.75,
            ambiguousThreshold = 0.65,
            gapThreshold = 0.20,
            maxClarifyAttempts = 1,
            selectionMaxOptions = 3
        )

        /**
         * 느슨한 설정 (자동 선택 우선)
         *
         * - 낮은 임계값
         * - 좁은 격차 허용
         * - 더 많은 시도
         */
        val LENIENT = DisambiguationConfig(
            exactThreshold = 0.75,
            fuzzyThreshold = 0.60,
            ambiguousThreshold = 0.50,
            gapThreshold = 0.10,
            maxClarifyAttempts = 3,
            selectionMaxOptions = 7
        )

        /**
         * 테스트용 설정 (무제한 시도)
         */
        val TEST = DisambiguationConfig(
            maxClarifyAttempts = 10,
            clarificationTimeoutMs = 60_000L,
            enableLoopGuard = false
        )
    }
}

/**
 * 모호성 결과
 */
@Serializable
enum class AmbiguityResult {
    /**
     * 단건 확정 (자동 진행)
     *
     * 조건:
     * - 단일 후보 + 높은 점수
     * - 복수 후보지만 1위가 확실히 높음 (격차 충분)
     */
    SINGLE,

    /**
     * 모호함 (Clarification 필요)
     *
     * 조건:
     * - 복수 후보
     * - 1위-2위 격차 부족
     * - 중간 점수대
     */
    AMBIGUOUS,

    /**
     * 매칭 없음 (직접 선택 요청)
     *
     * 조건:
     * - 후보 0건
     * - 모든 후보 점수가 너무 낮음
     */
    NONE
}

/**
 * SpiceConfig Provider SPI - Hot Reload 지원
 *
 * 구현체: RedisSpiceConfigProvider, ConsulSpiceConfigProvider 등
 * 실서비스에서 재시작 없이 threshold 튜닝 가능.
 *
 * @since 1.1.0
 */
interface SpiceConfigProvider {

    /**
     * 설정 조회 (캐시 적용)
     *
     * @param key 설정 키 (e.g., "spice.disambiguation")
     * @param defaultValue 기본값
     * @return 설정 값
     */
    fun <T : Any> get(key: String, defaultValue: T): T

    /**
     * 설정 변경 구독
     *
     * @param key 설정 키
     * @param listener 변경 리스너
     */
    fun <T : Any> subscribe(key: String, listener: (T) -> Unit)

    /**
     * 수동 리로드 트리거
     *
     * @param key 특정 키만 리로드 (null이면 전체)
     */
    fun reload(key: String? = null)
}

/**
 * 기본 Config Provider (정적)
 *
 * 동적 리로드 없이 초기값만 사용.
 */
object StaticSpiceConfigProvider : SpiceConfigProvider {

    private val configs = mutableMapOf<String, Any>()

    init {
        configs[DisambiguationConfig.CONFIG_KEY] = DisambiguationConfig.DEFAULT
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(key: String, defaultValue: T): T =
        (configs[key] as? T) ?: defaultValue

    override fun <T : Any> subscribe(key: String, listener: (T) -> Unit) {
        // 정적 provider는 변경 알림 없음
    }

    override fun reload(key: String?) {
        // 정적 provider는 리로드 없음
    }

    /**
     * 테스트용 설정 주입
     */
    fun <T : Any> set(key: String, value: T) {
        configs[key] = value
    }

    /**
     * 설정 초기화
     */
    fun reset() {
        configs.clear()
        configs[DisambiguationConfig.CONFIG_KEY] = DisambiguationConfig.DEFAULT
    }
}
