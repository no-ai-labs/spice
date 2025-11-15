package io.github.noailabs.spice.springboot.ai.dsl

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.springboot.ai.factory.*

/**
 * 🏗️ Agent Builder DSL
 *
 * Kotlin idiomatic DSL for creating Spring AI agents.
 *
 * **Usage:**
 * ```kotlin
 * @Configuration
 * class AgentConfig {
 *     @Bean
 *     fun fastAgent() = springAIAgent {
 *         provider = "openai"
 *         model = "gpt-3.5-turbo"
 *         temperature = 0.3
 *         maxTokens = 500
 *         agentId = "fast-agent"
 *         agentName = "Fast Response Agent"
 *     }
 *
 *     @Bean
 *     fun smartAgent() = springAIAgent {
 *         provider = "openai"
 *         model = "gpt-4"
 *         temperature = 0.7
 *         systemPrompt = "You are an expert software architect."
 *         tools.addAll(listOf(webSearchTool, codeAnalysisTool))
 *     }
 *
 *     @Bean
 *     fun claudeAgent() = springAIAgent {
 *         provider = "anthropic"
 *         model = "claude-3-5-sonnet-20241022"
 *         maxTokens = 4000
 *         systemPrompt = "You are a helpful AI assistant."
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class AgentBuilder {
    var provider: String? = null
    var model: String? = null
    var agentId: String? = null
    var agentName: String? = null
    var agentDescription: String? = null
    var temperature: Double? = null
    var maxTokens: Int? = null
    var systemPrompt: String? = null
    val tools: MutableList<Tool> = mutableListOf()

    // OpenAI-specific
    var apiKey: String? = null
    var baseUrl: String? = null
    var organizationId: String? = null
    var topP: Double? = null
    var frequencyPenalty: Double? = null
    var presencePenalty: Double? = null

    // Anthropic-specific
    var topK: Int? = null

    // Ollama-specific
    var numGpu: Int? = null
    var numThread: Int? = null

    /**
     * Build agent using the provided factory
     *
     * @param factory Spring AI agent factory
     * @return Agent instance
     */
    fun build(factory: SpringAIAgentFactory): Agent {
        val prov = provider ?: throw IllegalArgumentException("Provider is required")
        val mod = model ?: throw IllegalArgumentException("Model is required")

        return when (prov.lowercase()) {
            "openai" -> {
                factory.openai(
                    model = mod,
                    config = OpenAIConfig(
                        agentId = agentId,
                        agentName = agentName,
                        agentDescription = agentDescription,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        organizationId = organizationId,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        topP = topP,
                        frequencyPenalty = frequencyPenalty,
                        presencePenalty = presencePenalty,
                        systemPrompt = systemPrompt
                    )
                )
            }
            "anthropic" -> {
                factory.anthropic(
                    model = mod,
                    config = AnthropicConfig(
                        agentId = agentId,
                        agentName = agentName,
                        agentDescription = agentDescription,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        topP = topP,
                        topK = topK,
                        systemPrompt = systemPrompt
                    )
                )
            }
            "ollama" -> {
                factory.ollama(
                    model = mod,
                    config = OllamaConfig(
                        agentId = agentId,
                        agentName = agentName,
                        agentDescription = agentDescription,
                        baseUrl = baseUrl,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        topP = topP,
                        topK = topK,
                        numGpu = numGpu,
                        numThread = numThread,
                        systemPrompt = systemPrompt
                    )
                )
            }
            else -> throw IllegalArgumentException("Unknown provider: $prov")
        }
    }
}

/**
 * Create an agent using DSL
 *
 * **Note:** This function requires a SpringAIAgentFactory instance.
 * In Spring Boot applications, use @Autowired factory and create agents via @Bean methods.
 *
 * **Usage (Spring Boot):**
 * ```kotlin
 * @Configuration
 * class AgentConfig(private val factory: SpringAIAgentFactory) {
 *     @Bean
 *     fun myAgent() = springAIAgent(factory) {
 *         provider = "openai"
 *         model = "gpt-4"
 *         temperature = 0.7
 *     }
 * }
 * ```
 *
 * @param factory Spring AI agent factory
 * @param block Builder DSL block
 * @return Agent instance
 */
fun springAIAgent(factory: SpringAIAgentFactory, block: AgentBuilder.() -> Unit): Agent {
    return AgentBuilder().apply(block).build(factory)
}

/**
 * Create an OpenAI agent using DSL
 *
 * @param factory Spring AI agent factory
 * @param model Model name (e.g., "gpt-4", "gpt-3.5-turbo")
 * @param block Builder DSL block
 * @return Agent instance
 */
fun openAIAgent(factory: SpringAIAgentFactory, model: String, block: AgentBuilder.() -> Unit = {}): Agent {
    return AgentBuilder().apply {
        this.provider = "openai"
        this.model = model
        block()
    }.build(factory)
}

/**
 * Create an Anthropic agent using DSL
 *
 * @param factory Spring AI agent factory
 * @param model Model name (e.g., "claude-3-5-sonnet-20241022")
 * @param block Builder DSL block
 * @return Agent instance
 */
fun anthropicAgent(factory: SpringAIAgentFactory, model: String, block: AgentBuilder.() -> Unit = {}): Agent {
    return AgentBuilder().apply {
        this.provider = "anthropic"
        this.model = model
        block()
    }.build(factory)
}

/**
 * Create an Ollama agent using DSL
 *
 * @param factory Spring AI agent factory
 * @param model Model name (e.g., "llama3", "mistral")
 * @param block Builder DSL block
 * @return Agent instance
 */
fun ollamaAgent(factory: SpringAIAgentFactory, model: String, block: AgentBuilder.() -> Unit = {}): Agent {
    return AgentBuilder().apply {
        this.provider = "ollama"
        this.model = model
        block()
    }.build(factory)
}
