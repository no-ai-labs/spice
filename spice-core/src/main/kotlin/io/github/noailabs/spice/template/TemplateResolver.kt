package io.github.noailabs.spice.template

import io.github.noailabs.spice.SpiceMessage

/**
 * TemplateResolver for Subgraph inputMapping
 *
 * Resolves template expressions (e.g., `{{data.selectedBookingId}}`) against a SpiceMessage.
 * This interface allows external systems (kai-core, etc.) to provide custom template resolution
 * while Spice provides a default implementation.
 *
 * **YAML inputMapping Example:**
 * ```yaml
 * subgraph_call:
 *   subgraph: generic_confirmation
 *   inputMapping:
 *     # childKey: template/literal
 *     preselectedItemId: "{{data.selectedBookingId}}"
 *     confirmationType: "cancel"  # literal value
 *     locale: "{{metadata.locale}}"
 * ```
 *
 * **Resolution Process:**
 * 1. If value is a template (`{{...}}`), resolve against message
 * 2. If value is a literal, return as-is
 * 3. Null values are omitted from child context
 *
 * **Built-in Default Implementation:**
 * - Uses `TemplateExpression.parse()` for template syntax
 * - Supports `{{data.key}}`, `{{data.nested.key}}`, `{{data.items[0]}}`, `{{metadata.key}}`
 * - Type casting via suffix: `{{data.count:int}}`, `{{data.flag:bool}}`
 *
 * **Custom Implementation (kai-core):**
 * ```kotlin
 * class KaiTemplateResolver(private val workflowLoader: WorkflowLoader) : TemplateResolver {
 *     override fun resolve(template: Any, message: SpiceMessage): Any? {
 *         return when (template) {
 *             is String -> workflowLoader.resolveTemplate(template, message.data)
 *             else -> template
 *         }
 *     }
 * }
 * ```
 *
 * @since 1.2.0
 */
fun interface TemplateResolver {
    /**
     * Resolve a template value against the given message
     *
     * @param template The template value (may be a template string like `{{data.x}}` or a literal)
     * @param message The SpiceMessage to resolve against
     * @return Resolved value, or null if resolution fails or path doesn't exist
     */
    fun resolve(template: Any, message: SpiceMessage): Any?

    companion object {
        /**
         * Default TemplateResolver using Spice's TemplateExpression
         *
         * - Template strings (`{{...}}`) are resolved via TemplateExpression
         * - Literal values are returned as-is
         */
        val DEFAULT: TemplateResolver = TemplateResolver { template, message ->
            when (template) {
                is String -> {
                    if (TemplateExpression.isTemplate(template)) {
                        TemplateExpression.parse<Any>(template).resolve(message)
                    } else {
                        template
                    }
                }
                else -> template
            }
        }

        /**
         * No-op resolver that returns templates unchanged
         *
         * Useful for testing or when template resolution is not needed.
         */
        val IDENTITY: TemplateResolver = TemplateResolver { template, _ -> template }
    }
}

/**
 * Extension function to resolve an inputMapping against a message
 *
 * @param inputMapping Map of childKey to template/literal value
 * @param message SpiceMessage to resolve against
 * @return Map of childKey to resolved value (null values omitted)
 */
fun TemplateResolver.resolveInputMapping(
    inputMapping: Map<String, Any>,
    message: SpiceMessage
): Map<String, Any> = buildMap {
    inputMapping.forEach { (childKey, template) ->
        val resolved = resolve(template, message)
        if (resolved != null) {
            put(childKey, resolved)
        }
    }
}
