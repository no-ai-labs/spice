package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.EngineDecisionNodeBuilder
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.routing.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for EngineDecisionNode and decisionNode() DSL.
 */
class EngineDecisionNodeTest {

    @Test
    fun `decisionNode routes YES result to correct target`() = runTest {
        // Given: Engine that always returns YES
        val engine = DecisionEngines.always(StandardResult.YES)

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("yes-handler")
                .on(StandardResult.NO).to("no-handler")
                .otherwise("default")

            output("yes-handler") { "YES_RESULT" }
            output("no-handler") { "NO_RESULT" }
            output("default") { "DEFAULT_RESULT" }
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test input", "user")

        // When
        val result = runner.execute(graph, message)

        // Then
        assertTrue(result.isSuccess)
        assertEquals("YES_RESULT", result.getOrThrow().content)
    }

    @Test
    fun `decisionNode routes NO result to correct target`() = runTest {
        // Given
        val engine = DecisionEngines.always(StandardResult.NO)

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("yes-handler")
                .on(StandardResult.NO).to("no-handler")

            output("yes-handler") { "YES" }
            output("no-handler") { "NO" }
        }

        val runner = DefaultGraphRunner()
        val result = runner.execute(graph, SpiceMessage.create("test", "user"))

        // Then
        assertTrue(result.isSuccess)
        assertEquals("NO", result.getOrThrow().content)
    }

    @Test
    fun `decisionNode uses fallback when no mapping matches`() = runTest {
        // Given: Engine that returns UNCERTAIN (not mapped)
        val engine = DecisionEngines.always(StandardResult.UNCERTAIN)

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("yes-handler")
                .on(StandardResult.NO).to("no-handler")
                .otherwise("fallback-handler")

            output("yes-handler") { "YES" }
            output("no-handler") { "NO" }
            output("fallback-handler") { "FALLBACK" }
        }

        val runner = DefaultGraphRunner()
        val result = runner.execute(graph, SpiceMessage.create("test", "user"))

        // Then
        assertTrue(result.isSuccess)
        assertEquals("FALLBACK", result.getOrThrow().content)
    }

    @Test
    fun `decisionNode fails when no mapping matches and no fallback`() = runTest {
        // Given: Engine that returns UNCERTAIN (not mapped, no fallback)
        val engine = DecisionEngines.always(StandardResult.UNCERTAIN)

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("yes-handler")
                .on(StandardResult.NO).to("no-handler")
            // No .otherwise()

            output("yes-handler") { "YES" }
            output("no-handler") { "NO" }
        }

        val runner = DefaultGraphRunner()
        val result = runner.execute(graph, SpiceMessage.create("test", "user"))

        // Then
        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertEquals("ROUTING_ERROR", error.code)
        assertTrue(error.message.contains("No target mapping"))
        assertTrue(error.message.contains("UNCERTAIN"))
    }

    @Test
    fun `decisionNode stores decision metadata in message`() = runTest {
        // Given
        val engine = DecisionEngines.always(StandardResult.YES)

        // Store metadata in data for later verification
        var capturedResult: String? = null
        var capturedTarget: String? = null
        var capturedEngine: String? = null
        var capturedNodeId: String? = null

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("handler")

            output("handler") { msg ->
                // Capture the decision metadata
                capturedResult = msg.getData<String>("_decisionResult")
                capturedTarget = msg.getData<String>("_decisionTarget")
                capturedEngine = msg.getData<String>("_decisionEngine")
                capturedNodeId = msg.getData<String>("_decisionNodeId")
                "DONE"
            }
        }

        val runner = DefaultGraphRunner()
        val result = runner.execute(graph, SpiceMessage.create("test", "user"))

        // Then
        assertTrue(result.isSuccess)
        assertEquals("YES", capturedResult)
        assertEquals("handler", capturedTarget)
        assertEquals("always[YES]", capturedEngine)
        assertEquals("route", capturedNodeId)
    }

    @Test
    fun `decisionNode with data-based engine`() = runTest {
        // Given: Engine that routes based on data field
        val engine = DecisionEngines.fromData("status", mapOf(
            "approved" to StandardResult.YES,
            "rejected" to StandardResult.NO
        ))

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("approved-flow")
                .on(StandardResult.NO).to("rejected-flow")
                .otherwise("pending-flow")

            output("approved-flow") { "APPROVED" }
            output("rejected-flow") { "REJECTED" }
            output("pending-flow") { "PENDING" }
        }

        val runner = DefaultGraphRunner()

        // Test approved
        val approved = runner.execute(
            graph,
            SpiceMessage.create("test", "user").withData(mapOf("status" to "approved"))
        )
        assertEquals("APPROVED", approved.getOrThrow().content)

        // Test rejected
        val rejected = runner.execute(
            graph,
            SpiceMessage.create("test", "user").withData(mapOf("status" to "rejected"))
        )
        assertEquals("REJECTED", rejected.getOrThrow().content)

        // Test unknown status (fallback)
        val pending = runner.execute(
            graph,
            SpiceMessage.create("test", "user").withData(mapOf("status" to "unknown"))
        )
        assertEquals("PENDING", pending.getOrThrow().content)
    }

    @Test
    fun `decisionNode with SelectionResult per-option routing`() = runTest {
        // Given: Engine that returns selection based on content
        val engine = DecisionEngines.create("option-router") { msg ->
            val content = msg.content
            when {
                content.contains("standard") -> SelectionResult.option("standard")
                content.contains("premium") -> SelectionResult.option("premium")
                else -> SelectionResult.option("basic")
            }
        }

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(SelectionResult.option("standard")).to("standard-flow")
                .on(SelectionResult.option("premium")).to("premium-flow")
                .otherwise("basic-flow")

            output("standard-flow") { "STANDARD" }
            output("premium-flow") { "PREMIUM" }
            output("basic-flow") { "BASIC" }
        }

        val runner = DefaultGraphRunner()

        // Test standard
        val standard = runner.execute(graph, SpiceMessage.create("I want standard", "user"))
        assertEquals("STANDARD", standard.getOrThrow().content)

        // Test premium
        val premium = runner.execute(graph, SpiceMessage.create("I want premium", "user"))
        assertEquals("PREMIUM", premium.getOrThrow().content)

        // Test basic (fallback)
        val basic = runner.execute(graph, SpiceMessage.create("I want something", "user"))
        assertEquals("BASIC", basic.getOrThrow().content)
    }

    @Test
    fun `decisionNode with DelegationResult`() = runTest {
        // Given
        val engine = DecisionEngines.always(DelegationResult.DELEGATE_TO_LLM("custom prompt"))

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("simple-handler")
                .on(DelegationResult.DELEGATE_TO_LLM()).to("llm-handler")
                .otherwise("fallback")

            output("simple-handler") { "SIMPLE" }
            output("llm-handler") { "LLM" }
            output("fallback") { "FALLBACK" }
        }

        val runner = DefaultGraphRunner()
        val result = runner.execute(graph, SpiceMessage.create("test", "user"))

        // Then: Should route to LLM handler (resultId matches)
        assertTrue(result.isSuccess)
        assertEquals("LLM", result.getOrThrow().content)
    }

    @Test
    fun `decisionNode build fails without engine`() {
        // When/Then: Call build() directly without setting engine
        // requireNotNull throws IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            EngineDecisionNodeBuilder("route").build()
        }
    }

    @Test
    fun `decisionNode build fails without mappings or fallback`() {
        // When/Then: Set engine but no mappings
        // require throws IllegalArgumentException
        val engine = DecisionEngines.always(StandardResult.YES)
        assertFailsWith<IllegalArgumentException> {
            EngineDecisionNodeBuilder("route")
                .by(engine)
                .build()  // No .on().to() or .otherwise()
        }
    }

    @Test
    fun `CustomResult routing works`() = runTest {
        // Given: Custom result types
        val NEEDS_APPROVAL = CustomResult("NEEDS_APPROVAL", "Requires approval")
        val engine = DecisionEngines.always(NEEDS_APPROVAL)

        val graph = graph("test") {
            decisionNode("route")
                .by(engine)
                .on(StandardResult.YES).to("auto-approve")
                .on(NEEDS_APPROVAL).to("approval-queue")
                .otherwise("fallback")

            output("auto-approve") { "AUTO" }
            output("approval-queue") { "QUEUE" }
            output("fallback") { "FALLBACK" }
        }

        val runner = DefaultGraphRunner()
        val result = runner.execute(graph, SpiceMessage.create("test", "user"))

        assertTrue(result.isSuccess)
        assertEquals("QUEUE", result.getOrThrow().content)
    }
}
