package io.github.noailabs.spice.extensions.sparql

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for HandlebarsTemplateEngine
 */
class HandlebarsTemplateEngineTest {

    private val engine = HandlebarsTemplateEngine()

    @Test
    fun `test simple template rendering`() {
        val template = """
            SELECT ?name WHERE {
                ?person foaf:name "{{name}}" .
            }
        """.trimIndent()

        val result = engine.render(template, mapOf("name" to "Alice"))

        assertTrue(result.contains("\"Alice\""))
    }

    @Test
    fun `test template with named graphs`() {
        val template = """
            SELECT ?s ?p ?o
            {{namedGraphsClause}}
            WHERE {
                ?s ?p ?o .
            }
        """.trimIndent()

        val graphs = listOf("http://example.com/graph1", "http://example.com/graph2")
        val result = engine.render(template, mapOf("namedGraphsClause" to buildNamedGraphsClause(graphs)))

        assertTrue(result.contains("FROM <http://example.com/graph1>"))
        assertTrue(result.contains("FROM <http://example.com/graph2>"))
    }

    @Test
    fun `test template with conditional`() {
        val template = """
            SELECT ?s ?p ?o WHERE {
                ?s ?p ?o .
                {{#if filter}}
                FILTER(?s = <{{filterValue}}>)
                {{/if}}
            }
        """.trimIndent()

        // With filter
        val result1 = engine.render(template, mapOf(
            "filter" to true,
            "filterValue" to "http://example.com/entity"
        ))
        assertTrue(result1.contains("FILTER"))

        // Without filter
        val result2 = engine.render(template, mapOf("filter" to false))
        assertTrue(!result2.contains("FILTER"))
    }

    @Test
    fun `test template with each loop`() {
        val template = """
            SELECT ?name WHERE {
                ?person foaf:name ?name .
                FILTER(?name IN ({{#each names}}"{{this}}"{{#unless @last}},{{/unless}}{{/each}}))
            }
        """.trimIndent()

        val result = engine.render(template, mapOf(
            "names" to listOf("Alice", "Bob", "Charlie")
        ))

        assertTrue(result.contains("\"Alice\""))
        assertTrue(result.contains("\"Bob\""))
        assertTrue(result.contains("\"Charlie\""))
    }

    @Test
    fun `test SPARQL string escaping`() {
        val value = """Line 1
Line 2 with "quotes" and \backslash"""

        val escaped = escapeSparqlString(value)

        assertTrue(escaped.contains("\\n"))
        assertTrue(escaped.contains("\\\""))
        assertTrue(escaped.contains("\\\\"))
    }

    @Test
    fun `test extract parameters from template`() {
        val template = """
            SELECT ?s WHERE {
                ?s foaf:name "{{name}}" .
                ?s foaf:age {{age}} .
                {{#if includeEmail}}
                ?s foaf:email "{{email}}" .
                {{/if}}
            }
        """.trimIndent()

        val params = engine.extractParameters(template)

        assertTrue(params.contains("name"))
        assertTrue(params.contains("age"))
        assertTrue(params.contains("includeEmail"))
        assertTrue(params.contains("email"))
    }

    @Test
    fun `test missing parameter validation`() {
        val template = "SELECT ?s WHERE { ?s foaf:name \"{{name}}\" . }"

        assertFailsWith<MissingParameterException> {
            engine.validate(template, emptyMap())
        }

        // Should not throw with all parameters
        engine.validate(template, mapOf("name" to "Alice"))
    }

    @Test
    fun `test named graphs clause builder`() {
        val graphs = listOf(
            "http://example.com/graph1",
            "http://example.com/graph2",
            "http://example.com/graph3"
        )

        val clause = buildNamedGraphsClause(graphs)

        assertEquals(
            "FROM <http://example.com/graph1>\nFROM <http://example.com/graph2>\nFROM <http://example.com/graph3>",
            clause
        )
    }

    @Test
    fun `test empty named graphs`() {
        val clause = buildNamedGraphsClause(emptyList())
        assertEquals("", clause)
    }

    @Test
    fun `test complex template with multiple features`() {
        val template = """
            PREFIX foaf: <http://xmlns.com/foaf/0.1/>
            PREFIX kai: <http://kaibrain.com/ontology/>

            SELECT ?product ?spec ?value
            {{namedGraphsClause}}
            WHERE {
                ?product kai:id "{{sku}}" .
                ?product ?spec ?value .
                {{#if spec_keys}}
                FILTER(?spec IN ({{#each spec_keys}}<{{this}}>{{#unless @last}},{{/unless}}{{/each}}))
                {{/if}}
                {{#if minValue}}
                FILTER(?value >= {{minValue}})
                {{/if}}
            }
            {{#if limit}}
            LIMIT {{limit}}
            {{/if}}
        """.trimIndent()

        val params = mapOf(
            "sku" to "SKU-12345",
            "spec_keys" to listOf("http://example.com/weight", "http://example.com/size"),
            "minValue" to 10,
            "limit" to 50,
            "namedGraphsClause" to buildNamedGraphsClause(listOf("http://example.com/catalog"))
        )

        val result = engine.render(template, params)

        assertTrue(result.contains("\"SKU-12345\""))
        assertTrue(result.contains("<http://example.com/weight>"))
        assertTrue(result.contains("<http://example.com/size>"))
        assertTrue(result.contains("FILTER(?value >= 10)"))
        assertTrue(result.contains("LIMIT 50"))
        assertTrue(result.contains("FROM <http://example.com/catalog>"))
    }
}
