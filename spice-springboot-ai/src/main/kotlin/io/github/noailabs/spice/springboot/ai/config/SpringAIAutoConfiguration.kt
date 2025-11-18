package io.github.noailabs.spice.springboot.ai.config

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.springboot.ai.factory.DefaultSpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.registry.AgentRegistry
import io.github.noailabs.spice.springboot.ai.registry.DefaultAgentRegistry
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * ⚙️ Spring AI Auto Configuration for Spice Framework
 *
 * Automatically configures Spring AI integration with support for:
 * - Multiple providers (OpenAI, Anthropic, Ollama, Azure, Vertex, Bedrock)
 * - Agent factory (programmatic agent creation)
 * - Agent registry (multi-agent management)
 * - Function callbacks (tool integration)
 *
 * **Enabled by default when `spice.spring-ai.enabled=true` (default)**
 *
 * **Configuration:**
 * ```yaml
 * spice:
 *   spring-ai:
 *     enabled: true
 *     default-provider: openai
 *     openai:
 *       enabled: true
 *       api-key: ${OPENAI_API_KEY}
 *       model: gpt-4
 *     anthropic:
 *       enabled: true
 *       api-key: ${ANTHROPIC_API_KEY}
 *       model: claude-3-5-sonnet-20241022
 * ```
 *
 * **Overriding Auto-Configuration:**
 * ```kotlin
 * @Configuration
 * class CustomSpringAIConfig {
 *     @Bean
 *     fun springAIAgentFactory(properties: SpringAIProperties): SpringAIAgentFactory {
 *         // Custom implementation
 *         return MyCustomAgentFactory(properties)
 *     }
 *
 *     @Bean
 *     fun agentRegistry(): AgentRegistry {
 *         // Custom registry with pre-registered agents
 *         return DefaultAgentRegistry().apply {
 *             register("custom", myCustomAgent)
 *         }
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
@AutoConfiguration
@EnableConfigurationProperties(SpringAIProperties::class)
@ConditionalOnProperty(
    prefix = "spice.spring-ai",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class SpringAIAutoConfiguration {

    /**
     * Spring AI Agent Factory Bean
     *
     * Creates agents programmatically from Spring AI ChatModels.
     * Can be overridden by defining your own SpringAIAgentFactory bean.
     *
     * **Usage:**
     * ```kotlin
     * @Service
     * class MyService(private val factory: SpringAIAgentFactory) {
     *     suspend fun chat(model: String, message: String): String {
     *         val agent = factory.openai(model)
     *         val response = agent.processMessage(SpiceMessage.create(message, "user"))
     *         return response.getOrThrow().content
     *     }
     * }
     * ```
     */
    @Bean
    @ConditionalOnMissingBean(SpringAIAgentFactory::class)
    fun springAIAgentFactory(
        properties: SpringAIProperties,
        functionCallbacksProvider: ObjectProvider<List<FunctionToolCallback<*, *>>>
    ): SpringAIAgentFactory {
        val functionCallbacks = functionCallbacksProvider.getIfAvailable() ?: emptyList()
        return DefaultSpringAIAgentFactory(
            properties = properties,
            functionCallbacks = functionCallbacks
        )
    }

    /**
     * Default Agent Bean (Auto-wired)
     *
     * Creates a default agent based on `spice.spring-ai.default-provider` configuration.
     * Can be overridden by defining your own Agent bean.
     *
     * **Usage:**
     * ```kotlin
     * @Service
     * class SimpleService(private val agent: Agent) {
     *     suspend fun chat(message: String) = agent.processMessage(SpiceMessage.create(message, "user"))
     * }
     * ```
     */
    @Bean("spice.spring-ai.defaultAgent")
    @ConditionalOnMissingBean(name = ["spice.spring-ai.defaultAgent"])
    @ConditionalOnBean(SpringAIAgentFactory::class)
    @ConditionalOnProperty(
        prefix = "spice.spring-ai",
        name = ["default-provider"]
    )
    fun defaultAgent(factory: SpringAIAgentFactory): Agent {
        return factory.default()
    }

    /**
     * Agent Registry Bean
     *
     * Provides multi-agent management with named agent registration.
     * Can be overridden by defining your own AgentRegistry bean.
     *
     * **Auto-registration:**
     * When `spice.spring-ai.registry.auto-register=true` (default),
     * agents are automatically registered from enabled providers.
     *
     * **Usage:**
     * ```kotlin
     * @Service
     * class ChatService(private val registry: AgentRegistry) {
     *     suspend fun chat(agentName: String, message: String): String {
     *         val agent = registry.get(agentName)
     *         val response = agent.processMessage(SpiceMessage.create(message, "user"))
     *         return response.getOrThrow().content
     *     }
     * }
     * ```
     */
    @Bean
    @ConditionalOnMissingBean(AgentRegistry::class)
    @ConditionalOnProperty(
        prefix = "spice.spring-ai.registry",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun agentRegistry(
        properties: SpringAIProperties,
        factory: SpringAIAgentFactory
    ): AgentRegistry {
        val registry = DefaultAgentRegistry(
            threadSafe = properties.registry.threadSafe
        )

        // Auto-register agents from enabled providers
        if (properties.registry.autoRegister) {
            if (properties.openai.enabled && properties.openai.apiKey != null) {
                try {
                    registry.register(
                        "openai",
                        factory.openai(properties.openai.model)
                    )
                } catch (e: Exception) {
                    // Log error but don't fail startup
                    println("Failed to auto-register OpenAI agent: ${e.message}")
                }
            }

            if (properties.anthropic.enabled && properties.anthropic.apiKey != null) {
                try {
                    registry.register(
                        "anthropic",
                        factory.anthropic(properties.anthropic.model)
                    )
                } catch (e: Exception) {
                    println("Failed to auto-register Anthropic agent: ${e.message}")
                }
            }

            if (properties.ollama.enabled) {
                try {
                    registry.register(
                        "ollama",
                        factory.ollama(properties.ollama.model)
                    )
                } catch (e: Exception) {
                    println("Failed to auto-register Ollama agent: ${e.message}")
                }
            }
        }

        return registry
    }
}
