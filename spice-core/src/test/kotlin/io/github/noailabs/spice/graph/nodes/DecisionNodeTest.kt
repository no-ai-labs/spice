package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class DecisionNodeTest {

    @Test
    fun `basic decision node selects correct branch`() = runTest {
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("type" to "A"))

        val node = DecisionNode(
            id = "route",
            branches = listOf(
                DecisionBranch("type-a", "handler-a") { msg ->
                    msg.getData<String>("type") == "A"
                },
                DecisionBranch("type-b", "handler-b") { msg ->
                    msg.getData<String>("type") == "B"
                }
            )
        )

        val result = node.run(message)

        assertTrue(result is SpiceResult.Success)
        val output = (result as SpiceResult.Success).value
        assertEquals("handler-a", output.getData<String>("_selectedBranch"))
        assertEquals("route", output.getData<String>("_decisionNodeId"))
        assertEquals("type-a", output.getData<String>("_branchName"))
    }

    @Test
    fun `decision node selects second branch when first doesn't match`() = runTest {
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("type" to "B"))

        val node = DecisionNode(
            id = "route",
            branches = listOf(
                DecisionBranch("type-a", "handler-a") { msg ->
                    msg.getData<String>("type") == "A"
                },
                DecisionBranch("type-b", "handler-b") { msg ->
                    msg.getData<String>("type") == "B"
                }
            )
        )

        val result = node.run(message)

        assertTrue(result is SpiceResult.Success)
        val output = (result as SpiceResult.Success).value
        assertEquals("handler-b", output.getData<String>("_selectedBranch"))
    }

    @Test
    fun `decision node fails when no branch matches`() = runTest {
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("type" to "C"))

        val node = DecisionNode(
            id = "route",
            branches = listOf(
                DecisionBranch("type-a", "handler-a") { msg ->
                    msg.getData<String>("type") == "A"
                },
                DecisionBranch("type-b", "handler-b") { msg ->
                    msg.getData<String>("type") == "B"
                }
            )
        )

        val result = node.run(message)

        assertTrue(result is SpiceResult.Failure)
        assertTrue((result as SpiceResult.Failure).error.message!!.contains("No decision branch matched"))
    }

    @Test
    fun `otherwise branch always matches`() = runTest {
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("type" to "UNKNOWN"))

        val node = DecisionNode(
            id = "route",
            branches = listOf(
                DecisionBranch("type-a", "handler-a") { msg ->
                    msg.getData<String>("type") == "A"
                },
                DecisionBranch("default", "fallback") { true }
            )
        )

        val result = node.run(message)

        assertTrue(result is SpiceResult.Success)
        val output = (result as SpiceResult.Success).value
        assertEquals("fallback", output.getData<String>("_selectedBranch"))
        assertEquals("default", output.getData<String>("_branchName"))
    }

    @Test
    fun `decision node preserves existing data`() = runTest {
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "type" to "A",
                "existingKey" to "existingValue"
            ))

        val node = DecisionNode(
            id = "route",
            branches = listOf(
                DecisionBranch("type-a", "handler-a") { msg ->
                    msg.getData<String>("type") == "A"
                }
            )
        )

        val result = node.run(message)

        assertTrue(result is SpiceResult.Success)
        val output = (result as SpiceResult.Success).value
        assertEquals("existingValue", output.getData<String>("existingKey"))
        assertEquals("handler-a", output.getData<String>("_selectedBranch"))
    }

    @Test
    fun `decision node handles exception in branch condition`() = runTest {
        val message = SpiceMessage.create("test", "user")

        val node = DecisionNode(
            id = "route",
            branches = listOf(
                DecisionBranch("error", "error-handler") { _ ->
                    throw RuntimeException("Branch evaluation error")
                }
            )
        )

        val result = node.run(message)

        assertTrue(result is SpiceResult.Failure)
        assertTrue((result as SpiceResult.Failure).error.message!!.contains("Decision branch evaluation failed"))
    }

    @Test
    fun `decision DSL with branch() creates correct edges`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("type-a", "handler-a")
                    .whenData("type") { it == "A" }
                branch("type-b", "handler-b")
                    .whenData("type") { it == "B" }
            }

            output("handler-a")
            output("handler-b")
        }

        // Check edges were generated
        val decisionEdges = g.edges.filter { it.from == "route" }
        assertEquals(2, decisionEdges.size)

        // Test edge conditions
        val messageA = SpiceMessage.create("test", "user")
            .withData(mapOf("_selectedBranch" to "handler-a"))
        val messageB = SpiceMessage.create("test", "user")
            .withData(mapOf("_selectedBranch" to "handler-b"))

        val edgeToA = decisionEdges.find { it.to == "handler-a" }
        val edgeToB = decisionEdges.find { it.to == "handler-b" }

        assertNotNull(edgeToA)
        assertNotNull(edgeToB)
        assertTrue(edgeToA!!.condition(messageA))
        assertFalse(edgeToA.condition(messageB))
        assertTrue(edgeToB!!.condition(messageB))
        assertFalse(edgeToB.condition(messageA))
    }

    @Test
    fun `decision DSL short syntax works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "handler-a".whenData("type") { it == "A" }
                "handler-b".whenData("type") { it == "B" }
                "default".otherwise()
            }

            output("handler-a")
            output("handler-b")
            output("default")
        }

        val decisionEdges = g.edges.filter { it.from == "route" }
        assertEquals(3, decisionEdges.size)
    }

    @Test
    fun `decision node integrates with graph runner`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("type-a", "output-a")
                    .whenData("type") { it == "A" }
                branch("type-b", "output-b")
                    .whenData("type") { it == "B" }
            }

            output("output-a") { "Result A" }
            output("output-b") { "Result B" }
        }

        val runner = DefaultGraphRunner()

        // Test routing to A
        val messageA = SpiceMessage.create("test", "user")
            .withData(mapOf("type" to "A"))
        val resultA = runner.execute(g, messageA)

        if (resultA is SpiceResult.Failure) {
            println("Test failed: ${resultA.error.message}")
            resultA.error.cause?.printStackTrace()
        }
        assertTrue(resultA is SpiceResult.Success, "Expected success but got: $resultA")
        assertEquals("Result A", (resultA as SpiceResult.Success).value.content)

        // Test routing to B
        val messageB = SpiceMessage.create("test", "user")
            .withData(mapOf("type" to "B"))
        val resultB = runner.execute(g, messageB)

        assertTrue(resultB is SpiceResult.Success)
        assertEquals("Result B", (resultB as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenMetadata condition works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("premium", "premium-handler")
                    .whenMetadata("tier") { it == "premium" }
                branch("free", "free-handler")
                    .otherwise()
            }

            output("premium-handler") { "Premium" }
            output("free-handler") { "Free" }
        }

        val runner = DefaultGraphRunner()

        val premiumMessage = SpiceMessage.create("test", "user")
            .withMetadata(mapOf("tier" to "premium"))
        val result = runner.execute(g, premiumMessage)

        assertTrue(result is SpiceResult.Success)
        assertEquals("Premium", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenContent condition works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("yes", "confirm-handler")
                    .whenContent { it.contains("yes", ignoreCase = true) }
                branch("no", "cancel-handler")
                    .otherwise()
            }

            output("confirm-handler") { "Confirmed" }
            output("cancel-handler") { "Cancelled" }
        }

        val runner = DefaultGraphRunner()

        val yesMessage = SpiceMessage.create("Yes, please", "user")
        val result = runner.execute(g, yesMessage)

        assertTrue(result is SpiceResult.Success)
        assertEquals("Confirmed", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenDataEquals helper works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("status-active", "active-handler")
                    .whenDataEquals("status", "active")
                branch("status-inactive", "inactive-handler")
                    .whenDataEquals("status", "inactive")
            }

            output("active-handler") { "Active" }
            output("inactive-handler") { "Inactive" }
        }

        val runner = DefaultGraphRunner()

        val activeMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("status" to "active"))
        val result = runner.execute(g, activeMessage)

        assertTrue(result is SpiceResult.Success)
        assertEquals("Active", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `complex whenMessage condition works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("high-priority", "urgent-handler")
                    .whenMessage { msg ->
                        val score = msg.getData<Int>("score") ?: 0
                        val tier = msg.getMetadata<String>("tier")
                        score > 80 && tier == "premium"
                    }
                branch("normal", "normal-handler")
                    .otherwise()
            }

            output("urgent-handler") { "Urgent" }
            output("normal-handler") { "Normal" }
        }

        val runner = DefaultGraphRunner()

        // High priority: score > 80 AND premium tier
        val urgentMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("score" to 90))
            .withMetadata(mapOf("tier" to "premium"))
        val urgentResult = runner.execute(g, urgentMessage)

        assertTrue(urgentResult is SpiceResult.Success)
        assertEquals("Urgent", (urgentResult as SpiceResult.Success).value.content)

        // Normal: score > 80 but NOT premium
        val normalMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("score" to 90))
            .withMetadata(mapOf("tier" to "free"))
        val normalResult = runner.execute(g, normalMessage)

        assertTrue(normalResult is SpiceResult.Success)
        assertEquals("Normal", (normalResult as SpiceResult.Success).value.content)
    }

    @Test
    fun `multiple otherwise() calls throws exception`() = runTest {
        val exception = assertThrows<IllegalArgumentException> {
            graph("test-workflow") {
                decision("route") {
                    "handler-a".whenData("type") { it == "A" }
                    "default1".otherwise()
                    "default2".otherwise()  // Should throw
                }

                output("handler-a")
                output("default1")
                output("default2")
            }
        }

        assertTrue(exception.message!!.contains("Only one fallback branch is allowed"))
    }

    @Test
    fun `multiple decision nodes in sequence work`() = runTest {
        val g = graph("test-workflow") {
            decision("first-check") {
                branch("proceed", "second-check")
                    .whenData("valid") { it == true }
                branch("invalid", "error-output")
                    .otherwise()
            }

            decision("second-check") {
                branch("type-a", "output-a")
                    .whenData("type") { it == "A" }
                branch("type-b", "output-b")
                    .otherwise()
            }

            output("output-a") { "Type A" }
            output("output-b") { "Type B" }
            output("error-output") { "Invalid" }
        }

        val runner = DefaultGraphRunner()

        // Valid + Type A
        val messageA = SpiceMessage.create("test", "user")
            .withData(mapOf("valid" to true, "type" to "A"))
        val resultA = runner.execute(g, messageA)
        assertEquals("Type A", (resultA as SpiceResult.Success).value.content)

        // Valid + Type B
        val messageB = SpiceMessage.create("test", "user")
            .withData(mapOf("valid" to true, "type" to "B"))
        val resultB = runner.execute(g, messageB)
        assertEquals("Type B", (resultB as SpiceResult.Success).value.content)

        // Invalid
        val invalidMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("valid" to false))
        val invalidResult = runner.execute(g, invalidMessage)
        assertEquals("Invalid", (invalidResult as SpiceResult.Success).value.content)
    }
}
