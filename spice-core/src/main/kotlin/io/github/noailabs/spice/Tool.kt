package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 🛠️ Core Tool interface for Spice Framework 1.0.0
 */
interface Tool {
    val name: String
    val description: String

    suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult>
}

@Serializable
data class ToolContext(
    val agentId: String,
    val userId: String? = null,
    val tenantId: String? = null,
    val correlationId: String? = null,
    val metadata: Map<String, @Contextual Any> = emptyMap()
)

@Serializable
data class ToolResult(
    val result: @Contextual Any?,
    val success: Boolean = true,
    val metadata: Map<String, @Contextual Any> = emptyMap()
)
