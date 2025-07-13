package io.github.spice.springboot

import io.github.spice.*
import io.github.spice.agents.*
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import org.slf4j.LoggerFactory

/**
 * 🌶️ Spice Framework Auto Configuration for Spring Boot
 * 
 * Automatically configures all LLM provider agents based on application properties
 */
@AutoConfiguration
@EnableConfigurationProperties(SpiceProperties::class)
@ConditionalOnProperty(prefix = "spice", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SpiceAutoConfiguration(
    private val spiceProperties: SpiceProperties
) {
    private val logger = LoggerFactory.getLogger(SpiceAutoConfiguration::class.java)

    /**
     * 🤖 AgentEngine Bean
     */
    @Bean
    @ConditionalOnMissingBean
    fun agentEngine(): AgentEngine {
        logger.info("🌶️ Creating AgentEngine with configuration: ${spiceProperties.engine}")
        
        return AgentEngine().apply {
            // Configure based on properties
            // Additional configuration can be added here
        }
    }

    /**
     * 🚀 OpenAI Agent Configuration
     */
    @Configuration
    @ConditionalOnProperty(prefix = "spice.openai", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = ["org.springframework.web.reactive.function.client.WebClient"])
    class OpenAIConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = ["openAiWebClient"])
        fun openAiWebClient(spiceProperties: SpiceProperties): WebClient {
            val openaiConfig = spiceProperties.openai
            
            return WebClient.builder()
                .baseUrl(openaiConfig.baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer ${openaiConfig.apiKey}")
                .codecs { configurer ->
                    configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
                }
                .build()
        }

        @Bean
        @ConditionalOnMissingBean
        fun openAIAgent(
            spiceProperties: SpiceProperties
        ): OpenAIAgent {
            val config = spiceProperties.openai
            
            return OpenAIAgent(
                apiKey = config.apiKey,
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                baseUrl = config.baseUrl,
                timeout = java.time.Duration.ofMillis(config.timeoutMs)
            )
        }
    }

    /**
     * 🧠 Anthropic Agent Configuration
     */
    @Configuration
    @ConditionalOnProperty(prefix = "spice.anthropic", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = ["org.springframework.web.reactive.function.client.WebClient"])
    class AnthropicConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = ["anthropicWebClient"])
        fun anthropicWebClient(spiceProperties: SpiceProperties): WebClient {
            val anthropicConfig = spiceProperties.anthropic
            
            return WebClient.builder()
                .baseUrl(anthropicConfig.baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("x-api-key", anthropicConfig.apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .codecs { configurer ->
                    configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
                }
                .build()
        }

        @Bean
        @ConditionalOnMissingBean
        fun anthropicAgent(
            spiceProperties: SpiceProperties
        ): AnthropicAgent {
            val config = spiceProperties.anthropic
            
            return AnthropicAgent(
                apiKey = config.apiKey,
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                baseUrl = config.baseUrl,
                timeout = java.time.Duration.ofMillis(config.timeoutMs)
            )
        }
    }

    /**
     * 🌍 Google Vertex AI Agent Configuration
     */
    @Configuration
    @ConditionalOnProperty(prefix = "spice.vertex", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    class VertexConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun vertexAgent(spiceProperties: SpiceProperties): VertexAgent {
            val config = spiceProperties.vertex
            
            return VertexAgent(
                projectId = config.projectId,
                location = config.location,
                accessToken = "", // OAuth 2.0 access token (if available)
                serviceAccountKeyPath = config.serviceAccountKeyPath,
                useApplicationDefaultCredentials = config.useApplicationDefaultCredentials,
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                timeout = java.time.Duration.ofMillis(config.timeoutMs)
            )
        }
    }

    /**
     * ⚡ vLLM Agent Configuration
     */
    @Configuration
    @ConditionalOnProperty(prefix = "spice.vllm", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = ["org.springframework.web.reactive.function.client.WebClient"])
    class VLLMConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = ["vllmWebClient"])
        fun vllmWebClient(spiceProperties: SpiceProperties): WebClient {
            val vllmConfig = spiceProperties.vllm
            
            return WebClient.builder()
                .baseUrl(vllmConfig.baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs { configurer ->
                    configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
                }
                .build()
        }

        @Bean
        @ConditionalOnMissingBean
        fun vllmAgent(
            spiceProperties: SpiceProperties
        ): VLLMAgent {
            val config = spiceProperties.vllm
            
            return VLLMAgent(
                baseUrl = config.baseUrl,
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                timeout = java.time.Duration.ofMillis(config.timeoutMs)
            )
        }
    }

    /**
     * 🎯 Agent Registration
     * 
     * Automatically register all configured agents with the AgentEngine
     */
    @Bean
    @ConditionalOnMissingBean
    fun spiceAgentRegistrar(
        agentEngine: AgentEngine
    ): SpiceAgentRegistrar {
        logger.info("🌶️ Creating SpiceAgentRegistrar with AgentEngine...")
        
        val registrar = SpiceAgentRegistrar(agentEngine)
        
        logger.info("🌶️ SpiceAgentRegistrar created successfully!")
        
        return registrar
    }
}

/**
 * 🎯 Agent Registration Helper for Spring Boot
 */
class SpiceAgentRegistrar(private val agentEngine: AgentEngine) {
    
    fun registerAgent(agent: Agent) {
        agentEngine.registerAgent(agent)
    }
    
    fun getAgentEngine(): AgentEngine = agentEngine
} 