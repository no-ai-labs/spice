package io.github.noailabs.spice.retry

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError

/**
 * Retry Policy Resolver
 *
 * Resolves the appropriate retry policy for a given context.
 * Supports tenant-specific, endpoint-specific, and error-specific overrides.
 *
 * **Resolution Order (first match wins):**
 * 1. Error-level hint (RetryHint in RetryableError)
 * 2. Endpoint-specific policy (by toolName/nodeId)
 * 3. Tenant-specific policy
 * 4. Default policy
 *
 * **Usage:**
 * ```kotlin
 * val resolver = RetryPolicyResolver.builder()
 *     .defaultPolicy(ExecutionRetryPolicy.DEFAULT)
 *     .forTenant("premium") { ExecutionRetryPolicy.AGGRESSIVE }
 *     .forEndpoint("external-api") { ExecutionRetryPolicy.CONSERVATIVE }
 *     .build()
 *
 * val policy = resolver.resolve(message, error, nodeId = "my-tool")
 * ```
 *
 * @since 1.0.4
 */
interface RetryPolicyResolver {

    /**
     * Resolve retry policy for the given context.
     *
     * @param message Current message (for metadata access)
     * @param error The error that occurred (for error-level hints)
     * @param nodeId Current node ID (for endpoint-specific policies)
     * @return Resolved retry policy
     */
    fun resolve(
        message: SpiceMessage,
        error: SpiceError,
        nodeId: String? = null
    ): ExecutionRetryPolicy

    /**
     * Resolve retry policy, using the provided default if no specific override matches.
     *
     * This allows callers to pass an explicit policy while still allowing
     * error-level, tenant-level, or endpoint-level overrides.
     *
     * @param message Current message (for metadata access)
     * @param error The error that occurred (for error-level hints)
     * @param nodeId Current node ID (for endpoint-specific policies)
     * @param defaultPolicy The default policy to use if no override matches
     * @return Resolved retry policy
     */
    fun resolveOrDefault(
        message: SpiceMessage,
        error: SpiceError,
        nodeId: String?,
        defaultPolicy: ExecutionRetryPolicy
    ): ExecutionRetryPolicy = defaultPolicy

    companion object {
        /**
         * Create a builder for RetryPolicyResolver
         */
        fun builder(): RetryPolicyResolverBuilder = DefaultRetryPolicyResolverBuilder()

        /**
         * Create a simple resolver with default policy only
         */
        fun default(policy: ExecutionRetryPolicy = ExecutionRetryPolicy.DEFAULT): RetryPolicyResolver =
            SimpleRetryPolicyResolver(policy)

        /**
         * Create a resolver that always returns NO_RETRY
         */
        fun noRetry(): RetryPolicyResolver = SimpleRetryPolicyResolver(ExecutionRetryPolicy.NO_RETRY)
    }
}

/**
 * Builder for RetryPolicyResolver
 */
interface RetryPolicyResolverBuilder {
    /**
     * Set the default policy (used when no override matches)
     */
    fun defaultPolicy(policy: ExecutionRetryPolicy): RetryPolicyResolverBuilder

    /**
     * Add a tenant-specific policy override
     *
     * @param tenantId Tenant ID to match
     * @param policyProvider Function to provide the policy
     */
    fun forTenant(tenantId: String, policyProvider: () -> ExecutionRetryPolicy): RetryPolicyResolverBuilder

    /**
     * Add an endpoint/node-specific policy override
     *
     * @param nodeId Node ID to match (tool name, agent ID, etc.)
     * @param policyProvider Function to provide the policy
     */
    fun forEndpoint(nodeId: String, policyProvider: () -> ExecutionRetryPolicy): RetryPolicyResolverBuilder

    /**
     * Add an error-code-specific policy override
     *
     * @param errorCode Error code to match
     * @param policyProvider Function to provide the policy
     */
    fun forErrorCode(errorCode: String, policyProvider: () -> ExecutionRetryPolicy): RetryPolicyResolverBuilder

    /**
     * Add a custom resolver function
     *
     * @param resolver Custom resolver function that returns policy or null to continue chain
     */
    fun addCustomResolver(
        resolver: (message: SpiceMessage, error: SpiceError, nodeId: String?) -> ExecutionRetryPolicy?
    ): RetryPolicyResolverBuilder

    /**
     * Build the resolver
     */
    fun build(): RetryPolicyResolver
}

/**
 * Simple resolver that always returns the same policy
 */
internal class SimpleRetryPolicyResolver(
    private val policy: ExecutionRetryPolicy
) : RetryPolicyResolver {
    override fun resolve(message: SpiceMessage, error: SpiceError, nodeId: String?): ExecutionRetryPolicy = policy

    override fun resolveOrDefault(
        message: SpiceMessage,
        error: SpiceError,
        nodeId: String?,
        defaultPolicy: ExecutionRetryPolicy
    ): ExecutionRetryPolicy = defaultPolicy
}

/**
 * Default implementation of RetryPolicyResolverBuilder
 */
internal class DefaultRetryPolicyResolverBuilder : RetryPolicyResolverBuilder {

    private var defaultPolicy: ExecutionRetryPolicy = ExecutionRetryPolicy.DEFAULT
    private val tenantPolicies = mutableMapOf<String, () -> ExecutionRetryPolicy>()
    private val endpointPolicies = mutableMapOf<String, () -> ExecutionRetryPolicy>()
    private val errorCodePolicies = mutableMapOf<String, () -> ExecutionRetryPolicy>()
    private val customResolvers = mutableListOf<(SpiceMessage, SpiceError, String?) -> ExecutionRetryPolicy?>()

    override fun defaultPolicy(policy: ExecutionRetryPolicy): RetryPolicyResolverBuilder {
        this.defaultPolicy = policy
        return this
    }

    override fun forTenant(tenantId: String, policyProvider: () -> ExecutionRetryPolicy): RetryPolicyResolverBuilder {
        tenantPolicies[tenantId] = policyProvider
        return this
    }

    override fun forEndpoint(nodeId: String, policyProvider: () -> ExecutionRetryPolicy): RetryPolicyResolverBuilder {
        endpointPolicies[nodeId] = policyProvider
        return this
    }

    override fun forErrorCode(errorCode: String, policyProvider: () -> ExecutionRetryPolicy): RetryPolicyResolverBuilder {
        errorCodePolicies[errorCode] = policyProvider
        return this
    }

    override fun addCustomResolver(
        resolver: (message: SpiceMessage, error: SpiceError, nodeId: String?) -> ExecutionRetryPolicy?
    ): RetryPolicyResolverBuilder {
        customResolvers.add(resolver)
        return this
    }

    override fun build(): RetryPolicyResolver = ChainedRetryPolicyResolver(
        defaultPolicy = defaultPolicy,
        tenantPolicies = tenantPolicies.toMap(),
        endpointPolicies = endpointPolicies.toMap(),
        errorCodePolicies = errorCodePolicies.toMap(),
        customResolvers = customResolvers.toList()
    )
}

/**
 * Chained resolver implementation
 */
internal class ChainedRetryPolicyResolver(
    private val defaultPolicy: ExecutionRetryPolicy,
    private val tenantPolicies: Map<String, () -> ExecutionRetryPolicy>,
    private val endpointPolicies: Map<String, () -> ExecutionRetryPolicy>,
    private val errorCodePolicies: Map<String, () -> ExecutionRetryPolicy>,
    private val customResolvers: List<(SpiceMessage, SpiceError, String?) -> ExecutionRetryPolicy?>
) : RetryPolicyResolver {

    override fun resolve(
        message: SpiceMessage,
        error: SpiceError,
        nodeId: String?
    ): ExecutionRetryPolicy {
        return resolveOrDefault(message, error, nodeId, defaultPolicy)
    }

    override fun resolveOrDefault(
        message: SpiceMessage,
        error: SpiceError,
        nodeId: String?,
        defaultPolicy: ExecutionRetryPolicy
    ): ExecutionRetryPolicy {
        // 1. Check error-level hint (RetryableError.retryHint.maxAttempts)
        if (error is SpiceError.RetryableError) {
            error.retryHint?.let { hint ->
                if (hint.skipRetry) {
                    return ExecutionRetryPolicy.NO_RETRY
                }
                hint.maxAttempts?.let { maxAttempts ->
                    return defaultPolicy.copy(maxAttempts = maxAttempts)
                }
            }
        }

        // 2. Check custom resolvers (in order)
        for (resolver in customResolvers) {
            resolver(message, error, nodeId)?.let { return it }
        }

        // 3. Check error-code-specific policy
        errorCodePolicies[error.code]?.let { return it() }

        // 4. Check endpoint-specific policy
        nodeId?.let { endpointPolicies[it]?.let { provider -> return provider() } }

        // 5. Check tenant-specific policy
        val tenantId = message.getMetadata<String>("tenantId")
        tenantId?.let { tenantPolicies[it]?.let { provider -> return provider() } }

        // 6. Return provided default (not the builder's default)
        return defaultPolicy
    }
}

/**
 * Extension to create a resolver from a map of tenant policies
 */
fun Map<String, ExecutionRetryPolicy>.toRetryPolicyResolver(
    defaultPolicy: ExecutionRetryPolicy = ExecutionRetryPolicy.DEFAULT
): RetryPolicyResolver {
    val builder = RetryPolicyResolver.builder().defaultPolicy(defaultPolicy)
    forEach { (tenantId, policy) ->
        builder.forTenant(tenantId) { policy }
    }
    return builder.build()
}
