package io.github.noailabs.spice.agents.examples

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.agents.*
import io.github.noailabs.spice.dsl.buildAgent
import io.github.noailabs.spice.error.SpiceResult

/**
 * Examples demonstrating all 3 API styles for creating agents
 *
 * This file demonstrates:
 * 1. Factory Functions (gptAgent, claudeAgent)
 * 2. Direct Constructor (GPTAgent, ClaudeAgent)
 * 3. Builder DSL (buildAgent)
 */

/**
 * Style 1: Factory Functions (Easiest, 0.9.0 compatible)
 */
suspend fun factoryFunctionExamples() {
    // OpenAI GPT Agent
    val gpt = gptAgent(
        apiKey = System.getenv("OPENAI_API_KEY") ?: "sk-test",
        model = "gpt-4",
        systemPrompt = "You are a helpful AI assistant.",
        temperature = 0.7
    )

    // Anthropic Claude Agent
    val claude = claudeAgent(
        apiKey = System.getenv("ANTHROPIC_API_KEY") ?: "sk-ant-test",
        model = "claude-3-5-sonnet-20241022",
        systemPrompt = "You are a helpful AI assistant.",
        maxTokens = 2000
    )

    // Mock Agent for testing (no API calls)
    val mock = mockGPTAgent(
        responses = listOf(
            "First response",
            "Second response",
            "Third response"
        )
    )

    // Use any agent
    val message = SpiceMessage.create("Hello, how are you?", "user")
    val response = gpt.processMessage(message)
    println("GPT Response: ${response.getOrNull()?.content}")
}

/**
 * Style 2: Direct Constructor (More control)
 */
suspend fun directConstructorExamples() {
    // GPT with all options
    val gpt = GPTAgent(
        apiKey = System.getenv("OPENAI_API_KEY") ?: "sk-test",
        model = "gpt-4-turbo",
        systemPrompt = "You are a helpful AI assistant.",
        temperature = 0.7,
        maxTokens = 2000,
        tools = emptyList(),
        id = "my-gpt-agent",
        name = "Custom GPT Agent",
        description = "My custom GPT-4 agent",
        baseUrl = "https://api.openai.com/v1",
        organizationId = null
    )

    // Claude with all options
    val claude = ClaudeAgent(
        apiKey = System.getenv("ANTHROPIC_API_KEY") ?: "sk-ant-test",
        model = "claude-3-5-sonnet-20241022",
        systemPrompt = "You are a helpful AI assistant.",
        temperature = 0.7,
        maxTokens = 4000,
        tools = emptyList(),
        baseUrl = "https://api.anthropic.com",
        id = "my-claude-agent",
        name = "Custom Claude Agent",
        description = "My custom Claude agent"
    )

    // Use the agents
    val message = SpiceMessage.create("What is the capital of France?", "user")
    val response = claude.processMessage(message)
    println("Claude Response: ${response.getOrNull()?.content}")
}

/**
 * Style 3: Builder DSL (Most flexible, custom logic)
 */
suspend fun builderDSLExamples() {
    // Simple custom agent
    val simpleAgent = buildAgent {
        id = "simple-agent"
        name = "Simple Agent"
        description = "A simple echo agent"

        handle { message ->
            SpiceResult.success(
                message.reply(
                    content = "Echo: ${message.content}",
                    from = id
                )
            )
        }
    }

    // Custom agent wrapping GPT
    val wrappedGPT = buildAgent {
        id = "wrapped-gpt"
        name = "Wrapped GPT Agent"
        description = "GPT agent with custom pre/post processing"

        val backend = gptAgent(
            apiKey = System.getenv("OPENAI_API_KEY") ?: "sk-test",
            model = "gpt-4"
        )

        handle { message ->
            // Pre-processing
            println("Before GPT: ${message.content}")

            // Call backend
            val result = backend.processMessage(message)

            // Post-processing
            result.onSuccess { response ->
                println("After GPT: ${response.content}")
            }

            result
        }
    }

    // Custom agent with tools (when tool integration is ready)
    val agentWithTools = buildAgent {
        id = "tool-agent"
        name = "Agent with Tools"
        description = "Custom agent with tool support"

        // tools("web_search", "calculator")  // Will be available after tool integration

        handle { message ->
            SpiceResult.success(
                message.reply(
                    content = "Processing with tools: ${message.content}",
                    from = id
                )
            )
        }
    }

    // Use the agents
    val message = SpiceMessage.create("Test message", "user")
    val response = simpleAgent.processMessage(message)
    println("Simple Agent: ${response.getOrNull()?.content}")
}

/**
 * Combined example: All styles working together
 */
suspend fun combinedExample() {
    // Create agents using different styles
    val agents: List<Agent> = listOf(
        // Factory function
        mockGPTAgent(responses = listOf("Mock response")),

        // Direct constructor
        MockAgent(
            id = "mock-direct",
            name = "Mock Direct",
            responses = listOf("Direct mock response")
        ),

        // Builder DSL
        buildAgent {
            id = "custom-echo"
            name = "Custom Echo"
            handle { msg ->
                SpiceResult.success(msg.reply("Echo: ${msg.content}", id))
            }
        }
    )

    // Use all agents
    val message = SpiceMessage.create("Hello!", "user")
    agents.forEach { agent ->
        val response = agent.processMessage(message)
        println("${agent.name}: ${response.getOrNull()?.content}")
    }
}

/**
 * Main function to run all examples
 */
suspend fun main() {
    println("=== Factory Function Examples ===")
    factoryFunctionExamples()

    println("\n=== Direct Constructor Examples ===")
    directConstructorExamples()

    println("\n=== Builder DSL Examples ===")
    builderDSLExamples()

    println("\n=== Combined Example ===")
    combinedExample()
}
