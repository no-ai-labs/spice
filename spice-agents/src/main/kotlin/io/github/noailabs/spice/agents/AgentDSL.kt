package io.github.noailabs.spice.agents

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Tool

/**
 * 🏭 Agent Factory DSL
 *
 * Provides factory functions for creating LLM agents with minimal boilerplate.
 * Compatible with Spice 0.9.0 API style.
 *
 * **Usage:**
 * ```kotlin
 * // Simple usage
 * val agent = gptAgent(
 *     apiKey = System.getenv("OPENAI_API_KEY"),
 *     model = "gpt-4"
 * )
 *
 * // With options
 * val agent = gptAgent(
 *     apiKey = "sk-...",
 *     model = "gpt-4",
 *     systemPrompt = "You are a helpful assistant.",
 *     temperature = 0.7,
 *     tools = listOf(webSearchTool, calculatorTool)
 * )
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */

/**
 * Create a GPT agent
 *
 * @param apiKey OpenAI API key
 * @param model Model name (default: "gpt-4")
 * @param systemPrompt System prompt
 * @param temperature Sampling temperature
 * @param maxTokens Maximum tokens
 * @param tools Tools available to agent
 * @param id Agent ID
 * @param name Agent name
 * @param description Agent description
 * @param baseUrl Custom API base URL
 * @param organizationId OpenAI organization ID
 * @return GPT Agent
 */
fun gptAgent(
    apiKey: String,
    model: String = "gpt-4",
    systemPrompt: String? = null,
    temperature: Double = 0.7,
    maxTokens: Int? = null,
    tools: List<Tool> = emptyList(),
    id: String = "gpt-$model",
    name: String = "GPT Agent ($model)",
    description: String = "OpenAI GPT agent using $model",
    baseUrl: String = "https://api.openai.com/v1",
    organizationId: String? = null
): Agent {
    return GPTAgent(
        apiKey = apiKey,
        model = model,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        tools = tools,
        id = id,
        name = name,
        description = description,
        baseUrl = baseUrl,
        organizationId = organizationId
    )
}

/**
 * Create a Claude agent
 *
 * @param apiKey Anthropic API key
 * @param model Model name (default: "claude-3-5-sonnet-20241022")
 * @param systemPrompt System prompt
 * @param temperature Sampling temperature
 * @param maxTokens Maximum tokens
 * @param tools Tools available to agent
 * @param id Agent ID
 * @param name Agent name
 * @param description Agent description
 * @param baseUrl Custom API base URL
 * @return Claude Agent
 */
fun claudeAgent(
    apiKey: String,
    model: String = "claude-3-5-sonnet-20241022",
    systemPrompt: String? = null,
    temperature: Double = 0.7,
    maxTokens: Int? = 1024,
    tools: List<Tool> = emptyList(),
    id: String = "claude-$model",
    name: String = "Claude Agent ($model)",
    description: String = "Anthropic Claude agent using $model",
    baseUrl: String = "https://api.anthropic.com"
): Agent {
    return ClaudeAgent(
        apiKey = apiKey,
        model = model,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens ?: 1024,
        tools = tools,
        id = id,
        name = name,
        description = description,
        baseUrl = baseUrl
    )
}

/**
 * Create a mock GPT agent for testing
 *
 * Returns predefined responses without calling external APIs.
 *
 * @param responses List of predefined responses
 * @param id Agent ID
 * @param name Agent name
 * @return Mock GPT Agent
 */
fun mockGPTAgent(
    responses: List<String> = listOf("Mock GPT response"),
    id: String = "mock-gpt",
    name: String = "Mock GPT Agent"
): Agent {
    return MockAgent(
        id = id,
        name = name,
        responses = responses
    )
}

/**
 * Create a mock Claude agent for testing
 *
 * @param responses List of predefined responses
 * @param id Agent ID
 * @param name Agent name
 * @return Mock Claude Agent
 */
fun mockClaudeAgent(
    responses: List<String> = listOf("Mock Claude response"),
    id: String = "mock-claude",
    name: String = "Mock Claude Agent"
): Agent {
    return MockAgent(
        id = id,
        name = name,
        responses = responses
    )
}
