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
    val engine: EngineProperties = EngineProperties()
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
    
    val model: String = "gemini-1.5-pro",
    
    @field:DecimalMin(value = "0.0", message = "Temperature must be >= 0.0")
    @field:DecimalMax(value = "2.0", message = "Temperature must be <= 2.0")
    val temperature: Double = 0.7,
    
    @field:Min(value = 1, message = "Max tokens must be >= 1")
    val maxTokens: Int = 1000,
    
    @field:Min(value = 1000, message = "Timeout must be >= 1000ms")
    val timeoutMs: Long = 30000,
    
    val functionCalling: Boolean = true,
    
    val multimodal: Boolean = true
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