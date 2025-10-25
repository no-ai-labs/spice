package io.github.noailabs.spice.extensions.sparql

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 📚 SPARQL Template Repository
 *
 * Centralized management for SPARQL query templates.
 * Supports template registration, loading, composition, and reuse.
 *
 * Features:
 * - Template registration and retrieval
 * - Template composition (partials)
 * - Built-in common templates
 * - File and string-based templates
 * - Template validation
 *
 * Example:
 * ```kotlin
 * // Register templates
 * SparqlTemplateRepository.register("get_product") {
 *     """
 *     SELECT ?spec ?value
 *     {{namedGraphsClause}}
 *     WHERE {
 *         ?product kai:id "{{sku}}" .
 *         ?product ?spec ?value .
 *     }
 *     """
 * }
 *
 * // Use in tool
 * sparql {
 *     endpoint = "..."
 *     templateRef = "get_product"  // Use registered template
 * }
 * ```
 *
 * @since 0.4.1
 */
object SparqlTemplateRepository {

    private val templates = ConcurrentHashMap<String, SparqlTemplate>()
    private val partials = ConcurrentHashMap<String, String>()

    /**
     * Register a template
     */
    fun register(name: String, template: String) {
        templates[name] = SparqlTemplate(name, template, TemplateSource.STRING)
    }

    /**
     * Register a template with builder
     */
    fun register(name: String, builder: SparqlTemplateBuilder.() -> Unit) {
        val templateBuilder = SparqlTemplateBuilder(name)
        templateBuilder.builder()
        templates[name] = templateBuilder.build()
    }

    /**
     * Register template from file
     */
    fun registerFile(name: String, file: File) {
        val content = file.readText()
        templates[name] = SparqlTemplate(name, content, TemplateSource.FILE, file.absolutePath)
    }

    /**
     * Register template from classpath resource
     */
    fun registerResource(name: String, resourcePath: String) {
        val content = this::class.java.classLoader.getResource(resourcePath)
            ?.readText()
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")
        templates[name] = SparqlTemplate(name, content, TemplateSource.RESOURCE, resourcePath)
    }

    /**
     * Register a partial template (for composition)
     */
    fun registerPartial(name: String, template: String) {
        partials[name] = template
    }

    /**
     * Get template by name
     */
    fun get(name: String): SparqlTemplate? = templates[name]

    /**
     * Get template or throw exception
     */
    fun require(name: String): SparqlTemplate =
        templates[name] ?: throw IllegalArgumentException("Template not found: $name")

    /**
     * Get partial by name
     */
    fun getPartial(name: String): String? = partials[name]

    /**
     * Check if template exists
     */
    fun has(name: String): Boolean = templates.containsKey(name)

    /**
     * List all registered templates
     */
    fun list(): List<String> = templates.keys.toList()

    /**
     * List all partials
     */
    fun listPartials(): List<String> = partials.keys.toList()

    /**
     * Clear all templates (for testing)
     */
    fun clear() {
        templates.clear()
        partials.clear()
    }

    /**
     * Load templates from directory
     */
    fun loadFromDirectory(dir: File, extension: String = ".sparql") {
        if (!dir.exists() || !dir.isDirectory) {
            throw IllegalArgumentException("Directory does not exist: ${dir.absolutePath}")
        }

        dir.listFiles { file -> file.extension == extension.removePrefix(".") }
            ?.forEach { file ->
                val name = file.nameWithoutExtension
                registerFile(name, file)
            }
    }

    /**
     * Register built-in common templates
     */
    fun registerCommonTemplates() {
        // SELECT template
        register("sparql:select", CommonTemplates.select)

        // ASK template
        register("sparql:ask", CommonTemplates.ask)

        // CONSTRUCT template
        register("sparql:construct", CommonTemplates.construct)

        // DESCRIBE template
        register("sparql:describe", CommonTemplates.describe)

        // INSERT template
        register("sparql:insert", CommonTemplates.insert)

        // DELETE template
        register("sparql:delete", CommonTemplates.delete)

        // Common partials
        registerPartial("filters", CommonTemplates.Partials.filters)
        registerPartial("optional", CommonTemplates.Partials.optional)
        registerPartial("union", CommonTemplates.Partials.union)
    }
}

/**
 * SPARQL Template
 */
data class SparqlTemplate(
    val name: String,
    val content: String,
    val source: TemplateSource,
    val sourcePath: String? = null,
    val description: String = "",
    val parameters: List<TemplateParameter> = emptyList(),
    val examples: List<TemplateExample> = emptyList()
) {
    /**
     * Render template with parameters
     */
    fun render(params: Map<String, Any>, engine: HandlebarsTemplateEngine): String {
        return engine.render(content, params)
    }

    /**
     * Validate parameters
     */
    fun validateParameters(params: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()
        parameters.filter { it.required }.forEach { param ->
            if (!params.containsKey(param.name)) {
                errors.add("Required parameter '${param.name}' is missing")
            }
        }
        return errors
    }
}

/**
 * Template source type
 */
enum class TemplateSource {
    STRING,
    FILE,
    RESOURCE
}

/**
 * Template parameter definition
 */
data class TemplateParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null
)

/**
 * Template usage example
 */
data class TemplateExample(
    val description: String,
    val parameters: Map<String, Any>,
    val expectedResult: String? = null
)

/**
 * Builder for SPARQL templates
 */
class SparqlTemplateBuilder(val name: String) {
    var content: String = ""
    var description: String = ""
    private val parameters = mutableListOf<TemplateParameter>()
    private val examples = mutableListOf<TemplateExample>()

    /**
     * Set template content
     */
    fun template(content: String) {
        this.content = content
    }

    /**
     * Add parameter
     */
    fun param(
        name: String,
        type: String = "string",
        description: String = "",
        required: Boolean = true,
        defaultValue: Any? = null
    ) {
        parameters.add(TemplateParameter(name, type, description, required, defaultValue))
    }

    /**
     * Add example
     */
    fun example(description: String, params: Map<String, Any>, expectedResult: String? = null) {
        examples.add(TemplateExample(description, params, expectedResult))
    }

    internal fun build(): SparqlTemplate {
        require(content.isNotEmpty()) { "Template content is required" }
        return SparqlTemplate(
            name = name,
            content = content,
            source = TemplateSource.STRING,
            description = description,
            parameters = parameters,
            examples = examples
        )
    }
}

/**
 * Common SPARQL templates
 */
object CommonTemplates {

    /**
     * Generic SELECT template
     */
    val select = """
        SELECT {{#if distinct}}DISTINCT {{/if}}{{{variables}}}
        {{namedGraphsClause}}
        WHERE {
            {{{whereClause}}}
            {{#if filters}}
            {{{filters}}}
            {{/if}}
        }
        {{#if groupBy}}
        GROUP BY {{{groupBy}}}
        {{/if}}
        {{#if having}}
        HAVING {{{having}}}
        {{/if}}
        {{#if orderBy}}
        ORDER BY {{{orderBy}}}
        {{/if}}
        {{#if limit}}
        LIMIT {{limit}}
        {{/if}}
        {{#if offset}}
        OFFSET {{offset}}
        {{/if}}
    """.trimIndent()

    /**
     * ASK template (boolean query)
     */
    val ask = """
        ASK
        {{namedGraphsClause}}
        WHERE {
            {{{whereClause}}}
        }
    """.trimIndent()

    /**
     * CONSTRUCT template
     */
    val construct = """
        CONSTRUCT {
            {{{constructClause}}}
        }
        {{namedGraphsClause}}
        WHERE {
            {{{whereClause}}}
        }
    """.trimIndent()

    /**
     * DESCRIBE template
     */
    val describe = """
        DESCRIBE {{{resource}}}
        {{namedGraphsClause}}
    """.trimIndent()

    /**
     * INSERT DATA template
     */
    val insert = """
        INSERT DATA {
            {{#if graph}}
            GRAPH <{{graph}}> {
                {{{triples}}}
            }
            {{else}}
            {{{triples}}}
            {{/if}}
        }
    """.trimIndent()

    /**
     * DELETE WHERE template
     */
    val delete = """
        DELETE WHERE {
            {{#if graph}}
            GRAPH <{{graph}}> {
                {{{whereClause}}}
            }
            {{else}}
            {{{whereClause}}}
            {{/if}}
        }
    """.trimIndent()

    /**
     * Common partial templates
     */
    object Partials {
        val filters = """
            {{#each filters}}
            FILTER({{{this}}})
            {{/each}}
        """.trimIndent()

        val optional = """
            {{#if optional}}
            OPTIONAL {
                {{{optional}}}
            }
            {{/if}}
        """.trimIndent()

        val union = """
            {{#each unions}}
            {{#unless @first}}UNION{{/unless}} {
                {{{this}}}
            }
            {{/each}}
        """.trimIndent()
    }
}

/**
 * DSL for template registration
 */
fun sparqlTemplate(name: String, builder: SparqlTemplateBuilder.() -> Unit) {
    SparqlTemplateRepository.register(name, builder)
}

/**
 * Extension for SparqlConfigBlock to use registered templates
 */
var SparqlConfigBlock.templateRef: String
    get() = throw UnsupportedOperationException("templateRef is write-only")
    set(value) {
        val registeredTemplate = SparqlTemplateRepository.require(value)
        this.template = registeredTemplate.content
    }
