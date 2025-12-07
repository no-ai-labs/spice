package io.github.noailabs.spice.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 입력 필드 명세 - 도메인 무관 최소 스펙
 *
 * HITL 노드, 폼 입력, API 자연어 인자 등에서 공통으로 사용.
 * 도메인별 확장 타입은 애플리케이션에서 별도 정의.
 *
 * ## YAML 예시
 * ```yaml
 * field:
 *   name: selectedReservation
 *   type: ENUM
 *   required: true
 *   allowsFreeText: true
 * ```
 *
 * @property fieldName 필드 이름 (결과 데이터의 키)
 * @property fieldType 필드 타입 (7개 기본 타입)
 * @property required 필수 여부 (기본 true)
 * @property enumOptions ENUM/MULTI_ENUM 타입의 선택지
 * @property allowsFreeText ENUM 타입에서 자유 입력 허용 여부
 * @property constraints 값 제약 조건
 * @property metadata 확장 메타데이터 (도메인 타입 정보 등)
 *
 * @since 1.7.0
 */
@Serializable
data class InputFieldSpec(
    val fieldName: String,
    val fieldType: FieldType,
    val required: Boolean = true,
    val enumOptions: List<EnumOption> = emptyList(),
    val allowsFreeText: Boolean = false,
    val constraints: FieldConstraints = FieldConstraints(),
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun fromJson(jsonString: String): InputFieldSpec = json.decodeFromString(jsonString)
    }

    fun toJson(): String = json.encodeToString(this)

    /**
     * ENUM/MULTI_ENUM 타입인지 확인
     */
    val isEnumType: Boolean
        get() = fieldType == FieldType.ENUM || fieldType == FieldType.MULTI_ENUM

    /**
     * enumOptions에서 id로 옵션 찾기
     */
    fun findOptionById(id: String): EnumOption? = enumOptions.find { it.id == id }

    /**
     * enumOptions에서 label 또는 alias로 옵션 찾기
     */
    fun findOptionByLabelOrAlias(text: String): EnumOption? {
        val normalized = text.trim().lowercase()
        return enumOptions.find { opt ->
            opt.label.lowercase() == normalized ||
                    opt.aliases.any { it.lowercase() == normalized }
        }
    }
}

/**
 * ENUM/MULTI_ENUM 선택지 정의
 *
 * @property id canonical 값 (내부 식별자)
 * @property label 사용자에게 표시되는 텍스트
 * @property aliases 동의어 목록 (시멘틱 매칭에 사용)
 * @property description 상세 설명 (선택적)
 */
@Serializable
data class EnumOption(
    val id: String,
    val label: String,
    val aliases: List<String> = emptyList(),
    val description: String? = null
) {
    /**
     * label과 aliases를 합친 모든 텍스트 (임베딩 매칭용)
     */
    val allTexts: List<String>
        get() = listOf(label) + aliases
}

/**
 * 필드 값 제약 조건
 *
 * 타입에 따라 적용 가능한 제약이 다름:
 * - TEXT: minLength, maxLength, pattern
 * - NUMBER: minValue, maxValue
 * - DATE/DATETIME: dateMin, dateMax
 *
 * @property minLength 최소 문자 길이 (TEXT)
 * @property maxLength 최대 문자 길이 (TEXT)
 * @property minValue 최소값 (NUMBER)
 * @property maxValue 최대값 (NUMBER)
 * @property pattern 정규식 패턴 (TEXT)
 * @property dateMin 최소 날짜 (DATE/DATETIME, ISO format)
 * @property dateMax 최대 날짜 (DATE/DATETIME, ISO format)
 */
@Serializable
data class FieldConstraints(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val pattern: String? = null,
    val dateMin: String? = null,
    val dateMax: String? = null
) {
    companion object {
        val EMPTY = FieldConstraints()
    }

    /**
     * 제약 조건이 있는지 확인
     */
    val hasConstraints: Boolean
        get() = minLength != null || maxLength != null ||
                minValue != null || maxValue != null ||
                pattern != null || dateMin != null || dateMax != null
}
