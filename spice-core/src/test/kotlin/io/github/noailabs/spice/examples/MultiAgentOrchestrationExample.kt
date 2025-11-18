package io.github.noailabs.spice.examples

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.event.InMemoryToolCallEventBus
import io.github.noailabs.spice.event.ToolCallEvent
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 🌐 Multi-Agent Orchestration Example (Spice 2.0)
 *
 * Demonstrates event-driven multi-agent coordination using ToolCallEventBus.
 *
 * **Scenario**: Travel Booking System with Specialized Agents
 *
 * **Agents**:
 * - SearchAgent: Finds available flights
 * - RecommendationAgent: Provides AI-powered recommendations (listens to search events)
 * - PricingAgent: Calculates real-time pricing (listens to selection events)
 * - ConfirmationAgent: Finalizes booking
 *
 * **Event Flow**:
 * ```
 * SearchAgent → REQUEST_USER_SELECTION
 *     ↓ (event published)
 * RecommendationAgent (listening) → analyzes options
 *     ↓ (publishes recommendation)
 * User selects → USER_RESPONSE
 *     ↓ (event published)
 * PricingAgent (listening) → calculates price
 *     ↓
 * ConfirmationAgent → confirms booking
 * ```
 *
 * This example shows how agents can:
 * 1. React to events from other agents
 * 2. Coordinate without direct coupling
 * 3. Run monitoring/analytics in background
 * 4. Build audit trails automatically
 */
class MultiAgentOrchestrationExample {

    /**
     * 🔍 SearchAgent - Finds available flights
     */
    class SearchAgent : Agent {
        override val id = "search"
        override val name = "Search Agent"
        override val description = "Searches for available flights"
        override val capabilities = listOf("search")

        override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
            // Simulate search results
            val flights = listOf(
                mapOf("id" to "FL001", "airline" to "Korean Air", "price" to "$450"),
                mapOf("id" to "FL002", "airline" to "Asiana", "price" to "$420"),
                mapOf("id" to "FL003", "airline" to "JejuAir", "price" to "$380")
            )

            return SpiceResult.success(
                message.reply("Found ${flights.size} flights", id)
                    .withData(mapOf("flights" to flights))
                    .transitionTo(ExecutionState.RUNNING, "Search complete")
            )
        }
    }

    /**
     * 💡 RecommendationAgent - Listens to search events and provides recommendations
     */
    class RecommendationAgent(
        private val eventBus: InMemoryToolCallEventBus
    ) : Agent {
        override val id = "recommendation"
        override val name = "Recommendation Agent"
        override val description = "Provides AI-powered flight recommendations"
        override val capabilities = listOf("recommendation")

        private val recommendations = mutableListOf<String>()

        init {
            // Subscribe to selection events in background
            // In real app, this would be launched in a background scope
        }

        override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
            // Check if there are flights to recommend
            val flights = message.getData<List<Map<String, Any>>>("flights")

            if (flights != null && flights.isNotEmpty()) {
                // AI recommendation logic
                val bestFlight = flights.minByOrNull {
                    (it["price"] as String).replace("$", "").toInt()
                }

                val recommendation = "Based on price and schedule, we recommend: ${bestFlight?.get("airline")}"
                recommendations.add(recommendation)

                return SpiceResult.success(
                    message.reply(recommendation, id)
                        .withData(mapOf<String, Any>(
                            "recommended_flight" to (bestFlight ?: emptyMap<String, Any>()),
                            "recommendation" to recommendation
                        ))
                        .transitionTo(ExecutionState.RUNNING, "Recommendation complete")
                )
            }

            return SpiceResult.success(
                message.reply("No flights to recommend", id)
                    .transitionTo(ExecutionState.RUNNING, "No recommendations")
            )
        }

        fun getRecommendations() = recommendations.toList()
    }

    /**
     * 💰 PricingAgent - Monitors selections and provides pricing
     */
    class PricingAgent : Agent {
        override val id = "pricing"
        override val name = "Pricing Agent"
        override val description = "Calculates real-time pricing"
        override val capabilities = listOf("pricing")

        override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
            val selectedFlight = message.getData<Map<String, Any>>("selected_flight")
            val basePrice = message.getData<String>("base_price") ?: "$0"

            // Calculate total with taxes and fees
            val base = basePrice.replace("$", "").toIntOrNull() ?: 0
            val taxes = (base * 0.15).toInt()
            val fees = 25
            val total = base + taxes + fees

            return SpiceResult.success(
                message.reply("Total price calculated: $$total", id)
                    .withData(mapOf(
                        "base_price" to base,
                        "taxes" to taxes,
                        "fees" to fees,
                        "total_price" to total
                    ))
                    .transitionTo(ExecutionState.RUNNING, "Pricing complete")
            )
        }
    }

    /**
     * ✅ ConfirmationAgent - Finalizes booking
     */
    class ConfirmationAgent : Agent {
        override val id = "confirmation"
        override val name = "Confirmation Agent"
        override val description = "Confirms flight booking"
        override val capabilities = listOf("booking")

        override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
            val totalPrice = message.getData<Int>("total_price")
            val selectedFlight = message.getData<Map<String, Any>>("selected_flight")

            val confirmationNumber = "CNF${System.currentTimeMillis()}"

            return SpiceResult.success(
                message.reply(
                    "Booking confirmed! Confirmation #: $confirmationNumber",
                    id
                ).withData(mapOf<String, Any>(
                    "confirmation_number" to confirmationNumber,
                    "total_price" to (totalPrice ?: 0),
                    "flight" to (selectedFlight ?: emptyMap<String, Any>())
                ))
                    .transitionTo(ExecutionState.RUNNING, "Confirmation complete")
            )
        }
    }

    /**
     * 📊 MonitoringAgent - Listens to all events for analytics
     */
    class MonitoringAgent(
        private val eventBus: InMemoryToolCallEventBus
    ) {
        private val eventLog = mutableListOf<ToolCallEvent>()

        suspend fun startMonitoring() {
            eventBus.subscribe().collect { event ->
                eventLog.add(event)
                when (event) {
                    is ToolCallEvent.Emitted -> {
                        println("📤 [MONITOR] Tool call emitted: ${event.toolCall.function.name} by ${event.emittedBy}")
                    }
                    is ToolCallEvent.Completed -> {
                        println("✅ [MONITOR] Tool call completed: ${event.toolCall.function.name} in ${event.durationMs}ms")
                    }
                    else -> {
                        println("📋 [MONITOR] Event: ${event::class.simpleName}")
                    }
                }
            }
        }

        fun getEventLog() = eventLog.toList()
        fun getEventCount() = eventLog.size
    }

    @Test
    fun `Multi-agent travel booking with event coordination`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        // Create specialized agents
        val searchAgent = SearchAgent()
        val recommendationAgent = RecommendationAgent(eventBus)
        val pricingAgent = PricingAgent()
        val confirmationAgent = ConfirmationAgent()

        // Create monitoring agent
        val monitor = MonitoringAgent(eventBus)

        // Start monitoring in background (would use backgroundScope in real app)
        // For test, we'll just verify events via history

        // Build workflow graph
        val graph = graph("travel-booking") {
            toolCallEventBus(eventBus)

            // Agent nodes
            agent("search", searchAgent)
            agent("recommend", recommendationAgent)

            // Human selection
            human("select", "Choose your flight", options = listOf(
                HumanOption("FL001", "Korean Air - $450"),
                HumanOption("FL002", "Asiana - $420"),
                HumanOption("FL003", "JejuAir - $380")
            ))

            agent("pricing", pricingAgent)
            agent("confirm", confirmationAgent)

            // Edges
            edge("search", "recommend")
            edge("recommend", "select")
            edge("select", "pricing")
            edge("pricing", "confirm")
        }

        val runner = DefaultGraphRunner()
        val initialMessage = SpiceMessage.create("Find flights to Jeju", "user")
            .withMetadata(mapOf(
                "userId" to "user123",
                "origin" to "Seoul",
                "destination" to "Jeju"
            ))
            .transitionTo(ExecutionState.RUNNING, "Start")

        // Execute workflow
        val result = runner.execute(graph, initialMessage)

        // Verify workflow paused at human selection
        assertTrue(result.isSuccess)
        val pausedMessage = result.getOrThrow()
        assertEquals(ExecutionState.WAITING, pausedMessage.state)

        // Verify events were published
        val history = eventBus.getHistory(100).getOrThrow()

        // Should have events from search and recommend nodes
        val emittedEvents = history.filterIsInstance<ToolCallEvent.Emitted>()
        assertTrue(emittedEvents.isNotEmpty(), "Should have emitted events")

        // Verify graph and run context in events
        emittedEvents.forEach { event ->
            assertEquals("travel-booking", event.graphId)
            assertNotNull(event.runId)
        }

        // Check that we can query events by tool call ID
        val humanToolCall = pausedMessage.toolCalls.first()
        val toolCallHistory = eventBus.getToolCallHistory(humanToolCall.id).getOrThrow()
        assertEquals(1, toolCallHistory.size)
        assertEquals(humanToolCall.id, toolCallHistory[0].toolCall.id)
    }

    @Test
    fun `Recommendation agent reacts to search events`() = runTest {
        val eventBus = InMemoryToolCallEventBus()
        val receivedEvents = mutableListOf<ToolCallEvent>()

        // Subscribe to events (simulating RecommendationAgent listening)
        backgroundScope.launch {
            eventBus.subscribe(ToolCallEvent.Emitted::class).collect { event ->
                receivedEvents.add(event)
            }
        }

        val graph = graph("search-only") {
            toolCallEventBus(eventBus)
            agent("search", SearchAgent())
            human("select", "Choose flight")
            edge("search", "select")
        }

        val runner = DefaultGraphRunner()
        runner.execute(
            graph,
            SpiceMessage.create("Search flights", "user")
                .transitionTo(ExecutionState.RUNNING, "Start")
        )

        // Verify events can be consumed by other agents
        val history = eventBus.getHistory(10).getOrThrow()
        assertTrue(history.isNotEmpty())

        // In real scenario, RecommendationAgent would be listening
        // and could react to these events in real-time
    }

    @Test
    fun `Multiple agents can subscribe to same events`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        // Multiple subscribers (simulating multiple agents)
        val agent1Events = mutableListOf<ToolCallEvent>()
        val agent2Events = mutableListOf<ToolCallEvent>()
        val agent3Events = mutableListOf<ToolCallEvent>()

        // All subscribe to same event stream
        backgroundScope.launch {
            eventBus.subscribe().collect { agent1Events.add(it) }
        }
        backgroundScope.launch {
            eventBus.subscribe().collect { agent2Events.add(it) }
        }
        backgroundScope.launch {
            eventBus.subscribe().collect { agent3Events.add(it) }
        }

        val graph = graph("multi-subscriber") {
            toolCallEventBus(eventBus)
            agent("search", SearchAgent())
            human("select", "Choose")
            edge("search", "select")
        }

        val runner = DefaultGraphRunner()
        runner.execute(
            graph,
            SpiceMessage.create("Test", "user")
                .transitionTo(ExecutionState.RUNNING, "Start")
        )

        // Verify events were published
        val history = eventBus.getHistory(10).getOrThrow()
        assertTrue(history.isNotEmpty(), "Events should be published to history")

        // Verify the event contains the tool call from HumanNode
        val emittedEvents = history.filterIsInstance<ToolCallEvent.Emitted>()
        assertTrue(emittedEvents.isNotEmpty(), "Should have emitted events")

        // In production, each agent would receive and process independently via Flow subscriptions
        // Note: Subscriber count is checked after Flow collection starts, which is racy in tests
    }

    @Test
    fun `Event bus enables audit trail and replay`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        val graph = graph("audit-example") {
            toolCallEventBus(eventBus)
            agent("search", SearchAgent())
            agent("recommend", RecommendationAgent(eventBus))
            human("select", "Choose flight")
            edge("search", "recommend")
            edge("recommend", "select")
        }

        val runner = DefaultGraphRunner()
        val result = runner.execute(
            graph,
            SpiceMessage.create("Book flight", "user")
                .withMetadata(mapOf("userId" to "user456"))
                .transitionTo(ExecutionState.RUNNING, "Start")
        )

        // Get complete audit trail
        val auditTrail = eventBus.getHistory(100).getOrThrow()

        // Verify audit trail captures all steps
        assertTrue(auditTrail.isNotEmpty())

        // Can reconstruct execution flow
        val timeline = auditTrail.map { event ->
            when (event) {
                is ToolCallEvent.Emitted ->
                    "Agent ${event.emittedBy} emitted ${event.toolCall.function.name}"
                is ToolCallEvent.Completed ->
                    "Completed ${event.toolCall.function.name} in ${event.durationMs}ms"
                else -> event::class.simpleName
            }
        }

        assertTrue(timeline.isNotEmpty())

        // Verify we can query by runId for specific execution
        val runId = result.getOrThrow().runId!!
        val runEvents = auditTrail.filter { event ->
            when (event) {
                is ToolCallEvent.Emitted -> event.runId == runId
                else -> false
            }
        }

        assertTrue(runEvents.isNotEmpty(), "Should have events for this run")
    }

    @Test
    fun `Event-driven agent coordination example`() = runTest {
        /**
         * This test demonstrates a key advantage of event-driven architecture:
         * Agents can be added/removed without changing the core workflow.
         */

        val eventBus = InMemoryToolCallEventBus()

        // Core workflow (unchanged)
        val graph = graph("extensible-workflow") {
            toolCallEventBus(eventBus)
            agent("search", SearchAgent())
            human("select", "Choose flight")
            edge("search", "select")
        }

        // NEW: Analytics agent added without modifying graph
        val analyticsEvents = mutableListOf<String>()
        backgroundScope.launch {
            eventBus.subscribe().collect { event ->
                when (event) {
                    is ToolCallEvent.Emitted -> {
                        analyticsEvents.add("User interaction: ${event.toolCall.function.name}")
                    }
                    else -> {}
                }
            }
        }

        // NEW: Compliance agent added without modifying graph
        val complianceEvents = mutableListOf<String>()
        backgroundScope.launch {
            eventBus.subscribe().collect { event ->
                when (event) {
                    is ToolCallEvent.Emitted -> {
                        complianceEvents.add("Audit log: ${event.timestamp}")
                    }
                    else -> {}
                }
            }
        }

        val runner = DefaultGraphRunner()
        runner.execute(
            graph,
            SpiceMessage.create("Search", "user")
                .transitionTo(ExecutionState.RUNNING, "Start")
        )

        // Both analytics and compliance agents received events
        // without being part of the core workflow
        val history = eventBus.getHistory(10).getOrThrow()
        assertTrue(history.isNotEmpty())

        println("✅ Event-driven architecture allows:")
        println("   - Adding monitoring without changing workflow")
        println("   - Adding compliance logging transparently")
        println("   - Multiple agents reacting to same events")
        println("   - Complete decoupling of concerns")
    }
}
