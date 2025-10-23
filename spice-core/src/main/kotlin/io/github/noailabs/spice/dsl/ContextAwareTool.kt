package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlin.coroutines.coroutineContext

/**
 * 🎯 Context-Aware Tool DSL
 *
 * Tools that automatically receive AgentContext from coroutine scope.
 * No need to manually pass tenantId, userId, sessionId as parameters!
 *
 * @since 0.4.0
 */

/**
 * Context-aware tool builder
 *
 * Example:
 * ```kotlin
 * val tool = contextAwareTool("policy_lookup") {
 *     description = "Look up policy by type"
 *     param("policyType", "string", "Policy type")
 *
 *     execute { params, context ->
 *         val tenantId = context.tenantId ?: "CHIC"  // ✅ Auto from context!
 *         val userId = context.userId ?: "unknown"
 *         val policyType = params["policyType"] as String
 *
 *         policyService.lookup(tenantId, policyType)
 *     }
 * }
 * ```
 */
class ContextAwareToolBuilder(val name: String) {
    var description: String = ""
    private val params = mutableMapOf<String, ParameterSchema>()
    private var handler: (suspend (Map<String, Any>, AgentContext) -> Any)? = null

    /**
     * Add a parameter to the tool schema
     */
    fun param(name: String, type: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema(type, description, required)
    }

    /**
     * Define the tool execution handler with automatic AgentContext injection
     */
    fun execute(handler: suspend (Map<String, Any>, AgentContext) -> Any) {
        this.handler = handler
    }

    internal fun build(): Tool = object : Tool {
        override val name = this@ContextAwareToolBuilder.name
        override val description = this@ContextAwareToolBuilder.description
        override val schema = ToolSchema(
            name = this@ContextAwareToolBuilder.name,
            description = this@ContextAwareToolBuilder.description,
            parameters = params
        )

        override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
            val context = coroutineContext[AgentContext]
                ?: return SpiceResult.success(ToolResult.error(
                    "No AgentContext available. Use withAgentContext { } block or provide runtime context."
                ))

            return try {
                val result = handler?.invoke(parameters, context)
                    ?: return SpiceResult.success(ToolResult.error("No handler defined"))

                SpiceResult.success(ToolResult.success(
                    result = result.toString(),
                    metadata = mapOf(
                        "tenantId" to (context.tenantId ?: "none"),
                        "userId" to (context.userId ?: "none"),
                        "contextAware" to "true"
                    )
                ))
            } catch (e: Exception) {
                SpiceResult.success(ToolResult.error(
                    "Tool execution failed: ${e.message}"
                ))
            }
        }
    }
}

/**
 * Create a context-aware tool
 *
 * Example:
 * ```kotlin
 * val policyLookup = contextAwareTool("policy_lookup") {
 *     description = "Look up policy by type"
 *     param("policyType", "string", "Policy type")
 *
 *     execute { params, context ->
 *         val tenantId = context.tenantId ?: "CHIC"
 *         val policyType = params["policyType"] as String
 *         policyService.lookup(tenantId, policyType)
 *     }
 * }
 * ```
 */
fun contextAwareTool(name: String, config: ContextAwareToolBuilder.() -> Unit): Tool {
    val builder = ContextAwareToolBuilder(name)
    builder.config()
    return builder.build()
}

/**
 * Create a context-aware tool with minimal DSL (execute only)
 *
 * Example:
 * ```kotlin
 * val getTenant = simpleContextTool("get_tenant") { params, context ->
 *     "Current tenant: ${context.tenantId}"
 * }
 * ```
 */
fun simpleContextTool(
    name: String,
    description: String = "",
    handler: suspend (Map<String, Any>, AgentContext) -> Any
): Tool {
    return contextAwareTool(name) {
        this.description = description
        execute(handler)
    }
}

/**
 * Extension: Add context-aware tool to CoreAgentBuilder
 *
 * Example:
 * ```kotlin
 * buildAgent {
 *     id = "policy-agent"
 *
 *     contextAwareTool("policy_lookup") {
 *         description = "Look up policy"
 *         param("policyType", "string", "Policy type")
 *
 *         execute { params, context ->
 *             val tenantId = context.tenantId ?: "CHIC"  // ✅ Auto!
 *             policyService.lookup(tenantId, params["policyType"] as String)
 *         }
 *     }
 * }
 * ```
 */
fun CoreAgentBuilder.contextAwareTool(name: String, config: ContextAwareToolBuilder.() -> Unit) {
    // Build context-aware tool using the builder directly
    val builder = ContextAwareToolBuilder(name)
    builder.config()
    val contextTool = builder.build()

    // Add as inline tool
    this.tool(name) {
        description(contextTool.description)

        // Forward parameters from original schema
        contextTool.schema.parameters.forEach { (paramName, paramSchema) ->
            parameter(paramName, paramSchema.type, paramSchema.description, paramSchema.required)
        }

        val executor: suspend (Map<String, Any>) -> SpiceResult<ToolResult> = { params ->
            val result = contextTool.execute(params)
            result.fold(
                onSuccess = { toolResult ->
                    val resultValue = toolResult.result?.toString() ?: toolResult.error ?: "No result"
                    SpiceResult.success(ToolResult.success(resultValue))
                },
                onFailure = { error ->
                    SpiceResult.success(ToolResult.error(error.message ?: "Execution failed"))
                }
            )
        }
        execute(executor)
    }
}

/**
 * Extension: Add simple context-aware tool to CoreAgentBuilder
 *
 * Example:
 * ```kotlin
 * buildAgent {
 *     simpleContextTool("get_tenant") { params, context ->
 *         "Tenant: ${context.tenantId}"
 *     }
 * }
 * ```
 */
fun CoreAgentBuilder.simpleContextTool(
    name: String,
    description: String = "",
    handler: suspend (Map<String, Any>, AgentContext) -> Any
) {
    // Build context-aware tool directly
    val builder = ContextAwareToolBuilder(name)
    builder.description = description
    builder.execute(handler)
    val contextTool = builder.build()

    this.tool(name) {
        description(description)
        val executor: suspend (Map<String, Any>) -> SpiceResult<ToolResult> = { params ->
            val result = contextTool.execute(params)
            result.fold(
                onSuccess = { toolResult ->
                    val resultValue = toolResult.result?.toString() ?: toolResult.error ?: "No result"
                    SpiceResult.success(ToolResult.success(resultValue))
                },
                onFailure = { error ->
                    SpiceResult.success(ToolResult.error(error.message ?: "Execution failed"))
                }
            )
        }
        execute(executor)
    }
}
