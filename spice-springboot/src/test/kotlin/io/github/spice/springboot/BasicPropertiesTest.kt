package io.github.spice.springboot

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Basic Properties Test - Minimal test without Spring context
 */
class BasicPropertiesTest {

    @Test
    fun `test SpiceProperties creation`() {
        val properties = SpiceProperties()
        assertTrue(properties.enabled)
    }

    @Test
    fun `test OpenAIProperties defaults`() {
        val properties = OpenAIProperties()
        assertTrue(properties.enabled)
        assertEquals("gpt-4", properties.model)
        assertEquals(1000, properties.maxTokens)
        assertEquals(0.7, properties.temperature)
    }

    @Test
    fun `test AnthropicProperties defaults`() {
        val properties = AnthropicProperties()
        assertTrue(properties.enabled)
        assertEquals("claude-3-5-sonnet-20241022", properties.model)
        assertEquals(1000, properties.maxTokens)
        assertEquals(0.7, properties.temperature)
    }

    @Test
    fun `test EngineProperties defaults`() {
        val properties = EngineProperties()
        assertTrue(properties.enabled)
        assertEquals(100, properties.maxAgents)
        assertEquals(60000, properties.cleanupIntervalMs)
    }
}