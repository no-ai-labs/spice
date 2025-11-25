package io.github.noailabs.spice.template

import io.github.noailabs.spice.SpiceMessage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TemplateExpressionTest {

    // ==================================
    // Literal Expression Tests
    // ==================================

    @Test
    fun `non-template string returns Literal`() {
        val expr = TemplateExpression.parse<String>("hello world")

        assertIs<TemplateExpression.Literal<*>>(expr)
        assertEquals("hello world", (expr as TemplateExpression.Literal).value)
    }

    @Test
    fun `Literal resolves to its value`() {
        val expr = TemplateExpression.parse<String>("constant")
        val message = SpiceMessage.create("test", "user")

        val result = expr.resolve(message)

        assertEquals("constant", result)
    }

    // ==================================
    // Simple Key Access Tests
    // ==================================

    @Test
    fun `simple data key access`() {
        val expr = TemplateExpression.parse<String>("{{data.toolId}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("toolId" to "my_tool"))

        val result = expr.resolve(message)

        assertEquals("my_tool", result)
    }

    @Test
    fun `simple metadata key access`() {
        val expr = TemplateExpression.parse<String>("{{metadata.userId}}")
        val message = SpiceMessage.create("test", "user")
            .withMetadata(mapOf("userId" to "user123"))

        val result = expr.resolve(message)

        assertEquals("user123", result)
    }

    @Test
    fun `missing key returns null`() {
        val expr = TemplateExpression.parse<String>("{{data.missing}}")
        val message = SpiceMessage.create("test", "user")

        val result = expr.resolve(message)

        assertNull(result)
    }

    // ==================================
    // Nested Key Access Tests
    // ==================================

    @Test
    fun `nested key access`() {
        val expr = TemplateExpression.parse<String>("{{data.user.name}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "user" to mapOf("name" to "Alice", "age" to 30)
            ))

        val result = expr.resolve(message)

        assertEquals("Alice", result)
    }

    @Test
    fun `deeply nested key access`() {
        val expr = TemplateExpression.parse<String>("{{data.level1.level2.level3.value}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "level1" to mapOf(
                    "level2" to mapOf(
                        "level3" to mapOf("value" to "deep")
                    )
                )
            ))

        val result = expr.resolve(message)

        assertEquals("deep", result)
    }

    @Test
    fun `nested access returns null when intermediate is missing`() {
        val expr = TemplateExpression.parse<String>("{{data.user.address.city}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "user" to mapOf("name" to "Alice")  // no address
            ))

        val result = expr.resolve(message)

        assertNull(result)
    }

    // ==================================
    // Array Index Access Tests
    // ==================================

    @Test
    fun `array index access`() {
        val expr = TemplateExpression.parse<String>("{{data.items[0]}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "items" to listOf("first", "second", "third")
            ))

        val result = expr.resolve(message)

        assertEquals("first", result)
    }

    @Test
    fun `array index access with nested property`() {
        val expr = TemplateExpression.parse<String>("{{data.items[1].name}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "items" to listOf(
                    mapOf("name" to "First"),
                    mapOf("name" to "Second"),
                    mapOf("name" to "Third")
                )
            ))

        val result = expr.resolve(message)

        assertEquals("Second", result)
    }

    @Test
    fun `array index out of bounds returns null`() {
        val expr = TemplateExpression.parse<String>("{{data.items[99]}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "items" to listOf("first", "second")
            ))

        val result = expr.resolve(message)

        assertNull(result)
    }

    // ==================================
    // Quoted Key Access Tests
    // ==================================

    @Test
    fun `quoted key with double quotes`() {
        val expr = TemplateExpression.parse<String>("""{{data["foo.bar"]}}""")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("foo.bar" to "dotted value"))

        val result = expr.resolve(message)

        assertEquals("dotted value", result)
    }

    @Test
    fun `quoted key with single quotes`() {
        val expr = TemplateExpression.parse<String>("{{data['special-key']}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("special-key" to "special value"))

        val result = expr.resolve(message)

        assertEquals("special value", result)
    }

    // ==================================
    // Type Casting Tests
    // ==================================

    @Test
    fun `cast to int`() {
        val expr = TemplateExpression.parse<Int>("{{data.count:int}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("count" to 42))

        val result = expr.resolve(message)

        assertEquals(42, result)
    }

    @Test
    fun `cast string to int`() {
        val expr = TemplateExpression.parse<Int>("{{data.count:int}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("count" to "123"))

        val result = expr.resolve(message)

        assertEquals(123, result)
    }

    @Test
    fun `cast to long`() {
        val expr = TemplateExpression.parse<Long>("{{data.bigNum:long}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("bigNum" to 9999999999L))

        val result = expr.resolve(message)

        assertEquals(9999999999L, result)
    }

    @Test
    fun `cast to double`() {
        val expr = TemplateExpression.parse<Double>("{{data.price:double}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("price" to 19.99))

        val result = expr.resolve(message)

        assertEquals(19.99, result)
    }

    @Test
    fun `cast to boolean`() {
        val expr = TemplateExpression.parse<Boolean>("{{data.enabled:bool}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("enabled" to true))

        val result = expr.resolve(message)

        assertEquals(true, result)
    }

    @Test
    fun `cast string to boolean`() {
        val expr = TemplateExpression.parse<Boolean>("{{data.enabled:boolean}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("enabled" to "true"))

        val result = expr.resolve(message)

        assertEquals(true, result)
    }

    @Test
    fun `cast to any preserves original type`() {
        val expr = TemplateExpression.parse<Any>("{{data.complex:any}}")
        val complexValue = mapOf("nested" to listOf(1, 2, 3))
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("complex" to complexValue))

        val result = expr.resolve(message)

        assertEquals(complexValue, result)
    }

    @Test
    fun `default cast to string`() {
        val expr = TemplateExpression.parse<String>("{{data.number}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("number" to 42))

        val result = expr.resolve(message)

        assertEquals("42", result)
    }

    // ==================================
    // isTemplate Tests
    // ==================================

    @Test
    fun `isTemplate returns true for template strings`() {
        assertTrue(TemplateExpression.isTemplate("{{data.key}}"))
        assertTrue(TemplateExpression.isTemplate("prefix {{metadata.userId}} suffix"))
        assertTrue(TemplateExpression.isTemplate("{{data.items[0].name:string}}"))
    }

    @Test
    fun `isTemplate returns false for non-template strings`() {
        assertFalse(TemplateExpression.isTemplate("hello world"))
        assertFalse(TemplateExpression.isTemplate("{ data.key }"))
        assertFalse(TemplateExpression.isTemplate("{data.key}"))
    }

    // ==================================
    // Extension Function Tests
    // ==================================

    @Test
    fun `resolveTemplate extension function works`() {
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("name" to "Alice"))

        val result = message.resolveTemplate("{{data.name}}")

        assertEquals("Alice", result)
    }

    // ==================================
    // Edge Cases
    // ==================================

    @Test
    fun `whitespace in template is trimmed`() {
        val expr = TemplateExpression.parse<String>("  {{data.key}}  ")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("key" to "value"))

        val result = expr.resolve(message)

        assertEquals("value", result)
    }

    @Test
    fun `complex mixed path expression`() {
        val expr = TemplateExpression.parse<String>("{{data.users[0].addresses[1].city}}")
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "users" to listOf(
                    mapOf(
                        "name" to "Alice",
                        "addresses" to listOf(
                            mapOf("city" to "New York"),
                            mapOf("city" to "Los Angeles")
                        )
                    )
                )
            ))

        val result = expr.resolve(message)

        assertEquals("Los Angeles", result)
    }
}
