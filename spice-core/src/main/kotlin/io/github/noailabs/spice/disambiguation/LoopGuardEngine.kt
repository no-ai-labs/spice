package io.github.noailabs.spice.disambiguation

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Clarification 루프 방지 엔진 SPI
 *
 * 사용자가 계속 모호한 답변을 하거나, 시스템이 잘못된 질문을 반복할 때
 * 무한 루프를 감지하고 탈출 전략을 제공합니다.
 *
 * ## 구현체 위치
 * - spice-infra: Redis 기반 구현
 * - kai-core: 애플리케이션 레벨 구현
 *
 * ## 사용 예시
 * ```kotlin
 * // Clarification 시도 전 체크
 * val guardResult = loopGuard.checkAttempt(
 *     sessionId = session.id,
 *     clarificationType = "reservation_selection",
 *     context = mapOf("userText" to userInput)
 * )
 *
 * if (guardResult?.blocked == true) {
 *     when (guardResult.suggestedAction) {
 *         LoopGuardAction.FORCE_SELECTION -> selectFirstCandidate()
 *         LoopGuardAction.DELEGATE_TO_LLM -> delegateToLLM()
 *         LoopGuardAction.ESCALATE_TO_HUMAN -> connectToAgent()
 *         // ...
 *     }
 * }
 * ```
 *
 * @since 1.1.0
 */
interface LoopGuardEngine {

    /**
     * 현재 시도 허용 여부 확인
     *
     * @param sessionId 세션 ID
     * @param clarificationType Clarification 유형 (e.g., "reservation_selection")
     * @param context 추가 컨텍스트 (userText 등)
     * @return 허용 시 null, 차단 시 LoopGuardResult
     */
    suspend fun checkAttempt(
        sessionId: String,
        clarificationType: String,
        context: Map<String, Any> = emptyMap()
    ): LoopGuardResult?

    /**
     * 시도 기록
     *
     * @param sessionId 세션 ID
     * @param clarificationType Clarification 유형
     * @param success 성공 여부 (true면 카운터 초기화)
     */
    suspend fun recordAttempt(
        sessionId: String,
        clarificationType: String,
        success: Boolean
    )

    /**
     * 세션 상태 초기화
     *
     * @param sessionId 세션 ID
     */
    suspend fun reset(sessionId: String)

    /**
     * 특정 타입의 상태 초기화
     *
     * @param sessionId 세션 ID
     * @param clarificationType Clarification 유형
     */
    suspend fun resetType(sessionId: String, clarificationType: String)

    /**
     * 현재 시도 횟수 조회
     *
     * @param sessionId 세션 ID
     * @param clarificationType Clarification 유형
     * @return 현재 시도 횟수
     */
    suspend fun getAttemptCount(sessionId: String, clarificationType: String): Int
}

/**
 * 루프 가드 결과
 *
 * @property blocked 차단 여부
 * @property reason 차단 이유 (로깅/분석/사용자 안내용)
 * @property attemptCount 현재까지 시도 횟수
 * @property maxAttempts 최대 허용 횟수
 * @property suggestedAction 권장 조치
 * @property metadata 추가 정보
 */
@Serializable
data class LoopGuardResult(
    val blocked: Boolean,
    val reason: LoopGuardReason,
    val attemptCount: Int,
    val maxAttempts: Int,
    val suggestedAction: LoopGuardAction,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 남은 시도 횟수
     */
    val remainingAttempts: Int
        get() = maxOf(0, maxAttempts - attemptCount)

    /**
     * 허용되었는지 여부
     */
    val allowed: Boolean
        get() = !blocked

    companion object {
        /**
         * 허용 결과 생성
         */
        fun allowed(
            attemptCount: Int,
            maxAttempts: Int
        ) = LoopGuardResult(
            blocked = false,
            reason = LoopGuardReason.ALLOWED,
            attemptCount = attemptCount,
            maxAttempts = maxAttempts,
            suggestedAction = LoopGuardAction.CONTINUE
        )

        /**
         * 최대 시도 초과로 차단
         */
        fun maxAttemptsExceeded(
            attemptCount: Int,
            maxAttempts: Int,
            suggestedAction: LoopGuardAction = LoopGuardAction.FORCE_SELECTION
        ) = LoopGuardResult(
            blocked = true,
            reason = LoopGuardReason.MAX_ATTEMPTS_EXCEEDED,
            attemptCount = attemptCount,
            maxAttempts = maxAttempts,
            suggestedAction = suggestedAction
        )

        /**
         * 사용자 불만 감지로 차단
         */
        fun frustrationDetected(
            attemptCount: Int,
            maxAttempts: Int,
            detectedPatterns: List<String>
        ) = LoopGuardResult(
            blocked = true,
            reason = LoopGuardReason.USER_FRUSTRATION_SIGNAL,
            attemptCount = attemptCount,
            maxAttempts = maxAttempts,
            suggestedAction = LoopGuardAction.DELEGATE_TO_LLM,
            metadata = mapOf("detectedPatterns" to detectedPatterns)
        )
    }
}

/**
 * 루프 가드 차단 이유
 *
 * Observability + 사용자 안내 메시지 생성에 활용
 */
@Serializable
enum class LoopGuardReason {
    /** 허용됨 (차단 아님) */
    ALLOWED,

    /** 최대 시도 횟수 초과 */
    MAX_ATTEMPTS_EXCEEDED,

    /** 동일 답변 반복 */
    REPEATED_SAME_ANSWER,

    /** 신뢰도 점수 개선 없음 */
    SCORE_NOT_IMPROVING,

    /** 응답 대기 시간 초과 */
    TIMEOUT,

    /** 사용자 불만 신호 감지 ("그냥 해줘", "됐어" 등) */
    USER_FRUSTRATION_SIGNAL,

    /** 순환 참조 (A→B→A) */
    CIRCULAR_REFERENCE
}

/**
 * 루프 가드 권장 조치
 */
@Serializable
enum class LoopGuardAction {
    /** 계속 진행 (차단 아님) */
    CONTINUE,

    /** 첫 번째 후보 강제 선택 */
    FORCE_SELECTION,

    /** 상담원 연결 */
    ESCALATE_TO_HUMAN,

    /** 상위 LLM에 위임 */
    DELEGATE_TO_LLM,

    /** 안내 메시지 후 종료 */
    ABORT_WITH_MESSAGE,

    /** 워크플로우 처음부터 다시 */
    RESTART_WORKFLOW
}

/**
 * 기본 LoopGuard 구현 (In-Memory)
 *
 * 테스트 및 단일 인스턴스 환경용.
 * 분산 환경에서는 Redis 기반 구현 사용.
 */
class InMemoryLoopGuardEngine(
    private val config: DisambiguationConfig = DisambiguationConfig.DEFAULT
) : LoopGuardEngine {

    private val attemptCounts = mutableMapOf<String, Int>()
    private val previousResponses = mutableMapOf<String, String?>()

    private fun key(sessionId: String, clarificationType: String) =
        "$sessionId:$clarificationType"

    /**
     * 텍스트 정규화 (동일 답변 비교용)
     *
     * - 소문자 변환
     * - 앞뒤 공백 제거
     * - 연속 공백을 단일 공백으로
     */
    private fun normalizeText(text: String): String =
        text.lowercase().trim().replace(Regex("\\s+"), " ")

    override suspend fun checkAttempt(
        sessionId: String,
        clarificationType: String,
        context: Map<String, Any>
    ): LoopGuardResult? {
        if (!config.enableLoopGuard) {
            return null // 비활성화되면 항상 허용
        }

        val k = key(sessionId, clarificationType)
        val attemptCount = attemptCounts[k] ?: 0

        // 1. 최대 시도 초과 확인
        if (attemptCount >= config.maxClarifyAttempts) {
            return LoopGuardResult.maxAttemptsExceeded(
                attemptCount = attemptCount,
                maxAttempts = config.maxClarifyAttempts
            )
        }

        // 2. 사용자 불만 신호 감지
        val userText = context["userText"] as? String
        if (userText != null) {
            val (frustrated, patterns) = config.detectFrustration(userText)
            if (frustrated) {
                return LoopGuardResult.frustrationDetected(
                    attemptCount = attemptCount,
                    maxAttempts = config.maxClarifyAttempts,
                    detectedPatterns = patterns
                )
            }

            // 3. 동일 답변 반복 확인 (normalize 적용: 소문자 + trim + 공백 정규화)
            val normalizedText = normalizeText(userText)
            val previousResponse = previousResponses[k]
            if (previousResponse != null && previousResponse == normalizedText) {
                return LoopGuardResult(
                    blocked = true,
                    reason = LoopGuardReason.REPEATED_SAME_ANSWER,
                    attemptCount = attemptCount,
                    maxAttempts = config.maxClarifyAttempts,
                    suggestedAction = LoopGuardAction.DELEGATE_TO_LLM,
                    metadata = mapOf("repeatedText" to userText)
                )
            }

            // 현재 응답 저장 (normalized)
            previousResponses[k] = normalizedText
        }

        return null // 허용
    }

    override suspend fun recordAttempt(
        sessionId: String,
        clarificationType: String,
        success: Boolean
    ) {
        val k = key(sessionId, clarificationType)
        if (success) {
            attemptCounts.remove(k)
            previousResponses.remove(k)
        } else {
            attemptCounts[k] = (attemptCounts[k] ?: 0) + 1
        }
    }

    override suspend fun reset(sessionId: String) {
        val prefix = "$sessionId:"
        attemptCounts.keys.filter { it.startsWith(prefix) }.forEach { attemptCounts.remove(it) }
        previousResponses.keys.filter { it.startsWith(prefix) }.forEach { previousResponses.remove(it) }
    }

    override suspend fun resetType(sessionId: String, clarificationType: String) {
        val k = key(sessionId, clarificationType)
        attemptCounts.remove(k)
        previousResponses.remove(k)
    }

    override suspend fun getAttemptCount(sessionId: String, clarificationType: String): Int =
        attemptCounts[key(sessionId, clarificationType)] ?: 0
}

/**
 * No-Op LoopGuard (항상 허용)
 *
 * LoopGuard 비활성화 시 사용.
 */
object NoOpLoopGuardEngine : LoopGuardEngine {
    override suspend fun checkAttempt(
        sessionId: String,
        clarificationType: String,
        context: Map<String, Any>
    ): LoopGuardResult? = null

    override suspend fun recordAttempt(
        sessionId: String,
        clarificationType: String,
        success: Boolean
    ) { /* No-op */ }

    override suspend fun reset(sessionId: String) { /* No-op */ }

    override suspend fun resetType(sessionId: String, clarificationType: String) { /* No-op */ }

    override suspend fun getAttemptCount(sessionId: String, clarificationType: String): Int = 0
}
