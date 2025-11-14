package io.github.noailabs.spice.validation

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.StateTransition
import io.github.noailabs.spice.ToolSchema
import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a validation failure for a specific field.
 */
data class ValidationError(
    val field: String,
    val message: String
)

/**
 * Result for schema/message validators.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
) {
    companion object {
        fun valid(): ValidationResult = ValidationResult(true)
        fun invalid(vararg errors: ValidationError): ValidationResult =
            ValidationResult(false, errors.toList())
    }

    fun throwIfInvalid(context: String) {
        if (isValid) return
        val detail = errors.joinToString { "${it.field}: ${it.message}" }
        throw IllegalArgumentException("$context failed validation: $detail")
    }
}

fun interface Validator<T> {
    fun validate(value: T): ValidationResult
}

/**
 * Validates ToolSchema definitions.
 */
class ToolSchemaValidator : Validator<ToolSchema> {
    private val allowedTypes = setOf("string", "number", "boolean", "array", "object")

    override fun validate(value: ToolSchema): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (value.name.isBlank()) {
            errors += ValidationError("name", "Tool name cannot be blank")
        }
        if (value.description.isBlank()) {
            errors += ValidationError("description", "Tool description cannot be blank")
        }

        value.parameters.forEach { (paramName, schema) ->
            if (paramName.isBlank()) {
                errors += ValidationError("parameters.$paramName", "Parameter name cannot be blank")
            }
            if (schema.type !in allowedTypes) {
                errors += ValidationError(
                    "parameters.$paramName.type",
                    "Unsupported type ${schema.type}"
                )
            }
            if (schema.description.isBlank()) {
                errors += ValidationError(
                    "parameters.$paramName.description",
                    "Description is required"
                )
            }
        }

        return if (errors.isEmpty()) ValidationResult.valid() else ValidationResult(false, errors)
    }
}

/**
 * Validates OAI tool call payloads before they leave the framework.
 */
class OAIToolCallValidator : Validator<OAIToolCall> {
    override fun validate(value: OAIToolCall): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (!value.id.startsWith("call_")) {
            errors += ValidationError("id", "Tool call id must start with call_")
        }
        if (value.type != "function") {
            errors += ValidationError("type", "Only function tool calls are supported")
        }
        if (value.function.name.isBlank()) {
            errors += ValidationError("function.name", "Function name cannot be blank")
        }
        if (value.function.arguments.any { (key, _) -> key.isBlank() }) {
            errors += ValidationError("function.arguments", "Argument keys cannot be blank")
        }

        return if (errors.isEmpty()) ValidationResult.valid() else ValidationResult(false, errors)
    }
}

/**
 * Validates unified SpiceMessage envelopes, including embedded tool calls.
 */
class SpiceMessageValidator(
    private val toolCallValidator: Validator<OAIToolCall> = OAIToolCallValidator()
) : Validator<SpiceMessage> {
    override fun validate(value: SpiceMessage): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (value.correlationId.isNullOrBlank()) {
            errors += ValidationError("correlationId", "Correlation id cannot be blank")
        }
        if (value.content.isNullOrBlank()) {
            errors += ValidationError("content", "Message content cannot be blank")
        }

        value.toolCalls.forEachIndexed { index, call ->
            val callResult = toolCallValidator.validate(call)
            if (!callResult.isValid) {
                errors += callResult.errors.map {
                    ValidationError("toolCalls[$index].${it.field}", it.message)
                }
            }
        }

        if (!value.stateHistory.all(StateTransition::isValid)) {
            errors += ValidationError("stateHistory", "Found invalid state transition sequence")
        }

        return if (errors.isEmpty()) ValidationResult.valid() else ValidationResult(false, errors)
    }
}

/**
 * Dead-letter interface for invalid payload routing.
 */
fun interface DeadLetterHandler {
    fun handle(record: DeadLetterRecord)
}

data class DeadLetterRecord(
    val payloadType: String,
    val payload: Any,
    val errors: List<ValidationError>,
    val timestamp: Instant = Clock.System.now()
)

/**
 * Simple in-memory dead-letter sink for local runs/tests.
 */
class InMemoryDeadLetterHandler : DeadLetterHandler {
    private val records = mutableListOf<DeadLetterRecord>()

    override fun handle(record: DeadLetterRecord) {
        synchronized(records) {
            records += record
        }
    }

    fun drain(): List<DeadLetterRecord> = synchronized(records) {
        val copy = records.toList()
        records.clear()
        copy
    }
}

/**
 * Utility wrapper that runs validation and automatically pushes failures to a dead-letter handler.
 */
class SchemaValidationPipeline(
    private val messageValidator: Validator<SpiceMessage> = SpiceMessageValidator(),
    private val toolCallValidator: Validator<OAIToolCall> = OAIToolCallValidator(),
    private val deadLetterHandler: DeadLetterHandler? = null
) {
    fun validateMessage(message: SpiceMessage): ValidationResult {
        val result = messageValidator.validate(message)
        if (!result.isValid) {
            deadLetterHandler?.handle(
                DeadLetterRecord(
                    payloadType = "SpiceMessage",
                    payload = message,
                    errors = result.errors
                )
            )
        }
        return result
    }

    fun validateToolCall(call: OAIToolCall): ValidationResult {
        val result = toolCallValidator.validate(call)
        if (!result.isValid) {
            deadLetterHandler?.handle(
                DeadLetterRecord(
                    payloadType = "OAIToolCall",
                    payload = call,
                    errors = result.errors
                )
            )
        }
        return result
    }
}
