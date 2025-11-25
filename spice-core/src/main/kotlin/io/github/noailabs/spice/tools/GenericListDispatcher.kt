package io.github.noailabs.spice.tools

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Wrapper tool that dispatches to another tool at runtime while keeping the tool id static.
 *
 * Useful when graph definitions must reference a concrete tool at build time but the concrete
 * list-fetching tool varies by request.
 */
class GenericListDispatcher : Tool {
    override val name: String = "generic_list_dispatcher"
    override val description: String =
        "Dispatches list retrieval to a target tool using targetToolId/targetParams."

    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val targetToolId = params["targetToolId"] as? String
            ?: return SpiceResult.failure(
                SpiceError.ValidationError(
                    message = "targetToolId is required",
                    field = "targetToolId",
                    expectedType = "String",
                    actualValue = params["targetToolId"]
                )
            )

        val namespace = params["targetNamespace"] as? String ?: "global"
        val targetParams = when (val rawParams = params["targetParams"]) {
            null -> emptyMap()
            is Map<*, *> -> rawParams.entries
                .filter { it.key is String }
                .associate { (key, value) -> key as String to value }
                .filterValues { it != null }
                .mapValues { (_, value) -> value as Any }
            else -> return SpiceResult.failure(
                SpiceError.ValidationError(
                    message = "targetParams must be a map",
                    field = "targetParams",
                    expectedType = "Map<String, Any>",
                    actualValue = rawParams
                )
            )
        }

        val targetTool = ToolRegistry.getTool(targetToolId, namespace)
            ?: return SpiceResult.failure(
                SpiceError.ExecutionError("Tool not found: $namespace::$targetToolId")
            )

        logger.info { "[GenericListDispatcher] Dispatching to $namespace::$targetToolId" }
        return targetTool.execute(targetParams, context)
    }
}
