package io.github.noailabs.spice.springboot.statemachine.transformer

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AuthContextTransformerTest {

    private val testGraph = Graph(
        id = "test-graph",
        nodes = emptyMap(),
        edges = emptyList(),
        entryPoint = "start"
    )

    @Test
    fun `detects logged-in user`() = runTest {
        val transformer = AuthContextTransformer { message ->
            message.getMetadata<String>("userId") != null
        }

        val message = SpiceMessage.create("test", "user")
            .withMetadata(mapOf("userId" to "user123"))

        val result = transformer.beforeExecution(testGraph, message)

        assertTrue(result is SpiceResult.Success)
        assertEquals(true, (result as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
    }

    @Test
    fun `detects logged-out user`() = runTest {
        val transformer = AuthContextTransformer { message ->
            message.getMetadata<String>("userId") != null
        }

        val message = SpiceMessage.create("test", "user")

        val result = transformer.beforeExecution(testGraph, message)

        assertTrue(result is SpiceResult.Success)
        assertEquals(false, (result as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
    }

    @Test
    fun `byUserId factory method works`() = runTest {
        val transformer = AuthContextTransformer.byUserId()

        val loggedIn = SpiceMessage.create("test", "user")
            .withMetadata(mapOf("userId" to "user123"))

        val loggedOut = SpiceMessage.create("test", "user")

        val result1 = transformer.beforeExecution(testGraph, loggedIn)
        val result2 = transformer.beforeExecution(testGraph, loggedOut)

        assertEquals(true, (result1 as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
        assertEquals(false, (result2 as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
    }

    @Test
    fun `bySessionToken factory method works`() = runTest {
        val transformer = AuthContextTransformer.bySessionToken()

        val withToken = SpiceMessage.create("test", "user")
            .withMetadata(mapOf("sessionToken" to "token-abc"))

        val withoutToken = SpiceMessage.create("test", "user")

        val result1 = transformer.beforeExecution(testGraph, withToken)
        val result2 = transformer.beforeExecution(testGraph, withoutToken)

        assertEquals(true, (result1 as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
        assertEquals(false, (result2 as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
    }

    @Test
    fun `alwaysLoggedIn for testing`() = runTest {
        val transformer = AuthContextTransformer.alwaysLoggedIn()
        val message = SpiceMessage.create("test", "user")

        val result = transformer.beforeExecution(testGraph, message)

        assertEquals(true, (result as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
    }

    @Test
    fun `alwaysLoggedOut for testing`() = runTest {
        val transformer = AuthContextTransformer.alwaysLoggedOut()
        val message = SpiceMessage.create("test", "user")

        val result = transformer.beforeExecution(testGraph, message)

        assertEquals(false, (result as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
    }

    @Test
    fun `handles detector exception gracefully`() = runTest {
        val transformer = AuthContextTransformer { message ->
            throw RuntimeException("Auth check failed")
        }

        val message = SpiceMessage.create("test", "user")

        val result = transformer.beforeExecution(testGraph, message)

        // Should default to logged-out on error
        assertTrue(result is SpiceResult.Success)
        assertEquals(false, (result as SpiceResult.Success).value.getMetadata<Boolean>("isLoggedIn"))
    }

    @Test
    fun `adds authCheckedAt timestamp`() = runTest {
        val transformer = AuthContextTransformer.byUserId()
        val message = SpiceMessage.create("test", "user")

        val before = System.currentTimeMillis()
        val result = transformer.beforeExecution(testGraph, message)
        val after = System.currentTimeMillis()

        assertTrue(result is SpiceResult.Success)
        val timestamp = (result as SpiceResult.Success).value.getMetadata<Long>("authCheckedAt")!!
        assertTrue(timestamp >= before && timestamp <= after)
    }
}
