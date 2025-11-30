package io.github.noailabs.spice.hitl.template

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HitlTemplateEngineTest {

    private val engine = HitlTemplateEngine()

    // ===========================================
    // Basic Variable Tests
    // ===========================================

    @Test
    fun `simple variable interpolation`() {
        val result = engine.render("Hello, {{name}}!", mapOf("name" to "World"))
        assertEquals("Hello, World!", result)
    }

    @Test
    fun `nested path interpolation`() {
        val result = engine.render(
            "Welcome {{user.name}}!",
            mapOf("user" to mapOf("name" to "Alice"))
        )
        assertEquals("Welcome Alice!", result)
    }

    @Test
    fun `missing variable returns empty string`() {
        val result = engine.render("Hello {{name}}!", emptyMap())
        assertEquals("Hello !", result)
    }

    @Test
    fun `default value filter`() {
        val result = engine.render(
            "Hello {{name | default:\"Guest\"}}!",
            emptyMap()
        )
        assertEquals("Hello Guest!", result)
    }

    // ===========================================
    // Block Helper Tests - #if
    // ===========================================

    @Test
    fun `if block renders when truthy`() {
        val result = engine.render(
            "{{#if show}}Visible{{/if}}",
            mapOf("show" to true)
        )
        assertEquals("Visible", result)
    }

    @Test
    fun `if block does not render when falsy`() {
        val result = engine.render(
            "{{#if show}}Visible{{/if}}",
            mapOf("show" to false)
        )
        assertEquals("", result)
    }

    @Test
    fun `if-else block renders else when falsy`() {
        val result = engine.render(
            "{{#if show}}Yes{{else}}No{{/if}}",
            mapOf("show" to false)
        )
        assertEquals("No", result)
    }

    @Test
    fun `if block with non-empty collection is truthy`() {
        val result = engine.render(
            "{{#if items}}Has items{{/if}}",
            mapOf("items" to listOf(1, 2, 3))
        )
        assertEquals("Has items", result)
    }

    @Test
    fun `if block with empty collection is falsy`() {
        val result = engine.render(
            "{{#if items}}Has items{{else}}Empty{{/if}}",
            mapOf("items" to emptyList<Any>())
        )
        assertEquals("Empty", result)
    }

    // ===========================================
    // Block Helper Tests - #each
    // ===========================================

    @Test
    fun `each block iterates list`() {
        val result = engine.render(
            "{{#each items}}{{this}},{{/each}}",
            mapOf("items" to listOf("A", "B", "C"))
        )
        assertEquals("A,B,C,", result)
    }

    @Test
    fun `each block with @index`() {
        val result = engine.render(
            "{{#each items}}{{@index}}:{{this}} {{/each}}",
            mapOf("items" to listOf("a", "b"))
        )
        assertEquals("0:a 1:b ", result)
    }

    @Test
    fun `each block with objects`() {
        val result = engine.render(
            "{{#each users}}{{name}}-{{/each}}",
            mapOf("users" to listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob")
            ))
        )
        assertEquals("Alice-Bob-", result)
    }

    @Test
    fun `each block renders else when empty`() {
        val result = engine.render(
            "{{#each items}}{{this}}{{else}}No items{{/each}}",
            mapOf("items" to emptyList<Any>())
        )
        assertEquals("No items", result)
    }

    // ===========================================
    // Nested Block Tests (Blocker Fix)
    // ===========================================

    @Test
    fun `nested if inside if`() {
        val result = engine.render(
            "{{#if a}}{{#if b}}Both{{/if}}{{/if}}",
            mapOf("a" to true, "b" to true)
        )
        assertEquals("Both", result)
    }

    @Test
    fun `nested each inside if`() {
        val template = """{{#if hasItems}}Items: {{#each items}}{{this}} {{/each}}{{else}}None{{/if}}"""

        val withItems = engine.render(template, mapOf(
            "hasItems" to true,
            "items" to listOf("Apple", "Banana")
        ))
        assertEquals("Items: Apple Banana ", withItems)

        val withoutItems = engine.render(template, mapOf("hasItems" to false))
        assertEquals("None", withoutItems)
    }

    @Test
    fun `nested if inside each`() {
        val template = "{{#each items}}{{#if active}}{{name}}{{/if}}{{/each}}"

        val result = engine.render(template, mapOf(
            "items" to listOf(
                mapOf("name" to "A", "active" to true),
                mapOf("name" to "B", "active" to false),
                mapOf("name" to "C", "active" to true)
            )
        ))
        assertEquals("AC", result)
    }

    @Test
    fun `deeply nested blocks`() {
        val template = """
{{#if level1}}
  {{#if level2}}
    {{#each items}}
      {{#if active}}[{{name}}]{{/if}}
    {{/each}}
  {{/if}}
{{/if}}
""".trimIndent()

        val result = engine.render(template, mapOf(
            "level1" to true,
            "level2" to true,
            "items" to listOf(
                mapOf("name" to "X", "active" to true),
                mapOf("name" to "Y", "active" to false)
            )
        ))
        assertTrue(result.contains("[X]"))
        assertTrue(!result.contains("[Y]"))
    }

    // ===========================================
    // Block Helper Tests - #unless
    // ===========================================

    @Test
    fun `unless block renders when falsy`() {
        val result = engine.render(
            "{{#unless hidden}}Visible{{/unless}}",
            mapOf("hidden" to false)
        )
        assertEquals("Visible", result)
    }

    @Test
    fun `unless block does not render when truthy`() {
        val result = engine.render(
            "{{#unless hidden}}Visible{{/unless}}",
            mapOf("hidden" to true)
        )
        assertEquals("", result)
    }

    // ===========================================
    // Block Helper Tests - #with
    // ===========================================

    @Test
    fun `with block changes context`() {
        val result = engine.render(
            "{{#with user}}Hello {{name}}!{{/with}}",
            mapOf("user" to mapOf("name" to "Alice"))
        )
        assertEquals("Hello Alice!", result)
    }

    // ===========================================
    // Built-in Helper Tests
    // ===========================================

    @Test
    fun `plural helper singular`() {
        val result = engine.render(
            "{{plural count \"item\" \"items\"}}",
            mapOf("count" to 1)
        )
        assertEquals("item", result)
    }

    @Test
    fun `plural helper plural`() {
        val result = engine.render(
            "{{plural count \"item\" \"items\"}}",
            mapOf("count" to 5)
        )
        assertEquals("items", result)
    }

    // ===========================================
    // Error Handling Tests
    // ===========================================

    @Test
    fun `unclosed block throws exception`() {
        val exception = assertThrows<TemplateParseException> {
            engine.render("{{#if show}}content", mapOf("show" to true))
        }
        assertTrue(exception.message!!.contains("Unclosed"))
    }

    @Test
    fun `unsupported block helper throws exception`() {
        val exception = assertThrows<TemplateParseException> {
            engine.render("{{#custom}}content{{/custom}}", emptyMap())
        }
        assertTrue(exception.message!!.contains("Unsupported"))
    }

    // ===========================================
    // Cache Tests
    // ===========================================

    @Test
    fun `cache is populated on render`() {
        val engine = HitlTemplateEngine(cacheEnabled = true)
        assertEquals(0, engine.getCacheStats().size)

        engine.render("{{name}}", mapOf("name" to "test"))
        assertEquals(1, engine.getCacheStats().size)

        // Same template doesn't increase cache size
        engine.render("{{name}}", mapOf("name" to "test2"))
        assertEquals(1, engine.getCacheStats().size)

        // Different template increases cache size
        engine.render("{{other}}", mapOf("other" to "value"))
        assertEquals(2, engine.getCacheStats().size)
    }

    @Test
    fun `clearCache empties the cache`() {
        val engine = HitlTemplateEngine(cacheEnabled = true)
        engine.render("{{name}}", mapOf("name" to "test"))
        assertEquals(1, engine.getCacheStats().size)

        engine.clearCache()
        assertEquals(0, engine.getCacheStats().size)
    }
}
