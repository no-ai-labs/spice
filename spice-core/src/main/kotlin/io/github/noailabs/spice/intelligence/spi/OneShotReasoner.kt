package io.github.noailabs.spice.intelligence.spi

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.CompositeDecision
import io.github.noailabs.spice.intelligence.PolicyHint

/**
 * One-Shot Reasoner SPI (Intelligence Layer v2)
 *
 * GPT-5.1급 모델로 모든 cross-cutting concern을 한 번에 판단하는 인터페이스.
 *
 * ## 판단 항목
 * - domainRelevance: 도메인 관련성
 * - offDomain: Off-domain 여부
 * - intentShift: Intent 전환 감지
 * - newWorkflow/resumeNode: 워크플로우 전환/재개
 * - aiCanonical: semantic canonical override
 *
 * ## Clean Architecture
 * - spice-core: 이 인터페이스 정의 (포트)
 * - kai-core: OpenAI/Claude 구현체 (어댑터)
 *
 * ## 캐시 키
 * hash(utterance + prevNodes + workflowId)
 *
 * @since 2.0.0
 */
interface OneShotReasoner {

    /**
     * 발화에 대한 종합 의사결정
     *
     * @param request 추론 요청
     * @return 복합 결정 결과
     */
    suspend fun reason(request: OneShotRequest): SpiceResult<CompositeDecision>

    /**
     * 지원하는 모델 식별자
     */
    val modelId: String

    /**
     * 최대 입력 토큰 수
     */
    val maxInputTokens: Int
        get() = 4096
}

/**
 * One-Shot 추론 요청
 *
 * ## 오메가 지침
 * Policy-RAG, session(utteranceHistory, clarifyAttempts),
 * semantic scores, workflow/node id 포함 필수.
 *
 * ## JSON 스키마 (LLM 프롬프트용)
 * ```json
 * {
 *   "utterance": "취소하고 싶어요",
 *   "workflowId": "reservation_flow",
 *   "currentNodeId": "confirm_node",
 *   "prevNodes": ["start", "input_date"],
 *   "options": [
 *     {"canonical": "confirm_yes", "description": "예, 확인합니다"},
 *     {"canonical": "confirm_no", "description": "아니요, 취소합니다"}
 *   ],
 *   "semanticScores": {
 *     "topScore": 0.72,
 *     "secondScore": 0.68,
 *     "gap": 0.04,
 *     "topCanonical": "confirm_no"
 *   },
 *   "policyHints": [
 *     {"policyId": "cancel-policy", "content": "취소는 체크인 3일 전까지...", "score": 0.85}
 *   ],
 *   "session": {
 *     "clarifyAttempts": 1,
 *     "maxClarifyAttempts": 3,
 *     "utteranceHistory": ["예약하고 싶어요", "10월 15일이요"],
 *     "frustrationDetected": false
 *   },
 *   "availableWorkflows": ["reservation_flow", "cancel_flow", "inquiry_flow"]
 * }
 * ```
 *
 * @property utterance 사용자 발화 (정규화된)
 * @property workflowId 현재 워크플로우 ID
 * @property currentNodeId 현재 노드 ID
 * @property prevNodes 이전 노드 경로 (최근 N개)
 * @property options 선택 가능한 옵션들 (HITL selection인 경우)
 * @property semanticScores Fast Layer 시멘틱 점수 (참고용)
 * @property policyHints Policy-RAG 검색 결과 (topK)
 * @property sessionContext 세션 컨텍스트
 * @property availableWorkflows 전환 가능한 워크플로우 목록
 * @property metadata 추가 메타데이터
 *
 * @since 2.0.0
 */
data class OneShotRequest(
    val utterance: String,
    val workflowId: String?,
    val currentNodeId: String?,
    val prevNodes: List<String>,
    val options: List<RoutingOption> = emptyList(),
    val semanticScores: SemanticScoreHint? = null,
    val policyHints: List<PolicyHint> = emptyList(),
    val sessionContext: SessionContext,
    val availableWorkflows: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * 캐시 키 생성
     */
    fun toCacheKey(): String {
        val normalizedUtterance = utterance.lowercase().trim()
        val prevNodesHash = prevNodes.joinToString("|").hashCode()
        return "$normalizedUtterance|$prevNodesHash|${workflowId ?: "_global"}"
    }

    /**
     * IntelligenceContext에서 생성
     */
    companion object {
        fun from(
            utterance: String,
            context: IntelligenceContext,
            semanticResult: SemanticMatchResult? = null,
            policyHints: List<PolicyHint> = emptyList(),
            availableWorkflows: List<String> = emptyList()
        ) = OneShotRequest(
            utterance = utterance,
            workflowId = context.workflowId,
            currentNodeId = context.currentNodeId,
            prevNodes = context.prevNodes,
            options = context.routingOptions,
            semanticScores = semanticResult?.let {
                SemanticScoreHint(
                    topScore = it.topScore,
                    secondScore = it.secondScore,
                    gap = it.gap,
                    topCanonical = it.topCanonical
                )
            },
            policyHints = policyHints,
            sessionContext = SessionContext.from(context.session),
            availableWorkflows = availableWorkflows
        )
    }
}

/**
 * 시멘틱 점수 힌트 (OneShotReasoner 입력용)
 *
 * Fast Layer 결과를 참고용으로 전달.
 *
 * @since 2.0.0
 */
data class SemanticScoreHint(
    val topScore: Double,
    val secondScore: Double,
    val gap: Double,
    val topCanonical: String?
)

// RoutingOption은 IntelligenceMiddleware.kt에서 정의됨

/**
 * 세션 컨텍스트 (One-Shot Reasoner 입력용)
 *
 * IntelligenceSession에서 추출한 핵심 정보만 포함.
 *
 * ## JSON 스키마 (LLM 프롬프트용)
 * ```json
 * {
 *   "sessionId": "sess_123",
 *   "tenantId": "tenant_abc",
 *   "userId": "user_456",
 *   "clarifyAttempts": 1,
 *   "maxClarifyAttempts": 3,
 *   "frustrationDetected": false,
 *   "llmOverrideRequested": false,
 *   "utteranceHistory": ["예약하고 싶어요", "10월 15일이요"]
 * }
 * ```
 *
 * @since 2.0.0
 */
data class SessionContext(
    val sessionId: String,
    val tenantId: String,
    val userId: String,
    val clarifyAttempts: Int,
    val maxClarifyAttempts: Int,
    val frustrationDetected: Boolean,
    val llmOverrideRequested: Boolean,
    val utteranceHistory: List<String>
) {
    /**
     * Clarification 가능 여부
     */
    val canClarify: Boolean
        get() = clarifyAttempts < maxClarifyAttempts

    /**
     * 재질문 한도 임박 여부 (1회 남음)
     */
    val isNearClarifyLimit: Boolean
        get() = clarifyAttempts == maxClarifyAttempts - 1

    companion object {
        /**
         * IntelligenceSession에서 생성
         */
        fun from(session: io.github.noailabs.spice.intelligence.IntelligenceSession) = SessionContext(
            sessionId = session.sessionId,
            tenantId = session.tenantId,
            userId = session.userId,
            clarifyAttempts = session.clarifyAttempts,
            maxClarifyAttempts = session.maxClarifyAttempts,
            frustrationDetected = session.frustrationDetected,
            llmOverrideRequested = session.llmOverrideRequested,
            utteranceHistory = session.utteranceHistory
        )

        /**
         * 기본 컨텍스트 (테스트용)
         */
        fun default(
            sessionId: String = "test-session",
            tenantId: String = "test-tenant",
            userId: String = "test-user"
        ) = SessionContext(
            sessionId = sessionId,
            tenantId = tenantId,
            userId = userId,
            clarifyAttempts = 0,
            maxClarifyAttempts = 3,
            frustrationDetected = false,
            llmOverrideRequested = false,
            utteranceHistory = emptyList()
        )
    }
}
