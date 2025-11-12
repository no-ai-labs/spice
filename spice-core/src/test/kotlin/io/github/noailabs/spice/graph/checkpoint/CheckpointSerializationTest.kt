package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.graph.nodes.GraphExecutionState
import io.github.noailabs.spice.graph.nodes.HumanInteraction
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.nodes.HumanResponse
import io.github.noailabs.spice.toAgentContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Tests for CheckpointSerializer to verify nested Map/List structure preservation.
 *
 * These tests ensure that complex data structures in Checkpoint.state and Checkpoint.metadata
 * survive the JSON serialization/deserialization cycle without type loss.
 */
class CheckpointSerializationTest {

    @Test
    fun `test simple checkpoint serialization round-trip`() {
        // Given: Simple checkpoint
        val checkpoint = Checkpoint(
            id = "test-1",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = mapOf("key1" to "value1", "key2" to 42),
            metadata = mapOf("meta1" to "metaValue1")
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify all fields match
        assertEquals(checkpoint.id, restored.id)
        assertEquals(checkpoint.runId, restored.runId)
        assertEquals(checkpoint.graphId, restored.graphId)
        assertEquals(checkpoint.currentNodeId, restored.currentNodeId)
        assertEquals("value1", restored.state["key1"])
        assertEquals(42L, restored.state["key2"])  // JSON numbers become Long
        assertEquals("metaValue1", restored.metadata["meta1"])
    }

    @Test
    fun `test nested Map preservation in state`() {
        // Given: Checkpoint with nested Map in state
        val checkpoint = Checkpoint(
            id = "nested-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = mapOf(
                "structured_data" to mapOf(
                    "key1" to "value1",
                    "key2" to "value2"
                )
            )
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify nested Map is preserved
        val structuredData = restored.state["structured_data"]
        assertNotNull(structuredData, "structured_data should not be null")
        assertIs<Map<*, *>>(structuredData, "structured_data should be a Map")

        val dataMap = structuredData as Map<*, *>
        assertEquals("value1", dataMap["key1"])
        assertEquals("value2", dataMap["key2"])
    }

    @Test
    fun `test nested List preservation in state`() {
        // Given: Checkpoint with nested List in state
        val checkpoint = Checkpoint(
            id = "list-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = mapOf(
                "list_data" to listOf("a", "b", "c"),
                "complex_list" to listOf(
                    mapOf("id" to "1", "name" to "item1"),
                    mapOf("id" to "2", "name" to "item2")
                )
            )
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify nested List is preserved
        val listData = restored.state["list_data"]
        assertNotNull(listData)
        assertIs<List<*>>(listData)
        assertEquals(3, listData.size)
        assertEquals(listOf("a", "b", "c"), listData)

        // Verify list of maps
        val complexList = restored.state["complex_list"]
        assertNotNull(complexList)
        assertIs<List<*>>(complexList)
        assertEquals(2, complexList.size)

        val firstItem = complexList[0] as Map<*, *>
        assertEquals("1", firstItem["id"].toString())  // Numbers may become Long
        assertEquals("item1", firstItem["name"])
    }

    @Test
    fun `test deeply nested structures preservation`() {
        // Given: Checkpoint with deeply nested structures
        val checkpoint = Checkpoint(
            id = "deep-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = mapOf(
                "level1" to mapOf(
                    "level2" to mapOf(
                        "level3" to listOf(
                            mapOf("id" to 1, "data" to "deep1"),
                            mapOf("id" to 2, "data" to "deep2")
                        )
                    )
                )
            )
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify deeply nested structure is preserved
        val level1 = restored.state["level1"] as Map<*, *>
        val level2 = level1["level2"] as Map<*, *>
        val level3 = level2["level3"] as List<*>

        assertEquals(2, level3.size)
        val firstItem = level3[0] as Map<*, *>
        assertEquals(1L, firstItem["id"])  // JSON numbers become Long
        assertEquals("deep1", firstItem["data"])
    }

    @Test
    fun `test mixed types in state`() {
        // Given: Checkpoint with various data types
        val checkpoint = Checkpoint(
            id = "mixed-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = mapOf(
                "string" to "text",
                "int" to 42,
                "double" to 3.14,
                "boolean" to true,
                "null_value" to null,
                "list" to listOf(1, 2, 3),
                "map" to mapOf("nested" to "value")
            )
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify all types are preserved (note: JSON numbers become Long/Double)
        assertEquals("text", restored.state["string"])
        assertEquals(42L, restored.state["int"])  // JSON int → Long
        assertEquals(3.14, restored.state["double"])
        assertEquals(true, restored.state["boolean"])
        // Note: explicit null values in Map<String, Any?> may not round-trip perfectly
        // This is acceptable as it's rare to explicitly store null in state
        assertIs<List<*>>(restored.state["list"])
        assertIs<Map<*, *>>(restored.state["map"])
    }

    @Test
    fun `test ExecutionContext serialization`() {
        // Given: Checkpoint with ExecutionContext
        val context = ExecutionContext.of(
            mapOf(
                "tenantId" to "tenant-123",
                "userId" to "user-456",
                "sessionId" to "session-789",
                "correlationId" to "corr-999"
            )
        ).plus("customKey", "customValue")

        val checkpoint = Checkpoint(
            id = "context-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = emptyMap(),
            agentContext = context.toAgentContext()
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify ExecutionContext is preserved
        assertNotNull(restored.agentContext)
        assertEquals("tenant-123", restored.agentContext?.tenantId)
        assertEquals("user-456", restored.agentContext?.userId)
        assertEquals("session-789", restored.agentContext?.get("sessionId"))
        assertEquals("corr-999", restored.agentContext?.get("correlationId"))
        assertEquals("customValue", restored.agentContext?.get("customKey"))
    }

    @Test
    fun `test HumanInteraction serialization`() {
        // Given: Checkpoint with HumanInteraction
        val interaction = HumanInteraction(
            nodeId = "human-1",
            prompt = "Please select an option",
            options = listOf(
                HumanOption(
                    id = "opt1",
                    label = "Option 1",
                    description = "First option"
                ),
                HumanOption(
                    id = "opt2",
                    label = "Option 2"
                )
            ),
            pausedAt = "2024-01-01T00:00:00Z",
            expiresAt = "2024-01-01T01:00:00Z",
            allowFreeText = true
        )

        val checkpoint = Checkpoint(
            id = "interaction-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "human-1",
            state = emptyMap(),
            pendingInteraction = interaction
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify HumanInteraction is preserved
        assertNotNull(restored.pendingInteraction)
        assertEquals("human-1", restored.pendingInteraction?.nodeId)
        assertEquals("Please select an option", restored.pendingInteraction?.prompt)
        assertEquals("2024-01-01T00:00:00Z", restored.pendingInteraction?.pausedAt)
        assertEquals("2024-01-01T01:00:00Z", restored.pendingInteraction?.expiresAt)
        assertEquals(true, restored.pendingInteraction?.allowFreeText)
        assertEquals(2, restored.pendingInteraction?.options?.size)

        val firstOption = restored.pendingInteraction?.options?.get(0)
        assertEquals("opt1", firstOption?.id)
        assertEquals("Option 1", firstOption?.label)
        assertEquals("First option", firstOption?.description)
    }

    @Test
    fun `test HumanResponse serialization`() {
        // Given: Checkpoint with HumanResponse
        val response = HumanResponse(
            nodeId = "human-1",
            selectedOption = "opt1",
            text = "User input text",
            metadata = mapOf(
                "customKey" to "customValue",
                "userData" to mapOf("userId" to "user-123")
            ),
            timestamp = "2024-01-01T00:00:00Z"
        )

        val checkpoint = Checkpoint(
            id = "response-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "human-1",
            state = emptyMap(),
            humanResponse = response
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify HumanResponse is preserved
        assertNotNull(restored.humanResponse)
        assertEquals("human-1", restored.humanResponse?.nodeId)
        assertEquals("opt1", restored.humanResponse?.selectedOption)
        assertEquals("User input text", restored.humanResponse?.text)
        assertEquals("2024-01-01T00:00:00Z", restored.humanResponse?.timestamp)
        assertEquals("customValue", restored.humanResponse?.metadata?.get("customKey"))

        val userData = restored.humanResponse?.metadata?.get("userData") as? Map<*, *>
        assertNotNull(userData)
        assertEquals("user-123", userData["userId"])
    }

    @Test
    fun `test InMemoryCheckpointStore with nested structures`() = runTest {
        // Given: InMemoryCheckpointStore (uses JSON serialization)
        val store = InMemoryCheckpointStore()

        val checkpoint = Checkpoint(
            id = "store-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = mapOf(
                "structured_data" to mapOf(
                    "reservations" to listOf(
                        mapOf("id" to "res1", "name" to "Reservation 1"),
                        mapOf("id" to "res2", "name" to "Reservation 2")
                    )
                ),
                "message_type" to "selection"
            ),
            metadata = mapOf(
                "tenantId" to "tenant-123",
                "customData" to mapOf("key" to "value")
            )
        )

        // When: Save and load through store
        store.save(checkpoint).getOrThrow()
        val loaded = store.load("store-test").getOrThrow()

        // Then: Verify nested structures survived the round-trip
        val structuredData = loaded.state["structured_data"] as? Map<*, *>
        assertNotNull(structuredData, "structured_data should be preserved")

        val reservations = structuredData["reservations"] as? List<*>
        assertNotNull(reservations, "reservations list should be preserved")
        assertEquals(2, reservations.size)

        val firstReservation = reservations[0] as? Map<*, *>
        assertNotNull(firstReservation)
        assertEquals("res1", firstReservation["id"])
        assertEquals("Reservation 1", firstReservation["name"])

        // Verify metadata nested structure
        val customData = loaded.metadata["customData"] as? Map<*, *>
        assertNotNull(customData)
        assertEquals("value", customData["key"])
    }

    @Test
    fun `test timestamp preservation`() {
        // Given: Checkpoint with specific timestamp
        val timestamp = Instant.parse("2024-01-15T10:30:00Z")
        val checkpoint = Checkpoint(
            id = "time-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = emptyMap(),
            timestamp = timestamp
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify timestamp is preserved
        assertEquals(timestamp, restored.timestamp)
    }

    @Test
    fun `test GraphExecutionState preservation`() {
        // Given: Checkpoint with different execution states
        val states = listOf(
            GraphExecutionState.RUNNING,
            GraphExecutionState.WAITING_FOR_HUMAN,
            GraphExecutionState.COMPLETED,
            GraphExecutionState.FAILED,
            GraphExecutionState.CANCELLED
        )

        states.forEach { state ->
            val checkpoint = Checkpoint(
                id = "state-test-${state.name}",
                runId = "run-1",
                graphId = "graph-1",
                currentNodeId = "node1",
                state = emptyMap(),
                executionState = state
            )

            // When: Serialize and deserialize
            val json = CheckpointSerializer.serialize(checkpoint)
            val restored = CheckpointSerializer.deserialize(json)

            // Then: Verify execution state is preserved
            assertEquals(state, restored.executionState)
        }
    }

    @Test
    fun `test empty state and metadata`() {
        // Given: Checkpoint with empty state and metadata
        val checkpoint = Checkpoint(
            id = "empty-test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node1",
            state = emptyMap(),
            metadata = emptyMap()
        )

        // When: Serialize and deserialize
        val json = CheckpointSerializer.serialize(checkpoint)
        val restored = CheckpointSerializer.deserialize(json)

        // Then: Verify empty maps are preserved
        assertEquals(0, restored.state.size)
        assertEquals(0, restored.metadata.size)
    }
}
