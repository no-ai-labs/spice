package io.github.noailabs.spice.input

import io.github.noailabs.spice.types.ConversationTurn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 정규화 컨텍스트
 *
 * PatternMiner가 정규화 결정을 내릴 때 참조할 수 있는 모든 컨텍스트 정보.
 * 채널(HITL/API/Form)을 모르고 컨텍스트만 알아야 함.
 *
 * ## 도메인 컨텍스트 활용 예시
 * ```kotlin
 * // Stayfolio: 날짜 입력 → 체크인/체크아웃 규칙
 * domainContext = mapOf("flowType" to "checkin_change")
 *
 * // 보험업: 날짜 → 사고일/지급일 구분
 * domainContext = mapOf("dateSemantics" to "accident_date")
 * ```
 *
 * @property sessionId 세션 ID
 * @property workflowId 현재 워크플로우 ID
 * @property currentNodeId 현재 노드 ID
 * @property locale 로케일 (기본 ko-KR)
 * @property conversationHistory 대화 히스토리
 * @property existingData 기존 데이터 (이전 Tool 결과 등)
 * @property domainContext 도메인별 컨텍스트 (flowType, dateSemantics 등)
 *
 * @since 1.7.0
 */
@Serializable
data class NormalizationContext(
    val sessionId: String,
    val workflowId: String,
    val currentNodeId: String,
    val locale: String = "ko-KR",
    val conversationHistory: List<ConversationTurn> = emptyList(),
    val existingData: Map<String, String> = emptyMap(),
    val domainContext: Map<String, String> = emptyMap()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun fromJson(jsonString: String): NormalizationContext = json.decodeFromString(jsonString)

        /**
         * 최소 컨텍스트 생성 (테스트용)
         */
        fun minimal(
            sessionId: String,
            workflowId: String = "unknown",
            nodeId: String = "unknown"
        ) = NormalizationContext(
            sessionId = sessionId,
            workflowId = workflowId,
            currentNodeId = nodeId
        )
    }

    fun toJson(): String = json.encodeToString(this)

    /**
     * 도메인 컨텍스트에서 값 조회
     */
    fun getDomainValue(key: String): String? = domainContext[key]

    /**
     * 기존 데이터에서 값 조회
     */
    fun getExistingValue(key: String): String? = existingData[key]

    /**
     * 마지막 사용자 메시지 조회
     */
    val lastUserMessage: String?
        get() = conversationHistory
            .lastOrNull { it.role == "user" }
            ?.content

    /**
     * 대화 턴 수
     */
    val turnCount: Int
        get() = conversationHistory.size

    /**
     * 컨텍스트 병합 (새 값이 우선)
     */
    fun merge(
        newExistingData: Map<String, String> = emptyMap(),
        newDomainContext: Map<String, String> = emptyMap()
    ) = copy(
        existingData = existingData + newExistingData,
        domainContext = domainContext + newDomainContext
    )
}
