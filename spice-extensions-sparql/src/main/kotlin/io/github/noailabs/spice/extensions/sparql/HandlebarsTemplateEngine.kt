package io.github.noailabs.spice.extensions.sparql

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.github.jknack.handlebars.io.FileTemplateLoader
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Handlebars-based SPARQL template engine
 *
 * Supports:
 * - Inline templates
 * - File-based templates (classpath and filesystem)
 * - Parameter binding with SQL injection prevention
 * - Named graph injection
 *
 * Example:
 * ```kotlin
 * val engine = HandlebarsTemplateEngine()
 *
 * val sparql = engine.render(
 *     template = """
 *         SELECT ?name WHERE {
 *             ?person foaf:name "{{name}}" .
 *         }
 *     """,
 *     parameters = mapOf("name" to "Alice")
 * )
 * ```
 */
class HandlebarsTemplateEngine(
    private val templateDir: File? = null
) {

    private val handlebars: Handlebars = Handlebars().apply {
        // Use concurrent cache for performance
        with(ConcurrentMapTemplateCache())

        // Setup template loaders
        if (templateDir != null) {
            with(FileTemplateLoader(templateDir))
        } else {
            with(ClassPathTemplateLoader("/templates", ""))
        }

        // Register helper for escaping SPARQL strings
        registerHelper("sparqlEscape") { value: Any?, _ ->
            escapeSparqlString(value?.toString() ?: "")
        }

        // Register helper for URI generation
        registerHelper("uri") { value: Any?, _ ->
            "<${value?.toString() ?: ""}>"
        }

        // Register helper for named graphs
        registerHelper("namedGraphs") { graphs: Any?, _ ->
            when (graphs) {
                is List<*> -> graphs.filterIsInstance<String>()
                    .joinToString("\n") { "FROM <$it>" }
                else -> ""
            }
        }
    }

    /**
     * Render inline template
     *
     * @param template SPARQL template string with Handlebars placeholders
     * @param parameters Template parameters
     * @return Rendered SPARQL query
     */
    fun render(
        template: String,
        parameters: Map<String, Any>
    ): String {
        return try {
            val compiled: Template = handlebars.compileInline(template)
            compiled.apply(parameters).also { rendered ->
                logger.debug { "📝 Template rendered:\n$rendered" }
            }
        } catch (e: Exception) {
            throw TemplateRenderException(
                "Failed to render template: ${e.message}",
                e
            )
        }
    }

    /**
     * Render template from file
     *
     * @param templateName Template file name (without extension)
     * @param parameters Template parameters
     * @return Rendered SPARQL query
     */
    fun renderFile(
        templateName: String,
        parameters: Map<String, Any>
    ): String {
        return try {
            val template: Template = handlebars.compile(templateName)
            template.apply(parameters).also { rendered ->
                logger.debug { "📝 Template '$templateName' rendered" }
            }
        } catch (e: Exception) {
            throw TemplateRenderException(
                "Failed to render template file '$templateName': ${e.message}",
                e
            )
        }
    }

    /**
     * Validate template parameters
     *
     * @param template Template string
     * @param parameters Provided parameters
     * @throws MissingParameterException if required parameters are missing
     */
    fun validate(
        template: String,
        parameters: Map<String, Any>
    ) {
        val required = extractParameters(template)
        val missing = required - parameters.keys

        if (missing.isNotEmpty()) {
            throw MissingParameterException(
                "Missing required parameters: ${missing.joinToString()}"
            )
        }
    }

    /**
     * Extract parameter names from template
     *
     * @param template Template string
     * @return List of parameter names found in template
     */
    fun extractParameters(template: String): List<String> {
        // Match {{paramName}} or {{#if paramName}} etc.
        val pattern = Regex("""\{\{[#/]?(\w+)(?:\s|\}\}|\})""")
        return pattern.findAll(template)
            .map { it.groupValues[1] }
            .distinct()
            .filter { it !in setOf("if", "unless", "each", "with") } // Exclude helpers
            .toList()
    }

    /**
     * Escape SPARQL string literals
     *
     * Prevents SPARQL injection by escaping special characters
     */
    private fun escapeSparqlString(value: String): String {
        return value
            .replace("\\", "\\\\")  // Escape backslashes
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\n", "\\n")   // Escape newlines
            .replace("\r", "\\r")   // Escape carriage returns
            .replace("\t", "\\t")   // Escape tabs
    }
}

/**
 * Template rendering exception
 */
class TemplateRenderException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Missing parameter exception
 */
class MissingParameterException(
    message: String
) : RuntimeException(message)

/**
 * Helper function to build named graphs clause
 *
 * Example:
 * ```kotlin
 * val graphs = listOf("http://example.com/graph1", "http://example.com/graph2")
 * val clause = buildNamedGraphsClause(graphs)
 * // Result: "FROM <http://example.com/graph1>\nFROM <http://example.com/graph2>"
 * ```
 */
fun buildNamedGraphsClause(graphs: List<String>): String {
    return graphs.joinToString("\n") { "FROM <$it>" }
}

/**
 * Helper function to escape SPARQL string literal
 */
fun escapeSparqlString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
