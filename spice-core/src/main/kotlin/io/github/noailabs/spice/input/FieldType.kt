package io.github.noailabs.spice.input

import kotlinx.serialization.Serializable

/**
 * Spice 기본 필드 타입 - 도메인 무관, 형식(Form)만 정의
 *
 * 도메인별 확장(GUEST_COUNT, BOOKING_ID 등)은 각 애플리케이션에서 별도 타입으로 정의.
 * Spice는 최소한의 기본 타입만 제공하여 도메인 중립성을 유지.
 *
 * ## Usage
 * ```kotlin
 * val spec = InputFieldSpec(
 *     fieldName = "guestCount",
 *     fieldType = FieldType.NUMBER,
 *     required = true
 * )
 * ```
 *
 * @since 1.7.0
 */
@Serializable
enum class FieldType {
    /**
     * 자유 텍스트 입력
     * - 길이 제약만 가능 (minLength, maxLength)
     * - 정규식 패턴 매칭 가능 (constraints.pattern)
     */
    TEXT,

    /**
     * 숫자 (정수/실수)
     * - minValue, maxValue 제약 가능
     * - 정수 강제는 constraints.pattern으로 처리
     */
    NUMBER,

    /**
     * Boolean (yes/no, true/false)
     * - 자연어 긍정/부정 표현 자동 변환
     * - canonical: "true" / "false"
     */
    BOOLEAN,

    /**
     * 날짜 (LocalDate)
     * - ISO format: YYYY-MM-DD
     * - 상대 날짜 지원 (내일, 다음주 등)
     */
    DATE,

    /**
     * 날짜+시간 (LocalDateTime)
     * - ISO format: YYYY-MM-DDTHH:MM:SS
     */
    DATETIME,

    /**
     * 단일 선택 (enumOptions 중 하나)
     * - canonical: 선택된 option의 id
     * - allowsFreeText=true 시 자유 입력 허용
     */
    ENUM,

    /**
     * 다중 선택 (enumOptions 중 여러 개)
     * - canonical: 쉼표로 구분된 option id들
     * - 예: "opt1,opt2,opt3"
     */
    MULTI_ENUM
}
