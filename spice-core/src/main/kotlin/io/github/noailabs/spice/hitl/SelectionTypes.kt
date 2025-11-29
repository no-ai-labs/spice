package io.github.noailabs.spice.hitl

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 확장 HITL 선택 타입
 *
 * 기존 HITLTypes를 확장하여 Multi-Select + Quantity를 지원합니다.
 * 옵션 변경 시나리오 (예약 옵션 추가/변경 등)에서 사용됩니다.
 *
 * ## 사용 시나리오
 * 1. 단순 단일 선택: "어떤 예약을 선택하시겠어요?" → 기존 HITLResponse 사용
 * 2. 단순 다중 선택: "어떤 옵션을 추가하시겠어요?" → 기존 HITLResponse.multiSelection 사용
 * 3. 수량 포함 선택: "조식 몇 개를 추가하시겠어요?" → QuantitySelectionResponse 사용
 *
 * @since 1.1.0
 */

/**
 * 수량을 포함한 선택 항목
 *
 * @property optionId 선택된 옵션 ID
 * @property quantity 수량 (기본: 1)
 * @property metadata 추가 메타데이터
 */
@Serializable
data class QuantitySelection(
    val optionId: String,
    val quantity: Int = 1,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    init {
        require(quantity >= 0) { "Quantity must be non-negative" }
    }

    /**
     * 수량이 양수인지 여부
     */
    val isPositive: Boolean
        get() = quantity > 0

    /**
     * 수량이 0인지 여부 (삭제/취소 의미)
     */
    val isZero: Boolean
        get() = quantity == 0

    companion object {
        /**
         * 단일 항목 생성
         */
        fun single(optionId: String): QuantitySelection =
            QuantitySelection(optionId = optionId, quantity = 1)

        /**
         * 수량 지정 항목 생성
         */
        fun withQuantity(optionId: String, quantity: Int): QuantitySelection =
            QuantitySelection(optionId = optionId, quantity = quantity)

        /**
         * 삭제 항목 생성 (수량 0)
         */
        fun remove(optionId: String): QuantitySelection =
            QuantitySelection(optionId = optionId, quantity = 0)
    }
}

/**
 * 수량 포함 선택 응답
 *
 * Multi-Select + Quantity를 지원하는 HITL 응답 타입.
 * 기존 HITLResponse와 호환되면서 수량 정보를 추가로 제공합니다.
 *
 * @property toolCallId 대응하는 HITL 요청 ID
 * @property selections 수량을 포함한 선택 항목들
 * @property responseType 응답 타입
 * @property timestamp 응답 시간 (epoch millis)
 * @property metadata 추가 메타데이터
 */
@Serializable
data class QuantitySelectionResponse(
    val toolCallId: String,
    val selections: List<QuantitySelection>,
    val responseType: String = RESPONSE_TYPE_QUANTITY_SELECTION,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 선택된 옵션 ID 목록
     */
    val selectedOptionIds: List<String>
        get() = selections.map { it.optionId }

    /**
     * 수량 맵 (optionId → quantity)
     */
    val quantityMap: Map<String, Int>
        get() = selections.associate { it.optionId to it.quantity }

    /**
     * 총 수량
     */
    val totalQuantity: Int
        get() = selections.sumOf { it.quantity }

    /**
     * 선택 항목 개수
     */
    val selectionCount: Int
        get() = selections.size

    /**
     * 양수 수량의 선택만 필터링
     */
    val positiveSelections: List<QuantitySelection>
        get() = selections.filter { it.isPositive }

    /**
     * 삭제(수량 0) 선택만 필터링
     */
    val removalSelections: List<QuantitySelection>
        get() = selections.filter { it.isZero }

    /**
     * 기존 HITLResponse로 변환 (호환성용)
     */
    fun toHITLResponse(): HITLResponse = HITLResponse(
        toolCallId = toolCallId,
        value = selections.joinToString(",") { "${it.optionId}:${it.quantity}" },
        selectedOptions = selectedOptionIds,
        responseType = responseType,
        timestamp = timestamp,
        metadata = metadata + mapOf("quantities" to quantityMap)
    )

    companion object {
        const val RESPONSE_TYPE_QUANTITY_SELECTION = "quantity_selection"

        /**
         * 단일 항목 응답 생성
         */
        fun single(
            toolCallId: String,
            optionId: String,
            quantity: Int = 1,
            metadata: Map<String, Any> = emptyMap()
        ): QuantitySelectionResponse = QuantitySelectionResponse(
            toolCallId = toolCallId,
            selections = listOf(QuantitySelection(optionId, quantity)),
            metadata = metadata
        )

        /**
         * 다중 항목 응답 생성 (동일 수량)
         */
        fun multiple(
            toolCallId: String,
            optionIds: List<String>,
            quantity: Int = 1,
            metadata: Map<String, Any> = emptyMap()
        ): QuantitySelectionResponse = QuantitySelectionResponse(
            toolCallId = toolCallId,
            selections = optionIds.map { QuantitySelection(it, quantity) },
            metadata = metadata
        )

        /**
         * 다중 항목 응답 생성 (개별 수량)
         */
        fun withQuantities(
            toolCallId: String,
            selections: List<QuantitySelection>,
            metadata: Map<String, Any> = emptyMap()
        ): QuantitySelectionResponse = QuantitySelectionResponse(
            toolCallId = toolCallId,
            selections = selections,
            metadata = metadata
        )

        /**
         * Map에서 응답 생성
         */
        fun fromMap(
            toolCallId: String,
            quantityMap: Map<String, Int>,
            metadata: Map<String, Any> = emptyMap()
        ): QuantitySelectionResponse = QuantitySelectionResponse(
            toolCallId = toolCallId,
            selections = quantityMap.map { (id, qty) -> QuantitySelection(id, qty) },
            metadata = metadata
        )

        /**
         * HITLResponse에서 변환 (역호환성)
         *
         * metadata의 "quantities" 키에서 수량 정보를 추출합니다.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromHITLResponse(response: HITLResponse): QuantitySelectionResponse {
            val quantities = response.metadata["quantities"] as? Map<String, Int>

            val selections = response.selectedOptions.map { optionId ->
                QuantitySelection(
                    optionId = optionId,
                    quantity = quantities?.get(optionId) ?: 1
                )
            }

            return QuantitySelectionResponse(
                toolCallId = response.toolCallId,
                selections = selections,
                timestamp = response.timestamp,
                metadata = response.metadata
            )
        }
    }
}

/**
 * 수량 선택 가능 옵션
 *
 * 수량 제약 정보를 포함한 확장 HITLOption.
 *
 * @property id 옵션 ID
 * @property label 표시 라벨
 * @property description 설명
 * @property minQuantity 최소 수량 (기본: 0)
 * @property maxQuantity 최대 수량 (null이면 제한 없음)
 * @property defaultQuantity 기본 수량 (기본: 1)
 * @property unitLabel 수량 단위 라벨 (예: "개", "명", "박")
 * @property unitPrice 단가 (선택)
 * @property metadata 추가 메타데이터
 */
@Serializable
data class QuantityOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val minQuantity: Int = 0,
    val maxQuantity: Int? = null,
    val defaultQuantity: Int = 1,
    val unitLabel: String = "개",
    val unitPrice: Double? = null,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 주어진 수량이 유효한지 확인
     */
    fun isValidQuantity(quantity: Int): Boolean {
        if (quantity < minQuantity) return false
        if (maxQuantity != null && quantity > maxQuantity) return false
        return true
    }

    /**
     * 수량 제약 범위 문자열
     */
    val quantityRange: String
        get() = when {
            maxQuantity == null -> "$minQuantity~"
            minQuantity == maxQuantity -> "$minQuantity"
            else -> "$minQuantity~$maxQuantity"
        }

    /**
     * 기본 HITLOption으로 변환 (호환성용)
     */
    fun toHITLOption(): HITLOption = HITLOption(
        id = id,
        label = label,
        description = description,
        metadata = metadata + mapOf(
            "minQuantity" to minQuantity,
            "maxQuantity" to (maxQuantity ?: -1),
            "defaultQuantity" to defaultQuantity,
            "unitLabel" to unitLabel
        ) + (unitPrice?.let { mapOf("unitPrice" to it) } ?: emptyMap())
    )

    companion object {
        /**
         * 단순 옵션 생성 (수량 1 고정)
         */
        fun simple(id: String, label: String, description: String? = null): QuantityOption =
            QuantityOption(
                id = id,
                label = label,
                description = description,
                minQuantity = 1,
                maxQuantity = 1,
                defaultQuantity = 1
            )

        /**
         * 범위 지정 옵션 생성
         */
        fun withRange(
            id: String,
            label: String,
            minQuantity: Int,
            maxQuantity: Int,
            description: String? = null,
            unitLabel: String = "개"
        ): QuantityOption = QuantityOption(
            id = id,
            label = label,
            description = description,
            minQuantity = minQuantity,
            maxQuantity = maxQuantity,
            defaultQuantity = minQuantity.coerceAtLeast(1),
            unitLabel = unitLabel
        )

        /**
         * 무제한 수량 옵션 생성
         */
        fun unlimited(
            id: String,
            label: String,
            description: String? = null,
            unitLabel: String = "개"
        ): QuantityOption = QuantityOption(
            id = id,
            label = label,
            description = description,
            minQuantity = 0,
            maxQuantity = null,
            unitLabel = unitLabel
        )

        /**
         * HITLOption에서 변환
         */
        fun fromHITLOption(option: HITLOption): QuantityOption = QuantityOption(
            id = option.id,
            label = option.label,
            description = option.description,
            minQuantity = (option.metadata["minQuantity"] as? Number)?.toInt() ?: 0,
            maxQuantity = (option.metadata["maxQuantity"] as? Number)?.toInt()?.let { if (it < 0) null else it },
            defaultQuantity = (option.metadata["defaultQuantity"] as? Number)?.toInt() ?: 1,
            unitLabel = option.metadata["unitLabel"] as? String ?: "개",
            unitPrice = (option.metadata["unitPrice"] as? Number)?.toDouble(),
            metadata = option.metadata
        )
    }
}

/**
 * 수량 선택 HITL 메타데이터
 *
 * 수량 선택을 위한 확장 HITL 메타데이터.
 *
 * @property toolCallId HITL 요청 ID
 * @property prompt 프롬프트 메시지
 * @property runId 그래프 실행 ID
 * @property nodeId 노드 ID
 * @property graphId 그래프 ID
 * @property options 수량 선택 가능 옵션들
 * @property selectionMode 선택 모드
 * @property minSelections 최소 선택 개수
 * @property maxSelections 최대 선택 개수
 * @property timeout 타임아웃 (밀리초)
 * @property additionalMetadata 추가 메타데이터
 */
@Serializable
data class QuantitySelectionMetadata(
    val toolCallId: String,
    val prompt: String,
    val runId: String,
    val nodeId: String,
    val graphId: String? = null,
    val options: List<QuantityOption>,
    val selectionMode: SelectionMode = SelectionMode.SINGLE,
    val minSelections: Int = 1,
    val maxSelections: Int? = null,
    val timeout: Long? = null,
    val additionalMetadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 기존 HITLMetadata로 변환 (호환성용)
     */
    fun toHITLMetadata(): HITLMetadata = HITLMetadata(
        toolCallId = toolCallId,
        hitlType = HITLMetadata.TYPE_SELECTION,
        prompt = prompt,
        runId = runId,
        nodeId = nodeId,
        graphId = graphId,
        options = options.map { it.toHITLOption() },
        timeout = timeout,
        additionalMetadata = additionalMetadata + mapOf(
            "selectionMode" to selectionMode.name,
            "minSelections" to minSelections,
            "maxSelections" to (maxSelections ?: -1),
            "hasQuantity" to true
        )
    )

    companion object {
        /**
         * 단일 선택 메타데이터 생성
         */
        fun forSingleSelection(
            runId: String,
            nodeId: String,
            prompt: String,
            options: List<QuantityOption>,
            graphId: String? = null,
            timeout: Long? = null
        ): QuantitySelectionMetadata = QuantitySelectionMetadata(
            toolCallId = HITLMetadata.generateToolCallId(runId, nodeId),
            prompt = prompt,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            options = options,
            selectionMode = SelectionMode.SINGLE,
            minSelections = 1,
            maxSelections = 1,
            timeout = timeout
        )

        /**
         * 다중 선택 메타데이터 생성
         */
        fun forMultiSelection(
            runId: String,
            nodeId: String,
            prompt: String,
            options: List<QuantityOption>,
            graphId: String? = null,
            minSelections: Int = 0,
            maxSelections: Int? = null,
            timeout: Long? = null
        ): QuantitySelectionMetadata = QuantitySelectionMetadata(
            toolCallId = HITLMetadata.generateToolCallId(runId, nodeId),
            prompt = prompt,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            options = options,
            selectionMode = SelectionMode.MULTIPLE,
            minSelections = minSelections,
            maxSelections = maxSelections,
            timeout = timeout
        )

        /**
         * 수량 지정 선택 메타데이터 생성
         */
        fun forQuantitySelection(
            runId: String,
            nodeId: String,
            prompt: String,
            options: List<QuantityOption>,
            graphId: String? = null,
            minSelections: Int = 1,
            timeout: Long? = null
        ): QuantitySelectionMetadata = QuantitySelectionMetadata(
            toolCallId = HITLMetadata.generateToolCallId(runId, nodeId),
            prompt = prompt,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            options = options,
            selectionMode = SelectionMode.QUANTITY,
            minSelections = minSelections,
            timeout = timeout
        )
    }
}

/**
 * 선택 모드
 */
@Serializable
enum class SelectionMode {
    /** 단일 선택 (라디오 버튼) */
    SINGLE,

    /** 다중 선택 (체크박스) */
    MULTIPLE,

    /** 수량 지정 선택 (스테퍼/입력) */
    QUANTITY
}

/**
 * 선택 검증 결과
 *
 * @property valid 유효 여부
 * @property errors 검증 오류 목록
 */
@Serializable
data class SelectionValidationResult(
    val valid: Boolean,
    val errors: List<SelectionValidationError> = emptyList()
) {
    companion object {
        /**
         * 유효한 결과
         */
        fun valid(): SelectionValidationResult =
            SelectionValidationResult(valid = true)

        /**
         * 오류 결과
         */
        fun invalid(vararg errors: SelectionValidationError): SelectionValidationResult =
            SelectionValidationResult(valid = false, errors = errors.toList())

        /**
         * 오류 목록으로 결과 생성
         */
        fun fromErrors(errors: List<SelectionValidationError>): SelectionValidationResult =
            if (errors.isEmpty()) valid()
            else SelectionValidationResult(valid = false, errors = errors)
    }
}

/**
 * 선택 검증 오류
 *
 * @property code 오류 코드
 * @property message 오류 메시지
 * @property optionId 관련 옵션 ID (선택)
 * @property details 추가 세부 정보
 */
@Serializable
data class SelectionValidationError(
    val code: String,
    val message: String,
    val optionId: String? = null,
    val details: Map<String, @Contextual Any> = emptyMap()
) {
    companion object {
        // 오류 코드 상수
        const val CODE_MIN_SELECTIONS = "MIN_SELECTIONS"
        const val CODE_MAX_SELECTIONS = "MAX_SELECTIONS"
        const val CODE_MIN_QUANTITY = "MIN_QUANTITY"
        const val CODE_MAX_QUANTITY = "MAX_QUANTITY"
        const val CODE_INVALID_OPTION = "INVALID_OPTION"
        const val CODE_DUPLICATE_OPTION = "DUPLICATE_OPTION"

        fun minSelections(required: Int, actual: Int): SelectionValidationError =
            SelectionValidationError(
                code = CODE_MIN_SELECTIONS,
                message = "최소 ${required}개 이상 선택해야 합니다 (현재: ${actual}개)",
                details = mapOf("required" to required, "actual" to actual)
            )

        fun maxSelections(max: Int, actual: Int): SelectionValidationError =
            SelectionValidationError(
                code = CODE_MAX_SELECTIONS,
                message = "최대 ${max}개까지 선택 가능합니다 (현재: ${actual}개)",
                details = mapOf("max" to max, "actual" to actual)
            )

        fun minQuantity(optionId: String, min: Int, actual: Int): SelectionValidationError =
            SelectionValidationError(
                code = CODE_MIN_QUANTITY,
                message = "최소 수량은 ${min}개입니다 (현재: ${actual}개)",
                optionId = optionId,
                details = mapOf("min" to min, "actual" to actual)
            )

        fun maxQuantity(optionId: String, max: Int, actual: Int): SelectionValidationError =
            SelectionValidationError(
                code = CODE_MAX_QUANTITY,
                message = "최대 수량은 ${max}개입니다 (현재: ${actual}개)",
                optionId = optionId,
                details = mapOf("max" to max, "actual" to actual)
            )

        fun invalidOption(optionId: String): SelectionValidationError =
            SelectionValidationError(
                code = CODE_INVALID_OPTION,
                message = "유효하지 않은 옵션입니다: $optionId",
                optionId = optionId
            )

        fun duplicateOption(optionId: String): SelectionValidationError =
            SelectionValidationError(
                code = CODE_DUPLICATE_OPTION,
                message = "중복된 옵션입니다: $optionId",
                optionId = optionId
            )
    }
}

/**
 * 선택 응답 검증기
 */
object SelectionValidator {

    /**
     * 수량 선택 응답 검증
     *
     * @param response 검증할 응답
     * @param metadata 선택 메타데이터
     * @return 검증 결과
     */
    fun validate(
        response: QuantitySelectionResponse,
        metadata: QuantitySelectionMetadata
    ): SelectionValidationResult {
        val errors = mutableListOf<SelectionValidationError>()
        val validOptionIds = metadata.options.map { it.id }.toSet()

        // 1. 선택 개수 검증
        val selectionCount = response.positiveSelections.size

        if (selectionCount < metadata.minSelections) {
            errors.add(SelectionValidationError.minSelections(metadata.minSelections, selectionCount))
        }

        if (metadata.maxSelections != null && selectionCount > metadata.maxSelections) {
            errors.add(SelectionValidationError.maxSelections(metadata.maxSelections, selectionCount))
        }

        // 2. 옵션 유효성 검증
        val seenOptionIds = mutableSetOf<String>()

        response.selections.forEach { selection ->
            // 중복 검사
            if (selection.optionId in seenOptionIds) {
                errors.add(SelectionValidationError.duplicateOption(selection.optionId))
            }
            seenOptionIds.add(selection.optionId)

            // 유효 옵션 검사
            if (selection.optionId !in validOptionIds) {
                errors.add(SelectionValidationError.invalidOption(selection.optionId))
            } else {
                // 수량 범위 검사
                val option = metadata.options.find { it.id == selection.optionId }
                if (option != null && !option.isValidQuantity(selection.quantity)) {
                    if (selection.quantity < option.minQuantity) {
                        errors.add(SelectionValidationError.minQuantity(
                            selection.optionId, option.minQuantity, selection.quantity
                        ))
                    }
                    if (option.maxQuantity != null && selection.quantity > option.maxQuantity) {
                        errors.add(SelectionValidationError.maxQuantity(
                            selection.optionId, option.maxQuantity, selection.quantity
                        ))
                    }
                }
            }
        }

        return SelectionValidationResult.fromErrors(errors)
    }

    /**
     * 단순 선택 검증 (기존 HITLResponse용)
     */
    fun validate(
        response: HITLResponse,
        metadata: HITLMetadata
    ): SelectionValidationResult {
        if (!response.isSelectionResponse) {
            return SelectionValidationResult.valid()
        }

        val errors = mutableListOf<SelectionValidationError>()
        val validOptionIds = metadata.options.map { it.id }.toSet()

        response.selectedOptions.forEach { optionId ->
            if (optionId !in validOptionIds) {
                errors.add(SelectionValidationError.invalidOption(optionId))
            }
        }

        return SelectionValidationResult.fromErrors(errors)
    }
}
