package io.github.noailabs.spice.hitl.template

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * HITL Template Engine
 *
 * Lightweight Handlebars-compatible template engine for rendering HITL prompts.
 * Provides a minimal subset of Handlebars features sufficient for HITL use cases.
 *
 * **Supported Syntax:**
 * - Variable interpolation: `{{variable}}`
 * - Nested paths: `{{user.name}}`
 * - Default values: `{{variable | default:"N/A"}}`
 * - Helpers: `{{helperName args}}`
 *
 * **Built-in Helpers:**
 * - `{{formatCurrency amount}}` - Format as currency
 * - `{{formatNumber value}}` - Format number with locale
 * - `{{formatDate date format}}` - Format date
 * - `{{plural count singular plural}}` - Pluralization
 *
 * **Block Helpers:**
 * - `{{#if condition}}...{{else}}...{{/if}}` - Conditionals
 * - `{{#each items}}{{this}}{{/each}}` - Iteration ({{this}} for current item, {{@index}} for index)
 * - `{{#unless condition}}...{{/unless}}` - Inverted conditional
 *
 * **Performance:**
 * - Templates are compiled and cached using ConcurrentHashMap
 * - Thread-safe for concurrent access
 *
 * @since Spice 1.3.5
 */
class HitlTemplateEngine(
    private val locale: Locale = Locale.getDefault(),
    private val cacheEnabled: Boolean = true
) {
    // Compiled template cache
    private val templateCache = ConcurrentHashMap<String, CompiledTemplate>()

    // Registered custom helpers
    private val helpers = ConcurrentHashMap<String, TemplateHelper>()

    init {
        // Register built-in helpers
        registerBuiltinHelpers()
    }

    /**
     * Render a template with the given context
     *
     * @param template Template string with Handlebars syntax
     * @param context Data context for rendering
     * @return Rendered string
     */
    fun render(template: String, context: Map<String, Any?>): String {
        val compiled = if (cacheEnabled) {
            templateCache.getOrPut(template) { compile(template) }
        } else {
            compile(template)
        }

        return compiled.render(context, helpers, locale)
    }

    /**
     * Register a custom helper
     *
     * @param name Helper name (used as {{name ...}})
     * @param helper Helper function
     */
    fun registerHelper(name: String, helper: TemplateHelper) {
        helpers[name] = helper
    }

    /**
     * Clear the template cache
     */
    fun clearCache() {
        templateCache.clear()
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats = CacheStats(
        size = templateCache.size,
        enabled = cacheEnabled
    )

    // ====================================================================
    // Private Implementation - Block-aware Template Parser
    // ====================================================================

    private fun compile(template: String): CompiledTemplate {
        val tokens = tokenize(template)
        val nodes = parseTokens(tokens, null)
        return CompiledTemplate(nodes)
    }

    /**
     * Tokenize template into a list of tokens
     */
    private fun tokenize(template: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val pattern = Regex("""\{\{([^}]+)}}""")
        var lastIndex = 0

        pattern.findAll(template).forEach { match ->
            // Add literal text before this match
            if (match.range.first > lastIndex) {
                tokens.add(Token.Literal(template.substring(lastIndex, match.range.first)))
            }

            val expression = match.groupValues[1].trim()
            tokens.add(classifyToken(expression))
            lastIndex = match.range.last + 1
        }

        // Add any remaining literal text
        if (lastIndex < template.length) {
            tokens.add(Token.Literal(template.substring(lastIndex)))
        }

        return tokens
    }

    /**
     * Classify a token based on its expression
     */
    private fun classifyToken(expression: String): Token {
        return when {
            expression.startsWith("#") -> {
                val parts = expression.substring(1).split(Regex("\\s+"), limit = 2)
                Token.BlockStart(parts[0], parts.getOrNull(1)?.trim() ?: "")
            }
            expression.startsWith("/") -> {
                Token.BlockEnd(expression.substring(1).trim())
            }
            expression == "else" -> Token.Else
            expression.contains("|") -> {
                val pipeParts = expression.split("|", limit = 2)
                Token.FilteredVariable(pipeParts[0].trim(), pipeParts[1].trim())
            }
            else -> {
                val parts = expression.split(Regex("\\s+"), limit = 2)
                if (parts.size > 1 && parts[0] in BUILTIN_HELPERS) {
                    Token.Helper(parts[0], parts[1])
                } else {
                    Token.Variable(expression)
                }
            }
        }
    }

    /**
     * Parse tokens into a tree of nodes, handling nested blocks
     */
    private fun parseTokens(tokens: List<Token>, untilBlock: String?): List<TemplateNode> {
        val nodes = mutableListOf<TemplateNode>()
        val iterator = tokens.listIterator()

        while (iterator.hasNext()) {
            when (val token = iterator.next()) {
                is Token.Literal -> nodes.add(LiteralNode(token.text))
                is Token.Variable -> nodes.add(VariableNode(token.path))
                is Token.FilteredVariable -> nodes.add(FilteredVariableNode(token.path, token.filter))
                is Token.Helper -> nodes.add(HelperNode(token.name, token.args))
                is Token.BlockStart -> {
                    // Collect tokens until matching end block
                    val (body, elseBody) = collectBlockContent(iterator, token.name)
                    nodes.add(createBlockNode(token.name, token.args, body, elseBody))
                }
                is Token.BlockEnd -> {
                    if (token.name == untilBlock) {
                        // Move back so caller can see the end token
                        iterator.previous()
                        return nodes
                    } else {
                        throw TemplateParseException("Unexpected {{/${token.name}}}, expected {{/$untilBlock}}")
                    }
                }
                is Token.Else -> {
                    // Else should be handled within collectBlockContent
                    throw TemplateParseException("Unexpected {{else}} outside of block")
                }
            }
        }

        if (untilBlock != null) {
            throw TemplateParseException("Unclosed block {{#$untilBlock}}")
        }

        return nodes
    }

    /**
     * Collect tokens for a block's body and optional else body
     *
     * Handles nested blocks correctly by tracking depth for ALL BlockEnd tokens,
     * not just those matching the outer block name.
     */
    private fun collectBlockContent(
        iterator: ListIterator<Token>,
        blockName: String
    ): Pair<List<Token>, List<Token>?> {
        val bodyTokens = mutableListOf<Token>()
        var elseTokens: MutableList<Token>? = null
        var currentList = bodyTokens
        var depth = 1

        while (iterator.hasNext() && depth > 0) {
            val token = iterator.next()

            when {
                token is Token.BlockStart -> {
                    depth++
                    currentList.add(token)
                }
                token is Token.BlockEnd -> {
                    // ALL BlockEnd tokens decrease depth (handles nested blocks)
                    depth--
                    if (depth > 0) {
                        // Still inside a nested block, add token to body/else
                        currentList.add(token)
                    }
                    // When depth == 0, we've found the matching end for our block
                }
                token is Token.Else && depth == 1 -> {
                    elseTokens = mutableListOf()
                    currentList = elseTokens
                }
                else -> {
                    currentList.add(token)
                }
            }
        }

        if (depth > 0) {
            throw TemplateParseException("Unclosed block {{#$blockName}}")
        }

        return Pair(bodyTokens, elseTokens)
    }

    /**
     * Create appropriate block node based on block type
     */
    private fun createBlockNode(
        name: String,
        args: String,
        bodyTokens: List<Token>,
        elseTokens: List<Token>?
    ): TemplateNode {
        val bodyNodes = parseTokens(bodyTokens, null)
        val elseNodes = elseTokens?.let { parseTokens(it, null) }

        return when (name) {
            "if" -> IfBlockNode(args, bodyNodes, elseNodes)
            "unless" -> UnlessBlockNode(args, bodyNodes, elseNodes)
            "each" -> EachBlockNode(args, bodyNodes, elseNodes)
            "with" -> WithBlockNode(args, bodyNodes, elseNodes)
            else -> throw TemplateParseException("Unsupported block helper: {{#$name}}. Supported: if, unless, each, with")
        }
    }

    private fun registerBuiltinHelpers() {
        // Currency formatting
        helpers["formatCurrency"] = TemplateHelper { args, context, locale ->
            val value = resolveValue(args.trim(), context)
            when (value) {
                is Number -> {
                    val formatter = NumberFormat.getCurrencyInstance(locale)
                    formatter.format(value)
                }
                else -> value?.toString() ?: ""
            }
        }

        // Number formatting
        helpers["formatNumber"] = TemplateHelper { args, context, locale ->
            val value = resolveValue(args.trim(), context)
            when (value) {
                is Number -> {
                    val formatter = NumberFormat.getNumberInstance(locale)
                    formatter.format(value)
                }
                else -> value?.toString() ?: ""
            }
        }

        // Date formatting
        helpers["formatDate"] = TemplateHelper { args, context, _ ->
            val parts = args.split(Regex("\\s+"), limit = 2)
            val value = resolveValue(parts[0].trim(), context)
            val format = parts.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: "yyyy-MM-dd"

            when (value) {
                is LocalDate -> value.format(DateTimeFormatter.ofPattern(format))
                is LocalDateTime -> value.format(DateTimeFormatter.ofPattern(format))
                is String -> value // Already formatted
                else -> value?.toString() ?: ""
            }
        }

        // Pluralization
        helpers["plural"] = TemplateHelper { args, context, _ ->
            val parts = args.split(Regex("\\s+"))
            if (parts.size < 3) return@TemplateHelper args

            val countValue = resolveValue(parts[0].trim(), context)
            val count = (countValue as? Number)?.toInt() ?: 1
            val singular = parts[1].removeSurrounding("\"")
            val plural = parts[2].removeSurrounding("\"")

            if (count == 1) singular else plural
        }

        // Uppercase
        helpers["uppercase"] = TemplateHelper { args, context, _ ->
            val value = resolveValue(args.trim(), context)
            value?.toString()?.uppercase() ?: ""
        }

        // Lowercase
        helpers["lowercase"] = TemplateHelper { args, context, _ ->
            val value = resolveValue(args.trim(), context)
            value?.toString()?.lowercase() ?: ""
        }
    }

    companion object {
        /**
         * Default engine instance with standard configuration
         */
        val default = HitlTemplateEngine()

        /**
         * Built-in helper names for token classification
         */
        private val BUILTIN_HELPERS = setOf(
            "formatCurrency", "formatNumber", "formatDate", "plural",
            "uppercase", "lowercase"
        )

        /**
         * Create an engine with a specific locale
         */
        fun withLocale(locale: Locale) = HitlTemplateEngine(locale = locale)

        /**
         * Resolve a value from context using dot-notation path
         */
        fun resolveValue(path: String, context: Map<String, Any?>): Any? {
            // Handle special paths
            if (path == "this" || path == ".") {
                return context["this"] ?: context
            }
            if (path == "@index") {
                return context["@index"]
            }
            if (path == "@first") {
                return context["@first"]
            }
            if (path == "@last") {
                return context["@last"]
            }

            val parts = path.split(".")
            var current: Any? = context

            for (part in parts) {
                current = when (current) {
                    is Map<*, *> -> current[part]
                    else -> return null
                }
            }

            return current
        }

        /**
         * Check if a value is "truthy" in Handlebars terms
         */
        fun isTruthy(value: Any?): Boolean = when (value) {
            null -> false
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.isNotEmpty()
            is Collection<*> -> value.isNotEmpty()
            is Array<*> -> value.isNotEmpty()
            else -> true
        }
    }
}

// ====================================================================
// Token Types
// ====================================================================

private sealed interface Token {
    data class Literal(val text: String) : Token
    data class Variable(val path: String) : Token
    data class FilteredVariable(val path: String, val filter: String) : Token
    data class Helper(val name: String, val args: String) : Token
    data class BlockStart(val name: String, val args: String) : Token
    data class BlockEnd(val name: String) : Token
    data object Else : Token
}

// ====================================================================
// Node Types (AST)
// ====================================================================

private sealed interface TemplateNode {
    fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String
}

private class LiteralNode(private val text: String) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale) = text
}

private class VariableNode(private val path: String) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        val value = HitlTemplateEngine.resolveValue(path, context)
        return value?.toString() ?: ""
    }
}

private class FilteredVariableNode(
    private val path: String,
    private val filter: String
) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        val value = HitlTemplateEngine.resolveValue(path, context)

        // Handle default filter
        if (filter.startsWith("default:")) {
            val defaultValue = filter.substring(8).removeSurrounding("\"")
            return value?.toString() ?: defaultValue
        }

        return value?.toString() ?: ""
    }
}

private class HelperNode(
    private val helperName: String,
    private val args: String
) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        val helper = helpers[helperName] ?: return "{{$helperName $args}}"
        return helper.invoke(args, context, locale)
    }
}

/**
 * {{#if condition}}...{{else}}...{{/if}}
 */
private class IfBlockNode(
    private val condition: String,
    private val bodyNodes: List<TemplateNode>,
    private val elseNodes: List<TemplateNode>?
) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        val conditionValue = HitlTemplateEngine.resolveValue(condition, context)
        val isTruthy = HitlTemplateEngine.isTruthy(conditionValue)

        val nodesToRender = if (isTruthy) bodyNodes else (elseNodes ?: emptyList())
        return nodesToRender.joinToString("") { it.render(context, helpers, locale) }
    }
}

/**
 * {{#unless condition}}...{{else}}...{{/unless}}
 */
private class UnlessBlockNode(
    private val condition: String,
    private val bodyNodes: List<TemplateNode>,
    private val elseNodes: List<TemplateNode>?
) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        val conditionValue = HitlTemplateEngine.resolveValue(condition, context)
        val isTruthy = HitlTemplateEngine.isTruthy(conditionValue)

        // unless is inverted if
        val nodesToRender = if (!isTruthy) bodyNodes else (elseNodes ?: emptyList())
        return nodesToRender.joinToString("") { it.render(context, helpers, locale) }
    }
}

/**
 * {{#each items}}...{{/each}}
 * Inside the block:
 * - {{this}} or {{.}} refers to current item
 * - {{@index}} is the 0-based index
 * - {{@first}} is true for first item
 * - {{@last}} is true for last item
 */
private class EachBlockNode(
    private val itemsPath: String,
    private val bodyNodes: List<TemplateNode>,
    private val elseNodes: List<TemplateNode>?
) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        val itemsValue = HitlTemplateEngine.resolveValue(itemsPath, context)

        val items: List<Any?> = when (itemsValue) {
            is List<*> -> itemsValue
            is Array<*> -> itemsValue.toList()
            is Iterable<*> -> itemsValue.toList()
            null -> emptyList()
            else -> listOf(itemsValue)
        }

        if (items.isEmpty()) {
            // Render else block if no items
            return elseNodes?.joinToString("") { it.render(context, helpers, locale) } ?: ""
        }

        return buildString {
            items.forEachIndexed { index, item ->
                // Create iteration context
                val iterContext = context.toMutableMap()

                // Add item to context
                when (item) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        iterContext.putAll(item as Map<String, Any?>)
                        iterContext["this"] = item
                    }
                    else -> {
                        iterContext["this"] = item
                    }
                }

                // Add iteration metadata
                iterContext["@index"] = index
                iterContext["@first"] = index == 0
                iterContext["@last"] = index == items.size - 1

                // Render body for this item
                bodyNodes.forEach { node ->
                    append(node.render(iterContext, helpers, locale))
                }
            }
        }
    }
}

/**
 * {{#with object}}...{{/with}}
 * Changes the context to the specified object
 */
private class WithBlockNode(
    private val objectPath: String,
    private val bodyNodes: List<TemplateNode>,
    private val elseNodes: List<TemplateNode>?
) : TemplateNode {
    override fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        val objectValue = HitlTemplateEngine.resolveValue(objectPath, context)

        if (objectValue == null || !HitlTemplateEngine.isTruthy(objectValue)) {
            return elseNodes?.joinToString("") { it.render(context, helpers, locale) } ?: ""
        }

        // Create new context with object properties
        val newContext = when (objectValue) {
            is Map<*, *> -> {
                val merged = context.toMutableMap()
                @Suppress("UNCHECKED_CAST")
                merged.putAll(objectValue as Map<String, Any?>)
                merged["this"] = objectValue
                merged
            }
            else -> {
                val merged = context.toMutableMap()
                merged["this"] = objectValue
                merged
            }
        }

        return bodyNodes.joinToString("") { it.render(newContext, helpers, locale) }
    }
}

/**
 * Compiled template representation
 */
private class CompiledTemplate(private val nodes: List<TemplateNode>) {
    fun render(context: Map<String, Any?>, helpers: Map<String, TemplateHelper>, locale: Locale): String {
        return nodes.joinToString("") { it.render(context, helpers, locale) }
    }
}

/**
 * Template helper function interface
 */
fun interface TemplateHelper {
    fun invoke(args: String, context: Map<String, Any?>, locale: Locale): String
}

/**
 * Cache statistics
 */
data class CacheStats(
    val size: Int,
    val enabled: Boolean
)

/**
 * Template parsing exception
 */
class TemplateParseException(message: String) : RuntimeException(message)
