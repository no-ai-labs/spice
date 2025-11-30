package io.github.noailabs.spice.hitl.template

import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * HITL Template Registry
 *
 * Central registry for HITL templates with support for:
 * - Programmatic template registration
 * - External template loading via HitlTemplateLoader
 * - Tenant-specific overrides
 * - Template caching
 *
 * **Template Resolution Order:**
 * 1. Registered templates (programmatic)
 * 2. External loader (file/database)
 * 3. Tenant-specific override (if tenantId provided)
 *
 * **Usage:**
 * ```kotlin
 * // Global registry
 * HitlTemplateRegistry.global.register(myTemplate)
 *
 * // Resolve template
 * val template = HitlTemplateRegistry.global.resolve("confirm-order", tenantId = "acme")
 * ```
 *
 * **Cache Configuration:**
 *
 * The registry has a built-in cache (`enableCaching=true` by default).
 * When using [CachingHitlTemplateLoader], disable the built-in cache
 * to avoid double caching:
 *
 * ```kotlin
 * // Recommended: Use CachingHitlTemplateLoader with TTL control
 * val registry = HitlTemplateRegistry(
 *     loader = CachingHitlTemplateLoader(myLoader, ttl = 30.minutes),
 *     enableCaching = false  // Use CachingHitlTemplateLoader's cache only
 * )
 * ```
 *
 * @since Spice 1.3.5
 */
class HitlTemplateRegistry(
    private val loader: HitlTemplateLoader = NoOpHitlTemplateLoader,
    private val enableCaching: Boolean = true
) {
    // Programmatically registered templates
    private val registeredTemplates = ConcurrentHashMap<String, HitlTemplate>()

    // Tenant-specific template overrides
    private val tenantOverrides = ConcurrentHashMap<String, ConcurrentHashMap<String, HitlTemplate>>()

    // Loaded template cache
    private val loadedCache = ConcurrentHashMap<String, HitlTemplate?>()

    /**
     * Register a template programmatically
     *
     * @param template Template to register
     * @param tenantId Optional tenant ID for tenant-specific registration
     */
    fun register(template: HitlTemplate, tenantId: String? = null) {
        if (tenantId != null) {
            tenantOverrides
                .getOrPut(tenantId) { ConcurrentHashMap() }
                .put(template.id, template)
            logger.debug { "[HitlTemplateRegistry] Registered template '${template.id}' for tenant '$tenantId'" }
        } else {
            registeredTemplates[template.id] = template
            logger.debug { "[HitlTemplateRegistry] Registered template '${template.id}'" }
        }
    }

    /**
     * Register multiple templates
     *
     * @param templates Templates to register
     * @param tenantId Optional tenant ID for tenant-specific registration
     */
    fun registerAll(templates: List<HitlTemplate>, tenantId: String? = null) {
        templates.forEach { register(it, tenantId) }
    }

    /**
     * Resolve a template by ID
     *
     * Resolution order:
     * 1. Tenant-specific override (if tenantId provided)
     * 2. Global registered templates
     * 3. External loader
     *
     * @param id Template ID
     * @param tenantId Optional tenant ID for tenant-specific lookup
     * @return Resolved template or null if not found
     */
    fun resolve(id: String, tenantId: String? = null): HitlTemplate? {
        // 1. Check tenant-specific override
        if (tenantId != null) {
            tenantOverrides[tenantId]?.get(id)?.let {
                logger.debug { "[HitlTemplateRegistry] Resolved template '$id' from tenant '$tenantId' override" }
                return it
            }
        }

        // 2. Check registered templates
        registeredTemplates[id]?.let {
            logger.debug { "[HitlTemplateRegistry] Resolved template '$id' from registry" }
            return it
        }

        // 3. Check cache or load from external source
        val cacheKey = "${tenantId ?: "default"}:$id"
        if (enableCaching) {
            loadedCache[cacheKey]?.let {
                logger.debug { "[HitlTemplateRegistry] Resolved template '$id' from cache" }
                return it
            }
        }

        // Load from external loader
        val loaded = loader.load(id, tenantId)
        if (enableCaching) {
            loadedCache[cacheKey] = loaded
        }

        if (loaded != null) {
            logger.debug { "[HitlTemplateRegistry] Loaded template '$id' from external loader" }
        } else {
            logger.warn { "[HitlTemplateRegistry] Template '$id' not found" }
        }

        return loaded
    }

    /**
     * Resolve a template, throwing if not found
     *
     * @param id Template ID
     * @param tenantId Optional tenant ID
     * @return Resolved template
     * @throws IllegalArgumentException if template not found
     */
    fun resolveOrThrow(id: String, tenantId: String? = null): HitlTemplate {
        return resolve(id, tenantId)
            ?: throw IllegalArgumentException("Template '$id' not found" +
                (tenantId?.let { " for tenant '$it'" } ?: ""))
    }

    /**
     * Check if a template exists
     *
     * @param id Template ID
     * @param tenantId Optional tenant ID
     * @return true if template exists
     */
    fun exists(id: String, tenantId: String? = null): Boolean {
        if (tenantId != null && tenantOverrides[tenantId]?.containsKey(id) == true) {
            return true
        }
        if (registeredTemplates.containsKey(id)) {
            return true
        }
        return loader.exists(id, tenantId)
    }

    /**
     * List all available template IDs
     *
     * @param tenantId Optional tenant ID for filtering
     * @return Set of template IDs
     */
    fun listTemplateIds(tenantId: String? = null): Set<String> {
        val ids = mutableSetOf<String>()

        // Add registered templates
        ids.addAll(registeredTemplates.keys)

        // Add tenant-specific templates
        if (tenantId != null) {
            tenantOverrides[tenantId]?.keys?.let { ids.addAll(it) }
        }

        // Add loader templates
        ids.addAll(loader.listTemplateIds(tenantId))

        return ids
    }

    /**
     * Remove a registered template
     *
     * @param id Template ID
     * @param tenantId Optional tenant ID
     * @return true if template was removed
     */
    fun unregister(id: String, tenantId: String? = null): Boolean {
        return if (tenantId != null) {
            tenantOverrides[tenantId]?.remove(id) != null
        } else {
            registeredTemplates.remove(id) != null
        }
    }

    /**
     * Clear all registered templates and cache
     */
    fun clear() {
        registeredTemplates.clear()
        tenantOverrides.clear()
        loadedCache.clear()
        logger.debug { "[HitlTemplateRegistry] Cleared all templates and cache" }
    }

    /**
     * Clear only the loaded template cache
     */
    fun clearCache() {
        loadedCache.clear()
        logger.debug { "[HitlTemplateRegistry] Cleared template cache" }
    }

    /**
     * Get registry statistics
     */
    fun getStats(): RegistryStats = RegistryStats(
        registeredCount = registeredTemplates.size,
        tenantCount = tenantOverrides.size,
        tenantTemplateCount = tenantOverrides.values.sumOf { it.size },
        cacheSize = loadedCache.size,
        cachingEnabled = enableCaching
    )

    companion object {
        /**
         * Global singleton registry
         */
        val global = HitlTemplateRegistry()

        /**
         * Create a registry with an external loader
         */
        fun withLoader(loader: HitlTemplateLoader, enableCaching: Boolean = true) =
            HitlTemplateRegistry(loader, enableCaching)
    }
}

/**
 * Registry statistics
 */
data class RegistryStats(
    val registeredCount: Int,
    val tenantCount: Int,
    val tenantTemplateCount: Int,
    val cacheSize: Int,
    val cachingEnabled: Boolean
)

/**
 * DSL for building and registering templates
 *
 * ```kotlin
 * val registry = hitlTemplates {
 *     template("confirm-order") {
 *         kind = HitlTemplateKind.CONFIRM
 *         prompt = "주문을 확정하시겠습니까?"
 *         option("yes", "확정")
 *         option("no", "취소")
 *     }
 *
 *     template("select-room") {
 *         kind = HitlTemplateKind.SINGLE_SELECT
 *         prompt = "객실을 선택해주세요"
 *         option("standard", "스탠다드", "기본 객실")
 *         option("deluxe", "디럭스", "넓은 객실")
 *     }
 * }
 * ```
 */
fun hitlTemplates(block: HitlTemplateRegistryBuilder.() -> Unit): HitlTemplateRegistry {
    return HitlTemplateRegistryBuilder().apply(block).build()
}

/**
 * Template registry builder for DSL
 */
class HitlTemplateRegistryBuilder {
    private val templates = mutableListOf<HitlTemplate>()

    fun template(id: String, block: HitlTemplateBuilder.() -> Unit) {
        templates.add(HitlTemplateBuilder(id).apply(block).build())
    }

    internal fun build(): HitlTemplateRegistry {
        val registry = HitlTemplateRegistry()
        templates.forEach { registry.register(it) }
        return registry
    }
}

/**
 * Template builder for DSL
 */
class HitlTemplateBuilder(private val id: String) {
    var kind: HitlTemplateKind = HitlTemplateKind.TEXT
    var prompt: String = ""
    var flags: HitlTemplateFlags = HitlTemplateFlags.DEFAULT
    var quantityConfig: QuantityConfig? = null

    private val options = mutableListOf<HitlOption>()
    private val validationRules = mutableListOf<ValidationRule>()
    private val metadata = mutableMapOf<String, String>()

    fun option(id: String, label: String, description: String? = null) {
        options.add(HitlOption(id, label, description))
    }

    fun validation(type: String, value: String, message: String? = null) {
        validationRules.add(ValidationRule(type, value, message))
    }

    fun meta(key: String, value: String) {
        metadata[key] = value
    }

    internal fun build(): HitlTemplate {
        require(prompt.isNotBlank()) { "Template prompt cannot be blank" }

        return HitlTemplate(
            id = id,
            kind = kind,
            promptTemplate = prompt,
            options = options.takeIf { it.isNotEmpty() },
            flags = flags,
            quantityConfig = quantityConfig,
            validationRules = validationRules,
            metadata = metadata
        )
    }
}
