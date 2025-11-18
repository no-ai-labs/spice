package io.github.noailabs.spice.error

import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.toolspec.OAIToolCall

data class ErrorReport(
    val code: String,
    val reason: String,
    val recoverable: Boolean = false,
    val context: Map<String, Any?> = emptyMap()
)

object ErrorReportAdapter {
    fun fromThrowable(error: Throwable, recoverable: Boolean = false): ErrorReport {
        val code = error::class.simpleName ?: "UnknownError"
        return ErrorReport(
            code = code,
            reason = error.message ?: code,
            recoverable = recoverable
        )
    }

    fun toToolCall(report: ErrorReport): OAIToolCall =
        OAIToolCall.error(
            message = report.reason,
            errorType = report.code,
            isRecoverable = report.recoverable
        )

    fun toToolResult(report: ErrorReport): ToolResult =
        ToolResult.error(
            error = mapOf(
                "code" to report.code,
                "reason" to report.reason,
                "recoverable" to report.recoverable,
                "context" to report.context
            )
        )
}