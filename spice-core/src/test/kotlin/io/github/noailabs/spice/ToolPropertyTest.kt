package io.github.noailabs.spice

import io.github.noailabs.spice.model.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking

/**
 * 🧪 Property-Based Tests for Tool and ToolRegistry
 *
 * Verifies Tool behavior across various inputs and edge cases
 */
class ToolPropertyTest : StringSpec({

    beforeTest {
        ToolRegistry.clear()
    }

    "AgentTool should preserve name and description" {
        checkAll(
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.string(1..200).filter { it.isNotBlank() }
        ) { name, description ->
            val tool = agentTool(name) {
                description(description)
                implement { ToolResult.success("") }
            }

            tool.name shouldBe name
            tool.description shouldBe description
        }
    }

    "AgentTool execution should handle various parameter types" {
        val tool = agentTool("multi-param-tool") {
            description("Tool with multiple parameter types")

            parameters {
                string("text", "Text parameter")
                number("count", "Number parameter")
                boolean("flag", "Boolean parameter")
            }

            implement { params ->
                val text = params["text"] as? String ?: ""
                val count = (params["count"] as? Number)?.toInt() ?: 0
                val flag = params["flag"] as? Boolean ?: false

                ToolResult.success("$text-$count-$flag")
            }
        }

        checkAll(
            Arb.string(1..20),
            Arb.int(0..100),
            Arb.boolean()
        ) { text, count, flag ->
            runBlocking {
                val result = tool.execute(mapOf(
                    "text" to text,
                    "count" to count,
                    "flag" to flag
                ))

                result.success shouldBe true
                result.result shouldBe "$text-$count-$flag"
            }
        }
    }

    "ToolRegistry should handle concurrent registrations" {
        checkAll(Arb.list(Arb.string(1..20).filter { it.isNotBlank() }, 1..50)) { names ->
            ToolRegistry.clear()

            names.forEach { name ->
                val tool = agentTool(name) {
                    description("Test tool")
                    implement { ToolResult.success("") }
                }
                ToolRegistry.register(tool)
            }

            val uniqueNames = names.toSet()
            ToolRegistry.size() shouldBe uniqueNames.size
        }
    }

    "ToolRegistry tags should be searchable" {
        checkAll(
            Arb.string(1..20).filter { it.isNotBlank() },
            Arb.list(Arb.string(1..15).filter { it.isNotBlank() }, 1..10)
        ) { toolName, tags ->
            ToolRegistry.clear()

            val tool = agentTool(toolName) {
                description("Tagged tool")
                tags(*tags.toTypedArray())
                implement { ToolResult.success("") }
            }

            ToolRegistry.register(tool)

            tags.forEach { tag ->
                val found = ToolRegistry.getByTag(tag)
                found shouldHaveSize 1
                found.first().name shouldBe toolName
            }
        }
    }

    "ToolResult success should preserve output" {
        checkAll(Arb.string(0..1000)) { output ->
            val result = ToolResult.success(output)

            result.success shouldBe true
            result.result shouldBe output
            result.error shouldBe null
        }
    }

    "ToolResult error should preserve error message" {
        checkAll(Arb.string(1..200).filter { it.isNotBlank() }) { errorMsg ->
            val result = ToolResult.error(errorMsg)

            result.success shouldBe false
            result.error shouldBe errorMsg
        }
    }

    "AgentTool with metadata should preserve all data" {
        checkAll(Arb.string(1..20).filter { it.isNotBlank() }) { name ->
            ToolRegistry.clear()

            val tool = agentTool(name) {
                description("Tool with metadata")
                metadata("version", "1.0")
                metadata("author", "test")
                metadata("category", "utility")
                implement { ToolResult.success("") }
            }

            ToolRegistry.register(tool)

            val agentTools = ToolRegistry.getAgentTools()
            val (_, metadata) = agentTools.first { it.first.name == name }

            val storedMeta = metadata["metadata"] as? Map<*, *>
            storedMeta shouldNotBe null
            storedMeta?.get("version") shouldBe "1.0"
            storedMeta?.get("author") shouldBe "test"
            storedMeta?.get("category") shouldBe "utility"
        }
    }

    "ToolRegistry namespace should isolate tools" {
        checkAll(
            Arb.string(1..20).filter { it.isNotBlank() },
            Arb.string(1..15).filter { it.isNotBlank() },
            Arb.string(1..15).filter { it.isNotBlank() }
        ) { toolName, namespace1, namespace2 ->
            ToolRegistry.clear()

            val tool = agentTool(toolName) {
                description("Namespaced tool")
                implement { ToolResult.success("") }
            }

            ToolRegistry.register(tool, namespace1)

            // Should be in namespace1
            ToolRegistry.getTool(toolName, namespace1) shouldNotBe null

            // Should not be in namespace2
            ToolRegistry.getTool(toolName, namespace2) shouldBe null

            // Should not be in global
            ToolRegistry.getTool(toolName, "global") shouldBe null
        }
    }

    "ToolSchema should correctly represent parameters" {
        val schema = ToolSchema(
            name = "test-tool",
            description = "Test tool",
            parameters = mapOf(
                "param1" to ParameterSchema("string", "First parameter", required = true),
                "param2" to ParameterSchema("number", "Second parameter", required = false)
            )
        )

        schema.name shouldBe "test-tool"
        schema.description shouldBe "Test tool"
        schema.parameters.keys shouldContain "param1"
        schema.parameters.keys shouldContain "param2"
        schema.parameters["param1"]?.required shouldBe true
        schema.parameters["param2"]?.required shouldBe false
    }

    "AgentTool execution should handle errors gracefully" {
        val errorTool = agentTool("error-tool") {
            description("Tool that may fail")

            parameters {
                number("input", "Input number")
            }

            implement { params ->
                val input = (params["input"] as? Number)?.toInt() ?: 0

                when {
                    input < 0 -> ToolResult.error("Input must be non-negative")
                    input > 100 -> ToolResult.error("Input must be <= 100")
                    else -> ToolResult.success("Valid: $input")
                }
            }
        }

        checkAll(Arb.int(-50..150)) { input ->
            runBlocking {
                val result = errorTool.execute(mapOf("input" to input))

                when {
                    input < 0 -> {
                        result.success shouldBe false
                        result.error shouldBe "Input must be non-negative"
                    }
                    input > 100 -> {
                        result.success shouldBe false
                        result.error shouldBe "Input must be <= 100"
                    }
                    else -> {
                        result.success shouldBe true
                        result.result shouldBe "Valid: $input"
                    }
                }
            }
        }
    }

    "ToolRegistry getBySource should filter correctly" {
        ToolRegistry.clear()

        val agentTool1 = agentTool("agent-tool-1") {
            description("Agent tool 1")
            implement { ToolResult.success("") }
        }

        val agentTool2 = agentTool("agent-tool-2") {
            description("Agent tool 2")
            implement { ToolResult.success("") }
        }

        ToolRegistry.register(agentTool1)
        ToolRegistry.register(agentTool2)

        val agentTools = ToolRegistry.getBySource("agent-tool")
        agentTools shouldHaveSize 2
    }
})
