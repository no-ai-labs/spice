package io.github.noailabs.spice.extensions.sparql

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.*

/**
 * Tests for SPARQL Template Repository
 */
class SparqlTemplateRepositoryTest {

    @AfterEach
    fun cleanup() {
        SparqlTemplateRepository.clear()
    }

    @Test
    fun `test register and retrieve template`() {
        val templateContent = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }"
        SparqlTemplateRepository.register("test_query", templateContent)

        val template = SparqlTemplateRepository.get("test_query")
        assertNotNull(template)
        assertEquals("test_query", template.name)
        assertEquals(templateContent, template.content)
        assertEquals(TemplateSource.STRING, template.source)
    }

    @Test
    fun `test register template with builder`() {
        sparqlTemplate("product_lookup") {
            description = "Look up product by SKU"

            param("sku", "string", "Product SKU", required = true)
            param("spec_keys", "array", "Specification keys", required = false)

            template("""
                SELECT ?spec ?value
                {{namedGraphsClause}}
                WHERE {
                    ?product kai:id "{{sku}}" .
                    ?product ?spec ?value .
                }
            """.trimIndent())

            example(
                description = "Get all specs for SKU-123",
                params = mapOf("sku" to "SKU-123")
            )
        }

        val template = SparqlTemplateRepository.require("product_lookup")
        assertEquals("product_lookup", template.name)
        assertEquals("Look up product by SKU", template.description)
        assertEquals(2, template.parameters.size)
        assertEquals(1, template.examples.size)
        assertTrue(template.content.contains("kai:id"))
    }

    @Test
    fun `test template parameter validation`() {
        sparqlTemplate("test_params") {
            param("required_param", required = true)
            param("optional_param", required = false)
            template("SELECT * WHERE { }")
        }

        val template = SparqlTemplateRepository.require("test_params")

        // Valid - has required param
        val errors1 = template.validateParameters(mapOf("required_param" to "value"))
        assertTrue(errors1.isEmpty())

        // Invalid - missing required param
        val errors2 = template.validateParameters(mapOf("optional_param" to "value"))
        assertEquals(1, errors2.size)
        assertTrue(errors2[0].contains("required_param"))
    }

    @Test
    fun `test register partial template`() {
        SparqlTemplateRepository.registerPartial("filter_block", """
            FILTER(?value > {{minValue}})
        """.trimIndent())

        val partial = SparqlTemplateRepository.getPartial("filter_block")
        assertNotNull(partial)
        assertTrue(partial.contains("FILTER"))
    }

    @Test
    fun `test list templates and partials`() {
        SparqlTemplateRepository.register("query1", "SELECT * WHERE { }")
        SparqlTemplateRepository.register("query2", "ASK WHERE { }")
        SparqlTemplateRepository.registerPartial("partial1", "FILTER(...)")

        val templates = SparqlTemplateRepository.list()
        assertEquals(2, templates.size)
        assertTrue(templates.contains("query1"))
        assertTrue(templates.contains("query2"))

        val partials = SparqlTemplateRepository.listPartials()
        assertEquals(1, partials.size)
        assertTrue(partials.contains("partial1"))
    }

    @Test
    fun `test has template`() {
        SparqlTemplateRepository.register("existing", "SELECT * WHERE { }")

        assertTrue(SparqlTemplateRepository.has("existing"))
        assertFalse(SparqlTemplateRepository.has("non_existing"))
    }

    @Test
    fun `test require throws exception for missing template`() {
        assertFailsWith<IllegalArgumentException> {
            SparqlTemplateRepository.require("missing_template")
        }
    }

    @Test
    fun `test template rendering`() {
        SparqlTemplateRepository.register("test_render", """
            SELECT ?s WHERE {
                ?s foaf:name "{{name}}" .
            }
        """.trimIndent())

        val template = SparqlTemplateRepository.require("test_render")
        val engine = HandlebarsTemplateEngine()

        val rendered = template.render(mapOf("name" to "Alice"), engine)
        assertTrue(rendered.contains("\"Alice\""))
    }

    @Test
    fun `test common templates registration`() {
        SparqlTemplateRepository.registerCommonTemplates()

        // Check built-in templates
        assertTrue(SparqlTemplateRepository.has("sparql:select"))
        assertTrue(SparqlTemplateRepository.has("sparql:ask"))
        assertTrue(SparqlTemplateRepository.has("sparql:construct"))
        assertTrue(SparqlTemplateRepository.has("sparql:describe"))
        assertTrue(SparqlTemplateRepository.has("sparql:insert"))
        assertTrue(SparqlTemplateRepository.has("sparql:delete"))

        // Check built-in partials
        assertNotNull(SparqlTemplateRepository.getPartial("filters"))
        assertNotNull(SparqlTemplateRepository.getPartial("optional"))
        assertNotNull(SparqlTemplateRepository.getPartial("union"))
    }

    @Test
    fun `test common SELECT template`() {
        val engine = HandlebarsTemplateEngine()

        val params = mapOf(
            "variables" to "?s ?p ?o",
            "whereClause" to "?s ?p ?o .",
            "namedGraphsClause" to "",
            "limit" to 10
        )

        val rendered = engine.render(CommonTemplates.select, params)
        assertTrue(rendered.contains("SELECT ?s ?p ?o"))
        assertTrue(rendered.contains("WHERE"))
        assertTrue(rendered.contains("LIMIT 10"))
    }

    @Test
    fun `test common ASK template`() {
        val engine = HandlebarsTemplateEngine()

        val params = mapOf(
            "whereClause" to "?s rdf:type foaf:Person .",
            "namedGraphsClause" to ""
        )

        val rendered = engine.render(CommonTemplates.ask, params)
        assertTrue(rendered.contains("ASK"))
        assertTrue(rendered.contains("WHERE"))
        assertTrue(rendered.contains("foaf:Person"))
    }

    @Test
    fun `test common INSERT template`() {
        val engine = HandlebarsTemplateEngine()

        val params = mapOf(
            "graph" to "http://example.com/graph",
            "triples" to "<http://ex/s> <http://ex/p> <http://ex/o> ."
        )

        val rendered = engine.render(CommonTemplates.insert, params)
        assertTrue(rendered.contains("INSERT DATA"))
        assertTrue(rendered.contains("GRAPH"))
        assertTrue(rendered.contains("http://example.com/graph"))
    }

    @Test
    fun `test templateRef in SparqlConfigBlock`() {
        SparqlTemplateRepository.register("my_query", """
            SELECT ?name WHERE {
                ?person foaf:name ?name .
            }
        """.trimIndent())

        val config = SparqlConfigBlock()
        config.templateRef = "my_query"

        assertEquals(config.template, SparqlTemplateRepository.require("my_query").content)
    }

    @Test
    fun `test template with multiple parameters`() {
        sparqlTemplate("complex_query") {
            description = "Complex query with multiple params"

            param("subject", "string", "Subject URI", required = true)
            param("predicate", "string", "Predicate URI", required = false)
            param("limit", "integer", "Result limit", required = false, defaultValue = 100)

            template("""
                SELECT ?p ?o WHERE {
                    <{{subject}}> {{#if predicate}}<{{predicate}}>{{else}}?p{{/if}} ?o .
                }
                {{#if limit}}LIMIT {{limit}}{{/if}}
            """.trimIndent())
        }

        val template = SparqlTemplateRepository.require("complex_query")

        assertEquals(3, template.parameters.size)

        // Check required param
        val requiredParam = template.parameters.find { it.name == "subject" }
        assertNotNull(requiredParam)
        assertTrue(requiredParam.required)

        // Check optional with default
        val limitParam = template.parameters.find { it.name == "limit" }
        assertNotNull(limitParam)
        assertFalse(limitParam.required)
        assertEquals(100, limitParam.defaultValue)
    }

    @Test
    fun `test template examples`() {
        sparqlTemplate("example_query") {
            template("SELECT ?s WHERE { ?s ?p ?o }")

            example(
                description = "Get all subjects",
                params = emptyMap(),
                expectedResult = "List of subjects"
            )

            example(
                description = "Get subjects with limit",
                params = mapOf("limit" to 10)
            )
        }

        val template = SparqlTemplateRepository.require("example_query")
        assertEquals(2, template.examples.size)
        assertEquals("Get all subjects", template.examples[0].description)
        assertEquals("List of subjects", template.examples[0].expectedResult)
    }

    @Test
    fun `test clear repository`() {
        SparqlTemplateRepository.register("query1", "SELECT * WHERE { }")
        SparqlTemplateRepository.registerPartial("partial1", "FILTER(...)")

        assertTrue(SparqlTemplateRepository.list().isNotEmpty())
        assertTrue(SparqlTemplateRepository.listPartials().isNotEmpty())

        SparqlTemplateRepository.clear()

        assertTrue(SparqlTemplateRepository.list().isEmpty())
        assertTrue(SparqlTemplateRepository.listPartials().isEmpty())
    }

    @Test
    fun `test SELECT template with all options`() {
        val engine = HandlebarsTemplateEngine()

        val params = mapOf(
            "distinct" to true,
            "variables" to "?name ?age",
            "whereClause" to "?person foaf:name ?name . ?person foaf:age ?age .",
            "namedGraphsClause" to "",
            "filters" to "FILTER(?age > 18)",
            "orderBy" to "?name",
            "limit" to 10,
            "offset" to 5
        )

        val rendered = engine.render(CommonTemplates.select, params)
        assertTrue(rendered.contains("SELECT DISTINCT"))
        assertTrue(rendered.contains("?name ?age"))
        assertTrue(rendered.contains("FILTER(?age > 18)"))
        assertTrue(rendered.contains("ORDER BY"))
        assertTrue(rendered.contains("LIMIT 10"))
        assertTrue(rendered.contains("OFFSET 5"))
    }

    @Test
    fun `test template source tracking`() {
        SparqlTemplateRepository.register("string_template", "SELECT * WHERE { }")

        val template = SparqlTemplateRepository.require("string_template")
        assertEquals(TemplateSource.STRING, template.source)
        assertNull(template.sourcePath)
    }

    @Test
    fun `test filters partial template`() {
        val engine = HandlebarsTemplateEngine()

        val params = mapOf(
            "filters" to listOf(
                "?age > 18",
                "?score > 0.5"
            )
        )

        val rendered = engine.render(CommonTemplates.Partials.filters, params)
        assertTrue(rendered.contains("FILTER(?age > 18)"))
        assertTrue(rendered.contains("FILTER(?score > 0.5)"))
    }
}
