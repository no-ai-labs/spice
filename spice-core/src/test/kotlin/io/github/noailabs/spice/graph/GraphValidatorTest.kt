package io.github.noailabs.spice.graph

import io.github.noailabs.spice.graph.nodes.OutputNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphValidatorTest {

    @Test
    fun `test valid simple graph`() {
        // Given: Valid linear graph
        val graph = Graph(
            id = "valid-graph",
            nodes = mapOf(
                "node1" to OutputNode("node1"),
                "node2" to OutputNode("node2")
            ),
            edges = listOf(
                Edge("node1", "node2")
            ),
            entryPoint = "node1"
        )

        // When: Validate
        val result = GraphValidator.validate(graph)

        // Then: Should pass
        assertTrue(result.isSuccess)
        assertTrue(GraphValidator.isDAG(graph))
    }

    @Test
    fun `test empty graph fails validation`() {
        // Given: Empty graph
        val graph = Graph(
            id = "empty-graph",
            nodes = emptyMap(),
            edges = emptyList(),
            entryPoint = "nonexistent"
        )

        // When: Validate
        val result = GraphValidator.validate(graph)

        // Then: Should fail
        assertTrue(result.isFailure)
    }

    @Test
    fun `test invalid entry point fails validation`() {
        // Given: Graph with invalid entry point
        val graph = Graph(
            id = "invalid-entry",
            nodes = mapOf(
                "node1" to OutputNode("node1")
            ),
            edges = emptyList(),
            entryPoint = "nonexistent"
        )

        // When: Validate
        val result = GraphValidator.validate(graph)

        // Then: Should fail
        assertTrue(result.isFailure)
    }

    @Test
    fun `test graph with cycle fails validation`() {
        // Given: Graph with cycle
        val graph = Graph(
            id = "cyclic-graph",
            nodes = mapOf(
                "node1" to OutputNode("node1"),
                "node2" to OutputNode("node2"),
                "node3" to OutputNode("node3")
            ),
            edges = listOf(
                Edge("node1", "node2"),
                Edge("node2", "node3"),
                Edge("node3", "node1")  // Cycle!
            ),
            entryPoint = "node1"
        )

        // When: Validate
        val result = GraphValidator.validate(graph)

        // Then: Should fail
        assertTrue(result.isFailure)
        assertFalse(GraphValidator.isDAG(graph))
    }

    @Test
    fun `test graph with unreachable nodes fails validation`() {
        // Given: Graph with unreachable node
        val graph = Graph(
            id = "unreachable-graph",
            nodes = mapOf(
                "node1" to OutputNode("node1"),
                "node2" to OutputNode("node2"),
                "orphan" to OutputNode("orphan")  // Unreachable
            ),
            edges = listOf(
                Edge("node1", "node2")
            ),
            entryPoint = "node1"
        )

        // When: Validate
        val result = GraphValidator.validate(graph)

        // Then: Should fail
        assertTrue(result.isFailure)
    }

    @Test
    fun `test graph with invalid edge reference fails validation`() {
        // Given: Graph with edge referencing non-existent node
        val graph = Graph(
            id = "invalid-edge-graph",
            nodes = mapOf(
                "node1" to OutputNode("node1")
            ),
            edges = listOf(
                Edge("node1", "nonexistent")
            ),
            entryPoint = "node1"
        )

        // When: Validate
        val result = GraphValidator.validate(graph)

        // Then: Should fail
        assertTrue(result.isFailure)
    }

    @Test
    fun `test find terminal nodes`() {
        // Given: Graph with terminal node
        val graph = Graph(
            id = "terminal-graph",
            nodes = mapOf(
                "node1" to OutputNode("node1"),
                "node2" to OutputNode("node2"),
                "terminal" to OutputNode("terminal")
            ),
            edges = listOf(
                Edge("node1", "node2"),
                Edge("node2", "terminal")
                // 'terminal' has no outgoing edges
            ),
            entryPoint = "node1"
        )

        // When: Find terminal nodes
        val terminals = GraphValidator.findTerminalNodes(graph)

        // Then: Should find 'terminal'
        assertEquals(1, terminals.size)
        assertTrue(terminals.contains("terminal"))
    }

    @Test
    fun `test DAG detection`() {
        // Given: Valid DAG
        val dag = Graph(
            id = "dag",
            nodes = mapOf(
                "a" to OutputNode("a"),
                "b" to OutputNode("b"),
                "c" to OutputNode("c"),
                "d" to OutputNode("d")
            ),
            edges = listOf(
                Edge("a", "b"),
                Edge("a", "c"),
                Edge("b", "d"),
                Edge("c", "d")
            ),
            entryPoint = "a"
        )

        // When: Check if DAG
        val isDAG = GraphValidator.isDAG(dag)

        // Then: Should be true
        assertTrue(isDAG)

        // And: Should pass validation
        assertTrue(GraphValidator.validate(dag).isSuccess)
    }

    @Test
    fun `test self-loop detection`() {
        // Given: Graph with self-loop
        val graph = Graph(
            id = "self-loop-graph",
            nodes = mapOf(
                "node1" to OutputNode("node1")
            ),
            edges = listOf(
                Edge("node1", "node1")  // Self-loop
            ),
            entryPoint = "node1"
        )

        // When: Validate
        val result = GraphValidator.validate(graph)

        // Then: Should fail (cycle detected)
        assertTrue(result.isFailure)
        assertFalse(GraphValidator.isDAG(graph))
    }
}
