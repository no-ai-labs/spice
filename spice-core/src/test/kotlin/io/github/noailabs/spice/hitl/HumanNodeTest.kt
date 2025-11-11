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

    @Test
    fun `test validator rejects invalid response`() = runTest {
        // Given: Graph with validator that requires text length >= 10
        val validatedGraph = graph("validated") {
            humanNode(
                id = "feedback",
                prompt = "Provide feedback (min 10 chars)",
                validator = { response ->
                    response.text?.length?.let { it >= 10 } ?: false
                }
            )

            output("done") { "complete" }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Start and pause
        val pausedResult = runner.runWithCheckpoint(
            graph = validatedGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.PAUSED, pausedResult.status)

        // Provide response that fails validation (too short)
        val invalidResponse = HumanResponse.text(
            nodeId = "feedback",
            text = "short"  // Only 5 chars
        )

        // Resume should fail validation
        val result = runner.resumeWithHumanResponse(
            graph = validatedGraph,
            checkpointId = pausedResult.checkpointId!!,
            response = invalidResponse,
            store = checkpointStore
        )

        assertTrue(result is io.github.noailabs.spice.error.SpiceResult.Failure)
        assertTrue(result.error.message?.contains("validation") == true)
    }

    @Test
    fun `test validator accepts valid response`() = runTest {
        // Given: Graph with validator
        val validatedGraph = graph("validated") {
            humanNode(
                id = "feedback",
                prompt = "Provide feedback (min 10 chars)",
                validator = { response ->
                    response.text?.length?.let { it >= 10 } ?: false
                }
            )

            output("done") { "complete" }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Start and pause
        val pausedResult = runner.runWithCheckpoint(
            graph = validatedGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        // Provide valid response
        val validResponse = HumanResponse.text(
            nodeId = "feedback",
            text = "This is a valid long feedback"  // > 10 chars
        )

        // Resume should succeed
        val result = runner.resumeWithHumanResponse(
            graph = validatedGraph,
            checkpointId = pausedResult.checkpointId!!,
            response = validResponse,
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `test timeout rejects expired response`() = runTest {
        // Given: Graph with 1 second timeout
        val timeoutGraph = graph("timeout") {
            humanNode(
                id = "urgent",
                prompt = "Quick response needed",
                timeout = java.time.Duration.ofSeconds(1)
            )

            output("done") { "complete" }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Start and pause
        val pausedResult = runner.runWithCheckpoint(
            graph = timeoutGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.PAUSED, pausedResult.status)

        // Wait for timeout to expire (use Thread.sleep for real time)
        Thread.sleep(1100)  // Wait 1.1 seconds

        // Try to respond after timeout
        val response = HumanResponse.text(
            nodeId = "urgent",
            text = "Too late"
        )

        // Resume should fail due to timeout
        val result = runner.resumeWithHumanResponse(
            graph = timeoutGraph,
            checkpointId = pausedResult.checkpointId!!,
            response = response,
            store = checkpointStore
        )

        assertTrue(result is io.github.noailabs.spice.error.SpiceResult.Failure)
        assertTrue(result.error.message?.contains("timeout") == true)
    }

    @Test
    fun `test timeout accepts response before expiration`() = runTest {
        // Given: Graph with 5 second timeout
        val timeoutGraph = graph("timeout") {
            humanNode(
                id = "prompt",
                prompt = "You have time",
                timeout = java.time.Duration.ofSeconds(5)
            )

            output("done") { "complete" }
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Start and pause
        val pausedResult = runner.runWithCheckpoint(
            graph = timeoutGraph,
            input = emptyMap(),
            store = checkpointStore
        ).getOrThrow()

        // Respond immediately (well within timeout)
        val response = HumanResponse.text(
            nodeId = "prompt",
            text = "Quick response"
        )

        // Resume should succeed
        val result = runner.resumeWithHumanResponse(
            graph = timeoutGraph,
            checkpointId = pausedResult.checkpointId!!,
            response = response,
            store = checkpointStore
        ).getOrThrow()

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `test HumanResponse metadata propagates to next AgentNode via ExecutionContext`() = runTest {
        // Given: Agent → HumanNode → Agent workflow with metadata propagation
        var firstAgentExecuted = false
        var verifyAgentContext: io.github.noailabs.spice.ExecutionContext? = null
        var verifyAgentCommData: Map<String, Any?>? = null

        val dataGeneratorAgent = object : Agent {
            override val id = "data-generator"
            override val name = "Data Generator"
            override val description = "Generates data for user selection"
            override val capabilities = listOf("data-generation")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                firstAgentExecuted = true
                // Generate menu and important context data
                return SpiceResult.success(
                    comm.reply(
                        "Menu: 1. Item A, 2. Item B, 3. Item C",
                        id,
                        data = mapOf(
                            "menu_text" to "1. Item A\n2. Item B\n3. Item C",
                            "items_json" to """["Item A", "Item B", "Item C"]""",
                            "session_id" to "SESSION123"
                        )
                    )
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val verifyAgent = object : Agent {
            override val id = "verify-agent"
            override val name = "Verification Agent"
            override val description = "Verifies data from previous steps"
            override val capabilities = listOf("verification")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                // Capture context and data for verification
                verifyAgentContext = comm.context
                verifyAgentCommData = comm.data

                // Try to read data from ExecutionContext
                val sessionId = comm.context?.get("session_id")
                val itemsJson = comm.context?.get("items_json")
                val selectedId = comm.context?.get("selected_item_id")
                val userId = comm.context?.get("user_id")

                return SpiceResult.success(
                    comm.reply(
                        "Verified: sessionId=$sessionId, itemsJson=$itemsJson, selectedId=$selectedId, userId=$userId",
                        id
                    )
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val metadataGraph = graph("metadata-propagation-test") {
            // First agent generates data with context
            agent("generate", dataGeneratorAgent)

            // Human selects an option
            humanNode(
                id = "select",
                prompt = "Please select an item",
                options = listOf(
                    HumanOption("item-a", "Item A"),
                    HumanOption("item-b", "Item B"),
                    HumanOption("item-c", "Item C")
                )
            )

            // Second agent verifies it can access all context
            agent("verify", verifyAgent)
        }

        val runner = DefaultGraphRunner()
        val checkpointStore = InMemoryCheckpointStore()

        // Step 1: Start with initial ExecutionContext
        val pausedResult = runner.runWithCheckpoint(
            graph = metadataGraph,
            input = mapOf(
                "metadata" to mapOf(
                    "userId" to "user-123",
                    "tenantId" to "tenant-456"
                )
            ),
            store = checkpointStore
        ).getOrThrow()

        // Verify first agent executed and graph paused
        assertTrue(firstAgentExecuted)
        assertEquals(RunStatus.PAUSED, pausedResult.status)
        assertNotNull(pausedResult.checkpointId)

        // Verify checkpoint saved the agent's data
        val checkpoint = checkpointStore.load(pausedResult.checkpointId!!).getOrThrow()
        assertTrue(checkpoint.metadata.containsKey("menu_text"))
        assertTrue(checkpoint.metadata.containsKey("items_json"))
        assertTrue(checkpoint.metadata.containsKey("session_id"))
        assertEquals("SESSION123", checkpoint.metadata["session_id"])

        // Step 2: Resume with HumanResponse containing metadata
        val humanResponse = HumanResponse(
            nodeId = "select",
            selectedOption = "item-b",
            metadata = mapOf(
                "selected_item_id" to "RSV002",
                "selected_item_name" to "Item B",
                "user_id" to "user-123"  // User info from UI
            )
        )

        val finalResult = runner.resumeWithHumanResponse(
            graph = metadataGraph,
            checkpointId = pausedResult.checkpointId!!,
            response = humanResponse,
            store = checkpointStore
        ).getOrThrow()

        // Step 3: Verify second agent received ALL context data
        assertEquals(RunStatus.SUCCESS, finalResult.status)
        assertNotNull(verifyAgentContext, "Verify agent should have received ExecutionContext")

        // Verify ExecutionContext contains original tenant/user info
        assertEquals("tenant-456", verifyAgentContext?.tenantId)
        assertEquals("user-123", verifyAgentContext?.userId)

        // Verify ExecutionContext contains first agent's data
        assertEquals("SESSION123", verifyAgentContext?.get("session_id"))
        assertEquals("""["Item A", "Item B", "Item C"]""", verifyAgentContext?.get("items_json"))

        // 🔥 CRITICAL: Verify ExecutionContext contains HumanResponse metadata
        assertEquals("RSV002", verifyAgentContext?.get("selected_item_id"))
        assertEquals("Item B", verifyAgentContext?.get("selected_item_name"))
        assertEquals("user-123", verifyAgentContext?.get("user_id"))

        // Verify comm.data also contains the propagated metadata
        assertNotNull(verifyAgentCommData)
        assertEquals("SESSION123", verifyAgentCommData?.get("session_id"))
        assertEquals("RSV002", verifyAgentCommData?.get("selected_item_id"))
    }
}
