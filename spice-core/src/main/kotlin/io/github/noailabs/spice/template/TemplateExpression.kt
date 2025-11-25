package io.github.noailabs.spice.template

import io.github.noailabs.spice.SpiceMessage

/**
 * Template Expression Parser for Dynamic Value Resolution
 *
 * Supports JSONPath-style expressions for extracting values from SpiceMessage:
 * - `{{data.key}}` - Simple key access
 * - `{{data.nested.key}}` - Nested key access
 * - `{{data.items[0].name}}` - Array index access
 * - `{{data["foo.bar"]}}` - Quoted key with dots
 * - `{{data.count:int}}` - Type casting
 *
 * **Supported Types:**
 * - `string` (default)
 * - `int`, `integer`
 * - `long`
 * - `double`, `float`
 * - `bool`, `boolean`
 * - `any` (no casting)
 *
 * **Usage:**
 * ```kotlin
 * val expr = TemplateExpression.parse("{{data.listToolId}}")
 * val toolId = expr.resolve(message)  // returns "stayfolio_list_reservations"
 *
 * val numExpr = TemplateExpression.parse("{{data.pagination.limit:int}}")
 * val limit = numExpr.resolve(message)  // returns 10 as Int
 * ```
 *
 * @since 1.0.4
 */
sealed class TemplateExpression<T> {
    /**
     * Resolve the expression value from the given message
     *
     * @param message SpiceMessage to extract value from
     * @return Resolved value or null if path doesn't exist
     */
    abstract fun resolve(message: SpiceMessage): T?

    /**
     * Literal value (non-template string)
     */
    data class Literal<T>(val value: T) : TemplateExpression<T>() {
        override fun resolve(message: SpiceMessage): T = value
    }

    /**
     * Dynamic expression with path segments
     */
    data class Dynamic<T>(
        val expression: String,
        val segments: List<PathSegment>,
        val source: Source,
        val targetType: TargetType = TargetType.STRING
    ) : TemplateExpression<T>() {

        enum class Source { DATA, METADATA }

        enum class TargetType {
            STRING, INT, LONG, DOUBLE, BOOLEAN, ANY
        }

        @Suppress("UNCHECKED_CAST")
        override fun resolve(message: SpiceMessage): T? {
            val root: Any = when (source) {
                Source.DATA -> message.data
                Source.METADATA -> message.metadata
            }

            var current: Any? = root
            for (segment in segments) {
                current = segment.resolve(current) ?: return null
            }

            return castTo(current, targetType) as? T
        }

        private fun castTo(value: Any?, type: TargetType): Any? = when (type) {
            TargetType.STRING -> value?.toString()
            TargetType.INT -> (value as? Number)?.toInt()
                ?: value?.toString()?.toIntOrNull()
            TargetType.LONG -> (value as? Number)?.toLong()
                ?: value?.toString()?.toLongOrNull()
            TargetType.DOUBLE -> (value as? Number)?.toDouble()
                ?: value?.toString()?.toDoubleOrNull()
            TargetType.BOOLEAN -> value as? Boolean
                ?: value?.toString()?.toBooleanStrictOrNull()
            TargetType.ANY -> value
        }
    }

    /**
     * Path segment for navigating nested structures
     */
    sealed class PathSegment {
        /**
         * Resolve this segment from the current value
         *
         * @param current Current value in the path
         * @return Next value or null if not found
         */
        abstract fun resolve(current: Any?): Any?

        /**
         * Simple key access: `data.key`
         */
        data class Key(val key: String) : PathSegment() {
            override fun resolve(current: Any?): Any? =
                (current as? Map<*, *>)?.get(key)
        }

        /**
         * Array index access: `data.items[0]`
         */
        data class Index(val index: Int) : PathSegment() {
            override fun resolve(current: Any?): Any? = when (current) {
                is List<*> -> current.getOrNull(index)
                is Array<*> -> current.getOrNull(index)
                else -> null
            }
        }

        /**
         * Quoted key access: `data["foo.bar"]` or `data['foo.bar']`
         */
        data class QuotedKey(val key: String) : PathSegment() {
            override fun resolve(current: Any?): Any? =
                (current as? Map<*, *>)?.get(key)
        }
    }

    companion object {
        // Main pattern: {{source.path}} or {{source.path:type}} or {{source["key"]}} or {{source["key"]:type}}
        // Supports both dot notation and bracket notation directly after source
        private val TEMPLATE_PATTERN = Regex("""\{\{(data|metadata)([.\[].+?)(?::(\w+))?\}\}""")

        // Path segment patterns: key, [index], ["quoted.key"], ['quoted.key']
        private val SEGMENT_PATTERN = Regex("""(\w+)|\[(\d+)]|\["([^"]+)"]|\['([^']+)']""")

        /**
         * Parse a template expression string
         *
         * @param template Template string to parse
         * @return TemplateExpression (Literal if not a template, Dynamic if template)
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> parse(template: String): TemplateExpression<T> {
            val trimmed = template.trim()
            val match = TEMPLATE_PATTERN.matchEntire(trimmed)
                ?: return Literal(trimmed) as TemplateExpression<T>

            val sourceStr = match.groupValues[1]
            val pathStr = match.groupValues[2]
            val typeStr = match.groupValues[3].takeIf { it.isNotEmpty() }

            val source = if (sourceStr == "data") Dynamic.Source.DATA else Dynamic.Source.METADATA
            val segments = parseSegments(pathStr)
            val targetType = parseType(typeStr)

            return Dynamic(trimmed, segments, source, targetType)
        }

        /**
         * Parse a template string and return String expression
         */
        fun parseString(template: String): TemplateExpression<String> = parse(template)

        /**
         * Check if a string contains template expressions
         */
        fun isTemplate(value: String): Boolean = TEMPLATE_PATTERN.containsMatchIn(value)

        /**
         * Parse path string into segments
         */
        private fun parseSegments(path: String): List<PathSegment> {
            val segments = mutableListOf<PathSegment>()

            SEGMENT_PATTERN.findAll(path).forEach { match ->
                when {
                    // Simple key: word characters
                    match.groupValues[1].isNotEmpty() ->
                        segments.add(PathSegment.Key(match.groupValues[1]))
                    // Array index: [0]
                    match.groupValues[2].isNotEmpty() ->
                        segments.add(PathSegment.Index(match.groupValues[2].toInt()))
                    // Double-quoted key: ["foo.bar"]
                    match.groupValues[3].isNotEmpty() ->
                        segments.add(PathSegment.QuotedKey(match.groupValues[3]))
                    // Single-quoted key: ['foo.bar']
                    match.groupValues[4].isNotEmpty() ->
                        segments.add(PathSegment.QuotedKey(match.groupValues[4]))
                }
            }

            return segments
        }

        /**
         * Parse type string to TargetType
         */
        private fun parseType(type: String?): Dynamic.TargetType = when (type?.lowercase()) {
            "int", "integer" -> Dynamic.TargetType.INT
            "long" -> Dynamic.TargetType.LONG
            "double", "float" -> Dynamic.TargetType.DOUBLE
            "bool", "boolean" -> Dynamic.TargetType.BOOLEAN
            "any" -> Dynamic.TargetType.ANY
            else -> Dynamic.TargetType.STRING
        }
    }
}

/**
 * Extension function to resolve template from message
 */
fun SpiceMessage.resolveTemplate(template: String): String? {
    val expr = TemplateExpression.parseString(template)
    return expr.resolve(this)
}

/**
 * Extension function to resolve template with explicit type casting.
 *
 * The type parameter takes precedence over any type suffix in the template.
 * For example, `resolveTemplate("{{data.count}}", Int::class.java)` will
 * cast the result to Int, regardless of whether the template has `:int` suffix.
 *
 * **Note:** If the template already has a type suffix (e.g., `{{data.count:int}}`),
 * the explicit type parameter should match to avoid potential ClassCastException.
 *
 * @param template The template expression string
 * @param type The target type class for casting
 * @return The resolved and cast value, or null if not found or cast fails
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> SpiceMessage.resolveTemplate(template: String, type: Class<T>): T? {
    // Parse as ANY to get the raw value, then cast explicitly
    val expr = TemplateExpression.parse<Any>(template)
    val rawValue = expr.resolve(this) ?: return null

    // Cast based on the type parameter
    return when (type) {
        String::class.java -> rawValue.toString() as? T
        Int::class.java, java.lang.Integer::class.java -> {
            when (rawValue) {
                is Number -> rawValue.toInt() as? T
                is String -> rawValue.toIntOrNull() as? T
                else -> null
            }
        }
        Long::class.java, java.lang.Long::class.java -> {
            when (rawValue) {
                is Number -> rawValue.toLong() as? T
                is String -> rawValue.toLongOrNull() as? T
                else -> null
            }
        }
        Double::class.java, java.lang.Double::class.java -> {
            when (rawValue) {
                is Number -> rawValue.toDouble() as? T
                is String -> rawValue.toDoubleOrNull() as? T
                else -> null
            }
        }
        Boolean::class.java, java.lang.Boolean::class.java -> {
            when (rawValue) {
                is Boolean -> rawValue as? T
                is String -> rawValue.toBooleanStrictOrNull() as? T
                else -> null
            }
        }
        else -> {
            // For other types, try direct cast
            rawValue as? T
        }
    }
}
