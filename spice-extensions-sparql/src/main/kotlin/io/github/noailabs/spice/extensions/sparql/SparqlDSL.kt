package io.github.noailabs.spice.extensions.sparql

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.dsl.ContextAwareToolBuilder
import mu.KotlinLogging
import java.io.File

// Import Named Graphs extension functions
import io.github.noailabs.spice.extensions.sparql.getNamedGraphs

private val logger = KotlinLogging.logger {}

/**
 * SPARQL configuration block for contextAwareTool
 *
 * Example:
 * ```kotlin
 * contextAwareTool("get_specs") {
 *     sparql {
 *         endpoint = "http://localhost:3030/kai/query"
 *         template = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT {{limit}}"
 *         timeout = 5000
 *
 *         namedGraphs { context ->
 *             context.getAs<List<String>>("catalogGraphs") ?: emptyList()
 *         }
 *     }
 * }
 * ```
 */
class SparqlConfigBlock {
    /**
     * SPARQL endpoint URL
     */
    var endpoint: String = ""

    /**
     * SPARQL UPDATE endpoint URL (optional, defaults to endpoint)
     */
    var updateEndpoint: String? = null

    /**
     * Inline SPARQL template with Handlebars placeholders
     */
    var template: String = ""

    /**
     * Template file name (alternative to inline template)
     */
    var templateFile: String = ""

    /**
     * Template directory for file-based templates
     */
    var templateDir: File? = null

    /**
     * ASK query mode (returns boolean instead of bindings)
     */
    var askMode: Boolean = false

    /**
     * UPDATE query mode (executes update, returns void)
     */
    var updateMode: Boolean = false

    /**
     * Query timeout in milliseconds
     */
    var timeout: Long = 30000

    /**
     * Additional HTTP headers (for Neptune IAM, etc.)
     */
    var headers: Map<String, String> = emptyMap()

    /**
     * Named graphs provider function
     */
    internal var namedGraphsProvider: ((AgentContext) -> List<String>)? = null

    /**
     * Use named graphs from AgentContext (auto-injected by NamedGraphsExtension)
     */
    var useContextGraphs: Boolean = false

    /**
     * Result transformer function
     */
    internal var resultTransformer: ((List<Map<String, Any>>) -> Any)? = null

    /**
     * Configure named graphs from context
     *
     * Example:
     * ```kotlin
     * namedGraphs { context ->
     *     context.getAs<List<String>>("catalogGraphs") ?: emptyList()
     * }
     * ```
     */
    fun namedGraphs(provider: (AgentContext) -> List<String>) {
        namedGraphsProvider = provider
    }

    /**
     * Use named graphs from AgentContext automatically
     *
     * Named graphs must be set via NamedGraphsExtension.
     *
     * Example:
     * ```kotlin
     * sparql {
     *     endpoint = "..."
     *     template = "..."
     *     useContextGraphs()  // Automatically use graphs from context
     * }
     * ```
     */
    fun useContextGraphs() {
        useContextGraphs = true
    }

    /**
     * Transform SPARQL results before returning
     *
     * Example:
     * ```kotlin
     * transform { results ->
     *     buildEvidenceJson(results)
     * }
     * ```
     */
    fun transform(transformer: (List<Map<String, Any>>) -> Any) {
        resultTransformer = transformer
    }

    /**
     * Validate configuration
     */
    fun validate() {
        require(endpoint.isNotEmpty()) { "SPARQL endpoint is required" }
        require(template.isNotEmpty() || templateFile.isNotEmpty()) {
            "Either template or templateFile must be specified"
        }
    }
}

/**
 * SPARQL extension for contextAwareTool
 *
 * Adds SPARQL query capabilities to context-aware tools.
 * Automatically executes the SPARQL query and returns the result.
 *
 * Example:
 * ```kotlin
 * contextAwareTool("get_product_specs") {
 *     description = "Retrieve product specifications from RDF store"
 *
 *     param("sku", "string", "Product SKU", required = true)
 *     param("spec_keys", "array", "Specification keys to retrieve", required = false)
 *
 *     sparql {
 *         endpoint = "http://localhost:3030/catalog/query"
 *         template = """
 *             SELECT ?spec ?value
 *             {{namedGraphsClause}}
 *             WHERE {
 *                 ?product kai:id "{{sku}}" .
 *                 ?product ?spec ?value .
 *                 {{#if spec_keys}}
 *                 FILTER(?spec IN ({{#each spec_keys}}<{{this}}>,{{/each}}))
 *                 {{/if}}
 *             }
 *         """
 *         timeout = 5000
 *
 *         // Option 1: Use context graphs (from NamedGraphsExtension)
 *         useContextGraphs()
 *
 *         // Option 2: Custom provider
 *         // namedGraphs { context ->
 *         //     context.getAs<List<String>>("catalogGraphs") ?: emptyList()
 *         // }
 *
 *         // Optional: transform results
 *         transform { results ->
 *             buildEvidenceJson(results)
 *         }
 *     }
 * }
 * ```
 */
fun ContextAwareToolBuilder.sparql(init: SparqlConfigBlock.() -> Unit) {
    val config = SparqlConfigBlock().apply(init)
    config.validate()

    // Automatically set execute handler to run SPARQL query
    this.execute { params, context ->
        executeSparqlQuery(config, params, context)
    }
}

/**
 * Execute SPARQL query and return result
 *
 * @param config SPARQL configuration
 * @param params Tool parameters
 * @param context Agent context
 * @return Query result
 */
private suspend fun executeSparqlQuery(
    config: SparqlConfigBlock,
    params: Map<String, Any>,
    context: AgentContext
): Any {
    logger.debug { "🔍 Executing SPARQL query for tool" }

    try {
        // Create RDF4J client
        val client = RDF4JClient(config.endpoint, config.updateEndpoint)

        // Set additional headers if provided
        if (config.headers.isNotEmpty()) {
            client.setAdditionalHeaders(config.headers)
        }

        client.use {
            // Get named graphs from context
            val namedGraphs = when {
                // Use context graphs if enabled
                config.useContextGraphs -> context.getNamedGraphs() ?: emptyList()
                // Use custom provider if set
                config.namedGraphsProvider != null -> config.namedGraphsProvider!!.invoke(context)
                // No graphs
                else -> emptyList()
            }

            logger.debug { "📊 Using Named Graphs: $namedGraphs" }

            // Prepare template parameters
            val templateParams = params.toMutableMap()
            templateParams["namedGraphsClause"] = buildNamedGraphsClause(namedGraphs)
            templateParams["namedGraphs"] = namedGraphs  // Also provide raw list

            // Render template
            val templateEngine = HandlebarsTemplateEngine(config.templateDir)
            val sparql = if (config.templateFile.isNotEmpty()) {
                templateEngine.renderFile(config.templateFile, templateParams)
            } else {
                templateEngine.render(config.template, templateParams)
            }

            logger.debug { "📝 Rendered SPARQL:\n$sparql" }

            // Execute query based on mode
            val result: Any = when {
                config.askMode -> {
                    client.ask(sparql, config.timeout)
                }
                config.updateMode -> {
                    client.update(sparql, config.timeout)
                    "Update successful"
                }
                else -> {
                    val queryResult = client.query(sparql, config.timeout)
                    // Apply transformer if provided
                    config.resultTransformer?.invoke(queryResult) ?: queryResult
                }
            }

            logger.debug { "✅ SPARQL query executed successfully" }
            return result
        }
    } catch (e: Exception) {
        logger.error(e) { "❌ SPARQL query execution failed" }
        throw SparqlException.QueryExecutionException(
            "Failed to execute SPARQL query: ${e.message}",
            e
        )
    }
}

/**
 * Check if ContextAwareToolBuilder has beforeExecute hook
 *
 * Note: This is a placeholder. The actual implementation depends on
 * whether ContextAwareToolBuilder supports hooks. If not, we'll need
 * to wrap the execute block differently.
 */
private fun ContextAwareToolBuilder.beforeExecute(
    hook: suspend (Map<String, Any>, AgentContext) -> AgentContext
) {
    // TODO: Implement this based on actual ContextAwareToolBuilder API
    // This might require modifying the core ContextAwareToolBuilder to support hooks
    // Or we can wrap the execute block directly

    // For now, store the hook and apply it in the execute wrapper
    // This is a conceptual implementation
    logger.warn { "⚠️ beforeExecute hook needs proper implementation in ContextAwareToolBuilder" }
}
