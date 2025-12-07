package io.github.noailabs.spice.intelligence

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Intelligence Layer 전용 세션 상태
 *
 * 기존 워크플로우 실행 세션(SessionHistory)과 별도로,
 * Clarification/HITL/Re-analysis를 정확히 추적하기 위한 지능 레이어 전용 세션 상태.
 *
 * Redis 저장 키 패턴: `kai:intelligence:{sessionId}`
 *
 * ## 주요 용도
 * - Clarification 시도 횟수 추적 (루프 방지)
 * - 의사결정 이력 기록 (A/B 테스트, 분석)
 * - 후보 선택 이력 관리
 * - 사용자 불만 신호 감지
 *
 * @property sessionId 세션 고유 식별자
 * @property tenantId 테넌트 식별자
 * @property userId 사용자 식별자
 * @property currentWorkflowId 현재 실행 중인 워크플로우 ID
 * @property currentNodeId 현재 노드 ID
 * @property currentRunId 현재 실행 ID (checkpoint 키)
 * @property clarifyAttempts 현재까지 Clarification 시도 횟수
 * @property maxClarifyAttempts 최대 허용 Clarification 횟수
 * @property clarificationHistory Clarification 시도 기록
 * @property decisionHistory 의사결정 기록
 * @property lastDecisionSource 마지막 결정 출처
 * @property lastDecisionConfidence 마지막 결정 신뢰도
 * @property selectedCandidateHistory 후보 선택 이력
 * @property lastUserUtterance 마지막 사용자 발화
 * @property utteranceHistory 최근 사용자 발화 이력 (최대 5개)
 * @property llmOverrideRequested LLM 강제 요청 여부 ("그냥 해줘" 등 감지)
 * @property frustrationDetected 사용자 불만 신호 감지 여부
 * @property createdAt 세션 생성 시각
 * @property lastActivityAt 마지막 활동 시각
 *
 * @since 1.1.0
 */
@Serializable
data class IntelligenceSession(
    val sessionId: String,
    val tenantId: String,
    val userId: String,

    // 현재 컨텍스트
    val currentWorkflowId: String? = null,
    val currentNodeId: String? = null,
    val currentRunId: String? = null,

    // Clarification 추적
    val clarifyAttempts: Int = 0,
    val maxClarifyAttempts: Int = 3,
    val clarificationHistory: List<ClarificationAttempt> = emptyList(),
    val lastClarificationType: String? = null,

    // 결정 추적
    val decisionHistory: List<DecisionRecord> = emptyList(),
    val lastDecisionSource: DecisionSource? = null,
    val lastDecisionConfidence: Double? = null,

    // 후보 선택 이력
    val selectedCandidateHistory: List<CandidateSelection> = emptyList(),

    // 사용자 발화 추적
    val lastUserUtterance: String? = null,
    val utteranceHistory: List<String> = emptyList(),

    // 특수 플래그
    val llmOverrideRequested: Boolean = false,
    val frustrationDetected: Boolean = false,

    // 타임스탬프
    @Contextual
    val createdAt: Instant = Clock.System.now(),
    @Contextual
    val lastActivityAt: Instant = Clock.System.now()
) {
    /**
     * Clarification 시도 가능 여부
     */
    val canClarify: Boolean
        get() = clarifyAttempts < maxClarifyAttempts

    /**
     * 남은 Clarification 시도 횟수
     */
    val remainingClarifyAttempts: Int
        get() = maxOf(0, maxClarifyAttempts - clarifyAttempts)

    /**
     * 세션 활성 여부 (최근 1시간 내 활동)
     */
    fun isActive(now: Instant = Clock.System.now()): Boolean {
        val oneHourMs = 60 * 60 * 1000L
        return (now.toEpochMilliseconds() - lastActivityAt.toEpochMilliseconds()) < oneHourMs
    }

    /**
     * Clarification 시도 횟수 증가
     */
    fun incrementClarifyAttempts(): IntelligenceSession =
        copy(
            clarifyAttempts = clarifyAttempts + 1,
            lastActivityAt = Clock.System.now()
        )

    /**
     * 결정 기록 추가
     */
    fun recordDecision(record: DecisionRecord): IntelligenceSession =
        copy(
            decisionHistory = decisionHistory + record,
            lastDecisionSource = record.source,
            lastDecisionConfidence = record.confidence,
            lastActivityAt = Clock.System.now()
        )

    /**
     * Clarification 시도 기록 추가 (권장)
     *
     * attemptNumber를 자동으로 계산하고 clarifyAttempts를 증가시킴.
     * 이 메서드는 이중 증가 문제를 방지함.
     *
     * @param clarificationType Clarification 유형
     * @param reason Clarification 이유
     * @param candidatesOffered 제시한 옵션들
     */
    fun recordClarification(
        clarificationType: String,
        reason: ClarificationReason,
        candidatesOffered: List<String>
    ): IntelligenceSession {
        val attempt = ClarificationAttempt.create(
            attemptNumber = clarifyAttempts + 1,
            clarificationType = clarificationType,
            reason = reason,
            candidatesOffered = candidatesOffered
        )
        return copy(
            clarificationHistory = clarificationHistory + attempt,
            clarifyAttempts = clarifyAttempts + 1,
            lastClarificationType = clarificationType,
            lastActivityAt = Clock.System.now()
        )
    }

    /**
     * Clarification 시도 기록 추가 (레거시)
     *
     * @deprecated 이중 증가 위험. recordClarification(clarificationType, reason, candidatesOffered) 사용 권장.
     */
    @Deprecated(
        message = "attemptNumber 불일치 위험. 새 시그니처 사용 권장",
        replaceWith = ReplaceWith("recordClarification(attempt.clarificationType, attempt.reason, attempt.candidatesOffered)")
    )
    fun recordClarificationLegacy(attempt: ClarificationAttempt): IntelligenceSession =
        copy(
            clarificationHistory = clarificationHistory + attempt,
            clarifyAttempts = clarifyAttempts + 1,
            lastClarificationType = attempt.clarificationType,
            lastActivityAt = Clock.System.now()
        )

    /**
     * 후보 선택 기록 추가
     */
    fun recordCandidateSelection(selection: CandidateSelection): IntelligenceSession =
        copy(
            selectedCandidateHistory = selectedCandidateHistory + selection,
            lastActivityAt = Clock.System.now()
        )

    /**
     * 사용자 발화 기록
     */
    fun recordUtterance(utterance: String): IntelligenceSession =
        copy(
            lastUserUtterance = utterance,
            utteranceHistory = (utteranceHistory + utterance).takeLast(5),
            lastActivityAt = Clock.System.now()
        )

    /**
     * 불만 신호 감지 설정
     */
    fun markFrustrationDetected(detected: Boolean = true): IntelligenceSession =
        copy(
            frustrationDetected = detected,
            lastActivityAt = Clock.System.now()
        )

    /**
     * LLM 위임 요청 설정
     */
    fun markLLMOverrideRequested(requested: Boolean = true): IntelligenceSession =
        copy(
            llmOverrideRequested = requested,
            lastActivityAt = Clock.System.now()
        )

    /**
     * 워크플로우 컨텍스트 업데이트
     */
    fun withWorkflowContext(workflowId: String, nodeId: String?, runId: String?): IntelligenceSession =
        copy(
            currentWorkflowId = workflowId,
            currentNodeId = nodeId,
            currentRunId = runId,
            lastActivityAt = Clock.System.now()
        )

    /**
     * Clarification 상태 초기화 (새 워크플로우 시작 시)
     */
    fun resetClarification(): IntelligenceSession =
        copy(
            clarifyAttempts = 0,
            lastClarificationType = null,
            frustrationDetected = false,
            llmOverrideRequested = false,
            lastActivityAt = Clock.System.now()
        )

    companion object {
        /**
         * 새 세션 생성
         */
        fun create(
            sessionId: String,
            tenantId: String,
            userId: String,
            maxClarifyAttempts: Int = 3
        ): IntelligenceSession = IntelligenceSession(
            sessionId = sessionId,
            tenantId = tenantId,
            userId = userId,
            maxClarifyAttempts = maxClarifyAttempts
        )

        /**
         * Redis 저장 키 생성
         */
        fun redisKey(sessionId: String): String = "kai:intelligence:$sessionId"
    }
}

/**
 * Clarification 시도 기록
 *
 * 각 Clarification 시도의 상세 정보를 기록합니다.
 *
 * @property attemptNumber 시도 번호 (1부터 시작)
 * @property clarificationType Clarification 유형 (e.g., "reservation_selection", "option_selection")
 * @property reason Clarification 이유
 * @property candidatesOffered 제시한 옵션 ID들
 * @property userResponse 사용자 응답 (null이면 응답 없음)
 * @property resultStatus 결과 상태
 * @property timestamp 시도 시각
 */
@Serializable
data class ClarificationAttempt(
    val attemptNumber: Int,
    val clarificationType: String,
    val reason: ClarificationReason,
    val candidatesOffered: List<String>,
    val userResponse: String? = null,
    val resultStatus: ClarificationResultStatus,
    @Contextual
    val timestamp: Instant = Clock.System.now()
) {
    companion object {
        /**
         * 새 Clarification 시도 생성
         */
        fun create(
            attemptNumber: Int,
            clarificationType: String,
            reason: ClarificationReason,
            candidatesOffered: List<String>
        ): ClarificationAttempt = ClarificationAttempt(
            attemptNumber = attemptNumber,
            clarificationType = clarificationType,
            reason = reason,
            candidatesOffered = candidatesOffered,
            resultStatus = ClarificationResultStatus.PENDING
        )

        /**
         * 성공으로 완료
         */
        fun ClarificationAttempt.complete(userResponse: String): ClarificationAttempt =
            copy(
                userResponse = userResponse,
                resultStatus = ClarificationResultStatus.SUCCESS
            )

        /**
         * 여전히 모호함으로 완료
         */
        fun ClarificationAttempt.stillAmbiguous(userResponse: String): ClarificationAttempt =
            copy(
                userResponse = userResponse,
                resultStatus = ClarificationResultStatus.STILL_AMBIGUOUS
            )
    }
}

/**
 * Clarification 결과 상태
 */
@Serializable
enum class ClarificationResultStatus {
    /** 대기 중 (응답 전) */
    PENDING,

    /** 명확해짐 (성공) */
    SUCCESS,

    /** 여전히 모호함 */
    STILL_AMBIGUOUS,

    /** 사용자 불만 감지 */
    USER_FRUSTRATED,

    /** 응답 없음 (타임아웃) */
    TIMEOUT,

    /** 사용자가 건너뜀 */
    SKIPPED
}

/**
 * Clarification 이유
 */
@Serializable
enum class ClarificationReason {
    /** 후보 간 유사도 차이 작음 */
    AMBIGUOUS,

    /** 전체 신뢰도 낮음 */
    LOW_CONFIDENCE,

    /** 복수 후보 존재 */
    MULTIPLE_CANDIDATES,

    /** 매칭 없음 */
    NO_MATCH
}

/**
 * 의사결정 기록
 *
 * A/B 테스트 및 분석을 위한 의사결정 기록.
 *
 * @property nodeId 노드 ID
 * @property decisionType 결정 유형
 * @property source 결정 출처
 * @property confidence 신뢰도 (0.0~1.0)
 * @property inputUtterance 입력 발화
 * @property selectedOption 선택된 옵션 (있을 경우)
 * @property alternativeOptions 대안 옵션들 (있을 경우)
 * @property latencyMs 결정 소요 시간 (밀리초)
 * @property timestamp 결정 시각
 */
@Serializable
data class DecisionRecord(
    val nodeId: String,
    val decisionType: DecisionType,
    val source: DecisionSource,
    val confidence: Double,
    val inputUtterance: String,
    val selectedOption: String? = null,
    val alternativeOptions: List<String>? = null,
    val latencyMs: Long,
    @Contextual
    val timestamp: Instant = Clock.System.now()
) {
    companion object {
        fun create(
            nodeId: String,
            decisionType: DecisionType,
            source: DecisionSource,
            confidence: Double,
            inputUtterance: String,
            latencyMs: Long,
            selectedOption: String? = null,
            alternativeOptions: List<String>? = null
        ): DecisionRecord = DecisionRecord(
            nodeId = nodeId,
            decisionType = decisionType,
            source = source,
            confidence = confidence,
            inputUtterance = inputUtterance,
            selectedOption = selectedOption,
            alternativeOptions = alternativeOptions,
            latencyMs = latencyMs
        )
    }
}

/**
 * 의사결정 유형
 */
@Serializable
enum class DecisionType {
    /** 단순 예/아니오 응답 */
    YES_NO,

    /** 단일 옵션 선택 */
    OPTION_SELECTED,

    /** 다중 옵션 선택 */
    MULTI_OPTION_SELECTED,

    /** 수량 선택 */
    QUANTITY_SELECTED,

    /** Clarification 필요 */
    NEED_CLARIFICATION,

    /** 상위 LLM에 위임 */
    DELEGATE_TO_LLM,

    /** 다른 워크플로우로 전환 */
    NEED_REORCHESTRATION
}

/**
 * 의사결정 출처
 *
 * Observability + A/B Test + Routing Metrics
 */
@Serializable
enum class DecisionSource {
    /** sLM (규칙 기반 / 경량 모델) */
    SLM,

    /** Embedding 시멘틱 매칭 */
    EMBEDDING,

    /** Nano LLM (GPT-4o-mini 급) */
    NANO,

    /** 폴백 (키워드 매칭 등) */
    FALLBACK,

    /** HITL 응답 */
    HITL,

    /** Intent 재분석 */
    REANALYSIS,

    /** One-Shot Reasoner (GPT-5.1급) - Intelligence Layer v2 */
    REASONER
}

/**
 * 후보 선택 기록
 *
 * @property candidateType 후보 유형 (e.g., "reservation", "option")
 * @property candidateId 선택된 후보 ID
 * @property selectionMethod 선택 방법
 * @property confidence 신뢰도
 * @property timestamp 선택 시각
 */
@Serializable
data class CandidateSelection(
    val candidateType: String,
    val candidateId: String,
    val selectionMethod: SelectionMethod,
    val confidence: Double,
    @Contextual
    val timestamp: Instant = Clock.System.now()
) {
    companion object {
        fun autoSingle(
            candidateType: String,
            candidateId: String,
            confidence: Double
        ): CandidateSelection = CandidateSelection(
            candidateType = candidateType,
            candidateId = candidateId,
            selectionMethod = SelectionMethod.AUTO_SINGLE,
            confidence = confidence
        )

        fun semanticMatch(
            candidateType: String,
            candidateId: String,
            confidence: Double
        ): CandidateSelection = CandidateSelection(
            candidateType = candidateType,
            candidateId = candidateId,
            selectionMethod = SelectionMethod.SEMANTIC_MATCH,
            confidence = confidence
        )

        fun userSelection(
            candidateType: String,
            candidateId: String
        ): CandidateSelection = CandidateSelection(
            candidateType = candidateType,
            candidateId = candidateId,
            selectionMethod = SelectionMethod.USER_SELECTION,
            confidence = 1.0
        )
    }
}

/**
 * 선택 방법
 */
@Serializable
enum class SelectionMethod {
    /** 단건 자동 선택 */
    AUTO_SINGLE,

    /** 시멘틱 매칭 */
    SEMANTIC_MATCH,

    /** 사용자 직접 선택 */
    USER_SELECTION,

    /** LoopGuard 강제 선택 */
    FORCE_SELECTION,

    /** LLM 위임 결과 */
    LLM_OVERRIDE
}
