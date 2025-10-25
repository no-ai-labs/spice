package io.github.noailabs.spice.validation

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.ToolResult

/**
 * 🎯 Output Validation DSL
 *
 * Validates tool outputs to enforce:
 * - Required fields (e.g., citations in Evidence JSON)
 * - Field types and structures
 * - Custom validation rules
 * - Value ranges and patterns
 *
 * Example:
 * ```kotlin
 * contextAwareTool("generate_evidence") {
 *     validate {
 *         requireField("citations", "Evidence must include citations")
 *         requireField("summary")
 *
 *         fieldType("citations", FieldType.ARRAY)
 *         fieldType("confidence", FieldType.NUMBER)
 *
 *         rule("citations must not be empty") { output ->
 *             val citations = output["citations"] as? List<*>
 *             citations != null && citations.isNotEmpty()
 *         }
 *
 *         range("confidence", 0.0, 1.0)
 *     }
 *
 *     execute { params, context ->
 *         mapOf(
 *             "citations" to listOf("source1", "source2"),
 *             "summary" to "Evidence summary",
 *             "confidence" to 0.95
 *         )
 *     }
 * }
 * ```
 *
 * @since 0.4.1
 */

/**
 * Field type enum for validation
 */
enum class FieldType {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT,
    ANY
}

/**
 * Validation rule
 */
sealed class ValidationRule {
    abstract fun validate(output: Any, context: AgentContext?): ValidationResult

    data class RequiredField(
        val field: String,
        val message: String = "Required field '$field' is missing"
    ) : ValidationRule() {
        override fun validate(output: Any, context: AgentContext?): ValidationResult {
            return when (output) {
                is Map<*, *> -> {
                    if (output.containsKey(field) && output[field] != null) {
                        ValidationResult.Valid
                    } else {
                        ValidationResult.Invalid(message)
                    }
                }
                else -> ValidationResult.Invalid("Output must be a Map to check required fields")
            }
        }
    }

    data class FieldTypeValidation(
        val field: String,
        val expectedType: FieldType,
        val message: String? = null
    ) : ValidationRule() {
        override fun validate(output: Any, context: AgentContext?): ValidationResult {
            return when (output) {
                is Map<*, *> -> {
                    val value = output[field]
                    if (value == null) {
                        return ValidationResult.Valid  // Type check only applies if field exists
                    }

                    val isValid = when (expectedType) {
                        FieldType.STRING -> value is String
                        FieldType.NUMBER -> value is Number
                        FieldType.INTEGER -> value is Int || value is Long
                        FieldType.BOOLEAN -> value is Boolean
                        FieldType.ARRAY -> value is List<*> || value is Array<*>
                        FieldType.OBJECT -> value is Map<*, *>
                        FieldType.ANY -> true
                    }

                    if (isValid) {
                        ValidationResult.Valid
                    } else {
                        ValidationResult.Invalid(
                            message ?: "Field '$field' must be of type ${expectedType.name.lowercase()}"
                        )
                    }
                }
                else -> ValidationResult.Invalid("Output must be a Map to check field types")
            }
        }
    }

    data class RangeValidation(
        val field: String,
        val min: Double,
        val max: Double,
        val message: String? = null
    ) : ValidationRule() {
        override fun validate(output: Any, context: AgentContext?): ValidationResult {
            return when (output) {
                is Map<*, *> -> {
                    val value = output[field] as? Number
                    if (value == null) {
                        return ValidationResult.Valid  // Range check only applies if field exists
                    }

                    val doubleValue = value.toDouble()
                    if (doubleValue in min..max) {
                        ValidationResult.Valid
                    } else {
                        ValidationResult.Invalid(
                            message ?: "Field '$field' must be between $min and $max (got $doubleValue)"
                        )
                    }
                }
                else -> ValidationResult.Invalid("Output must be a Map to check field ranges")
            }
        }
    }

    data class CustomRule(
        val description: String,
        val validator: (Any, AgentContext?) -> Boolean
    ) : ValidationRule() {
        override fun validate(output: Any, context: AgentContext?): ValidationResult {
            return if (validator(output, context)) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("Validation failed: $description")
            }
        }
    }

    data class PatternValidation(
        val field: String,
        val pattern: Regex,
        val message: String? = null
    ) : ValidationRule() {
        override fun validate(output: Any, context: AgentContext?): ValidationResult {
            return when (output) {
                is Map<*, *> -> {
                    val value = output[field] as? String
                    if (value == null) {
                        return ValidationResult.Valid  // Pattern check only applies if field exists
                    }

                    if (pattern.matches(value)) {
                        ValidationResult.Valid
                    } else {
                        ValidationResult.Invalid(
                            message ?: "Field '$field' does not match required pattern"
                        )
                    }
                }
                else -> ValidationResult.Invalid("Output must be a Map to check field patterns")
            }
        }
    }
}

/**
 * Validation result
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
    val error: String? get() = (this as? Invalid)?.message
}

/**
 * Output validator configuration block
 */
class OutputValidatorBuilder {
    private val rules = mutableListOf<ValidationRule>()

    /**
     * Require a field to be present in output
     */
    fun requireField(field: String, message: String? = null) {
        rules.add(
            ValidationRule.RequiredField(
                field,
                message ?: "Required field '$field' is missing"
            )
        )
    }

    /**
     * Validate field type
     */
    fun fieldType(field: String, type: FieldType, message: String? = null) {
        rules.add(ValidationRule.FieldTypeValidation(field, type, message))
    }

    /**
     * Validate numeric field is within range
     */
    fun range(field: String, min: Double, max: Double, message: String? = null) {
        rules.add(ValidationRule.RangeValidation(field, min, max, message))
    }

    /**
     * Validate string field matches pattern
     */
    fun pattern(field: String, regex: Regex, message: String? = null) {
        rules.add(ValidationRule.PatternValidation(field, regex, message))
    }

    /**
     * Validate string field matches pattern (string version)
     */
    fun pattern(field: String, pattern: String, message: String? = null) {
        rules.add(ValidationRule.PatternValidation(field, Regex(pattern), message))
    }

    /**
     * Add custom validation rule
     */
    fun rule(description: String, validator: (Any, AgentContext?) -> Boolean) {
        rules.add(ValidationRule.CustomRule(description, validator))
    }

    /**
     * Add custom validation rule (simple version without context)
     */
    fun rule(description: String, validator: (Any) -> Boolean) {
        rules.add(ValidationRule.CustomRule(description) { output, _ -> validator(output) })
    }

    /**
     * Add custom validation rule (alias for rule with context)
     *
     * This is an alias for better readability in some contexts.
     */
    fun custom(description: String, validator: (Any, AgentContext?) -> Boolean) {
        rule(description, validator)
    }

    /**
     * Add custom validation rule (alias for rule without context)
     *
     * This is an alias for better readability in some contexts.
     */
    fun custom(description: String, validator: (Any) -> Boolean) {
        rule(description, validator)
    }

    internal fun build(): OutputValidator {
        return OutputValidator(rules)
    }
}

/**
 * Output validator
 */
class OutputValidator(
    private val rules: List<ValidationRule>
) {
    /**
     * Validate output
     */
    fun validate(output: Any, context: AgentContext? = null): ValidationResult {
        for (rule in rules) {
            val result = rule.validate(output, context)
            if (!result.isValid) {
                return result
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validate and wrap in ToolResult
     */
    fun validateAndWrap(output: Any, context: AgentContext? = null): ToolResult {
        val result = validate(output, context)
        return if (result.isValid) {
            ToolResult.success(output.toString())
        } else {
            ToolResult.error("Output validation failed: ${result.error}")
        }
    }
}

/**
 * Create output validator with DSL
 */
fun outputValidator(block: OutputValidatorBuilder.() -> Unit): OutputValidator {
    val builder = OutputValidatorBuilder()
    builder.block()
    return builder.build()
}
