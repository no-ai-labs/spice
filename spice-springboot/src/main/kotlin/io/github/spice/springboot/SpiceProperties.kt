package io.github.spice.springboot

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.DecimalMax

/**
 * 🌶️ Spice Framework Configuration Properties for Spring Boot
 * 
 * All LLM provider configurations for auto-configuration
 */
@ConfigurationProperties(prefix = "spice")
@Validated
data class SpiceProperties(
    /**
     * Enable/disable Spice Framework
     */
    val enabled: Boolean = true,
    
    /**
     * OpenAI configuration
     */
    val openai: OpenAIProperties = OpenAIProperties(),
    
    /**
     * Anthropic configuration
     */
    val anthropic: AnthropicProperties = AnthropicProperties(),
    
    /**
     * Google Vertex AI configuration
     */
    val vertex: VertexProperties = VertexProperties(),
    
    /**
     * vLLM configuration
     */
    val vllm: VLLMProperties = VLLMProperties(),
    
    /**
     * AgentEngine configuration
     */
    val engine: EngineProperties = EngineProperties(),
    
    /**
     * Debug configuration
     */
    val debug: DebugProperties? = null,
    
    /**
     * Mock configuration for testing
     */
    val mock: MockProperties = MockProperties(),
    
    /**
     * Vector store configuration
     */
    val vectorstore: VectorStoreProperties = VectorStoreProperties()
)

/**
 * OpenAI Agent Configuration
 */
data class OpenAIProperties(
    val enabled: Boolean = true,
    
    @field:NotBlank(message = "OpenAI API key is required")
    val apiKey: String = "",
    
    val baseUrl: String = "https://api.openai.com/v1",
    
    val model: String = "gpt-4",
    
    @field:DecimalMin(value = "0.0", message = "Temperature must be >= 0.0")
    @field:DecimalMax(value = "2.0", message = "Temperature must be <= 2.0")
    val temperature: Double = 0.7,
    
    @field:Min(value = 1, message = "Max tokens must be >= 1")
    val maxTokens: Int = 1000,
    
    @field:Min(value = 1000, message = "Timeout must be >= 1000ms")
    val timeoutMs: Long = 30000,
    
    val functionCalling: Boolean = true,
    
    val vision: Boolean = true
)

/**
 * Anthropic Claude Configuration
 */
data class AnthropicProperties(
    val enabled: Boolean = true,
    
    @field:NotBlank(message = "Anthropic API key is required")
    val apiKey: String = "",
    
    val baseUrl: String = "https://api.anthropic.com",
    
    val model: String = "claude-3-5-sonnet-20241022",
    
    @field:DecimalMin(value = "0.0", message = "Temperature must be >= 0.0")
    @field:DecimalMax(value = "1.0", message = "Temperature must be <= 1.0")
    val temperature: Double = 0.7,
    
    @field:Min(value = 1, message = "Max tokens must be >= 1")
    val maxTokens: Int = 1000,
    
    @field:Min(value = 1000, message = "Timeout must be >= 1000ms")
    val timeoutMs: Long = 30000,
    
    val toolUse: Boolean = true,
    
    val vision: Boolean = true
)

/**
 * Google Vertex AI Configuration
 */
data class VertexProperties(
    val enabled: Boolean = true,
    
    @field:NotBlank(message = "Google Cloud project ID is required")
    val projectId: String = "",
    
    @field:NotBlank(message = "Google Cloud location is required")
    val location: String = "us-central1",
    
    val model: String = "gemini-1.5-flash-002",
    
    @field:DecimalMin(value = "0.0", message = "Temperature must be >= 0.0")
    @field:DecimalMax(value = "2.0", message = "Temperature must be <= 2.0")
    val temperature: Double = 0.7,
    
    @field:Min(value = 1, message = "Max tokens must be >= 1")
    val maxTokens: Int = 1000,
    
    @field:Min(value = 1000, message = "Timeout must be >= 1000ms")
    val timeoutMs: Long = 30000,
    
    val functionCalling: Boolean = true,
    
    val multimodal: Boolean = true,
    
    // API Key for authentication (not supported by Vertex AI)
    @Deprecated("Vertex AI does not support API key authentication")
    val apiKey: String = "",
    
    // Service Account JSON key file path for authentication
    val serviceAccountKeyPath: String = "",
    
    // Use Application Default Credentials (ADC) if no service account key is provided
    val useApplicationDefaultCredentials: Boolean = true
)

/**
 * vLLM Configuration
 */
data class VLLMProperties(
    val enabled: Boolean = true,
    
    @field:NotBlank(message = "vLLM server URL is required")
    val baseUrl: String = "http://localhost:8000",
    
    val model: String = "meta-llama/Llama-2-7b-chat-hf",
    
    @field:DecimalMin(value = "0.0", message = "Temperature must be >= 0.0")
    @field:DecimalMax(value = "2.0", message = "Temperature must be <= 2.0")
    val temperature: Double = 0.7,
    
    @field:Min(value = 1, message = "Max tokens must be >= 1")
    val maxTokens: Int = 1000,
    
    @field:Min(value = 1000, message = "Timeout must be >= 1000ms")
    val timeoutMs: Long = 30000,
    
    @field:Min(value = 1, message = "Batch size must be >= 1")
    val batchSize: Int = 8,
    
    @field:Min(value = 1, message = "Max concurrent requests must be >= 1")
    val maxConcurrentRequests: Int = 10
)

/**
 * AgentEngine Configuration
 */
data class EngineProperties(
    val enabled: Boolean = true,
    
    @field:Min(value = 1, message = "Max agents must be >= 1")
    val maxAgents: Int = 100,
    
    @field:Min(value = 1000, message = "Cleanup interval must be >= 1000ms")
    val cleanupIntervalMs: Long = 60000,
    
    @field:Min(value = 1, message = "Max message history must be >= 1")
    val maxMessageHistory: Int = 1000,
    
    val enableStreaming: Boolean = true,
    
    val enableCycleDetection: Boolean = true,
    
    val enableHealthCheck: Boolean = true
)

/**
 * Debug Configuration
 */
data class DebugProperties(
    val enabled: Boolean = false,
    val prefix: String = "[SPICE]",
    val logLevel: String = "INFO",
    val logHttpRequests: Boolean = false,
    val logAgentCommunication: Boolean = false
)

/**
 * Mock Configuration for testing
 */
data class MockProperties(
    val enabled: Boolean = false
)

/**
 * Vector Store Configuration
 */
data class VectorStoreProperties(
    val enabled: Boolean = true,
    val defaultProvider: String = "memory",
    val qdrant: QdrantProperties? = null,
    val pinecone: PineconeProperties? = null,
    val weaviate: WeaviateProperties? = null
)

/**
 * Qdrant Vector Store Properties
 */
data class QdrantProperties(
    val host: String = "localhost",
    
    @field:Min(value = 1, message = "Port must be >= 1")
    @field:Max(value = 65535, message = "Port must be <= 65535")
    val port: Int = 6333,
    
    val apiKey: String? = null,
    val useTls: Boolean = false
)

/**
 * Pinecone Vector Store Properties
 */
data class PineconeProperties(
    @field:NotBlank(message = "Pinecone API key is required")
    val apiKey: String = "",
    
    @field:NotBlank(message = "Pinecone environment is required")
    val environment: String = "",
    
    val projectName: String? = null,
    val indexName: String = "spice-vectors"
)

/**
 * Weaviate Vector Store Properties
 */
data class WeaviateProperties(
    val host: String = "localhost",
    
    @field:Min(value = 1, message = "Port must be >= 1")
    @field:Max(value = 65535, message = "Port must be <= 65535")
    val port: Int = 8080,
    
    val scheme: String = "http",
    val apiKey: String? = null
) 