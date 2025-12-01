package io.github.noailabs.spice.hitl.template

import kotlinx.serialization.Serializable

/**
 * HITL Template Kind
 *
 * Defines the type of HITL interaction for proper UI rendering and validation.
 *
 * | Kind           | UI Rendering       | Response Type              |
 * |----------------|--------------------|-----------------------------|
 * | TEXT           | Text input field   | HitlResult.text()          |
 * | SINGLE_SELECT  | Radio buttons      | HitlResult.single()        |
 * | MULTI_SELECT   | Checkboxes         | HitlResult.multi()         |
 * | QUANTITY       | Stepper/counter    | HitlResult.quantity()      |
 * | MULTI_QUANTITY | Multiple steppers  | HitlResult.multiQuantity() |
 * | CONFIRM        | Yes/No buttons     | HitlResult.single()        |
 * | INFO           | Display only       | HitlResult.text("ack")     |
 * | ESCALATE       | Transfer to human  | N/A (workflow ends)        |
 *
 * @since Spice 1.3.5
 */
@Serializable
enum class HitlTemplateKind {
    /** Free-form text input */
    TEXT,

    /** Single selection from options (radio button style) */
    SINGLE_SELECT,

    /** Multiple selections from options (checkbox style) */
    MULTI_SELECT,

    /** Quantity selection for single item (stepper/counter) */
    QUANTITY,

    /** Quantity selection for multiple items */
    MULTI_QUANTITY,

    /** Confirmation request (Yes/No style) */
    CONFIRM,

    /** Information display only (acknowledgment expected) */
    INFO,

    /** Escalation to human agent */
    ESCALATE
}

/**
 * HITL Option
 *
 * Represents a selectable option in selection-type HITL templates.
 *
 * @property id Unique identifier for this option (used in routing)
 * @property label Display label for the option
 * @property description Optional description text
 * @property icon Optional icon identifier
 * @property metadata Additional metadata for this option
 *
 * @since Spice 1.3.5
 */
@Serializable
data class HitlOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val icon: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "HitlOption id cannot be blank" }
        require(label.isNotBlank()) { "HitlOption label cannot be blank" }
    }

    companion object {
        /**
         * Create a simple option with just id and label
         */
        fun simple(id: String, label: String) = HitlOption(id = id, label = label)

        /**
         * Create a Yes option for confirmations
         */
        fun yes(id: String = "yes", label: String = "Yes") = HitlOption(id = id, label = label)

        /**
         * Create a No option for confirmations
         */
        fun no(id: String = "no", label: String = "No") = HitlOption(id = id, label = label)

        /**
         * Create standard Yes/No options for confirmations
         */
        fun yesNo(
            yesId: String = "yes",
            yesLabel: String = "Yes",
            noId: String = "no",
            noLabel: String = "No"
        ): List<HitlOption> = listOf(yes(yesId, yesLabel), no(noId, noLabel))
    }
}

/**
 * HITL Template Flags
 *
 * Configuration flags for HITL template behavior.
 *
 * @property required Whether user input is required (default: true)
 * @property autoProceed Whether to auto-proceed without confirmation (default: false)
 * @property allowSkip Whether to allow skipping this HITL (default: false)
 * @property allowFreeText Whether to allow free text input for selection types (default: false)
 * @property timeout Timeout in milliseconds (null for no timeout)
 * @property retryCount Maximum retry count for invalid input (default: 3)
 *
 * @since Spice 1.3.5
 * @since Spice 1.5.5 Added allowFreeText
 */
@Serializable
data class HitlTemplateFlags(
    val required: Boolean = true,
    val autoProceed: Boolean = false,
    val allowSkip: Boolean = false,
    val allowFreeText: Boolean = false,
    val timeout: Long? = null,
    val retryCount: Int = 3
) {
    companion object {
        val DEFAULT = HitlTemplateFlags()

        val OPTIONAL = HitlTemplateFlags(required = false, allowSkip = true)

        val AUTO_PROCEED = HitlTemplateFlags(autoProceed = true)

        /**
         * Create flags with a specific timeout
         */
        fun withTimeout(timeoutMs: Long) = HitlTemplateFlags(timeout = timeoutMs)

        /**
         * Create flags allowing free text input for selection types
         * @since 1.5.5
         */
        fun withFreeText() = HitlTemplateFlags(allowFreeText = true)
    }
}

/**
 * Quantity configuration for QUANTITY and MULTI_QUANTITY templates
 *
 * @property min Minimum allowed quantity
 * @property max Maximum allowed quantity
 * @property defaultValue Default quantity value
 * @property step Step increment for the stepper
 *
 * @since Spice 1.3.5
 */
@Serializable
data class QuantityConfig(
    val min: Int = 0,
    val max: Int = 100,
    val defaultValue: Int = 1,
    val step: Int = 1
) {
    init {
        require(min >= 0) { "min must be non-negative" }
        require(max >= min) { "max must be >= min" }
        require(step > 0) { "step must be positive" }
        require(defaultValue in min..max) { "defaultValue must be in range [min, max]" }
    }

    companion object {
        val DEFAULT = QuantityConfig()

        fun range(min: Int, max: Int, default: Int = min) =
            QuantityConfig(min = min, max = max, defaultValue = default)
    }
}

/**
 * Validation rule for HITL input
 *
 * @property type Validation type (e.g., "regex", "min_length", "max_length", "email")
 * @property value Validation value (e.g., regex pattern, length value)
 * @property message Error message for validation failure
 *
 * @since Spice 1.3.5
 */
@Serializable
data class ValidationRule(
    val type: String,
    val value: String,
    val message: String? = null
) {
    companion object {
        fun regex(pattern: String, message: String? = null) =
            ValidationRule("regex", pattern, message)

        fun minLength(length: Int, message: String? = null) =
            ValidationRule("min_length", length.toString(), message)

        fun maxLength(length: Int, message: String? = null) =
            ValidationRule("max_length", length.toString(), message)

        fun email(message: String? = null) =
            ValidationRule("email", "", message ?: "Invalid email format")
    }
}
