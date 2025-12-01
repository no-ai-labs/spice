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

    // ==================== Tool Metadata Routing Tests ====================

    @Test
    fun `whenToolMetadata routes based on tool metadata condition`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("escalate", "escalation-handler")
                    .whenToolMetadata("action_type") { it == "escalate" }
                branch("normal", "normal-handler")
                    .otherwise()
            }

            output("escalation-handler") { "Escalated" }
            output("normal-handler") { "Normal" }
        }

        val runner = DefaultGraphRunner()

        // Test with escalate action_type
        val escalateMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("action_type" to "escalate")
            ))
        val escalateResult = runner.execute(g, escalateMessage)

        assertTrue(escalateResult is SpiceResult.Success)
        assertEquals("Escalated", (escalateResult as SpiceResult.Success).value.content)

        // Test without escalate action_type
        val normalMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("action_type" to "normal")
            ))
        val normalResult = runner.execute(g, normalMessage)

        assertTrue(normalResult is SpiceResult.Success)
        assertEquals("Normal", (normalResult as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenToolMetadataEquals routes based on exact value match`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("high", "high-handler")
                    .whenToolMetadataEquals("priority", 100)
                branch("medium", "medium-handler")
                    .whenToolMetadataEquals("priority", 50)
                branch("low", "low-handler")
                    .otherwise()
            }

            output("high-handler") { "High Priority" }
            output("medium-handler") { "Medium Priority" }
            output("low-handler") { "Low Priority" }
        }

        val runner = DefaultGraphRunner()

        // Test high priority
        val highMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("priority" to 100)
            ))
        val highResult = runner.execute(g, highMessage)
        assertEquals("High Priority", (highResult as SpiceResult.Success).value.content)

        // Test medium priority
        val mediumMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("priority" to 50)
            ))
        val mediumResult = runner.execute(g, mediumMessage)
        assertEquals("Medium Priority", (mediumResult as SpiceResult.Success).value.content)

        // Test low priority (falls through to otherwise)
        val lowMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("priority" to 10)
            ))
        val lowResult = runner.execute(g, lowMessage)
        assertEquals("Low Priority", (lowResult as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenToolActionType routes based on action_type metadata`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("escalate", "escalation-handler")
                    .whenToolActionType("escalate")
                branch("cancel", "cancel-handler")
                    .whenToolActionType("cancel")
                branch("default", "default-handler")
                    .otherwise()
            }

            output("escalation-handler") { "Escalated" }
            output("cancel-handler") { "Cancelled" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // Test escalate
        val escalateMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("action_type" to "escalate")
            ))
        val escalateResult = runner.execute(g, escalateMessage)
        assertEquals("Escalated", (escalateResult as SpiceResult.Success).value.content)

        // Test cancel
        val cancelMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("action_type" to "cancel")
            ))
        val cancelResult = runner.execute(g, cancelMessage)
        assertEquals("Cancelled", (cancelResult as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenToolName routes based on tool_name metadata`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("search", "search-handler")
                    .whenToolName("web_search")
                branch("calculator", "calc-handler")
                    .whenToolName("calculator")
                branch("default", "default-handler")
                    .otherwise()
            }

            output("search-handler") { "Search Result" }
            output("calc-handler") { "Calculation Result" }
            output("default-handler") { "Default Result" }
        }

        val runner = DefaultGraphRunner()

        // Test web_search - tool_name is stored at top-level data, not in _tool.lastMetadata
        val searchMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("tool_name" to "web_search"))
        val searchResult = runner.execute(g, searchMessage)
        assertEquals("Search Result", (searchResult as SpiceResult.Success).value.content)

        // Test calculator
        val calcMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("tool_name" to "calculator"))
        val calcResult = runner.execute(g, calcMessage)
        assertEquals("Calculation Result", (calcResult as SpiceResult.Success).value.content)
    }

    @Test
    fun `tool metadata helpers with short syntax work`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "escalation-handler".whenToolActionType("escalate")
                "normal-handler".otherwise()
            }

            output("escalation-handler") { "Escalated" }
            output("normal-handler") { "Normal" }
        }

        val runner = DefaultGraphRunner()

        val escalateMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("action_type" to "escalate")
            ))
        val result = runner.execute(g, escalateMessage)
        assertEquals("Escalated", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `tool metadata routing handles missing metadata gracefully`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("escalate", "escalation-handler")
                    .whenToolActionType("escalate")
                branch("fallback", "fallback-handler")
                    .otherwise()
            }

            output("escalation-handler") { "Escalated" }
            output("fallback-handler") { "Fallback" }
        }

        val runner = DefaultGraphRunner()

        // Test with no _tool.lastMetadata
        val noMetadataMessage = SpiceMessage.create("test", "user")
        val result = runner.execute(g, noMetadataMessage)
        assertEquals("Fallback", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `tool metadata routing handles empty metadata map`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("escalate", "escalation-handler")
                    .whenToolMetadata("action_type") { it != null }
                branch("fallback", "fallback-handler")
                    .otherwise()
            }

            output("escalation-handler") { "Escalated" }
            output("fallback-handler") { "Fallback" }
        }

        val runner = DefaultGraphRunner()

        // Test with empty metadata map
        val emptyMetadataMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to emptyMap<String, Any>()
            ))
        val result = runner.execute(g, emptyMetadataMessage)
        assertEquals("Fallback", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenToolMetadataEquals with short syntax works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "priority-handler".whenToolMetadataEquals("priority", "high")
                "default-handler".otherwise()
            }

            output("priority-handler") { "High Priority" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        val priorityMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "_tool.lastMetadata" to mapOf("priority" to "high")
            ))
        val result = runner.execute(g, priorityMessage)
        assertEquals("High Priority", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenToolName with short syntax works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "search-handler".whenToolName("search")
                "default-handler".otherwise()
            }

            output("search-handler") { "Search" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // tool_name is stored at top-level data, not in _tool.lastMetadata
        val searchMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("tool_name" to "search"))
        val result = runner.execute(g, searchMessage)
        assertEquals("Search", (result as SpiceResult.Success).value.content)
    }

    // ==================== Tool Success/Failed Routing Tests ====================

    @Test
    fun `whenToolSuccess routes based on tool_success true`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("success", "success-handler")
                    .whenToolSuccess()
                branch("failed", "failed-handler")
                    .whenToolFailed()
                branch("default", "default-handler")
                    .otherwise()
            }

            output("success-handler") { "Success" }
            output("failed-handler") { "Failed" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // Test with tool_success = true
        val successMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("tool_success" to true))
        val successResult = runner.execute(g, successMessage)
        assertEquals("Success", (successResult as SpiceResult.Success).value.content)

        // Test with tool_success = false
        val failedMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("tool_success" to false))
        val failedResult = runner.execute(g, failedMessage)
        assertEquals("Failed", (failedResult as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenToolSuccess with short syntax works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "success-handler".whenToolSuccess()
                "failed-handler".whenToolFailed()
                "default-handler".otherwise()
            }

            output("success-handler") { "Success" }
            output("failed-handler") { "Failed" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        val successMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("tool_success" to true))
        val result = runner.execute(g, successMessage)
        assertEquals("Success", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenToolFailed routes to fallback when tool_success is missing`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "success-handler".whenToolSuccess()
                "failed-handler".whenToolFailed()
                "default-handler".otherwise()
            }

            output("success-handler") { "Success" }
            output("failed-handler") { "Failed" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // Test with no tool_success - should fall through to default
        val noStatusMessage = SpiceMessage.create("test", "user")
        val result = runner.execute(g, noStatusMessage)
        assertEquals("Default", (result as SpiceResult.Success).value.content)
    }

    // ==================== HITL Prefix Routing Tests (1.5.5+) ====================

    @Test
    fun `whenHitlPrefix routes when canonical starts with prefix`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("confirm", "confirm-handler")
                    .whenHitlPrefix("confirm_")
                branch("cancel", "cancel-handler")
                    .whenHitlPrefix("cancel_")
                branch("default", "default-handler")
                    .otherwise()
            }

            output("confirm-handler") { "Confirmed" }
            output("cancel-handler") { "Cancelled" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // Test confirm_yes matches "confirm_" prefix
        val confirmMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("confirm_yes").toMap()
            ))
        val confirmResult = runner.execute(g, confirmMessage)
        assertEquals("Confirmed", (confirmResult as SpiceResult.Success).value.content)

        // Test cancel_order matches "cancel_" prefix
        val cancelMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("cancel_order").toMap()
            ))
        val cancelResult = runner.execute(g, cancelMessage)
        assertEquals("Cancelled", (cancelResult as SpiceResult.Success).value.content)

        // Test other_action falls through to default
        val otherMessage = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("other_action").toMap()
            ))
        val otherResult = runner.execute(g, otherMessage)
        assertEquals("Default", (otherResult as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlPrefix with ignoreCase true matches case-insensitively`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("confirm", "confirm-handler")
                    .whenHitlPrefix("CONFIRM_", ignoreCase = true)
                branch("default", "default-handler")
                    .otherwise()
            }

            output("confirm-handler") { "Confirmed" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // Test lowercase "confirm_yes" matches uppercase prefix with ignoreCase
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("confirm_yes").toMap()
            ))
        val result = runner.execute(g, message)
        assertEquals("Confirmed", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlPrefix with ignoreCase false is case-sensitive`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("confirm", "confirm-handler")
                    .whenHitlPrefix("CONFIRM_", ignoreCase = false)
                branch("default", "default-handler")
                    .otherwise()
            }

            output("confirm-handler") { "Confirmed" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // Test lowercase "confirm_yes" does NOT match uppercase prefix
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("confirm_yes").toMap()
            ))
        val result = runner.execute(g, message)
        assertEquals("Default", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlPrefix shorthand syntax works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "confirm-handler".whenHitlPrefix("confirm_")
                "cancel-handler".whenHitlPrefix("cancel_")
                "default-handler".otherwise()
            }

            output("confirm-handler") { "Confirmed" }
            output("cancel-handler") { "Cancelled" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("confirm_action").toMap()
            ))
        val result = runner.execute(g, message)
        assertEquals("Confirmed", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlStartsWith alias works same as whenHitlPrefix`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "confirm-handler".whenHitlStartsWith("confirm_")
                "default-handler".otherwise()
            }

            output("confirm-handler") { "Confirmed" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("confirm_yes").toMap()
            ))
        val result = runner.execute(g, message)
        assertEquals("Confirmed", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlContains shorthand syntax works`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "premium-handler".whenHitlContains("premium")
                "basic-handler".otherwise()
            }

            output("premium-handler") { "Premium" }
            output("basic-handler") { "Basic" }
        }

        val runner = DefaultGraphRunner()

        // Test multi-selection containing "premium"
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.multi(listOf("feature_basic", "feature_premium")).toMap()
            ))
        val result = runner.execute(g, message)
        assertEquals("Premium", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlContains with ignoreCase true matches case-insensitively`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("premium", "premium-handler")
                    .whenHitlContains("PREMIUM", ignoreCase = true)
                branch("basic", "basic-handler")
                    .otherwise()
            }

            output("premium-handler") { "Premium" }
            output("basic-handler") { "Basic" }
        }

        val runner = DefaultGraphRunner()

        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("feature_premium_plus").toMap()
            ))
        val result = runner.execute(g, message)
        assertEquals("Premium", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlContains default ignoreCase false maintains backward compatibility`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                branch("premium", "premium-handler")
                    .whenHitlContains("PREMIUM")  // Default ignoreCase = false
                branch("basic", "basic-handler")
                    .otherwise()
            }

            output("premium-handler") { "Premium" }
            output("basic-handler") { "Basic" }
        }

        val runner = DefaultGraphRunner()

        // Lowercase "premium" should NOT match uppercase "PREMIUM" with default ignoreCase=false
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf(
                "hitl" to io.github.noailabs.spice.hitl.result.HitlResult.single("feature_premium").toMap()
            ))
        val result = runner.execute(g, message)
        assertEquals("Basic", (result as SpiceResult.Success).value.content)
    }

    @Test
    fun `whenHitlPrefix handles null hitl data gracefully`() = runTest {
        val g = graph("test-workflow") {
            decision("route") {
                "confirm-handler".whenHitlPrefix("confirm_")
                "default-handler".otherwise()
            }

            output("confirm-handler") { "Confirmed" }
            output("default-handler") { "Default" }
        }

        val runner = DefaultGraphRunner()

        // No hitl data - should fall through to default
        val message = SpiceMessage.create("test", "user")
        val result = runner.execute(g, message)
        assertEquals("Default", (result as SpiceResult.Success).value.content)
    }
}
