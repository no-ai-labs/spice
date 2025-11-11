package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.performance.CachedTool
import io.github.noailabs.spice.validation.OutputValidator
import io.github.noailabs.spice.validation.OutputValidatorBuilder
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
 * Parameters configuration block for context-aware tools
 *
 * Example:
 * ```kotlin
 * contextAwareTool("user_lookup") {
 *     parameters {
 *         string("email", "User email", required = true)
 *         number("age", "User age", required = false)
 *         boolean("active", "Is active", required = false)
 *     }
 * }
 * ```
 */
class ParametersBlock {
    internal val params = mutableMapOf<String, ParameterSchema>()

    /**
     * Add a string parameter
     */
    fun string(name: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema("string", description, required)
    }

    /**
     * Add a number parameter
     */
    fun number(name: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema("number", description, required)
    }

    /**
     * Add an integer parameter
     */
    fun integer(name: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema("integer", description, required)
    }

    /**
     * Add a boolean parameter
     */
    fun boolean(name: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema("boolean", description, required)
    }

    /**
     * Add an array parameter
     */
    fun array(name: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema("array", description, required)
    }

    /**
     * Add an object parameter
     */
    fun `object`(name: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema("object", description, required)
    }

    /**
     * Add a parameter with custom type
     */
    fun param(name: String, type: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema(type, description, required)
    }
}

/**
 * Cache configuration block for context-aware tools
 *
 * Example:
 * ```kotlin
 * contextAwareTool("expensive_query") {
 *     cache {
 *         keyBuilder = { params, context ->
 *             "${context.tenantId}|${params["id"]}"
 *         }
 *         ttl = 300
 *         maxSize = 1000
 *     }
 * }
 * ```
 */
class CacheConfigBlock {
    var ttl: Long = 3600  // Time-to-live in seconds (default: 1 hour)
    var maxSize: Int = 1000  // Maximum cache entries
    var enableMetrics: Boolean = true
    var keyBuilder: ((Map<String, Any>, AgentContext) -> String)? = null

    internal fun toToolCacheConfig(): CachedTool.ToolCacheConfig {
        return CachedTool.ToolCacheConfig(
            maxSize = maxSize,
            ttl = ttl,
            enableMetrics = enableMetrics,
            keyBuilder = keyBuilder
        )
    }
}

/**
 * Context-aware tool builder
 *
 * Example:
 * ```kotlin
 * val tool = contextAwareTool("policy_lookup") {
 *     description = "Look up policy by type"
 *     param("policyType", "string", "Policy type")
 *
 *     // 🆕 Cache configuration
 *     cache {
 *         keyBuilder = { params, context ->
 *             "${context.tenantId}|${params["policyType"]}"
 *         }
 *         ttl = 300
 *         maxSize = 500
 *     }
 *
 *     execute { params, context ->
 *         val tenantId = context.tenantId ?: "CHIC"  // ✅ Auto from context!
 *         val userId = context.userId ?: "unknown"
 *         val policyType = params["policyType"]?.toString() ?: throw IllegalArgumentException("Missing 'policyType'")
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
    private var cacheConfig: CacheConfigBlock? = null
    private var outputValidator: OutputValidator? = null

    /**
     * Add a parameter to the tool schema (individual method)
     */
    fun param(name: String, type: String, description: String, required: Boolean = true) {
        params[name] = ParameterSchema(type, description, required)
    }

    /**
     * Configure parameters using DSL block
     *
     * Example:
     * ```kotlin
     * parameters {
     *     string("email", "User email", required = true)
     *     number("age", "User age", required = false)
     *     boolean("active", "Is active", required = false)
     * }
     * ```
     */
    fun parameters(config: ParametersBlock.() -> Unit) {
        val block = ParametersBlock()
        block.config()
        params.putAll(block.params)
    }

    /**
     * Configure caching for this tool
     *
     * Example:
     * ```kotlin
     * cache {
     *     keyBuilder = { params, context ->
     *         "${context.tenantId}|${params["id"]}"
     *     }
     *     ttl = 300  // 5 minutes
     *     maxSize = 1000
     * }
     * ```
     */
    fun cache(config: CacheConfigBlock.() -> Unit) {
        val block = CacheConfigBlock()
        block.config()
        this.cacheConfig = block
    }

    /**
     * Configure output validation for this tool
     *
     * Example:
     * ```kotlin
     * validate {
     *     requireField("citations", "Evidence must include citations")
     *     requireField("summary")
     *     fieldType("citations", FieldType.ARRAY)
     *     fieldType("confidence", FieldType.NUMBER)
     *     rule("citations must not be empty") { output ->
     *         val citations = (output as? Map<*, *>)?.get("citations") as? List<*>
     *         citations != null && citations.isNotEmpty()
     *     }
     *     range("confidence", 0.0, 1.0)
     * }
     * ```
     */
    fun validate(config: OutputValidatorBuilder.() -> Unit) {
        val builder = OutputValidatorBuilder()
        builder.config()
        this.outputValidator = builder.build()
    }

    /**
     * Define the tool execution handler with automatic AgentContext injection
     */
    fun execute(handler: suspend (Map<String, Any>, AgentContext) -> Any) {
        this.handler = handler
    }

    internal fun build(): Tool {
        val baseTool = object : Tool {
            override val name = this@ContextAwareToolBuilder.name
            override val description = this@ContextAwareToolBuilder.description
            override val schema = ToolSchema(
                name = this@ContextAwareToolBuilder.name,
                description = this@ContextAwareToolBuilder.description,
                parameters = params
            )

            override suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult> {
                val context = coroutineContext[AgentContext]
                    ?: return SpiceResult.success(ToolResult.error(
                        "No AgentContext available. Use withAgentContext { } block or provide runtime context."
                    ))

                // Add context to parameters for cache key generation
                val enrichedParams = parameters.toMutableMap()
                enrichedParams["__context"] = context

                @Suppress("UNCHECKED_CAST")
                val nonNullParams = parameters.filterValues { it != null } as Map<String, Any>

                return try {
                    val result = handler?.invoke(nonNullParams, context)
                        ?: return SpiceResult.success(ToolResult.error("No handler defined"))

                    // Validate output if validator is configured
                    val validator = this@ContextAwareToolBuilder.outputValidator
                    if (validator != null) {
                        val validationResult = validator.validate(result, context)
                        if (!validationResult.isValid) {
                            return SpiceResult.success(ToolResult.error(
                                "Output validation failed: ${validationResult.error}"
                            ))
                        }
                    }

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

        // Wrap with caching if configured
        return if (cacheConfig != null) {
            CachedTool(baseTool, cacheConfig!!.toToolCacheConfig())
        } else {
            baseTool
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
 *         val policyType = params["policyType"]?.toString() ?: throw IllegalArgumentException("Missing 'policyType'")
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
 *             val policyType = params["policyType"]?.toString() ?: throw IllegalArgumentException("Missing 'policyType'")
 *             policyService.lookup(tenantId, policyType)
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

        val executor: suspend (Map<String, Any?>) -> SpiceResult<ToolResult> = { params ->
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
        val executor: suspend (Map<String, Any?>) -> SpiceResult<ToolResult> = { params ->
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
