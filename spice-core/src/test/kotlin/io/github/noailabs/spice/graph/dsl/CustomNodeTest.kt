package io.github.noailabs.spice.graph.dsl

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.preserveMetadata
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test for custom node() function in Graph DSL
 */
class CustomNodeTest {

    /**
     * Simple custom node for testing
     */
    class ProcessingNode(
        override val id: String,
        private val processor: (String) -> String,
        private val inputKey: String = "input"
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            val input = ctx.state[inputKey]?.toString()
                ?: return SpiceResult.failure(SpiceError.validationError("Missing $inputKey"))

            val result = processor(input)

            return SpiceResult.success(
                NodeResult.fromContext(
                    ctx = ctx,
                    data = result,
                    additional = mapOf(
                        "processed" to true,
                        "length" to result.length
                    )
                )
            )
        }
    }

    /**
     * Simple counter node
     */
    class CounterNode(override val id: String) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            val count = (ctx.state["count"] as? Number)?.toInt() ?: 0
            val newCount = count + 1

            return SpiceResult.success(
                NodeResult.fromContext(
                    ctx = ctx,
                    data = newCount,
                    additional = mapOf("count" to newCount)
                )
            )
        }
    }

    @Test
    fun `test custom node() function with ProcessingNode`() = runTest {
        val graph = graph("processing-workflow") {
            node(ProcessingNode(
                id = "uppercase",
                processor = { it.uppercase() },
                inputKey = "input"
            ))

            node(ProcessingNode(
                id = "reverse",
                processor = { it.reversed() },
                inputKey = "uppercase"  // Read from previous node's output
            ))

            output("result") { ctx -> ctx.state["reverse"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf("input" to "hello")).getOrThrow()

        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("OLLEH", report.result)  // "hello" -> "HELLO" -> "OLLEH"
    }

    @Test
    fun `test multiple custom nodes in sequence`() = runTest {
        val graph = graph("multi-custom-workflow") {
            node(ProcessingNode(
                id = "step1",
                processor = { "[$it]" }
            ))

            node(CounterNode("increment"))

            output("result") { ctx -> ctx.state["increment"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf("input" to "test")).getOrThrow()

        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals(1, report.result)
    }

    @Test
    fun `test custom node state access`() = runTest {
        class StateAccessNode(override val id: String) : Node {
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                // Test state access patterns
                val input = ctx.state["input"]?.toString() ?: "default"
                val count = (ctx.state["count"] as? Number)?.toInt() ?: 0
                val enabled = ctx.state["enabled"] as? Boolean ?: false

                val output = buildString {
                    append("input=$input")
                    append(", count=$count")
                    append(", enabled=$enabled")
                }

                return SpiceResult.success(
                    NodeResult.fromContext(ctx, output)
                )
            }
        }

        val graph = graph("state-access-workflow") {
            node(StateAccessNode("reader"))
            output("result") { ctx -> ctx.state["reader"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf(
            "input" to "hello",
            "count" to 42,
            "enabled" to true
        )).getOrThrow()

        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("input=hello, count=42, enabled=true", report.result)
    }

    @Test
    fun `test custom node error handling`() = runTest {
        class FailingNode(override val id: String) : Node {
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.UnknownError("Intentional failure"))
            }
        }

        val graph = graph("error-workflow") {
            node(FailingNode("fail"))
            output("result")
        }

        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        assertTrue(result.isFailure)
    }
}
