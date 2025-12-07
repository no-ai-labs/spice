package io.github.noailabs.spice.intelligence.impl

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.PolicyHint
import io.github.noailabs.spice.intelligence.PolicyType
import io.github.noailabs.spice.intelligence.spi.PolicyDocument
import io.github.noailabs.spice.intelligence.spi.PolicyStore

/**
 * InMemory Policy Store (테스트용)
 *
 * 테스트/개발 환경에서 사용하는 간단한 키워드 매칭 기반 구현체.
 * 실제 벡터 검색은 kai-core의 OpenSearch 구현에서 수행.
 *
 * ## 핵심 원칙 준수
 * - 이 구현체도 "검색"만 수행
 * - 판단 로직 절대 포함 안 함!
 *
 * ## 사용 예시
 * ```kotlin
 * val policyStore = InMemoryPolicyStore()
 *
 * // 정책 추가
 * policyStore.addPolicy(
 *     PolicyDocument(
 *         id = "policy-1",
 *         content = "취소는 체크인 3일 전까지 가능합니다.",
 *         policyType = PolicyType.BUSINESS_RULE,
 *         workflowId = "reservation_cancel"
 *     )
 * )
 *
 * // 검색
 * val hints = policyStore.search("취소 가능한가요?", topK = 3)
 * ```
 *
 * @since 2.0.0
 */
class InMemoryPolicyStore : PolicyStore {

    private val policies = mutableListOf<PolicyDocument>()

    override suspend fun search(
        query: String,
        workflowId: String?,
        topK: Int
    ): SpiceResult<List<PolicyHint>> {
        val normalizedQuery = query.lowercase()

        val matches = policies
            // 워크플로우 필터링
            .filter { workflowId == null || it.workflowId == null || it.workflowId == workflowId }
            // 키워드 매칭 (간단한 유사도)
            .map { policy ->
                val score = calculateSimpleScore(normalizedQuery, policy.content.lowercase())
                policy to score
            }
            // 점수순 정렬
            .sortedByDescending { it.second }
            // topK 선택
            .take(topK)
            // PolicyHint로 변환
            .map { (policy, score) ->
                PolicyHint(
                    policyId = policy.id,
                    content = policy.content,
                    workflowId = policy.workflowId,
                    score = score,
                    policyType = policy.policyType,
                    metadata = policy.metadata
                )
            }

        return SpiceResult.success(matches)
    }

    override suspend fun store(policy: PolicyDocument): SpiceResult<String> {
        // 기존 정책 업데이트 또는 새로 추가
        val existingIndex = policies.indexOfFirst { it.id == policy.id }
        if (existingIndex >= 0) {
            policies[existingIndex] = policy
        } else {
            policies.add(policy)
        }
        return SpiceResult.success(policy.id)
    }

    override suspend fun delete(policyId: String): SpiceResult<Unit> {
        policies.removeIf { it.id == policyId }
        return SpiceResult.success(Unit)
    }

    /**
     * 정책 추가 (편의 메서드)
     */
    fun addPolicy(policy: PolicyDocument) {
        val existingIndex = policies.indexOfFirst { it.id == policy.id }
        if (existingIndex >= 0) {
            policies[existingIndex] = policy
        } else {
            policies.add(policy)
        }
    }

    /**
     * 정책 추가 (간단한 형태)
     */
    fun addPolicy(
        id: String,
        content: String,
        policyType: PolicyType = PolicyType.BUSINESS_RULE,
        workflowId: String? = null
    ) {
        addPolicy(PolicyDocument(id, content, workflowId, policyType))
    }

    /**
     * 모든 정책 초기화
     */
    fun reset() {
        policies.clear()
    }

    /**
     * 정책 개수
     */
    fun size(): Int = policies.size

    /**
     * 간단한 키워드 기반 유사도 계산
     *
     * 실제 구현에서는 embedding 기반 유사도 사용
     */
    private fun calculateSimpleScore(query: String, content: String): Double {
        val queryWords = query.split(Regex("\\s+")).filter { it.length >= 2 }
        if (queryWords.isEmpty()) return 0.0

        val matchCount = queryWords.count { word -> content.contains(word) }
        return matchCount.toDouble() / queryWords.size
    }

    companion object {
        /**
         * 샘플 정책이 포함된 인스턴스 (테스트용)
         */
        fun withSamplePolicies(): InMemoryPolicyStore {
            return InMemoryPolicyStore().apply {
                addPolicy(
                    id = "cancel-policy",
                    content = "예약 취소는 체크인 3일 전까지 전액 환불됩니다. 이후 취소 시 50% 환불됩니다.",
                    policyType = PolicyType.BUSINESS_RULE,
                    workflowId = "reservation_cancel"
                )
                addPolicy(
                    id = "change-policy",
                    content = "예약 날짜 변경은 체크인 1일 전까지 가능합니다. 요금 차이가 발생할 수 있습니다.",
                    policyType = PolicyType.BUSINESS_RULE,
                    workflowId = "reservation_change"
                )
                addPolicy(
                    id = "escalation-policy",
                    content = "고객이 3회 이상 동일 질문을 반복하면 상담원 연결을 제안합니다.",
                    policyType = PolicyType.ESCALATION
                )
            }
        }
    }
}
