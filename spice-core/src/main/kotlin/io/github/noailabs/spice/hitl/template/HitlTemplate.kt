package io.github.noailabs.spice.hitl.template

import io.github.noailabs.spice.hitl.result.HITLMetadata
import kotlinx.serialization.Serializable

/**
 * HITL Template
 *
 * Defines a reusable HITL interaction pattern with Handlebars-based prompt templating.
 * Templates can be loaded from files, databases, or registered programmatically.
 *
 * **Usage Example:**
 * ```kotlin
 * val template = HitlTemplate(
 *     id = "confirm-order",
 *     kind = HitlTemplateKind.CONFIRM,
 *     promptTemplate = "주문을 확정하시겠습니까? {{itemCount}}개 상품, 총 {{totalPrice}}원",
 *     options = HitlOption.yesNo("confirm_yes", "확정", "confirm_no", "취소")
 * )
 *
 * // Bind data to create HITLMetadata
 * val metadata = template.bind(mapOf(
 *     "itemCount" to 3,
 *     "totalPrice" to 45000
 * ))
 * ```
 *
 * **Template Resolution:**
 * Templates are resolved using the following priority:
 * 1. Tenant-specific override: `{tenantId}/{templateId}.hbs`
 * 2. Default template: `templates/hitl/{templateId}.hbs`
 * 3. Programmatic registration via HitlTemplateRegistry
 *
 * @property id Unique template identifier
 * @property kind Type of HITL interaction
 * @property promptTemplate Handlebars template for the prompt
 * @property options Selection options (for SELECT types)
 * @property flags Configuration flags
 * @property quantityConfig Configuration for QUANTITY types
 * @property validationRules Validation rules for input
 * @property metadata Additional template metadata
 *
 * @since Spice 1.3.5
 */
@Serializable
data class HitlTemplate(
    val id: String,
    val kind: HitlTemplateKind,
    val promptTemplate: String,
    val options: List<HitlOption>? = null,
    val flags: HitlTemplateFlags = HitlTemplateFlags.DEFAULT,
    val quantityConfig: QuantityConfig? = null,
    val validationRules: List<ValidationRule> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "Template id cannot be blank" }
        require(promptTemplate.isNotBlank()) { "Template promptTemplate cannot be blank" }

        // Validate options for selection types
        when (kind) {
            HitlTemplateKind.SINGLE_SELECT,
            HitlTemplateKind.MULTI_SELECT -> {
                require(!options.isNullOrEmpty()) {
                    "Options are required for $kind templates"
                }
            }
            HitlTemplateKind.CONFIRM -> {
                // Default to Yes/No if no options provided
            }
            HitlTemplateKind.QUANTITY,
            HitlTemplateKind.MULTI_QUANTITY -> {
                // quantityConfig is optional, defaults will be used
            }
            else -> {
                // No specific validation for TEXT, INFO, ESCALATE
            }
        }
    }

    /**
     * Bind context data to this template
     *
     * This method renders the promptTemplate with the provided context
     * and returns a HITLMetadata ready for use.
     *
     * @param context Data to bind to the template
     * @param toolCallId Tool call ID for this HITL request
     * @param runId Graph run ID for checkpoint correlation
     * @param nodeId Node ID that initiated this request
     * @param graphId Optional graph ID for context
     * @param engine Template engine to use (default: HitlTemplateEngine.default)
     * @return HITLMetadata with rendered prompt
     */
    fun bind(
        context: Map<String, Any>,
        toolCallId: String,
        runId: String,
        nodeId: String,
        graphId: String? = null,
        engine: HitlTemplateEngine = HitlTemplateEngine.default
    ): HITLMetadata {
        val renderedPrompt = engine.render(promptTemplate, context)

        // Convert options to HITLOption format with full metadata (icon, metadata)
        val hitlOptions = resolveOptions().map { opt ->
            io.github.noailabs.spice.hitl.result.HITLOption(
                id = opt.id,
                label = opt.label,
                description = opt.description,
                icon = opt.icon,
                metadata = opt.metadata
            )
        }

        // Build additional metadata including flags and quantityConfig
        val additionalMeta = buildMap<String, Any> {
            // Include template metadata
            putAll(metadata)

            // Include flags
            put("flags", mapOf(
                "required" to flags.required,
                "autoProceed" to flags.autoProceed,
                "allowSkip" to flags.allowSkip,
                "retryCount" to flags.retryCount
            ).let { flagsMap ->
                flags.timeout?.let { flagsMap + ("timeout" to it) } ?: flagsMap
            })

            // Include quantityConfig if present
            quantityConfig?.let { config ->
                put("quantityConfig", mapOf(
                    "min" to config.min,
                    "max" to config.max,
                    "default" to config.defaultValue,
                    "step" to config.step
                ))
            }

            // Include template ID and kind for reference
            put("templateId", id)
            put("templateKind", kind.name)
        }

        return HITLMetadata(
            toolCallId = toolCallId,
            prompt = renderedPrompt,
            hitlType = mapKindToHitlType(),
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            options = hitlOptions,
            timeout = flags.timeout,
            validationRules = validationRules.associate { it.type to it.value },
            additionalMetadata = additionalMeta
        )
    }

    /**
     * Resolve options, providing defaults for CONFIRM type
     */
    private fun resolveOptions(): List<HitlOption> {
        return when {
            !options.isNullOrEmpty() -> options
            kind == HitlTemplateKind.CONFIRM -> HitlOption.yesNo()
            else -> emptyList()
        }
    }

    /**
     * Map HitlTemplateKind to HITLMetadata.hitlType string
     */
    private fun mapKindToHitlType(): String = when (kind) {
        HitlTemplateKind.TEXT -> "input"
        HitlTemplateKind.SINGLE_SELECT -> "selection"
        HitlTemplateKind.MULTI_SELECT -> "multi_selection"
        HitlTemplateKind.QUANTITY -> "quantity"
        HitlTemplateKind.MULTI_QUANTITY -> "multi_quantity"
        HitlTemplateKind.CONFIRM -> "confirmation"
        HitlTemplateKind.INFO -> "info"
        HitlTemplateKind.ESCALATE -> "escalate"
    }

    companion object {
        /**
         * Create a simple text input template
         */
        fun text(id: String, prompt: String, flags: HitlTemplateFlags = HitlTemplateFlags.DEFAULT) =
            HitlTemplate(
                id = id,
                kind = HitlTemplateKind.TEXT,
                promptTemplate = prompt,
                flags = flags
            )

        /**
         * Create a single selection template
         */
        fun singleSelect(
            id: String,
            prompt: String,
            options: List<HitlOption>,
            flags: HitlTemplateFlags = HitlTemplateFlags.DEFAULT
        ) = HitlTemplate(
            id = id,
            kind = HitlTemplateKind.SINGLE_SELECT,
            promptTemplate = prompt,
            options = options,
            flags = flags
        )

        /**
         * Create a multi selection template
         */
        fun multiSelect(
            id: String,
            prompt: String,
            options: List<HitlOption>,
            flags: HitlTemplateFlags = HitlTemplateFlags.DEFAULT
        ) = HitlTemplate(
            id = id,
            kind = HitlTemplateKind.MULTI_SELECT,
            promptTemplate = prompt,
            options = options,
            flags = flags
        )

        /**
         * Create a confirmation template
         */
        fun confirm(
            id: String,
            prompt: String,
            options: List<HitlOption>? = null,
            flags: HitlTemplateFlags = HitlTemplateFlags.DEFAULT
        ) = HitlTemplate(
            id = id,
            kind = HitlTemplateKind.CONFIRM,
            promptTemplate = prompt,
            options = options ?: HitlOption.yesNo(),
            flags = flags
        )

        /**
         * Create a quantity selection template
         */
        fun quantity(
            id: String,
            prompt: String,
            config: QuantityConfig = QuantityConfig.DEFAULT,
            flags: HitlTemplateFlags = HitlTemplateFlags.DEFAULT
        ) = HitlTemplate(
            id = id,
            kind = HitlTemplateKind.QUANTITY,
            promptTemplate = prompt,
            quantityConfig = config,
            flags = flags
        )

        /**
         * Create an info display template
         */
        fun info(id: String, prompt: String) = HitlTemplate(
            id = id,
            kind = HitlTemplateKind.INFO,
            promptTemplate = prompt,
            flags = HitlTemplateFlags(autoProceed = true)
        )

        /**
         * Create an escalation template
         */
        fun escalate(id: String, prompt: String) = HitlTemplate(
            id = id,
            kind = HitlTemplateKind.ESCALATE,
            promptTemplate = prompt
        )
    }
}

/**
 * HITL Template Loader SPI
 *
 * Service Provider Interface for loading HITL templates from external sources.
 * Implementations can load from files, databases, remote services, etc.
 *
 * **Resolution Priority:**
 * 1. Tenant-specific: `{tenantId}/{templateId}` (if tenantId provided)
 * 2. Default: `{templateId}`
 *
 * @since Spice 1.3.5
 */
interface HitlTemplateLoader {

    /**
     * Load a template by ID
     *
     * @param id Template identifier
     * @param tenantId Optional tenant ID for multi-tenant support
     * @return HitlTemplate or null if not found
     */
    fun load(id: String, tenantId: String? = null): HitlTemplate?

    /**
     * Check if a template exists
     *
     * @param id Template identifier
     * @param tenantId Optional tenant ID
     * @return true if template exists
     */
    fun exists(id: String, tenantId: String? = null): Boolean = load(id, tenantId) != null

    /**
     * List available template IDs
     *
     * @param tenantId Optional tenant ID for filtering
     * @return List of template IDs
     */
    fun listTemplateIds(tenantId: String? = null): List<String> = emptyList()
}

/**
 * No-Op Template Loader
 *
 * Default implementation that returns null for all lookups.
 * Used when no external template loading is configured.
 */
object NoOpHitlTemplateLoader : HitlTemplateLoader {
    override fun load(id: String, tenantId: String?): HitlTemplate? = null
    override fun listTemplateIds(tenantId: String?): List<String> = emptyList()
}
