package io.github.noailabs.spice.graph

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.HumanInteraction
import io.github.noailabs.spice.graph.nodes.HumanResponse
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for DynamicHumanNode functionality.
 */
class DynamicHumanNodeTest {

    @Test
    fun `test DynamicHumanNode reads prompt from state`() = runTest {
        // Given: Agent that generates dynamic menu
        val menuAgent = object : Agent {
            override val id = "menu-agent"
            override val name = "Menu Generator"
            override val description = "Generates menus"
            override val capabilities = listOf("menu-generation")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                val menuText = """
                    취소할 예약을 선택하세요:
                    1. 제주 오션뷰 호텔
                    2. 강릉 힐링 펜션
                    3. 부산 해변 리조트
                """.trimIndent()

                val response = comm.reply(menuText, id)

                // Set data in comm for state propagation
                return SpiceResult.success(
                    response.copy(
                        data = response.data + mapOf(
                            "needs_selection" to "true",
                            "menu_text" to menuText
                        )
                    )
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val graph = graph("dynamic-menu-test") {
            agent("menu", menuAgent)

            // Dynamic human node reads from state["menu_text"]
            dynamicHumanNode(
                id = "select",
                promptKey = "menu_text",
                fallbackPrompt = "No menu available"
            )

            edge("menu", "select") { result ->
                (result.data as? Map<*, *>)?.get("needs_selection") == "true"
            }

            output("result") { it.state["select"] }
        }

        val runner = DefaultGraphRunner()
        val store = InMemoryCheckpointStore()

        // When: Execute graph until DynamicHumanNode
        val result = runner.runWithCheckpoint(
            graph = graph,
            input = emptyMap(),
            store = store
        ).getOrThrow()

        // Then: Graph paused at DynamicHumanNode
        assertEquals(RunStatus.PAUSED, result.status)
        assertTrue(result.result is HumanInteraction)

        val interaction = result.result as HumanInteraction

        // Verify dynamic prompt contains menu text
        assertTrue(interaction.prompt.contains("제주 오션뷰 호텔"))
        assertTrue(interaction.prompt.contains("강릉 힐링 펜션"))
        assertTrue(interaction.prompt.contains("부산 해변 리조트"))
        assertEquals("select", interaction.nodeId)

        // Resume with user selection
        val userResponse = HumanResponse.text("select", "1번")

        val finalResult = runner.resumeWithHumanResponse(
            graph = graph,
            checkpointId = result.checkpointId!!,
            response = userResponse,
            store = store
        ).getOrThrow()

        // Verify completion
        assertEquals(RunStatus.SUCCESS, finalResult.status)
    }

    @Test
    fun `test DynamicHumanNode uses fallback when key not found`() = runTest {
        // Given: Agent that doesn't set menu_text
        val simpleAgent = object : Agent {
            override val id = "simple-agent"
            override val name = "Simple Agent"
            override val description = "Simple processing"
            override val capabilities = listOf("processing")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                val response = comm.reply("Processing...", id)
                return SpiceResult.success(
                    response.copy(
                        data = response.data + mapOf("needs_input" to "true")
                    )
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val graph = graph("fallback-test") {
            agent("process", simpleAgent)

            dynamicHumanNode(
                id = "input",
                promptKey = "nonexistent_key",
                fallbackPrompt = "기본 프롬프트입니다"
            )

            edge("process", "input") { result ->
                (result.data as? Map<*, *>)?.get("needs_input") == "true"
            }

            output("result") { it.state["input"] }
        }

        val runner = DefaultGraphRunner()
        val store = InMemoryCheckpointStore()

        // When: Execute graph
        val result = runner.runWithCheckpoint(
            graph = graph,
            input = emptyMap(),
            store = store
        ).getOrThrow()

        // Then: Should use fallback prompt
        assertEquals(RunStatus.PAUSED, result.status)

        val interaction = result.result as HumanInteraction
        assertEquals("기본 프롬프트입니다", interaction.prompt)
    }

    @Test
    fun `test DynamicHumanNode with custom promptKey`() = runTest {
        // Given: Agent using custom key "reservation_menu"
        val reservationAgent = object : Agent {
            override val id = "reservation-agent"
            override val name = "Reservation Agent"
            override val description = "Manages reservations"
            override val capabilities = listOf("reservation-management")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                val response = comm.reply("Reservations loaded", id)
                return SpiceResult.success(
                    response.copy(
                        data = response.data + mapOf(
                            "reservation_menu" to "Custom menu: A, B, C",
                            "needs_selection" to "true"
                        )
                    )
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
            override fun isReady() = true
        }

        val graph = graph("custom-key-test") {
            agent("reservations", reservationAgent)

            dynamicHumanNode(
                id = "select",
                promptKey = "reservation_menu",  // Custom key
                fallbackPrompt = "No reservations"
            )

            edge("reservations", "select") { result ->
                (result.data as? Map<*, *>)?.get("needs_selection") == "true"
            }

            output("result") { it.state["select"] }
        }

        val runner = DefaultGraphRunner()
        val store = InMemoryCheckpointStore()

        // When: Execute
        val result = runner.runWithCheckpoint(
            graph = graph,
            input = emptyMap(),
            store = store
        ).getOrThrow()

        // Then: Should read from custom key
        val interaction = result.result as HumanInteraction
        assertEquals("Custom menu: A, B, C", interaction.prompt)
    }
}
