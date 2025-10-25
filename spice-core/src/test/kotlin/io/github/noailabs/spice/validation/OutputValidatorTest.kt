package io.github.noailabs.spice.validation

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.dsl.contextAwareTool
import io.github.noailabs.spice.dsl.withAgentContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for Output Validation DSL
 */
class OutputValidatorTest {

    @Test
    fun `test requireField validation - success`() {
        val validator = outputValidator {
            requireField("name")
            requireField("email")
        }

        val output = mapOf(
            "name" to "Alice",
            "email" to "alice@example.com"
        )

        val result = validator.validate(output)
        assertTrue(result.isValid)
        assertNull(result.error)
    }

    @Test
    fun `test requireField validation - missing field`() {
        val validator = outputValidator {
            requireField("name")
            requireField("email", "Email is required for registration")
        }

        val output = mapOf(
            "name" to "Alice"
            // email is missing
        )

        val result = validator.validate(output)
        assertFalse(result.isValid)
        assertEquals("Email is required for registration", result.error)
    }

    @Test
    fun `test fieldType validation - success`() {
        val validator = outputValidator {
            fieldType("name", FieldType.STRING)
            fieldType("age", FieldType.NUMBER)
            fieldType("active", FieldType.BOOLEAN)
            fieldType("tags", FieldType.ARRAY)
            fieldType("metadata", FieldType.OBJECT)
        }

        val output = mapOf(
            "name" to "Alice",
            "age" to 30,
            "active" to true,
            "tags" to listOf("user", "admin"),
            "metadata" to mapOf("role" to "admin")
        )

        val result = validator.validate(output)
        assertTrue(result.isValid)
    }

    @Test
    fun `test fieldType validation - type mismatch`() {
        val validator = outputValidator {
            fieldType("age", FieldType.NUMBER)
        }

        val output = mapOf(
            "age" to "thirty"  // String instead of number
        )

        val result = validator.validate(output)
        assertFalse(result.isValid)
        assertTrue(result.error!!.contains("must be of type number"))
    }

    @Test
    fun `test range validation - success`() {
        val validator = outputValidator {
            range("confidence", 0.0, 1.0)
        }

        val output = mapOf(
            "confidence" to 0.85
        )

        val result = validator.validate(output)
        assertTrue(result.isValid)
    }

    @Test
    fun `test range validation - out of range`() {
        val validator = outputValidator {
            range("confidence", 0.0, 1.0)
        }

        val output = mapOf(
            "confidence" to 1.5
        )

        val result = validator.validate(output)
        assertFalse(result.isValid)
        assertTrue(result.error!!.contains("must be between 0.0 and 1.0"))
    }

    @Test
    fun `test pattern validation - success`() {
        val validator = outputValidator {
            pattern("email", Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""))
        }

        val output = mapOf(
            "email" to "alice@example.com"
        )

        val result = validator.validate(output)
        assertTrue(result.isValid)
    }

    @Test
    fun `test pattern validation - does not match`() {
        val validator = outputValidator {
            pattern("email", """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
        }

        val output = mapOf(
            "email" to "invalid-email"
        )

        val result = validator.validate(output)
        assertFalse(result.isValid)
        assertTrue(result.error!!.contains("does not match required pattern"))
    }

    @Test
    fun `test custom rule validation - success`() {
        val validator = outputValidator {
            rule("citations must not be empty") { output ->
                val citations = (output as? Map<*, *>)?.get("citations") as? List<*>
                citations != null && citations.isNotEmpty()
            }
        }

        val output = mapOf(
            "citations" to listOf("source1", "source2")
        )

        val result = validator.validate(output)
        assertTrue(result.isValid)
    }

    @Test
    fun `test custom rule validation - failure`() {
        val validator = outputValidator {
            rule("citations must not be empty") { output ->
                val citations = (output as? Map<*, *>)?.get("citations") as? List<*>
                citations != null && citations.isNotEmpty()
            }
        }

        val output = mapOf(
            "citations" to emptyList<String>()
        )

        val result = validator.validate(output)
        assertFalse(result.isValid)
        assertEquals("Validation failed: citations must not be empty", result.error)
    }

    @Test
    fun `test multiple validation rules - all pass`() {
        val validator = outputValidator {
            requireField("citations")
            requireField("summary")
            requireField("confidence")

            fieldType("citations", FieldType.ARRAY)
            fieldType("summary", FieldType.STRING)
            fieldType("confidence", FieldType.NUMBER)

            range("confidence", 0.0, 1.0)

            rule("citations must not be empty") { output ->
                val citations = (output as? Map<*, *>)?.get("citations") as? List<*>
                citations != null && citations.isNotEmpty()
            }
        }

        val output = mapOf(
            "citations" to listOf("source1", "source2"),
            "summary" to "Evidence summary",
            "confidence" to 0.95
        )

        val result = validator.validate(output)
        assertTrue(result.isValid)
    }

    @Test
    fun `test multiple validation rules - first failure stops validation`() {
        val validator = outputValidator {
            requireField("citations")
            requireField("summary")  // This will fail
            range("confidence", 0.0, 1.0)  // Should not be checked
        }

        val output = mapOf(
            "citations" to listOf("source1"),
            // summary is missing
            "confidence" to 2.0  // Out of range, but should not be checked
        )

        val result = validator.validate(output)
        assertFalse(result.isValid)
        assertTrue(result.error!!.contains("summary"))
        assertFalse(result.error!!.contains("confidence"))
    }

    @Test
    fun `test validation with AgentContext`() {
        val validator = outputValidator {
            rule("tenant must match context") { output, context ->
                val tenantId = (output as? Map<*, *>)?.get("tenantId") as? String
                tenantId == context?.tenantId
            }
        }

        val output = mapOf(
            "tenantId" to "TENANT-A"
        )

        val context = AgentContext.of("tenantId" to "TENANT-A")
        val result = validator.validate(output, context)
        assertTrue(result.isValid)

        val wrongContext = AgentContext.of("tenantId" to "TENANT-B")
        val failResult = validator.validate(output, wrongContext)
        assertFalse(failResult.isValid)
    }

    @Test
    fun `test contextAwareTool with validation DSL - success`() = runTest {
        val tool = contextAwareTool("generate_evidence") {
            description = "Generate evidence with citations"

            validate {
                requireField("citations", "Evidence must include citations")
                requireField("summary")
                requireField("confidence")

                fieldType("citations", FieldType.ARRAY)
                fieldType("confidence", FieldType.NUMBER)

                rule("citations must not be empty") { output ->
                    val citations = (output as? Map<*, *>)?.get("citations") as? List<*>
                    citations != null && citations.isNotEmpty()
                }

                range("confidence", 0.0, 1.0)
            }

            execute { params, context ->
                mapOf(
                    "citations" to listOf("source1", "source2"),
                    "summary" to "Evidence summary",
                    "confidence" to 0.95
                )
            }
        }

        withAgentContext("tenantId" to "TENANT-A") {
            val result = tool.execute(emptyMap())
            assertTrue(result.isSuccess)
            val toolResult = result.getOrNull()!!
            assertTrue(toolResult.success)
        }
    }

    @Test
    fun `test contextAwareTool with validation DSL - missing required field`() = runTest {
        val tool = contextAwareTool("generate_evidence") {
            description = "Generate evidence with citations"

            validate {
                requireField("citations", "Evidence must include citations")
                requireField("summary")
            }

            execute { params, context ->
                mapOf(
                    // citations is missing
                    "summary" to "Evidence summary"
                )
            }
        }

        withAgentContext("tenantId" to "TENANT-A") {
            val result = tool.execute(emptyMap())
            assertTrue(result.isSuccess)
            val toolResult = result.getOrNull()!!
            assertFalse(toolResult.success)
            assertTrue(toolResult.error!!.contains("Evidence must include citations"))
        }
    }

    @Test
    fun `test contextAwareTool with validation DSL - type mismatch`() = runTest {
        val tool = contextAwareTool("process_data") {
            description = "Process data"

            validate {
                fieldType("count", FieldType.NUMBER)
            }

            execute { params, context ->
                mapOf(
                    "count" to "invalid"  // String instead of number
                )
            }
        }

        withAgentContext("tenantId" to "TENANT-A") {
            val result = tool.execute(emptyMap())
            assertTrue(result.isSuccess)
            val toolResult = result.getOrNull()!!
            assertFalse(toolResult.success)
            assertTrue(toolResult.error!!.contains("must be of type number"))
        }
    }

    @Test
    fun `test contextAwareTool with validation DSL - custom rule failure`() = runTest {
        val tool = contextAwareTool("submit_order") {
            description = "Submit order"

            validate {
                requireField("items")

                rule("order must have at least one item") { output ->
                    val items = (output as? Map<*, *>)?.get("items") as? List<*>
                    items != null && items.isNotEmpty()
                }
            }

            execute { params, context ->
                mapOf(
                    "items" to emptyList<String>()  // Empty list
                )
            }
        }

        withAgentContext("tenantId" to "TENANT-A") {
            val result = tool.execute(emptyMap())
            assertTrue(result.isSuccess)
            val toolResult = result.getOrNull()!!
            assertFalse(toolResult.success)
            assertTrue(toolResult.error!!.contains("order must have at least one item"))
        }
    }

    @Test
    fun `test validation skipped when field is missing (optional field type check)`() {
        val validator = outputValidator {
            fieldType("optional", FieldType.NUMBER)  // Only validates if field exists
        }

        val output = mapOf(
            "other" to "value"
            // optional field is not present
        )

        val result = validator.validate(output)
        assertTrue(result.isValid)  // Should pass because field is not required
    }

    @Test
    fun `test complex Evidence validation example`() {
        val validator = outputValidator {
            // Required fields
            requireField("citations", "Evidence must include citations")
            requireField("summary", "Evidence must include a summary")
            requireField("confidence", "Evidence must include confidence score")
            requireField("source_type")

            // Type validations
            fieldType("citations", FieldType.ARRAY)
            fieldType("summary", FieldType.STRING)
            fieldType("confidence", FieldType.NUMBER)
            fieldType("source_type", FieldType.STRING)
            fieldType("metadata", FieldType.OBJECT)  // Optional

            // Range validations
            range("confidence", 0.0, 1.0)

            // Pattern validation
            pattern("source_type", "^(academic|news|blog|report)$",
                "source_type must be one of: academic, news, blog, report")

            // Custom rules
            rule("citations must not be empty") { output ->
                val citations = (output as? Map<*, *>)?.get("citations") as? List<*>
                citations != null && citations.isNotEmpty()
            }

            rule("summary must be meaningful") { output ->
                val summary = (output as? Map<*, *>)?.get("summary") as? String
                summary != null && summary.trim().length >= 10
            }
        }

        // Valid evidence
        val validEvidence = mapOf(
            "citations" to listOf("https://example.com/paper1", "https://example.com/paper2"),
            "summary" to "This study demonstrates the effectiveness of the approach.",
            "confidence" to 0.92,
            "source_type" to "academic",
            "metadata" to mapOf("year" to 2024)
        )

        assertTrue(validator.validate(validEvidence).isValid)

        // Invalid: missing citations
        val noCitations = mapOf(
            "summary" to "Evidence without citations",
            "confidence" to 0.8,
            "source_type" to "blog"
        )
        assertFalse(validator.validate(noCitations).isValid)

        // Invalid: confidence out of range
        val highConfidence = mapOf(
            "citations" to listOf("source"),
            "summary" to "Valid summary",
            "confidence" to 1.5,  // Too high
            "source_type" to "news"
        )
        assertFalse(validator.validate(highConfidence).isValid)

        // Invalid: wrong source_type
        val wrongSourceType = mapOf(
            "citations" to listOf("source"),
            "summary" to "Valid summary",
            "confidence" to 0.8,
            "source_type" to "invalid_type"
        )
        assertFalse(validator.validate(wrongSourceType).isValid)
    }

    @Test
    fun `test validateAndWrap returns ToolResult`() {
        val validator = outputValidator {
            requireField("name")
        }

        val validOutput = mapOf("name" to "Alice")
        val validResult = validator.validateAndWrap(validOutput)
        assertTrue(validResult.success)

        val invalidOutput = mapOf("other" to "value")
        val invalidResult = validator.validateAndWrap(invalidOutput)
        assertFalse(invalidResult.success)
        assertTrue(invalidResult.error!!.contains("Output validation failed"))
    }

    @Test
    fun `test INTEGER type validation`() {
        val validator = outputValidator {
            fieldType("count", FieldType.INTEGER)
        }

        val validOutput = mapOf("count" to 42)
        assertTrue(validator.validate(validOutput).isValid)

        val invalidOutput = mapOf("count" to 42.5)
        assertFalse(validator.validate(invalidOutput).isValid)
    }

    @Test
    fun `test contextAwareTool with validation and caching together`() = runTest {
        var callCount = 0

        val tool = contextAwareTool("cached_validated_tool") {
            description = "Tool with both caching and validation"

            cache {
                ttl = 300
                maxSize = 100
            }

            validate {
                requireField("result")
                fieldType("result", FieldType.STRING)
            }

            execute { params, context ->
                callCount++
                mapOf("result" to "data_${callCount}")
            }
        }

        withAgentContext("tenantId" to "TENANT-A") {
            // First call
            val result1 = tool.execute(mapOf("id" to "123"))
            assertTrue(result1.isSuccess)
            assertTrue(result1.getOrNull()!!.success)
            assertEquals(1, callCount)

            // Second call - should be cached
            val result2 = tool.execute(mapOf("id" to "123"))
            assertTrue(result2.isSuccess)
            assertTrue(result2.getOrNull()!!.success)
            assertEquals(1, callCount)  // Still 1 because cached
        }
    }

    /**
     * Test: custom() alias for rule() should work identically
     */
    @Test
    fun `custom validation alias works same as rule`() = runTest {
        // Given: Tool using custom() alias
        val tool = contextAwareTool("test-custom-alias") {
            validate {
                custom("value must be positive") { output ->
                    val value = (output as? Map<*, *>)?.get("value") as? Number
                    (value?.toDouble() ?: 0.0) > 0.0
                }

                custom("description required") { output, _ ->
                    val desc = (output as? Map<*, *>)?.get("description") as? String
                    desc != null && desc.isNotBlank()
                }
            }

            execute { _, _ ->
                mapOf(
                    "value" to 10,
                    "description" to "Test"
                )
            }
        }

        // When: Execute with valid output
        val result = withAgentContext("tenantId" to "TEST") {
            tool.execute(emptyMap())
        }

        // Then: Should pass validation
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertTrue(toolResult.success)
    }

    /**
     * Test: custom() validation failure
     */
    @Test
    fun `custom validation should fail on invalid output`() = runTest {
        // Given: Tool with custom validation
        val tool = contextAwareTool("test-custom-fail") {
            validate {
                custom("array must not be empty") { output ->
                    val items = (output as? Map<*, *>)?.get("items") as? List<*>
                    items?.isNotEmpty() == true
                }
            }

            execute { _, _ ->
                mapOf("items" to emptyList<String>())
            }
        }

        // When: Execute with empty array
        val result = withAgentContext("tenantId" to "TEST") {
            tool.execute(emptyMap())
        }

        // Then: Should fail validation
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertFalse(toolResult.success)
        assertTrue(toolResult.error!!.contains("array must not be empty"))
    }
}
