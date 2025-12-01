package io.github.noailabs.spice.intelligence.spi

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.PolicyHint
import io.github.noailabs.spice.intelligence.PolicyType

/**
 * Policy Store SPI (Intelligence Layer v2)
 *
 * 정책 벡터스토어에서 관련 정책을 검색하는 인터페이스.
 * One-Shot Reasoner에 컨텍스트로 제공됨.
 *
 * ## 핵심 원칙: 정보 제공자 ONLY!
 * - 이 인터페이스는 "검색"만 수행
 * - 정책 해석/판단은 OneShotReasoner(kai-core 구현체)에서 수행
 * - search() 결과에 판단 로직 포함 금지!
 * - PolicyHint에 "shouldBlock", "isViolation" 같은 판단 필드 금지!
 *
 * ## 검색 파라미터 계약
 * - query: 사용자 발화 (embedding 변환용)
 * - workflowId: 워크플로우 필터 (optional, null이면 전체 검색)
 * - topK: 반환할 최대 정책 수 (기본 3, 최대 10)
 *
 * ## 반환 필드 계약
 * PolicyHint에 포함되는 필드:
 * - policyId: 정책 ID
 * - content: 정책 텍스트
 * - workflowId: 관련 워크플로우 (nullable)
 * - score: 검색 유사도 점수 (0.0-1.0)
 * - policyType: 정책 유형 (ROUTING, BUSINESS_RULE, RESPONSE_GUIDELINE, PROHIBITION, ESCALATION)
 * - metadata: 추가 메타데이터 (tags, priority, createdAt 등)
 *
 * ## 금지 필드 (판단 로직 포함)
 * - shouldBlock ❌
 * - isViolation ❌
 * - suggestedAction ❌
 * - priority (순위 판단) ❌
 *
 * ## Degrade 정책 (검색 실패/타임아웃 시)
 * ```
 * ┌─────────────────────┬──────────────────────────────────────────────────┐
 * │ 상황                │ Stage3 (OneShotReasoner) 동작                    │
 * ├─────────────────────┼──────────────────────────────────────────────────┤
 * │ 검색 성공           │ policyHints 포함하여 Reasoner 호출               │
 * │                     │ (정상 동작)                                      │
 * ├─────────────────────┼──────────────────────────────────────────────────┤
 * │ 검색 실패/타임아웃  │ policyHints = emptyList()로 Reasoner 호출        │
 * │                     │ Reasoner는 최소 프롬프트로 동작                  │
 * │                     │ 로그: policy_rag_fallback = true                 │
 * ├─────────────────────┼──────────────────────────────────────────────────┤
 * │ PolicyStore 없음    │ policyHints = emptyList()로 Reasoner 호출        │
 * │ (미구현)            │ (위와 동일)                                      │
 * └─────────────────────┴──────────────────────────────────────────────────┘
 * ```
 *
 * **핵심**: Policy-RAG 실패해도 Stage3는 계속 동작!
 * - Reasoner는 policyHints 없이도 기본 판단 수행 가능
 * - 다만 정책 기반 drift 차단은 불가능
 *
 * ## Clean Architecture
 * - spice-core: 이 인터페이스 정의 (벡터DB 독립)
 * - kai-core: OpenSearch/Pinecone 구현체
 *
 * @since 2.0.0
 */
interface PolicyStore {

    /**
     * 발화에 관련된 정책 검색 (정보 제공만!)
     *
     * ## 입력 파라미터
     * @param query 검색 쿼리 (사용자 발화)
     * @param workflowId 현재 워크플로우 (필터링용, optional)
     * @param topK 반환할 최대 정책 수 (기본 3, 최대 10)
     *
     * ## 반환값
     * @return 관련 정책 힌트 목록 (판단 없이 raw 정보만!)
     *
     * ## 정렬 기준
     * score 내림차순 (가장 유사한 정책 먼저)
     */
    suspend fun search(
        query: String,
        workflowId: String? = null,
        topK: Int = DEFAULT_TOP_K
    ): SpiceResult<List<PolicyHint>>

    /**
     * 정책 저장 (optional - 구현체에서 지원 시)
     *
     * @param policy 저장할 정책 문서
     * @return 저장된 정책 ID
     */
    suspend fun store(policy: PolicyDocument): SpiceResult<String> {
        return SpiceResult.failure(
            io.github.noailabs.spice.error.SpiceError.executionError("PolicyStore.store() not implemented")
        )
    }

    /**
     * 정책 삭제 (optional - 구현체에서 지원 시)
     *
     * @param policyId 삭제할 정책 ID
     */
    suspend fun delete(policyId: String): SpiceResult<Unit> {
        return SpiceResult.failure(
            io.github.noailabs.spice.error.SpiceError.executionError("PolicyStore.delete() not implemented")
        )
    }

    companion object {
        /** 기본 topK 값 */
        const val DEFAULT_TOP_K = 3

        /** 최대 topK 값 */
        const val MAX_TOP_K = 10
    }
}

/**
 * 정책 문서 (저장용)
 *
 * @property id 정책 ID
 * @property content 정책 텍스트
 * @property workflowId 관련 워크플로우 ID (optional)
 * @property policyType 정책 유형
 * @property metadata 추가 메타데이터
 *
 * @since 2.0.0
 */
data class PolicyDocument(
    val id: String,
    val content: String,
    val workflowId: String? = null,
    val policyType: PolicyType,
    val metadata: Map<String, Any> = emptyMap()
)
