package io.github.noailabs.spice.extensions.sparql

import mu.KotlinLogging
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.TupleQuery
import org.eclipse.rdf4j.query.BooleanQuery
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository

private val logger = KotlinLogging.logger {}

/**
 * RDF4J SPARQL Client
 *
 * Supports both AWS Neptune and Apache Fuseki endpoints.
 * Neptune uses native RDF4J SPARQLRepository support.
 *
 * Example:
 * ```kotlin
 * // Fuseki
 * val client = RDF4JClient("http://localhost:3030/dataset/query")
 *
 * // Neptune
 * val client = RDF4JClient("https://your-neptune.amazonaws.com:8182/sparql")
 * client.setAdditionalHeaders(neptuneIAMHeaders)
 * ```
 */
class RDF4JClient(
    private val endpoint: String,
    private val updateEndpoint: String? = null
) : AutoCloseable {

    private val repository: SPARQLRepository = if (updateEndpoint != null) {
        SPARQLRepository(endpoint, updateEndpoint)
    } else {
        SPARQLRepository(endpoint)
    }.apply {
        init()
    }

    /**
     * Execute SPARQL SELECT query
     *
     * @param sparql SPARQL query string
     * @param timeout Query timeout in milliseconds (default: 30 seconds)
     * @return List of result bindings as Maps
     */
    fun query(sparql: String, timeout: Long = 30000): List<Map<String, Any>> {
        logger.debug { "🔍 SPARQL Query:\n$sparql" }

        return repository.connection.use { conn ->
            val tupleQuery: TupleQuery = conn.prepareTupleQuery(sparql)
            tupleQuery.maxExecutionTime = (timeout / 1000).toInt()

            val results = tupleQuery.evaluate()

            buildList {
                while (results.hasNext()) {
                    val bindingSet: BindingSet = results.next()
                    val binding = mutableMapOf<String, Any>()

                    bindingSet.bindingNames.forEach { name ->
                        val value: Value = bindingSet.getValue(name)
                        binding[name] = convertValue(value)
                    }

                    add(binding)
                }
            }.also { resultList ->
                logger.info { "✅ Query returned ${resultList.size} results" }
            }
        }
    }

    /**
     * Execute SPARQL ASK query
     *
     * @param sparql SPARQL ASK query
     * @param timeout Query timeout in milliseconds (default: 10 seconds)
     * @return Boolean result
     */
    fun ask(sparql: String, timeout: Long = 10000): Boolean {
        logger.debug { "❓ SPARQL Ask:\n$sparql" }

        return repository.connection.use { conn ->
            val booleanQuery: BooleanQuery = conn.prepareBooleanQuery(sparql)
            booleanQuery.maxExecutionTime = (timeout / 1000).toInt()

            booleanQuery.evaluate().also { result ->
                logger.info { "✅ Ask result: $result" }
            }
        }
    }

    /**
     * Execute SPARQL UPDATE query
     *
     * @param sparql SPARQL UPDATE query
     * @param timeout Query timeout in milliseconds (default: 30 seconds)
     */
    fun update(sparql: String, timeout: Long = 30000) {
        logger.debug { "📝 SPARQL Update:\n$sparql" }

        repository.connection.use { conn ->
            val updateQuery = conn.prepareUpdate(sparql)
            updateQuery.maxExecutionTime = (timeout / 1000).toInt()
            updateQuery.execute()

            logger.info { "✅ Update completed" }
        }
    }

    /**
     * Set additional HTTP headers for Neptune IAM authentication
     *
     * Example:
     * ```kotlin
     * val headers = mapOf(
     *     "Authorization" to "AWS4-HMAC-SHA256 ...",
     *     "Host" to "your-neptune.amazonaws.com"
     * )
     * client.setAdditionalHeaders(headers)
     * ```
     */
    fun setAdditionalHeaders(headers: Map<String, String>) {
        repository.setAdditionalHttpHeaders(headers)
        logger.debug { "🔐 Set additional headers: ${headers.keys}" }
    }

    /**
     * Convert RDF4J Value to Kotlin type
     */
    private fun convertValue(value: Value): Any {
        return when {
            value.isLiteral -> {
                val literal = value as org.eclipse.rdf4j.model.Literal
                // Try to get native value (handles xsd:int, xsd:double, etc.)
                literal.label
            }
            value.isIRI -> value.stringValue()
            value.isBNode -> value.stringValue()
            else -> value.toString()
        }
    }

    /**
     * Test connection to SPARQL endpoint
     */
    fun testConnection(): Boolean {
        return try {
            // Simple ASK query to test connection
            ask("ASK { ?s ?p ?o }", timeout = 5000)
            true
        } catch (e: Exception) {
            logger.error(e) { "❌ Connection test failed" }
            false
        }
    }

    override fun close() {
        repository.shutDown()
        logger.debug { "🔌 Repository closed" }
    }
}

/**
 * SPARQL query result
 */
data class SparqlResult(
    val bindings: List<Map<String, Any>>,
    val variables: List<String> = emptyList()
)

/**
 * SPARQL exception types
 */
sealed class SparqlException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    class QueryExecutionException(message: String, cause: Throwable? = null) :
        SparqlException(message, cause)

    class ConnectionException(message: String, cause: Throwable? = null) :
        SparqlException(message, cause)

    class TimeoutException(message: String) :
        SparqlException(message)

    class InvalidQueryException(message: String, cause: Throwable? = null) :
        SparqlException(message, cause)
}
