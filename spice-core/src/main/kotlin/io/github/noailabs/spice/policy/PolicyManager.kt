package io.github.noailabs.spice.policy

import io.github.noailabs.spice.tenant.TenantContext
import io.github.noailabs.spice.tenant.TenantStore
import io.github.noailabs.spice.tenant.InMemoryTenantStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 📋 Policy Definition
 * 
 * Base interface for all policies
 */
interface Policy {
    val id: String
    val name: String
    val description: String
    val metadata: Map<String, Any>
    
    /**
     * Validate policy structure
     */
    fun validate(): ValidationResult
}

/**
 * Policy Validation Result
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun valid(warnings: List<String> = emptyList()) = 
            ValidationResult(true, emptyList(), warnings)
        
        fun invalid(errors: List<String>, warnings: List<String> = emptyList()) = 
            ValidationResult(false, errors, warnings)
    }
}

/**
 * 📦 Versioned Policy
 * 
 * Policy with version information
 */
@Serializable
data class PolicyVersion(
    val policyId: String,
    val version: Int,
    val content: JsonElement,
    val metadata: JsonObject = JsonObject(emptyMap()),
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String,
    val comment: String? = null,
    val tags: List<String> = emptyList()
) {
    /**
     * Check if this version is newer than another
     */
    fun isNewerThan(other: PolicyVersion): Boolean = version > other.version
}

/**
 * 📋 Policy History Entry
 */
data class PolicyHistoryEntry(
    val version: Int,
    val createdAt: Instant,
    val createdBy: String,
    val comment: String?,
    val tags: List<String>
)

/**
 * 🎯 Policy Manager Interface
 * 
 * Manages policies with versioning support
 */
interface PolicyManager {
    /**
     * Save a new policy version
     */
    suspend fun savePolicy(
        policyId: String,
        content: JsonElement,
        metadata: Map<String, Any> = emptyMap(),
        createdBy: String,
        comment: String? = null,
        tags: List<String> = emptyList()
    ): PolicyVersion
    
    /**
     * Get current policy version
     */
    suspend fun getPolicy(policyId: String): PolicyVersion?
    
    /**
     * Get specific policy version
     */
    suspend fun getPolicy(policyId: String, version: Int): PolicyVersion?
    
    /**
     * Get policy history
     */
    suspend fun getPolicyHistory(policyId: String): List<PolicyHistoryEntry>
    
    /**
     * List all policy IDs
     */
    suspend fun listPolicies(): List<String>
    
    /**
     * Rollback to specific version
     */
    suspend fun rollbackPolicy(
        policyId: String,
        targetVersion: Int,
        rolledBackBy: String,
        comment: String? = null
    ): PolicyVersion
    
    /**
     * Delete policy (all versions)
     */
    suspend fun deletePolicy(policyId: String)
    
    /**
     * Compare two policy versions
     */
    suspend fun comparePolicies(
        policyId: String,
        version1: Int,
        version2: Int
    ): PolicyComparison?
    
    /**
     * Validate policy content
     */
    suspend fun validatePolicy(content: JsonElement): ValidationResult
}

/**
 * Policy Comparison Result
 */
data class PolicyComparison(
    val policyId: String,
    val version1: Int,
    val version2: Int,
    val differences: List<Difference>
) {
    data class Difference(
        val path: String,
        val type: DifferenceType,
        val oldValue: Any?,
        val newValue: Any?
    )
    
    enum class DifferenceType {
        ADDED, REMOVED, MODIFIED
    }
}

/**
 * 🏢 Tenant Policy Manager
 * 
 * Policy management with tenant isolation
 */
interface TenantPolicyManager : PolicyManager {
    /**
     * Get tenant-specific policy
     */
    suspend fun getTenantPolicy(tenantId: String, policyId: String): PolicyVersion?
    
    /**
     * Save tenant-specific policy
     */
    suspend fun saveTenantPolicy(
        tenantId: String,
        policyId: String,
        content: JsonElement,
        metadata: Map<String, Any> = emptyMap(),
        createdBy: String,
        comment: String? = null
    ): PolicyVersion
    
    /**
     * List policies for tenant
     */
    suspend fun listTenantPolicies(tenantId: String): List<String>
}

/**
 * 📦 Default Policy Manager Implementation
 * 
 * In-memory implementation with full versioning support
 */
class DefaultPolicyManager(
    private val store: TenantStore<PolicyVersion> = InMemoryTenantStore()
) : PolicyManager {
    
    // Version counters per policy
    private val versionCounters = ConcurrentHashMap<String, Int>()
    
    private fun versionKey(policyId: String, version: Int): String = 
        "$policyId:v$version"
    
    private fun currentVersionKey(policyId: String): String = 
        "$policyId:current"
    
    override suspend fun savePolicy(
        policyId: String,
        content: JsonElement,
        metadata: Map<String, Any>,
        createdBy: String,
        comment: String?,
        tags: List<String>
    ): PolicyVersion {
        // Validate policy first
        val validation = validatePolicy(content)
        if (!validation.isValid) {
            throw IllegalArgumentException(
                "Invalid policy: ${validation.errors.joinToString(", ")}"
            )
        }
        
        // Get next version number
        val nextVersion = versionCounters.compute(policyId) { _, current ->
            (current ?: 0) + 1
        } ?: 1
        
        // Create policy version
        val policyVersion = PolicyVersion(
            policyId = policyId,
            version = nextVersion,
            content = content,
            metadata = JsonObject(metadata.mapValues { JsonPrimitive(it.value.toString()) }),
            createdBy = createdBy,
            comment = comment,
            tags = tags
        )
        
        // Save version
        store.save(versionKey(policyId, nextVersion), policyVersion)
        
        // Update current version pointer
        store.save(currentVersionKey(policyId), policyVersion)
        
        return policyVersion
    }
    
    override suspend fun getPolicy(policyId: String): PolicyVersion? {
        return store.get(currentVersionKey(policyId))
    }
    
    override suspend fun getPolicy(policyId: String, version: Int): PolicyVersion? {
        return store.get(versionKey(policyId, version))
    }
    
    override suspend fun getPolicyHistory(policyId: String): List<PolicyHistoryEntry> {
        val currentVersion = versionCounters[policyId] ?: return emptyList()
        
        return (1..currentVersion).mapNotNull { version ->
            getPolicy(policyId, version)?.let { policy ->
                PolicyHistoryEntry(
                    version = policy.version,
                    createdAt = Instant.ofEpochMilli(policy.createdAt),
                    createdBy = policy.createdBy,
                    comment = policy.comment,
                    tags = policy.tags
                )
            }
        }.sortedByDescending { it.version }
    }
    
    override suspend fun listPolicies(): List<String> {
        return store.list()
            .map { it.first }
            .filter { it.endsWith(":current") }
            .map { it.removeSuffix(":current") }
            .distinct()
    }
    
    override suspend fun rollbackPolicy(
        policyId: String,
        targetVersion: Int,
        rolledBackBy: String,
        comment: String?
    ): PolicyVersion {
        // Get target version
        val targetPolicy = getPolicy(policyId, targetVersion)
            ?: throw IllegalArgumentException("Version $targetVersion not found for policy $policyId")
        
        // Create new version from target
        return savePolicy(
            policyId = policyId,
            content = targetPolicy.content,
            metadata = targetPolicy.metadata.toMap(),
            createdBy = rolledBackBy,
            comment = comment ?: "Rolled back to version $targetVersion",
            tags = listOf("rollback", "from-v$targetVersion")
        )
    }
    
    override suspend fun deletePolicy(policyId: String) {
        val currentVersion = versionCounters[policyId] ?: return
        
        // Delete all versions
        (1..currentVersion).forEach { version ->
            store.delete(versionKey(policyId, version))
        }
        
        // Delete current pointer
        store.delete(currentVersionKey(policyId))
        
        // Remove from version counter
        versionCounters.remove(policyId)
    }
    
    override suspend fun comparePolicies(
        policyId: String,
        version1: Int,
        version2: Int
    ): PolicyComparison? {
        val policy1 = getPolicy(policyId, version1) ?: return null
        val policy2 = getPolicy(policyId, version2) ?: return null
        
        // Simple comparison - in real implementation, deep diff would be better
        val differences = mutableListOf<PolicyComparison.Difference>()
        
        if (policy1.content != policy2.content) {
            differences.add(
                PolicyComparison.Difference(
                    path = "/",
                    type = PolicyComparison.DifferenceType.MODIFIED,
                    oldValue = policy1.content.toString(),
                    newValue = policy2.content.toString()
                )
            )
        }
        
        return PolicyComparison(policyId, version1, version2, differences)
    }
    
    override suspend fun validatePolicy(content: JsonElement): ValidationResult {
        // Basic validation - check if it's a valid JSON object
        return when (content) {
            is JsonObject -> {
                if (content.isEmpty()) {
                    ValidationResult.invalid(listOf("Policy content cannot be empty"))
                } else {
                    ValidationResult.valid()
                }
            }
            else -> ValidationResult.invalid(listOf("Policy must be a JSON object"))
        }
    }
}

/**
 * 📦 Default Tenant Policy Manager Implementation
 */
class DefaultTenantPolicyManager(
    private val baseManager: PolicyManager = DefaultPolicyManager()
) : TenantPolicyManager, PolicyManager by baseManager {
    
    private fun tenantPolicyId(tenantId: String, policyId: String): String = 
        "$tenantId::$policyId"
    
    override suspend fun getTenantPolicy(
        tenantId: String, 
        policyId: String
    ): PolicyVersion? {
        return getPolicy(tenantPolicyId(tenantId, policyId))
    }
    
    override suspend fun saveTenantPolicy(
        tenantId: String,
        policyId: String,
        content: JsonElement,
        metadata: Map<String, Any>,
        createdBy: String,
        comment: String?
    ): PolicyVersion {
        return savePolicy(
            policyId = tenantPolicyId(tenantId, policyId),
            content = content,
            metadata = metadata + ("tenantId" to tenantId),
            createdBy = createdBy,
            comment = comment
        )
    }
    
    override suspend fun listTenantPolicies(tenantId: String): List<String> {
        val prefix = "$tenantId::"
        return listPolicies()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }
}