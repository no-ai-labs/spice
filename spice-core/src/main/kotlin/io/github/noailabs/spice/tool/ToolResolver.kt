package io.github.noailabs.spice.tool

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult

/**
 * Tool Resolver for Dynamic Tool Selection
 *
 * Abstraction for resolving tools at runtime. Supports both static (build-time)
 * and dynamic (runtime) tool selection while maintaining a unified API.
 *
 * **Usage:**
 * ```kotlin
 * // Static tool (existing pattern)
 * tool("search", searchTool)
 *
 * // Dynamic by registry lookup
 * tool("fetch", ToolResolver.byRegistry(
 *     nameSelector = { msg -> msg.getData<String>("toolId")!! },
 *     namespace = "stayfolio",
 *     expectedTools = setOf("list_reservations", "list_coupons"),
 *     strict = false  // WARNING on missing tools (default)
 * ))
 *
 * // Dynamic with custom logic
 * tool("smart", ToolResolver.dynamic("complexity-based") { msg ->
 *     val complexity = msg.getData<Int>("complexity") ?: 0
 *     if (complexity > 50) SpiceResult.success(advancedTool)
 *     else SpiceResult.success(simpleTool)
 * })
 *
 * // Fallback chain
 * tool("resilient", ToolResolver.fallback(
 *     ToolResolver.byRegistry { "primary_tool" },
 *     ToolResolver.static(fallbackTool)
 * ))
 * ```
 *
 * @since 1.0.4
 */
sealed interface ToolResolver {
    /**
     * Resolve tool for the given message context.
     *
     * @param message Current SpiceMessage for context-aware resolution
     * @return SpiceResult with resolved Tool or error if resolution fails
     */
    suspend fun resolve(message: SpiceMessage): SpiceResult<Tool>

    /**
     * Validate that this resolver is properly configured.
     * Called at graph build time for early error detection.
     *
     * @param registry Optional ToolRegistry for validating dynamic lookups
     * @param defaultNamespace Default namespace for validation
     * @return List of validation results (info, warnings, errors)
     */
    fun validate(
        registry: ToolRegistry? = null,
        defaultNamespace: String = "global"
    ): List<ToolResolverValidation>

    /**
     * Get descriptive name for logging/debugging
     */
    val displayName: String

    companion object {
        /**
         * Create static resolver from tool (wraps existing tool)
         */
        fun static(tool: Tool): ToolResolver = StaticToolResolver(tool)

        /**
         * Create registry-based resolver with name selector
         *
         * @param nameSelector Function to extract tool name from message
         * @param namespace Tool registry namespace (default: "global")
         * @param expectedTools Set of expected tool names for build-time validation
         * @param strict If true, missing expected tools cause ERROR; if false, WARNING
         */
        fun byRegistry(
            nameSelector: (SpiceMessage) -> String,
            namespace: String = "global",
            expectedTools: Set<String>? = null,
            strict: Boolean = false
        ): ToolResolver = RegistryToolResolver(
            nameSelector = nameSelector,
            namespaceSelector = { namespace },
            expectedTools = expectedTools,
            defaultNamespace = namespace,
            strict = strict
        )

        /**
         * Create registry-based resolver with both name and namespace selectors
         */
        fun byRegistry(
            nameSelector: (SpiceMessage) -> String,
            namespaceSelector: (SpiceMessage) -> String,
            expectedTools: Set<String>? = null,
            defaultNamespace: String = "global",
            strict: Boolean = false
        ): ToolResolver = RegistryToolResolver(
            nameSelector = nameSelector,
            namespaceSelector = namespaceSelector,
            expectedTools = expectedTools,
            defaultNamespace = defaultNamespace,
            strict = strict
        )

        /**
         * Create dynamic resolver with custom selector
         *
         * @param description Description for logging/debugging
         * @param selector Suspend function to resolve tool from message
         */
        fun dynamic(
            description: String = "custom",
            selector: suspend (SpiceMessage) -> SpiceResult<Tool>
        ): ToolResolver = DynamicToolResolver(selector, description)

        /**
         * Create fallback chain resolver
         *
         * @param resolvers Resolvers to try in order until one succeeds
         */
        fun fallback(vararg resolvers: ToolResolver): ToolResolver {
            require(resolvers.isNotEmpty()) { "Fallback requires at least one resolver" }
            return if (resolvers.size == 1) resolvers[0]
            else FallbackToolResolver(resolvers.toList())
        }
    }
}

/**
 * Static tool resolver with build-time binding.
 *
 * This is the default resolver type, maintaining full backward compatibility
 * with existing ToolNode usage patterns.
 */
data class StaticToolResolver(
    val tool: Tool
) : ToolResolver {

    override suspend fun resolve(message: SpiceMessage): SpiceResult<Tool> {
        return SpiceResult.success(tool)
    }

    override fun validate(
        registry: ToolRegistry?,
        defaultNamespace: String
    ): List<ToolResolverValidation> {
        // Static tools are always valid - they exist at build time
        return emptyList()
    }

    override val displayName: String get() = "static:${tool.name}"
}

/**
 * Registry-based tool resolver using name/namespace lookup.
 *
 * Validates expected tools at build time, performs lookup at runtime.
 *
 * **Build-time Validation Limitation:**
 * When `namespaceSelector` is dynamic (selects namespace from message at runtime),
 * build-time validation can only check against `defaultNamespace`. If the actual
 * runtime namespace differs, tools may be missing at runtime despite passing
 * build-time validation.
 *
 * @property nameSelector Function to extract tool name from message
 * @property namespaceSelector Function to extract namespace from message
 * @property expectedTools Set of expected tool names for build-time validation
 * @property defaultNamespace Default namespace for validation (and for static namespace)
 * @property strict If true, missing expected tools cause ERROR; if false, WARNING
 */
data class RegistryToolResolver(
    val nameSelector: (SpiceMessage) -> String,
    val namespaceSelector: (SpiceMessage) -> String = { "global" },
    val expectedTools: Set<String>? = null,
    val defaultNamespace: String = "global",
    val strict: Boolean = false
) : ToolResolver {

    // Track if namespaceSelector is dynamic (not the default constant selector)
    private val isDynamicNamespace: Boolean = try {
        // Create a test message to check if namespace varies
        // If namespaceSelector returns something other than defaultNamespace, it's dynamic
        val testMsg = SpiceMessage.create("test", "validator")
        namespaceSelector(testMsg) != defaultNamespace
    } catch (e: Exception) {
        // If selector throws on test message, it's definitely dynamic
        true
    }

    override suspend fun resolve(message: SpiceMessage): SpiceResult<Tool> {
        val name = try {
            nameSelector(message)
        } catch (e: Exception) {
            return SpiceResult.failure(
                SpiceError.ToolLookupError(
                    message = "Failed to resolve tool name from message: ${e.message}",
                    cause = e
                )
            )
        }

        val namespace = try {
            namespaceSelector(message)
        } catch (e: Exception) {
            return SpiceResult.failure(
                SpiceError.ToolLookupError(
                    message = "Failed to resolve namespace from message: ${e.message}",
                    cause = e
                )
            )
        }

        return ToolRegistry.getTool(name, namespace)?.let {
            SpiceResult.success(it)
        } ?: SpiceResult.failure(
            SpiceError.ToolLookupError(
                message = "Tool '$name' not found in namespace '$namespace'",
                toolName = name,
                namespace = namespace,
                availableTools = ToolRegistry.getByNamespace(namespace).map { it.name }
            )
        )
    }

    override fun validate(
        registry: ToolRegistry?,
        defaultNamespace: String
    ): List<ToolResolverValidation> {
        val validations = mutableListOf<ToolResolverValidation>()

        // Warn if dynamic namespace - build-time validation has limitations
        if (isDynamicNamespace) {
            validations.add(
                ToolResolverValidation(
                    level = ToolResolverValidation.Level.INFO,
                    message = "Using dynamic namespace selector - build-time validation " +
                        "checks defaultNamespace='${this.defaultNamespace}' only. " +
                        "Runtime namespace may differ.",
                    context = mapOf("defaultNamespace" to this.defaultNamespace)
                )
            )
        }

        // Validate expected tools against defaultNamespace
        if (expectedTools != null && registry != null) {
            val ns = this.defaultNamespace
            expectedTools.forEach { toolName ->
                if (!registry.hasTool(toolName, ns)) {
                    // Downgrade strict to WARNING when namespace is dynamic
                    // since we can't reliably validate runtime namespace at build time
                    val effectiveLevel = when {
                        isDynamicNamespace -> ToolResolverValidation.Level.WARNING
                        strict -> ToolResolverValidation.Level.ERROR
                        else -> ToolResolverValidation.Level.WARNING
                    }
                    val dynamicNote = if (isDynamicNamespace && strict) {
                        " (strict downgraded to WARNING due to dynamic namespace)"
                    } else ""

                    validations.add(
                        ToolResolverValidation(
                            level = effectiveLevel,
                            message = "Expected tool '$toolName' not found in namespace '$ns'$dynamicNote",
                            context = mapOf(
                                "toolName" to toolName,
                                "namespace" to ns,
                                "strictDowngraded" to (isDynamicNamespace && strict)
                            )
                        )
                    )
                }
            }
        }

        return validations
    }

    override val displayName: String get() = "registry:$defaultNamespace"
}

/**
 * Dynamic tool resolver using a selector function.
 *
 * Ideal for context-aware tool routing based on message data/metadata.
 *
 * @property selector Suspend function to resolve tool from message
 * @property description Description for logging/debugging
 */
data class DynamicToolResolver(
    val selector: suspend (SpiceMessage) -> SpiceResult<Tool>,
    val description: String
) : ToolResolver {

    override suspend fun resolve(message: SpiceMessage): SpiceResult<Tool> {
        return try {
            selector(message)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.ToolLookupError(
                    message = "Dynamic tool selection failed: ${e.message}",
                    cause = e
                )
            )
        }
    }

    override fun validate(
        registry: ToolRegistry?,
        defaultNamespace: String
    ): List<ToolResolverValidation> {
        return listOf(
            ToolResolverValidation(
                level = ToolResolverValidation.Level.INFO,
                message = "Using dynamic tool selection: $description"
            )
        )
    }

    override val displayName: String get() = "dynamic:$description"
}

/**
 * Fallback chain resolver - tries resolvers in order until one succeeds.
 *
 * @property resolvers List of resolvers to try in order
 */
data class FallbackToolResolver(
    val resolvers: List<ToolResolver>
) : ToolResolver {

    init {
        require(resolvers.isNotEmpty()) { "FallbackToolResolver requires at least one resolver" }
    }

    override suspend fun resolve(message: SpiceMessage): SpiceResult<Tool> {
        val errors = mutableListOf<SpiceError>()

        for (resolver in resolvers) {
            when (val result = resolver.resolve(message)) {
                is SpiceResult.Success -> return result
                is SpiceResult.Failure -> errors.add(result.error)
            }
        }

        return SpiceResult.failure(
            SpiceError.ToolLookupError(
                message = "All ${resolvers.size} fallback resolvers failed",
                context = mapOf("errors" to errors.map { it.message })
            )
        )
    }

    override fun validate(
        registry: ToolRegistry?,
        defaultNamespace: String
    ): List<ToolResolverValidation> {
        return resolvers.flatMap { it.validate(registry, defaultNamespace) }
    }

    override val displayName: String
        get() = "fallback:[${resolvers.joinToString(",") { it.displayName }}]"
}

/**
 * Validation result from ToolResolver.validate()
 */
data class ToolResolverValidation(
    val level: Level,
    val message: String,
    val context: Map<String, Any> = emptyMap()
) {
    enum class Level {
        INFO,     // Informational (e.g., "Using dynamic resolution")
        WARNING,  // Potential issue (e.g., "Tool may not exist at runtime")
        ERROR     // Definite problem (e.g., "Required configuration missing")
    }
}
