package io.github.noailabs.spice.hitl

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.HumanInteraction
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.nodes.HumanResponse
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HumanNodeTest {

    @Test
    fun `test basic approval workflow with HumanNode`() = runTest {
        // Given: A simple approval graph
        val draftAgent = object : Agent {
            override val id = "draft-agent"
            override val name = "Draft Creator"
            override val description = "Creates drafts"
            override val capabilities = listOf("drafting")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                return SpiceResult.success(comm.reply("Draft created", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val publishAgent = object : Agent {
            override val id = "publish-agent"
            override val name = "Publisher"
            override val description = "Publishes content"
            override val capabilities = listOf("publishing")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                return SpiceResult.success(comm.reply("Published!", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val approvalGraph = graph("approval-workflow") {
            agent("draft", draftAgent)

            // Human reviews and approves/rejects
            humanNode(
                id = "review",
                prompt = "Please review the draft",
                options = listOf(
                    HumanOption("approve", "Approve", "Approve draft and continue"),
                    HumanOption("reject", "Reject", "Reject draft and rewrite")
                )
            )

            // Conditional branching
            edge("review", "publish") { result ->
                (result.data as? HumanResponse)?.selectedOption == "approve"
            }

            agent("publish", publishAgent)
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Step 1: Start graph execution (should pause at HumanNode)
        val initialResult = runner.runWithCheckpoint(
            graph = approvalGraph,
            input = mapOf("content" to "Initial draft"),
            store = checkpointStore
        ).getOrThrow()

        // Verify graph paused
        assertEquals(RunStatus.PAUSED, initialResult.status)
        assertNotNull(initialResult.checkpointId)
        assertTrue(initialResult.result is HumanInteraction)

        val interaction = initialResult.result as HumanInteraction
        assertEquals("review", interaction.nodeId)
        assertEquals("Please review the draft", interaction.prompt)
        assertEquals(2, interaction.options.size)

        // Step 2: Check pending interactions
        val pending = runner.getPendingInteractions(
            checkpointId = initialResult.checkpointId!!,
            store = checkpointStore
        ).getOrThrow()

        assertEquals(1, pending.size)
        assertEquals("review", pending.first().nodeId)

        // Step 3: Provide human response (approve)
        val humanResponse = HumanResponse.choice(
            nodeId = "review",
            optionId = "approve"
        )

        // Step 4: Resume execution
        val finalResult = runner.resumeWithHumanResponse(
            graph = approvalGraph,
            checkpointId = initialResult.checkpointId!!,
            response = humanResponse,
            store = checkpointStore
        ).getOrThrow()

        // Verify completion
        assertEquals(RunStatus.SUCCESS, finalResult.status)

        // Note: After resume, nodeReports only contains nodes executed after the resume point
        // The initial run had: draft, review (pause)
        // The resume run has: publish
        assertEquals(1, finalResult.nodeReports.size)
        assertEquals("publish", finalResult.nodeReports.first().nodeId)

        // Verify initial run also had the expected nodes
        assertEquals(2, initialResult.nodeReports.size) // draft, review
        assertEquals("draft", initialResult.nodeReports[0].nodeId)
        assertEquals("review", initialResult.nodeReports[1].nodeId)
    }

    @Test
    fun `test free text input with HumanNode`() = runTest {
        // Given: A graph that collects free text input
        val inputGraph = graph("data-collection") {
            humanNode(
                id = "get-input",
                prompt = "Please provide additional information"
                // No options = free text input
            )

            output("final") { ctx ->
                val response = ctx.state["get-input"] as? HumanResponse
                "User said: ${response?.text}"
            }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Step 1: Start graph (pauses at HumanNode)
        val initialResult = runner.runWithCheckpoint(
            graph = inputGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.PAUSED, initialResult.status)
        val interaction = initialResult.result as HumanInteraction
        assertTrue(interaction.allowFreeText)

        // Step 2: Provide free text response
        val humanResponse = HumanResponse.text(
            nodeId = "get-input",
            text = "This is my detailed feedback about the system"
        )

        // Step 3: Resume
        val finalResult = runner.resumeWithHumanResponse(
            graph = inputGraph,
            checkpointId = initialResult.checkpointId!!,
            response = humanResponse,
            store = checkpointStore
        ).getOrThrow()

        // Verify result contains the text
        assertEquals(RunStatus.SUCCESS, finalResult.status)
        assertEquals("User said: This is my detailed feedback about the system", finalResult.result)
    }

    @Test
    fun `test rejection workflow - human rejects and returns to draft`() = runTest {
        // Given: An approval graph with rejection path
        val draftAgent = object : Agent {
            override val id = "draft-agent"
            override val name = "Draft Creator"
            override val description = "Creates drafts"
            override val capabilities = listOf("drafting")

            private var draftCount = 0

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                draftCount++
                return SpiceResult.success(comm.reply("Draft v$draftCount", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val rejectedGraph = graph("rejection-workflow") {
            agent("draft", draftAgent)

            humanNode(
                id = "review",
                prompt = "Review and approve/reject",
                options = listOf(
                    HumanOption("approve", "Approve"),
                    HumanOption("reject", "Reject")
                )
            )

            // Rejection path - output rejection message
            edge("review", "rejected-output") { result ->
                (result.data as? HumanResponse)?.selectedOption == "reject"
            }

            output("rejected-output") { ctx ->
                "Draft was rejected by human reviewer"
            }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Start and pause
        val initialResult = runner.runWithCheckpoint(
            graph = rejectedGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.PAUSED, initialResult.status)

        // Human rejects
        val rejectResponse = HumanResponse.choice("review", "reject")

        // Resume
        val finalResult = runner.resumeWithHumanResponse(
            graph = rejectedGraph,
            checkpointId = initialResult.checkpointId!!,
            response = rejectResponse,
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.SUCCESS, finalResult.status)
        assertEquals("Draft was rejected by human reviewer", finalResult.result)
    }

    @Test
    fun `test multiple HumanNodes in sequence`() = runTest {
        // Given: Graph with two human approval steps
        val multiApprovalGraph = graph("multi-approval") {
            humanNode(
                id = "first-review",
                prompt = "First approval",
                options = listOf(HumanOption("ok", "OK"))
            )

            humanNode(
                id = "second-review",
                prompt = "Second approval",
                options = listOf(HumanOption("ok", "OK"))
            )

            output("done") { "All approvals complete" }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // First pause
        val firstPause = runner.runWithCheckpoint(
            graph = multiApprovalGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.PAUSED, firstPause.status)
        val firstInteraction = firstPause.result as HumanInteraction
        assertEquals("first-review", firstInteraction.nodeId)

        // First resume
        val secondPause = runner.resumeWithHumanResponse(
            graph = multiApprovalGraph,
            checkpointId = firstPause.checkpointId!!,
            response = HumanResponse.choice("first-review", "ok"),
            store = checkpointStore
        ).getOrThrow()

        // Should pause again at second HumanNode
        assertEquals(RunStatus.PAUSED, secondPause.status)
        val secondInteraction = secondPause.result as HumanInteraction
        assertEquals("second-review", secondInteraction.nodeId)

        // Second resume
        val finalResult = runner.resumeWithHumanResponse(
            graph = multiApprovalGraph,
            checkpointId = secondPause.checkpointId!!,
            response = HumanResponse.choice("second-review", "ok"),
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.SUCCESS, finalResult.status)
        assertEquals("All approvals complete", finalResult.result)
    }

    @Test
    fun `test HumanResponse helper methods`() = runTest {
        // Test choice helper
        val choiceResponse = HumanResponse.choice("test-node", "option-1")
        assertEquals("test-node", choiceResponse.nodeId)
        assertEquals("option-1", choiceResponse.selectedOption)
        assertEquals(null, choiceResponse.text)

        // Test text helper
        val textResponse = HumanResponse.text("test-node", "User input")
        assertEquals("test-node", textResponse.nodeId)
        assertEquals(null, textResponse.selectedOption)
        assertEquals("User input", textResponse.text)
    }

    @Test
    fun `test getPendingInteractions returns empty for non-HITL checkpoint`() = runTest {
        // Given: A normal graph without HumanNode
        val normalGraph = graph("normal") {
            output("result") { "done" }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Run normally
        val result = runner.runWithCheckpoint(
            graph = normalGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        // Should complete without pausing
        assertEquals(RunStatus.SUCCESS, result.status)
    }
}
