package io.github.noailabs.spice.springboot.statemachine.transformer

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TransformerChainTest {

    private val testGraph = Graph(
        id = "test-graph",
        nodes = emptyMap(),
        edges = emptyList(),
        entryPoint = "start"
    )

    @Test
    fun `empty chain returns message unchanged`() = runTest {
        val chain = TransformerChain(emptyList())
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        assertTrue(result is SpiceResult.Success)
        assertEquals(message, (result as SpiceResult.Success).value)
    }

    @Test
    fun `single transformer is applied`() = runTest {
        val transformer = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                return SpiceResult.success(
                    message.withMetadata(mapOf("transformed" to true))
                )
            }
        }

        val chain = TransformerChain(listOf(transformer))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        assertTrue(result is SpiceResult.Success)
        assertEquals(true, (result as SpiceResult.Success).value.getMetadata<Boolean>("transformed"))
    }

    @Test
    fun `multiple transformers are applied in order`() = runTest {
        val transformer1 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                return SpiceResult.success(
                    message.withMetadata(mapOf("step" to 1))
                )
            }
        }

        val transformer2 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                val step = message.getMetadata<Int>("step") ?: 0
                return SpiceResult.success(
                    message.withMetadata(mapOf("step" to step + 1))
                )
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        assertTrue(result is SpiceResult.Success)
        assertEquals(2, (result as SpiceResult.Success).value.getMetadata<Int>("step"))
    }

    @Test
    fun `chain stops on first failure`() = runTest {
        val transformer1 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                return SpiceResult.success(
                    message.withMetadata(mapOf("step" to 1))
                )
            }
        }

        val transformer2 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                return SpiceResult.failure(
                    SpiceError.validationError("Test failure")
                )
            }
        }

        val transformer3 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                // This should never be called
                return SpiceResult.success(
                    message.withMetadata(mapOf("step" to 3))
                )
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2, transformer3))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        assertTrue(result is SpiceResult.Failure)
        assertEquals("Test failure", (result as SpiceResult.Failure).error.message)
    }

    @Test
    fun `beforeNode passes correct nodeId`() = runTest {
        var capturedNodeId: String? = null

        val transformer = object : MessageTransformer {
            override suspend fun beforeNode(
                graph: Graph,
                nodeId: String,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                capturedNodeId = nodeId
                return SpiceResult.success(message)
            }
        }

        val chain = TransformerChain(listOf(transformer))
        val message = SpiceMessage.create("test", "user")

        chain.beforeNode(testGraph, "test-node", message)

        assertEquals("test-node", capturedNodeId)
    }

    @Test
    fun `afterNode receives both input and output`() = runTest {
        var capturedInput: SpiceMessage? = null
        var capturedOutput: SpiceMessage? = null

        val transformer = object : MessageTransformer {
            override suspend fun afterNode(
                graph: Graph,
                nodeId: String,
                input: SpiceMessage,
                output: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                capturedInput = input
                capturedOutput = output
                return SpiceResult.success(output)
            }
        }

        val chain = TransformerChain(listOf(transformer))
        val inputMessage = SpiceMessage.create("input", "user")
        val outputMessage = SpiceMessage.create("output", "agent")

        chain.afterNode(testGraph, "test-node", inputMessage, outputMessage)

        assertEquals("input", capturedInput?.content)
        assertEquals("output", capturedOutput?.content)
    }

    @Test
    fun `afterExecution continues even on transformer failure`() = runTest {
        var transformer1Called = false
        var transformer2Called = false
        var transformer3Called = false

        val transformer1 = object : MessageTransformer {
            override suspend fun afterExecution(
                graph: Graph,
                input: SpiceMessage,
                output: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer1Called = true
                return SpiceResult.success(output)
            }
        }

        val transformer2 = object : MessageTransformer {
            override suspend fun afterExecution(
                graph: Graph,
                input: SpiceMessage,
                output: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer2Called = true
                return SpiceResult.failure(
                    SpiceError.executionError("Cleanup failed")
                )
            }
        }

        val transformer3 = object : MessageTransformer {
            override suspend fun afterExecution(
                graph: Graph,
                input: SpiceMessage,
                output: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer3Called = true
                return SpiceResult.success(output)
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2, transformer3))
        val message = SpiceMessage.create("test", "user")

        val result = chain.afterExecution(testGraph, message, message)

        // All transformers should be called (cleanup phase doesn't stop on error)
        assertTrue(transformer1Called)
        assertTrue(transformer2Called)
        assertTrue(transformer3Called)

        // Result should still be success
        assertTrue(result is SpiceResult.Success)
    }

    @Test
    fun `exception in transformer is caught and returned as failure`() = runTest {
        val transformer = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                throw RuntimeException("Transformer crashed")
            }
        }

        val chain = TransformerChain(listOf(transformer))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        assertTrue(result is SpiceResult.Failure)
        assertTrue((result as SpiceResult.Failure).error.message!!.contains("Transformer error"))
    }

    @Test
    fun `continueOnFailure allows chain to continue on failure`() = runTest {
        var transformer1Called = false
        var transformer2Called = false
        var transformer3Called = false

        val transformer1 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer1Called = true
                return SpiceResult.success(message)
            }
        }

        val transformer2 = object : MessageTransformer {
            override val continueOnFailure: Boolean = true

            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer2Called = true
                return SpiceResult.failure(
                    SpiceError.executionError("Non-critical failure")
                )
            }
        }

        val transformer3 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer3Called = true
                return SpiceResult.success(
                    message.withMetadata(mapOf("step3" to true))
                )
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2, transformer3))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        // All transformers should be called
        assertTrue(transformer1Called)
        assertTrue(transformer2Called)
        assertTrue(transformer3Called)

        // Result should be success from transformer3
        assertTrue(result is SpiceResult.Success)
        assertEquals(true, (result as SpiceResult.Success).value.getMetadata<Boolean>("step3"))
    }

    @Test
    fun `continueOnFailure false stops chain on failure`() = runTest {
        var transformer2Called = false

        val transformer1 = object : MessageTransformer {
            override val continueOnFailure: Boolean = false  // Default, but explicit for test

            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                return SpiceResult.failure(
                    SpiceError.validationError("Critical failure")
                )
            }
        }

        val transformer2 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer2Called = true
                return SpiceResult.success(message)
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        // Transformer2 should NOT be called
        assertFalse(transformer2Called)

        // Result should be failure
        assertTrue(result is SpiceResult.Failure)
        assertEquals("Critical failure", (result as SpiceResult.Failure).error.message)
    }

    @Test
    fun `continueOnFailure works for beforeNode`() = runTest {
        var transformer2Called = false

        val transformer1 = object : MessageTransformer {
            override val continueOnFailure: Boolean = true

            override suspend fun beforeNode(
                graph: Graph,
                nodeId: String,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                return SpiceResult.failure(
                    SpiceError.executionError("Non-critical node failure")
                )
            }
        }

        val transformer2 = object : MessageTransformer {
            override suspend fun beforeNode(
                graph: Graph,
                nodeId: String,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer2Called = true
                return SpiceResult.success(message)
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeNode(testGraph, "test-node", message)

        // Transformer2 should be called despite transformer1 failure
        assertTrue(transformer2Called)
        assertTrue(result is SpiceResult.Success)
    }

    @Test
    fun `continueOnFailure works for afterNode`() = runTest {
        var transformer2Called = false

        val transformer1 = object : MessageTransformer {
            override val continueOnFailure: Boolean = true

            override suspend fun afterNode(
                graph: Graph,
                nodeId: String,
                input: SpiceMessage,
                output: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                return SpiceResult.failure(
                    SpiceError.executionError("Non-critical afterNode failure")
                )
            }
        }

        val transformer2 = object : MessageTransformer {
            override suspend fun afterNode(
                graph: Graph,
                nodeId: String,
                input: SpiceMessage,
                output: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer2Called = true
                return SpiceResult.success(output)
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2))
        val inputMessage = SpiceMessage.create("input", "user")
        val outputMessage = SpiceMessage.create("output", "agent")

        val result = chain.afterNode(testGraph, "test-node", inputMessage, outputMessage)

        // Transformer2 should be called despite transformer1 failure
        assertTrue(transformer2Called)
        assertTrue(result is SpiceResult.Success)
    }

    @Test
    fun `continueOnFailure handles exception in beforeExecution`() = runTest {
        var transformer2Called = false

        val transformer1 = object : MessageTransformer {
            override val continueOnFailure: Boolean = true

            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                throw RuntimeException("Transformer crashed but continueOnFailure=true")
            }
        }

        val transformer2 = object : MessageTransformer {
            override suspend fun beforeExecution(
                graph: Graph,
                message: SpiceMessage
            ): SpiceResult<SpiceMessage> {
                transformer2Called = true
                return SpiceResult.success(message)
            }
        }

        val chain = TransformerChain(listOf(transformer1, transformer2))
        val message = SpiceMessage.create("test", "user")

        val result = chain.beforeExecution(testGraph, message)

        // Transformer2 should be called despite transformer1 exception
        assertTrue(transformer2Called)
        assertTrue(result is SpiceResult.Success)
    }
}
