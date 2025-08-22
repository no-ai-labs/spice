package io.github.noailabs.spice.platform

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.tenant.TenantContext
import io.github.noailabs.spice.tenant.TenantStore
import io.github.noailabs.spice.tenant.InMemoryTenantStore
import java.time.Instant

/**
 * 🏗️ Platform Information
 * 
 * Metadata about a platform
 */
data class PlatformInfo(
    val id: String,
    val name: String,
    val description: String,
    val type: PlatformType,
    val metadata: Map<String, Any> = emptyMap(),
    val capabilities: Set<String> = emptySet(),
    val status: PlatformStatus = PlatformStatus.ACTIVE,
    val registeredAt: Instant = Instant.now()
) {
    /**
     * Check if platform has capability
     */
    fun hasCapability(capability: String): Boolean = capabilities.contains(capability)
    
    /**
     * Get metadata value
     */
    fun <T> getMetadataAs(key: String): T? = metadata[key] as? T
}

/**
 * Platform Types
 */
enum class PlatformType {
    MARKETPLACE,      // E-commerce marketplace (Naver, Amazon)
    SOCIAL_COMMERCE,  // Social commerce (Instagram Shop)
    DIRECT_SALES,     // Direct sales platform (Own website)
    MESSAGING,        // Messaging platform (KakaoTalk, WhatsApp)
    CUSTOM           // Custom platform
}

/**
 * Platform Status
 */
enum class PlatformStatus {
    ACTIVE,          // Platform is active and available
    INACTIVE,        // Platform is temporarily inactive
    MAINTENANCE,     // Platform is under maintenance
    DEPRECATED,      // Platform is deprecated
    ERROR           // Platform has errors
}

/**
 * 🔧 Platform Configuration
 * 
 * Configuration for connecting to a platform
 */
data class PlatformConfig(
    val platformId: String,
    val credentials: Map<String, String> = emptyMap(),
    val endpoints: Map<String, String> = emptyMap(),
    val settings: Map<String, Any> = emptyMap(),
    val rateLimits: RateLimitConfig? = null
)

/**
 * Rate Limiting Configuration
 */
data class RateLimitConfig(
    val requestsPerMinute: Int,
    val requestsPerHour: Int? = null,
    val burstSize: Int = requestsPerMinute
)

/**
 * 🎯 Platform Manager Interface
 * 
 * Manages platform registration and discovery
 */
interface PlatformManager {
    /**
     * Register a new platform
     */
    suspend fun registerPlatform(platform: PlatformInfo)
    
    /**
     * Get platform by ID
     */
    suspend fun getPlatform(platformId: String): PlatformInfo?
    
    /**
     * List all platforms
     */
    suspend fun listPlatforms(
        type: PlatformType? = null,
        status: PlatformStatus? = null
    ): List<PlatformInfo>
    
    /**
     * Update platform status
     */
    suspend fun updatePlatformStatus(platformId: String, status: PlatformStatus)
    
    /**
     * Remove platform
     */
    suspend fun removePlatform(platformId: String)
    
    /**
     * Check if platform exists
     */
    suspend fun platformExists(platformId: String): Boolean
    
    /**
     * Get platform health status
     */
    suspend fun checkPlatformHealth(platformId: String): PlatformHealth
}

/**
 * Platform Health Status
 */
data class PlatformHealth(
    val platformId: String,
    val status: HealthStatus,
    val message: String? = null,
    val lastChecked: Instant = Instant.now(),
    val metrics: Map<String, Any> = emptyMap()
)

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}

/**
 * 🏢 Tenant Platform Manager
 * 
 * Manages platform configurations per tenant
 */
interface TenantPlatformManager {
    /**
     * Register platform for tenant
     */
    suspend fun registerPlatformForTenant(
        tenantId: String,
        platformId: String,
        config: PlatformConfig
    )
    
    /**
     * Get tenant's platform configuration
     */
    suspend fun getTenantPlatformConfig(
        tenantId: String,
        platformId: String
    ): PlatformConfig?
    
    /**
     * List platforms for tenant
     */
    suspend fun listTenantPlatforms(tenantId: String): List<String>
    
    /**
     * Remove platform from tenant
     */
    suspend fun removePlatformFromTenant(
        tenantId: String,
        platformId: String
    )
    
    /**
     * Check if tenant has platform
     */
    suspend fun tenantHasPlatform(
        tenantId: String,
        platformId: String
    ): Boolean
}

/**
 * 🛠️ Platform Tool Provider
 * 
 * Interface for platforms that provide tools
 */
interface PlatformToolProvider {
    /**
     * Platform ID
     */
    val platformId: String
    
    /**
     * Get tools provided by this platform
     */
    fun getTools(): List<Tool>
    
    /**
     * Get tool by name
     */
    fun getTool(name: String): Tool?
    
    /**
     * List tool names
     */
    fun listToolNames(): List<String>
}

/**
 * 📦 Default Platform Manager Implementation
 * 
 * In-memory implementation for development
 */
class DefaultPlatformManager(
    private val store: TenantStore<PlatformInfo> = InMemoryTenantStore()
) : PlatformManager {
    
    override suspend fun registerPlatform(platform: PlatformInfo) {
        store.save(platform.id, platform)
    }
    
    override suspend fun getPlatform(platformId: String): PlatformInfo? {
        return store.get(platformId)
    }
    
    override suspend fun listPlatforms(
        type: PlatformType?,
        status: PlatformStatus?
    ): List<PlatformInfo> {
        return store.list()
            .map { it.second }
            .filter { platform ->
                (type == null || platform.type == type) &&
                (status == null || platform.status == status)
            }
    }
    
    override suspend fun updatePlatformStatus(platformId: String, status: PlatformStatus) {
        val platform = getPlatform(platformId) ?: return
        store.save(platformId, platform.copy(status = status))
    }
    
    override suspend fun removePlatform(platformId: String) {
        store.delete(platformId)
    }
    
    override suspend fun platformExists(platformId: String): Boolean {
        return getPlatform(platformId) != null
    }
    
    override suspend fun checkPlatformHealth(platformId: String): PlatformHealth {
        val platform = getPlatform(platformId)
        return if (platform == null) {
            PlatformHealth(
                platformId = platformId,
                status = HealthStatus.UNKNOWN,
                message = "Platform not found"
            )
        } else {
            // Simple health check based on status
            when (platform.status) {
                PlatformStatus.ACTIVE -> PlatformHealth(
                    platformId = platformId,
                    status = HealthStatus.HEALTHY
                )
                PlatformStatus.MAINTENANCE -> PlatformHealth(
                    platformId = platformId,
                    status = HealthStatus.DEGRADED,
                    message = "Platform is under maintenance"
                )
                PlatformStatus.ERROR -> PlatformHealth(
                    platformId = platformId,
                    status = HealthStatus.UNHEALTHY,
                    message = "Platform has errors"
                )
                else -> PlatformHealth(
                    platformId = platformId,
                    status = HealthStatus.UNKNOWN
                )
            }
        }
    }
}

/**
 * 📦 Default Tenant Platform Manager Implementation
 */
class DefaultTenantPlatformManager(
    private val configStore: TenantStore<PlatformConfig> = InMemoryTenantStore(),
    private val platformManager: PlatformManager
) : TenantPlatformManager {
    
    private fun configKey(platformId: String): String = "platform:$platformId"
    
    override suspend fun registerPlatformForTenant(
        tenantId: String,
        platformId: String,
        config: PlatformConfig
    ) {
        // Verify platform exists
        if (!platformManager.platformExists(platformId)) {
            throw IllegalArgumentException("Platform $platformId does not exist")
        }
        
        configStore.save(configKey(platformId), config, tenantId)
    }
    
    override suspend fun getTenantPlatformConfig(
        tenantId: String,
        platformId: String
    ): PlatformConfig? {
        return configStore.get(configKey(platformId), tenantId)
    }
    
    override suspend fun listTenantPlatforms(tenantId: String): List<String> {
        return configStore.list(tenantId)
            .filter { it.first.startsWith("platform:") }
            .map { it.first.removePrefix("platform:") }
    }
    
    override suspend fun removePlatformFromTenant(
        tenantId: String,
        platformId: String
    ) {
        configStore.delete(configKey(platformId), tenantId)
    }
    
    override suspend fun tenantHasPlatform(
        tenantId: String,
        platformId: String
    ): Boolean {
        return getTenantPlatformConfig(tenantId, platformId) != null
    }
}