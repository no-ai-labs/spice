package io.github.noailabs.spice.config

/**
 * Provider Registry for managing all LLM and service provider configurations
 */
class ProviderRegistry {
    private val providers = mutableMapOf<String, ProviderConfig>()
    
    /**
     * OpenAI configuration
     */
    fun openai(config: OpenAIConfig.() -> Unit) {
        val openaiConfig = OpenAIConfig().apply(config)
        providers["openai"] = openaiConfig
    }
    
    /**
     * Anthropic configuration
     */
    fun anthropic(config: AnthropicConfig.() -> Unit) {
        val anthropicConfig = AnthropicConfig().apply(config)
        providers["anthropic"] = anthropicConfig
    }
    
    /**
     * Google Vertex AI configuration
     */
    fun vertex(config: VertexConfig.() -> Unit) {
        val vertexConfig = VertexConfig().apply(config)
        providers["vertex"] = vertexConfig
    }
    
    /**
     * vLLM configuration
     */
    fun vllm(config: VLLMConfig.() -> Unit) {
        val vllmConfig = VLLMConfig().apply(config)
        providers["vllm"] = vllmConfig
    }
    
    /**
     * Custom provider configuration
     */
    fun custom(name: String, config: CustomProviderConfig.() -> Unit) {
        val customConfig = CustomProviderConfig(name).apply(config)
        providers[name] = customConfig
    }
    
    /**
     * Get provider configuration by name
     */
    fun get(name: String): ProviderConfig? = providers[name]
    
    /**
     * Get typed provider configuration
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ProviderConfig> getTyped(name: String): T? {
        return providers[name] as? T
    }
    
    /**
     * Check if provider is configured
     */
    fun has(name: String): Boolean = providers.containsKey(name)
    
    /**
     * Get all configured providers
     */
    fun all(): Map<String, ProviderConfig> = providers.toMap()
}

/**
 * Base provider configuration
 */
sealed class ProviderConfig {
    abstract var enabled: Boolean
    abstract val name: String
}

/**
 * OpenAI provider configuration
 */
class OpenAIConfig : ProviderConfig() {
    override var enabled: Boolean = true
    override val name: String = "openai"
    var apiKey: String = ""
    var baseUrl: String = "https://api.openai.com/v1"
    var model: String = "gpt-4"
    var temperature: Double = 0.7
    var maxTokens: Int = 4096
    var timeoutMs: Long = 30000
    var functionCalling: Boolean = true
    var vision: Boolean = true
    var systemPrompt: String = "You are a helpful AI assistant."
    val headers: MutableMap<String, String> = mutableMapOf()
    
    /**
     * Add custom header
     */
    fun header(key: String, value: String): OpenAIConfig {
        headers[key] = value
        return this
    }
    
    /**
     * Validate configuration
     */
    fun validate() {
        require(apiKey.isNotEmpty()) { "OpenAI API key is required" }
        require(baseUrl.isNotEmpty()) { "OpenAI base URL is required" }
        require(model.isNotEmpty()) { "OpenAI model is required" }
        require(temperature in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
        require(maxTokens > 0) { "Max tokens must be positive" }
        require(timeoutMs > 0) { "Timeout must be positive" }
    }
}

/**
 * Anthropic provider configuration
 */
class AnthropicConfig : ProviderConfig() {
    override var enabled: Boolean = true
    override val name: String = "anthropic"
    var apiKey: String = ""
    var baseUrl: String = "https://api.anthropic.com"
    var model: String = "claude-3-5-sonnet-20241022"
    var temperature: Double = 0.7
    var maxTokens: Int = 4096
    var timeoutMs: Long = 30000
    var toolUse: Boolean = true
    var vision: Boolean = true
    var systemPrompt: String = "You are Claude, a helpful AI assistant."
    var anthropicVersion: String = "2023-06-01"
    val headers: MutableMap<String, String> = mutableMapOf()
    
    /**
     * Add custom header
     */
    fun header(key: String, value: String): AnthropicConfig {
        headers[key] = value
        return this
    }
    
    /**
     * Validate configuration
     */
    fun validate() {
        require(apiKey.isNotEmpty()) { "Anthropic API key is required" }
        require(baseUrl.isNotEmpty()) { "Anthropic base URL is required" }
        require(model.isNotEmpty()) { "Anthropic model is required" }
        require(temperature in 0.0..1.0) { "Temperature must be between 0.0 and 1.0" }
        require(maxTokens > 0) { "Max tokens must be positive" }
        require(timeoutMs > 0) { "Timeout must be positive" }
    }
}

/**
 * Google Vertex AI provider configuration
 */
class VertexConfig : ProviderConfig() {
    override var enabled: Boolean = true
    override val name: String = "vertex"
    var projectId: String = ""
    var location: String = "us-central1"
    var model: String = "gemini-1.5-flash-002"
    var temperature: Double = 0.7
    var maxTokens: Int = 4096
    var timeoutMs: Long = 30000
    var functionCalling: Boolean = true
    var multimodal: Boolean = true
    var serviceAccountKeyPath: String = ""
    var useApplicationDefaultCredentials: Boolean = true
    
    /**
     * Validate configuration
     */
    fun validate() {
        require(projectId.isNotEmpty()) { "Google Cloud project ID is required" }
        require(location.isNotEmpty()) { "Google Cloud location is required" }
        require(model.isNotEmpty()) { "Vertex AI model is required" }
        require(temperature in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
        require(maxTokens > 0) { "Max tokens must be positive" }
        require(timeoutMs > 0) { "Timeout must be positive" }
        
        if (!useApplicationDefaultCredentials) {
            require(serviceAccountKeyPath.isNotEmpty()) {
                "Service account key path is required when not using ADC"
            }
        }
    }
}

/**
 * vLLM provider configuration
 */
class VLLMConfig : ProviderConfig() {
    override var enabled: Boolean = true
    override val name: String = "vllm"
    var baseUrl: String = "http://localhost:8000"
    var model: String = "meta-llama/Llama-2-7b-chat-hf"
    var temperature: Double = 0.7
    var maxTokens: Int = 4096
    var timeoutMs: Long = 30000
    var batchSize: Int = 8
    var maxConcurrentRequests: Int = 10
    val headers: MutableMap<String, String> = mutableMapOf()
    
    /**
     * Add custom header
     */
    fun header(key: String, value: String): VLLMConfig {
        headers[key] = value
        return this
    }
    
    /**
     * Validate configuration
     */
    fun validate() {
        require(baseUrl.isNotEmpty()) { "vLLM base URL is required" }
        require(model.isNotEmpty()) { "vLLM model is required" }
        require(temperature in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
        require(maxTokens > 0) { "Max tokens must be positive" }
        require(timeoutMs > 0) { "Timeout must be positive" }
        require(batchSize > 0) { "Batch size must be positive" }
        require(maxConcurrentRequests > 0) { "Max concurrent requests must be positive" }
    }
}

/**
 * Custom provider configuration for extensibility
 */
class CustomProviderConfig(
    override val name: String,
    override var enabled: Boolean = true
) : ProviderConfig() {
    val properties: MutableMap<String, Any> = mutableMapOf()
    
    /**
     * Set property
     */
    fun property(key: String, value: Any) {
        properties[key] = value
    }
    
    /**
     * Get property
     */
    inline fun <reified T> getProperty(key: String): T? {
        return properties[key] as? T
    }
    
    /**
     * Get property with default
     */
    inline fun <reified T> getProperty(key: String, default: T): T {
        return properties[key] as? T ?: default
    }
}