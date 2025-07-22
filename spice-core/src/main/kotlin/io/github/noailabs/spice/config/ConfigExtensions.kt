package io.github.noailabs.spice.config

import io.github.noailabs.spice.*
import io.github.noailabs.spice.agents.*

/**
 * Extension functions for SpiceConfig to create agents
 */

/**
 * Create an OpenAI agent from the current configuration
 */
fun SpiceConfig.createOpenAIAgent(
    id: String = "openai-agent-${System.currentTimeMillis()}",
    configOverrides: (OpenAIConfig.() -> Unit)? = null
): Agent {
    val config = providers.getTyped<OpenAIConfig>("openai")
        ?: throw IllegalStateException("OpenAI configuration not found")
    
    configOverrides?.invoke(config)
    config.validate()
    
    return gptAgent(
        id = id,
        apiKey = config.apiKey,
        model = config.model,
        systemPrompt = config.systemPrompt,
        debugEnabled = debug.enabled
    )
}

/**
 * Create an Anthropic Claude agent from the current configuration
 */
fun SpiceConfig.createClaudeAgent(
    id: String = "claude-agent-${System.currentTimeMillis()}",
    configOverrides: (AnthropicConfig.() -> Unit)? = null
): Agent {
    val config = providers.getTyped<AnthropicConfig>("anthropic")
        ?: throw IllegalStateException("Anthropic configuration not found")
    
    configOverrides?.invoke(config)
    config.validate()
    
    return claudeAgent(
        id = id,
        apiKey = config.apiKey,
        model = config.model,
        systemPrompt = config.systemPrompt,
        debugEnabled = debug.enabled
    )
}

/**
 * Create all configured agents
 */
fun SpiceConfig.createAllAgents(): Map<String, Agent> {
    val agents = mutableMapOf<String, Agent>()
    
    // OpenAI
    providers.getTyped<OpenAIConfig>("openai")?.let { config ->
        if (config.enabled) {
            try {
                agents["openai"] = createOpenAIAgent()
            } catch (e: Exception) {
                // Log error but continue
                println("Failed to create OpenAI agent: ${e.message}")
            }
        }
    }
    
    // Anthropic
    providers.getTyped<AnthropicConfig>("anthropic")?.let { config ->
        if (config.enabled) {
            try {
                agents["anthropic"] = createClaudeAgent()
            } catch (e: Exception) {
                // Log error but continue
                println("Failed to create Claude agent: ${e.message}")
            }
        }
    }
    
    // Add more providers as needed
    
    return agents
}

/**
 * Check if a specific provider is configured and enabled
 */
fun SpiceConfig.isProviderEnabled(providerName: String): Boolean {
    return providers.get(providerName)?.enabled ?: false
}

/**
 * Get API key for a provider safely
 */
fun SpiceConfig.getApiKey(providerName: String): String? {
    return when (val config = providers.get(providerName)) {
        is OpenAIConfig -> config.apiKey
        is AnthropicConfig -> config.apiKey
        is CustomProviderConfig -> config.getProperty<String>("apiKey")
        else -> null
    }
}