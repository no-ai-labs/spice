package io.github.noailabs.spice.routing.gateway

import kotlinx.serialization.Serializable

/**
 * Off-Domain 처리 유형
 *
 * GatewayAgent가 Off-Domain 판정 시 세부 유형을 구분.
 * 각 유형에 따라 다른 처리 전략이 적용됨.
 *
 * ## 처리 전략
 *
 * | Type | 처리 | API 호출 |
 * |------|------|----------|
 * | INTENT_UNMAPPED | 상담사 이관 | ✅ Escalation API |
 * | CLASSIFICATION_FAILED | 안내 메시지 | ❌ |
 * | SEMANTIC_OUT_OF_SCOPE | 안내 메시지 | ❌ |
 *
 * @since 1.5.0
 */
@Serializable
enum class OffDomainType {
    /**
     * Intent 분류 성공, 워크플로우 매핑 없음
     *
     * 예: "예약 변경" intent 분류됨, 하지만 변경 워크플로우 미구현
     * 처리: 상담사 이관 (Escalation API 호출)
     */
    INTENT_UNMAPPED,

    /**
     * Intent 분류 실패
     *
     * 예: 분류기가 신뢰할 수 있는 intent를 찾지 못함
     * 처리: 안내 메시지 표시
     */
    CLASSIFICATION_FAILED,

    /**
     * 의미론적으로 서비스 범위 외
     *
     * 예: "오늘 날씨 어때?" - 도메인과 전혀 무관
     * 처리: 안내 메시지 표시
     */
    SEMANTIC_OUT_OF_SCOPE;

    /**
     * 상담사 이관이 필요한지 여부
     */
    val requiresEscalation: Boolean
        get() = this == INTENT_UNMAPPED

    /**
     * 사용자에게 보여줄 기본 메시지
     */
    val defaultMessage: String
        get() = when (this) {
            INTENT_UNMAPPED ->
                "문의하신 내용은 현재 자동 처리가 어려워 상담사에게 전달해드렸습니다.\n" +
                "잠시만 기다려주시면 담당자가 확인 후 안내드리겠습니다."
            CLASSIFICATION_FAILED,
            SEMANTIC_OUT_OF_SCOPE ->
                "문의주신 내용은 안내드리기 어려운 내용입니다.\n" +
                "예약 취소, 위약금 조회 등 예약 관련 문의만 답변드릴 수 있습니다."
        }
}
