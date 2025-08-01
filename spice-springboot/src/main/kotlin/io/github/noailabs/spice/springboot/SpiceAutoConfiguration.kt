package io.github.noailabs.spice.springboot

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.CommHub
import io.github.noailabs.spice.SpiceConfig
import io.github.noailabs.spice.VectorStoreSettings
import io.github.noailabs.spice.agents.claudeAgent
import io.github.noailabs.spice.agents.gptAgent
import io.github.noailabs.spice.agents.mockClaudeAgent
import io.github.noailabs.spice.agents.mockGPTAgent
import io.github.noailabs.spice.config.AnthropicConfig
import io.github.noailabs.spice.config.OpenAIConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Spice Framework Spring Boot Auto Configuration
 * 
 * Automatically configures Spice Framework components based on application properties.
 */
@AutoConfiguration
@EnableConfigurationProperties(SpiceProperties::class)
class SpiceAutoConfiguration {
    
    /**
     * Initialize SpiceConfig from Spring properties
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = ["spice.enabled"], havingValue = "true", matchIfMissing = true)
    fun spiceConfig(properties: SpiceProperties): SpiceConfig {
        
        SpiceConfig.initialize {
            // Configure providers
            providers {
                if (properties.openai.enabled && properties.openai.apiKey.isNotEmpty()) {
                    openai {
                        enabled = properties.openai.enabled
                        apiKey = properties.openai.apiKey
                        baseUrl = properties.openai.baseUrl
                        model = properties.openai.model
                        temperature = properties.openai.temperature
                        maxTokens = properties.openai.maxTokens
                        timeoutMs = properties.openai.timeoutMs
                        functionCalling = properties.openai.functionCalling
                        vision = properties.openai.vision
                    }
                }
                
                if (properties.anthropic.enabled && properties.anthropic.apiKey.isNotEmpty()) {
                    anthropic {
                        enabled = properties.anthropic.enabled
                        apiKey = properties.anthropic.apiKey
                        baseUrl = properties.anthropic.baseUrl
                        model = properties.anthropic.model
                        temperature = properties.anthropic.temperature
                        maxTokens = properties.anthropic.maxTokens
                        timeoutMs = properties.anthropic.timeoutMs
                        toolUse = properties.anthropic.toolUse
                        vision = properties.anthropic.vision
                    }
                }
                
                if (properties.vertex.enabled && properties.vertex.projectId.isNotEmpty()) {
                    vertex {
                        enabled = properties.vertex.enabled
                        projectId = properties.vertex.projectId
                        location = properties.vertex.location
                        model = properties.vertex.model
                        temperature = properties.vertex.temperature
                        maxTokens = properties.vertex.maxTokens
                        timeoutMs = properties.vertex.timeoutMs
                        functionCalling = properties.vertex.functionCalling
                        multimodal = properties.vertex.multimodal
                        serviceAccountKeyPath = properties.vertex.serviceAccountKeyPath
                        useApplicationDefaultCredentials = properties.vertex.useApplicationDefaultCredentials
                    }
                }
                
                if (properties.vllm.enabled && properties.vllm.baseUrl.isNotEmpty()) {
                    vllm {
                        enabled = properties.vllm.enabled
                        baseUrl = properties.vllm.baseUrl
                        model = properties.vllm.model
                        temperature = properties.vllm.temperature
                        maxTokens = properties.vllm.maxTokens
                        timeoutMs = properties.vllm.timeoutMs
                        batchSize = properties.vllm.batchSize
                        maxConcurrentRequests = properties.vllm.maxConcurrentRequests
                    }
                }
            }
            
            // Configure engine
            engine {
                enabled = properties.engine.enabled
                maxAgents = properties.engine.maxAgents
                cleanupIntervalMs = properties.engine.cleanupIntervalMs
                maxMessageHistory = properties.engine.maxMessageHistory
                enableStreaming = properties.engine.enableStreaming
                enableCycleDetection = properties.engine.enableCycleDetection
                enableHealthCheck = properties.engine.enableHealthCheck
            }
            
            // Configure debug if needed
            debug {
                enabled = properties.debug?.enabled ?: false
                prefix = properties.debug?.prefix ?: "[SPICE]"
                logAgentCommunication = properties.debug?.logAgentCommunication ?: false
            }
            
            // Configure vector store
            vectorStore {
                enabled = properties.vectorstore.enabled
                defaultProvider = properties.vectorstore.defaultProvider
                
                properties.vectorstore.qdrant?.let { qdrantProps ->
                    qdrant = VectorStoreSettings.QdrantConfig(
                        host = qdrantProps.host,
                        port = qdrantProps.port,
                        apiKey = qdrantProps.apiKey,
                        useTls = qdrantProps.useTls
                    )
                }
                
                properties.vectorstore.pinecone?.let { pineconeProps ->
                    pinecone = VectorStoreSettings.PineconeConfig(
                        apiKey = pineconeProps.apiKey,
                        environment = pineconeProps.environment,
                        projectName = pineconeProps.projectName,
                        indexName = pineconeProps.indexName
                    )
                }
                
                properties.vectorstore.weaviate?.let { weaviateProps ->
                    weaviate = VectorStoreSettings.WeaviateConfig(
                        host = weaviateProps.host,
                        port = weaviateProps.port,
                        scheme = weaviateProps.scheme,
                        apiKey = weaviateProps.apiKey
                    )
                }
            }
        }
        
        return SpiceConfig.current()
    }
    
    /**
     * CommHub bean
     */
    @Bean
    @ConditionalOnProperty(name = ["spice.enabled"], havingValue = "true", matchIfMissing = true)
    fun commHub(): CommHub {
        CommHub.reset() // Ensure clean state
        return CommHub
    }

    /**
     * OpenAI Agent bean
     */
    @Bean
    @ConditionalOnProperty(name = ["spice.openai.enabled"], havingValue = "true")
    fun openaiAgent(spiceConfig: SpiceConfig): Agent? {
        val config = spiceConfig.providers.getTyped<OpenAIConfig>("openai") ?: return null
        
        return gptAgent(
            id = "spring-openai-agent",
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = config.systemPrompt,
            debugEnabled = spiceConfig.debug.enabled
        )
    }

    /**
     * Claude Agent bean
     */
    @Bean
    @ConditionalOnProperty(name = ["spice.anthropic.enabled"], havingValue = "true")
    fun claudeAgent(spiceConfig: SpiceConfig): Agent? {
        val config = spiceConfig.providers.getTyped<AnthropicConfig>("anthropic") ?: return null
        
        return claudeAgent(
            id = "spring-claude-agent",
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = config.systemPrompt,
            debugEnabled = spiceConfig.debug.enabled
        )
    }

    /**
     * Agent registry bean
     */
    @Bean
    fun spiceAgentRegistry(): MutableMap<String, Agent> {
        return mutableMapOf()
    }
    
    /**
     * Mock agents for development/testing
     */
    @Configuration
    @ConditionalOnProperty(name = ["spice.mock.enabled"], havingValue = "true")
    class MockAgentConfiguration {
        
        @Bean
        fun mockGPTAgent(): Agent {
            return mockGPTAgent(
                id = "mock-gpt-spring",
                personality = "professional",
                debugEnabled = true
            )
        }
        
        @Bean
        fun mockClaudeAgent(): Agent {
            return mockClaudeAgent(
                id = "mock-claude-spring",
                personality = "friendly",
                debugEnabled = true
            )
        }
    }
}