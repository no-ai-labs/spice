package io.github.noailabs.spice.intelligence

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.hitl.result.HITLOption
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Intelligence Layer 에러 핸들러 SPI
 *
 * Edge-case flow 정의로 Enterprise급 안정성 확보.
 * 구현체는 spice-infra 또는 kai-core에서 제공.
 *
 * ## 복구 전략
 * - Embedding 오류 → Keyword Fallback
 * - LLM Rate Limit → Retry with delay
 * - HITL 파싱 오류 → 재질문 또는 Escalate
 * - Decision 분기 실패 → LLM 위임
 *
 * @since 1.1.0
 */
interface IntelligenceErrorHandler {
    /**
     * 에러 처리 및 복구 전략 결정
     *
     * @param error Intelligence Layer 에러
     * @param context 에러 발생 컨텍스트
     * @return 복구 전략
     */
    suspend fun handleError(
        error: IntelligenceError,
        context: IntelligenceErrorContext
    ): ErrorRecoveryResult
}

/**
 * 에러 발생 컨텍스트
 *
 * @property sessionId 세션 ID
 * @property tenantId 테넌트 ID
 * @property userId 사용자 ID
 * @property nodeId 현재 노드 ID
 * @property attemptNumber 현재 시도 번호
 * @property clarifyAttempts Clarification 시도 횟수
 * @property metadata 추가 메타데이터
 */
@Serializable
data class IntelligenceErrorContext(
    val sessionId: String,
    val tenantId: String,
    val userId: String,
    val nodeId: String? = null,
    val attemptNumber: Int = 1,
    val clarifyAttempts: Int = 0,
    val metadata: Map<String, @Contextual Any> = emptyMap()
)

/**
 * Intelligence Layer 에러 타입
 *
 * 각 에러 타입별로 적절한 복구 전략이 다름.
 */
sealed class IntelligenceError(
    open val message: String,
    open val cause: Throwable? = null
) {
    /**
     * Error code for programmatic handling
     */
    abstract val code: String

    /**
     * Convert to SpiceError
     */
    fun toSpiceError(): SpiceError = SpiceError.ExecutionError(
        message = message,
        cause = cause,
        context = mapOf("intelligenceErrorCode" to code)
    )

    // ═══════════════════════════════════════════════════════
    // 1. Embedding 에러
    // ═══════════════════════════════════════════════════════

    /**
     * Embedding 모델 호출 실패
     */
    data class EmbeddingError(
        override val message: String,
        val model: String,
        val inputLength: Int,
        override val cause: Throwable? = null
    ) : IntelligenceError(message, cause) {
        override val code: String = "EMBEDDING_ERROR"
    }

    /**
     * Embedding 호출 타임아웃
     */
    data class EmbeddingTimeoutError(
        override val message: String,
        val model: String,
        val timeoutMs: Long
    ) : IntelligenceError(message) {
        override val code: String = "EMBEDDING_TIMEOUT"
    }

    /**
     * Embedding 차원 불일치
     */
    data class EmbeddingDimensionMismatch(
        override val message: String,
        val expected: Int,
        val actual: Int
    ) : IntelligenceError(message) {
        override val code: String = "EMBEDDING_DIMENSION_MISMATCH"
    }

    // ═══════════════════════════════════════════════════════
    // 2. LLM 에러
    // ═══════════════════════════════════════════════════════

    /**
     * LLM 호출 실패
     */
    data class LLMCallError(
        override val message: String,
        val model: String,
        val errorCode: String?,
        val isRetryable: Boolean,
        override val cause: Throwable? = null
    ) : IntelligenceError(message, cause) {
        override val code: String = "LLM_CALL_ERROR"
    }

    /**
     * LLM Rate Limit
     */
    data class LLMRateLimitError(
        override val message: String,
        val model: String,
        val retryAfterSeconds: Long?
    ) : IntelligenceError(message) {
        override val code: String = "LLM_RATE_LIMIT"
    }

    /**
     * LLM 토큰 한도 초과
     */
    data class LLMTokenLimitError(
        override val message: String,
        val model: String,
        val requestedTokens: Int,
        val maxTokens: Int
    ) : IntelligenceError(message) {
        override val code: String = "LLM_TOKEN_LIMIT"
    }

    // ═══════════════════════════════════════════════════════
    // 3. HITL 에러
    // ═══════════════════════════════════════════════════════

    /**
     * HITL 응답 파싱 실패
     */
    data class HITLParseError(
        override val message: String,
        val rawResponse: String,
        val expectedFormat: String
    ) : IntelligenceError(message) {
        override val code: String = "HITL_PARSE_ERROR"
    }

    /**
     * HITL 응답 타임아웃
     */
    data class HITLTimeoutError(
        override val message: String,
        val toolCallId: String,
        val waitedMs: Long
    ) : IntelligenceError(message) {
        override val code: String = "HITL_TIMEOUT"
    }

    /**
     * HITL 응답 취소
     */
    data class HITLCancelledError(
        override val message: String,
        val toolCallId: String,
        val reason: String?
    ) : IntelligenceError(message) {
        override val code: String = "HITL_CANCELLED"
    }

    // ═══════════════════════════════════════════════════════
    // 4. Decision 에러
    // ═══════════════════════════════════════════════════════

    /**
     * Decision 분기 매칭 실패
     */
    data class DecisionNoBranchMatched(
        override val message: String,
        val nodeId: String,
        val variable: String,
        val actualValue: Any?,
        val availableCases: List<String>
    ) : IntelligenceError(message) {
        override val code: String = "DECISION_NO_BRANCH_MATCHED"
    }

    /**
     * Decision 변수 없음
     */
    data class DecisionVariableNotFound(
        override val message: String,
        val nodeId: String,
        val variable: String
    ) : IntelligenceError(message) {
        override val code: String = "DECISION_VARIABLE_NOT_FOUND"
    }

    /**
     * Decision 타입 불일치
     */
    data class DecisionTypeMismatch(
        override val message: String,
        val nodeId: String,
        val variable: String,
        val expectedType: String,
        val actualType: String
    ) : IntelligenceError(message) {
        override val code: String = "DECISION_TYPE_MISMATCH"
    }

    // ═══════════════════════════════════════════════════════
    // 5. 사용자 입력 에러
    // ═══════════════════════════════════════════════════════

    /**
     * 잘못된 사용자 입력
     */
    data class InvalidUserInput(
        override val message: String,
        val input: String,
        val reason: String
    ) : IntelligenceError(message) {
        override val code: String = "INVALID_USER_INPUT"
    }

    /**
     * 사용자 불만 신호 감지
     */
    data class UserFrustrationDetected(
        override val message: String,
        val input: String,
        val signals: List<String>
    ) : IntelligenceError(message) {
        override val code: String = "USER_FRUSTRATION_DETECTED"
    }

    // ═══════════════════════════════════════════════════════
    // 6. Semantic Matching 에러
    // ═══════════════════════════════════════════════════════

    /**
     * 시멘틱 매칭 실패 (신뢰도 너무 낮음)
     */
    data class SemanticMatchFailed(
        override val message: String,
        val utterance: String,
        val bestScore: Double,
        val threshold: Double
    ) : IntelligenceError(message) {
        override val code: String = "SEMANTIC_MATCH_FAILED"
    }

    /**
     * 시멘틱 매칭 모호 (격차 부족)
     */
    data class SemanticMatchAmbiguous(
        override val message: String,
        val utterance: String,
        val topScore: Double,
        val secondScore: Double,
        val gap: Double
    ) : IntelligenceError(message) {
        override val code: String = "SEMANTIC_MATCH_AMBIGUOUS"
    }

    companion object {
        /**
         * Embedding 에러 생성
         */
        fun embeddingError(
            message: String,
            model: String,
            inputLength: Int,
            cause: Throwable? = null
        ) = EmbeddingError(message, model, inputLength, cause)

        /**
         * LLM 호출 에러 생성
         */
        fun llmError(
            message: String,
            model: String,
            errorCode: String?,
            isRetryable: Boolean,
            cause: Throwable? = null
        ) = LLMCallError(message, model, errorCode, isRetryable, cause)

        /**
         * HITL 파싱 에러 생성
         */
        fun hitlParseError(
            rawResponse: String,
            expectedFormat: String
        ) = HITLParseError(
            message = "Failed to parse HITL response: expected $expectedFormat",
            rawResponse = rawResponse,
            expectedFormat = expectedFormat
        )

        /**
         * Decision 분기 실패 생성
         */
        fun noBranchMatched(
            nodeId: String,
            variable: String,
            actualValue: Any?,
            availableCases: List<String>
        ) = DecisionNoBranchMatched(
            message = "No branch matched for $variable = $actualValue in node $nodeId",
            nodeId = nodeId,
            variable = variable,
            actualValue = actualValue,
            availableCases = availableCases
        )

        /**
         * 사용자 불만 감지 생성
         */
        fun frustrationDetected(
            input: String,
            signals: List<String>
        ) = UserFrustrationDetected(
            message = "User frustration detected: ${signals.joinToString(", ")}",
            input = input,
            signals = signals
        )
    }
}

/**
 * 에러 복구 결과
 */
sealed class ErrorRecoveryResult {

    /**
     * Fallback으로 처리됨
     *
     * @property fromSource 원래 결정 출처
     * @property toSource Fallback 대상
     * @property result Fallback 결과
     */
    data class Fallback(
        val fromSource: DecisionSource,
        val toSource: DecisionSource,
        val result: FallbackResult
    ) : ErrorRecoveryResult()

    /**
     * Retry 필요
     *
     * @property delayMs 대기 시간 (밀리초)
     * @property maxAttempts 최대 시도 횟수
     * @property currentAttempt 현재 시도 번호
     */
    data class Retry(
        val delayMs: Long,
        val maxAttempts: Int,
        val currentAttempt: Int
    ) : ErrorRecoveryResult() {
        val hasMoreAttempts: Boolean
            get() = currentAttempt < maxAttempts
    }

    /**
     * Degrade (품질 저하 허용)
     *
     * @property result 저하된 결과
     * @property qualityLoss 품질 손실 설명
     */
    data class Degrade(
        val result: FallbackResult,
        val qualityLoss: String
    ) : ErrorRecoveryResult()

    /**
     * 사용자에게 직접 질문
     *
     * @property question 질문 내용
     * @property options 선택지 (있는 경우)
     * @property hitlType HITL 유형
     */
    data class AskUser(
        val question: String,
        val options: List<HITLOption> = emptyList(),
        val hitlType: HitlType = HitlType.FREE_TEXT
    ) : ErrorRecoveryResult()

    /**
     * 상담원 연결
     *
     * @property reason 에스컬레이션 사유
     * @property context 컨텍스트 정보
     */
    data class Escalate(
        val reason: String,
        val context: Map<String, Any> = emptyMap()
    ) : ErrorRecoveryResult()

    /**
     * 복구 불가
     *
     * @property error 원본 에러
     * @property userMessage 사용자에게 표시할 메시지
     */
    data class Unrecoverable(
        val error: IntelligenceError,
        val userMessage: String
    ) : ErrorRecoveryResult()

    /**
     * 무시 (에러를 무시하고 계속 진행)
     *
     * @property reason 무시 사유
     */
    data class Ignore(
        val reason: String
    ) : ErrorRecoveryResult()
}

/**
 * Fallback 결과
 *
 * @property selectedOptionId 선택된 옵션 ID (있는 경우)
 * @property confidence 신뢰도
 * @property reasoning 추론 근거
 */
@Serializable
data class FallbackResult(
    val selectedOptionId: String? = null,
    val confidence: Double = 0.5,
    val reasoning: String? = null
)

/**
 * HITL 유형
 */
@Serializable
enum class HitlType {
    /** 선택지 제시 */
    SELECTION,

    /** 자유 텍스트 입력 */
    FREE_TEXT,

    /** 수량 선택 */
    QUANTITY,

    /** 확인 (예/아니오) */
    CONFIRMATION
}

/**
 * 에러 핸들러 설정
 */
@Serializable
data class IntelligenceErrorConfig(
    /** LLM 호출 최대 재시도 횟수 */
    val maxLLMRetries: Int = 3,

    /** Clarification 최대 시도 횟수 */
    val maxClarifyAttempts: Int = 3,

    /** 매칭 실패 시 LLM에 위임 */
    val delegateUnmatchedToLLM: Boolean = true,

    /** 반복 실패 시 에스컬레이션 */
    val escalateOnRepeatedFailures: Boolean = true,

    /** LLM Rate Limit 기본 대기 시간 (초) */
    val defaultRateLimitWaitSeconds: Long = 5,

    /** Embedding 오류 시 키워드 폴백 활성화 */
    val enableKeywordFallback: Boolean = true
) {
    companion object {
        val DEFAULT = IntelligenceErrorConfig()

        val AGGRESSIVE = IntelligenceErrorConfig(
            maxLLMRetries = 5,
            maxClarifyAttempts = 5,
            delegateUnmatchedToLLM = true,
            escalateOnRepeatedFailures = false
        )

        val CONSERVATIVE = IntelligenceErrorConfig(
            maxLLMRetries = 2,
            maxClarifyAttempts = 2,
            delegateUnmatchedToLLM = false,
            escalateOnRepeatedFailures = true
        )
    }
}

/**
 * 기본 에러 핸들러 (No-Op)
 *
 * 모든 에러를 Unrecoverable로 처리.
 * 실제 구현체는 kai-core 또는 spice-infra에서 제공.
 */
object NoOpIntelligenceErrorHandler : IntelligenceErrorHandler {
    override suspend fun handleError(
        error: IntelligenceError,
        context: IntelligenceErrorContext
    ): ErrorRecoveryResult {
        return ErrorRecoveryResult.Unrecoverable(
            error = error,
            userMessage = "처리 중 문제가 발생했습니다."
        )
    }
}
