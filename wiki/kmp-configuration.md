# Kotlin Multiplatform Configuration Guide

## Overview

SpiceConfig provides a platform-agnostic configuration system that works seamlessly across all Kotlin Multiplatform targets including JVM, Android, iOS, and JS.

## Getting Started

### Dependencies
```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.spice:spice-core:0.1.0")
            }
        }
    }
}
```

## Basic Configuration

### Initialize SpiceConfig
```kotlin
// In commonMain
fun initializeSpice() {
    SpiceConfig.initialize {
        providers {
            openai {
                enabled = true
                apiKey = getApiKey("OPENAI_API_KEY")  // Platform-specific
                model = "gpt-4o-mini"
                baseUrl = "https://api.openai.com/v1"
            }
            
            anthropic {
                enabled = true
                apiKey = getApiKey("ANTHROPIC_API_KEY")
                model = "claude-3-opus-20240229"
            }
        }
        
        engine {
            maxConcurrentAgents = 10
            defaultTimeout = 30_000
            enableHealthCheck = true
        }
        
        debug {
            enabled = getPlatform() != Platform.PRODUCTION
            prefix = "[SPICE-${getPlatform()}]"
        }
    }
}
```

### Platform-Specific Configuration

#### Android
```kotlin
// androidMain
actual fun getApiKey(name: String): String {
    return BuildConfig.getValue(name) ?: System.getenv(name) ?: ""
}

actual fun getPlatform(): Platform = Platform.ANDROID

// In Application class
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeSpice()
    }
}
```

#### iOS
```kotlin
// iosMain
actual fun getApiKey(name: String): String {
    return NSBundle.mainBundle.objectForInfoDictionaryKey(name) as? String ?: ""
}

actual fun getPlatform(): Platform = Platform.IOS

// In iOS app
fun initializeApp() {
    initializeSpice()
}
```

#### Desktop (JVM)
```kotlin
// jvmMain
actual fun getApiKey(name: String): String {
    return System.getenv(name) 
        ?: Properties().apply {
            load(FileInputStream("config.properties"))
        }.getProperty(name, "")
}

actual fun getPlatform(): Platform = Platform.JVM
```

#### JavaScript
```kotlin
// jsMain
actual fun getApiKey(name: String): String {
    return js("process.env[name]") ?: ""
}

actual fun getPlatform(): Platform = Platform.JS
```

## Using SpiceConfig

### Creating Agents
```kotlin
// commonMain - Works on all platforms
suspend fun createAIAgent(): Agent {
    val config = SpiceConfig.current()
    
    // Create agent based on available providers
    return when {
        config.providers.has("openai") -> {
            buildAgent {
                id = "ai-assistant"
                name = "AI Assistant"
                
                val openAIConfig = config.providers.getTyped<OpenAIConfig>("openai")!!
                
                handle { comm ->
                    val response = callOpenAI(
                        apiKey = openAIConfig.apiKey,
                        model = openAIConfig.model,
                        prompt = comm.content
                    )
                    comm.reply(response, id)
                }
            }
        }
        config.providers.has("anthropic") -> {
            config.createAnthropicAgent()
        }
        else -> {
            createMockAgent()  // Fallback for testing
        }
    }
}
```

### Dynamic Configuration Loading
```kotlin
// Support for loading config from various sources
interface ConfigLoader {
    suspend fun load(): Map<String, Any>
}

// JSON configuration loader
class JsonConfigLoader(private val jsonString: String) : ConfigLoader {
    override suspend fun load(): Map<String, Any> {
        return Json.decodeFromString(jsonString)
    }
}

// Remote configuration loader
class RemoteConfigLoader(private val url: String) : ConfigLoader {
    override suspend fun load(): Map<String, Any> {
        val response = httpClient.get(url)
        return Json.decodeFromString(response.body())
    }
}

// Apply dynamic configuration
suspend fun applyDynamicConfig(loader: ConfigLoader) {
    val configMap = loader.load()
    
    SpiceConfig.update {
        providers {
            configMap["providers"]?.let { providers ->
                (providers as Map<String, Map<String, Any>>).forEach { (name, settings) ->
                    when (name) {
                        "openai" -> openai {
                            apiKey = settings["apiKey"] as String
                            model = settings["model"] as String
                        }
                        "anthropic" -> anthropic {
                            apiKey = settings["apiKey"] as String
                        }
                    }
                }
            }
        }
    }
}
```

### Vector Store Configuration
```kotlin
// Platform-agnostic vector store setup
fun configureVectorStore() {
    SpiceConfig.update {
        vectorStore {
            when (getPlatform()) {
                Platform.ANDROID, Platform.IOS -> {
                    // Use local vector store for mobile
                    custom("local") {
                        type = "sqlite-vec"
                        path = getLocalDbPath()
                    }
                }
                Platform.JVM, Platform.JS -> {
                    // Use Qdrant for server/desktop
                    qdrant {
                        host = "localhost"
                        port = 6333
                        apiKey = getApiKey("QDRANT_API_KEY")
                    }
                }
            }
        }
    }
}
```

## Environment-Specific Configuration

### Development vs Production
```kotlin
sealed class Environment {
    object Development : Environment()
    object Staging : Environment()
    object Production : Environment()
}

fun configureForEnvironment(env: Environment) {
    SpiceConfig.initialize {
        providers {
            when (env) {
                is Environment.Development -> {
                    // Use mock providers
                    custom("mock") {
                        endpoint = "http://localhost:8080/mock"
                    }
                }
                is Environment.Staging -> {
                    openai {
                        apiKey = getApiKey("STAGING_OPENAI_KEY")
                        baseUrl = "https://staging-proxy.company.com/openai"
                    }
                }
                is Environment.Production -> {
                    openai {
                        apiKey = getApiKey("PROD_OPENAI_KEY")
                        model = "gpt-4"  // Use better model in production
                    }
                }
            }
        }
        
        debug {
            enabled = env != Environment.Production
            logLevel = when (env) {
                is Environment.Development -> LogLevel.VERBOSE
                is Environment.Staging -> LogLevel.DEBUG
                is Environment.Production -> LogLevel.ERROR
            }
        }
    }
}
```

### Feature Flags
```kotlin
// Feature flag integration
interface FeatureFlags {
    fun isEnabled(feature: String): Boolean
}

fun configureWithFeatureFlags(flags: FeatureFlags) {
    SpiceConfig.update {
        providers {
            if (flags.isEnabled("anthropic_support")) {
                anthropic {
                    enabled = true
                    apiKey = getApiKey("ANTHROPIC_API_KEY")
                }
            }
            
            if (flags.isEnabled("local_llm")) {
                vllm {
                    enabled = true
                    endpoint = "http://localhost:8000"
                }
            }
        }
        
        engine {
            if (flags.isEnabled("experimental_features")) {
                experimental = true
                enableBetaFeatures = listOf("async-flows", "tool-chains")
            }
        }
    }
}
```

## Testing Configuration

### Mock Configuration for Tests
```kotlin
// commonTest
class SpiceConfigTest {
    
    @BeforeTest
    fun setup() {
        SpiceConfig.initialize {
            providers {
                custom("mock-llm") {
                    endpoint = "mock://llm"
                    responses = mapOf(
                        "test" to "Mock response"
                    )
                }
            }
            
            engine {
                testMode = true
                randomSeed = 42  // Deterministic for tests
            }
        }
    }
    
    @Test
    fun testWithMockConfig() {
        val agent = SpiceConfig.current().createMockAgent()
        
        runBlocking {
            val response = agent.process(comm("test"))
            assertEquals("Mock response", response.content)
        }
    }
}
```

### Configuration Validation
```kotlin
// Validate configuration on startup
fun validateConfiguration() {
    val config = SpiceConfig.current()
    
    val errors = mutableListOf<String>()
    
    // Check required providers
    if (!config.providers.has("openai") && !config.providers.has("anthropic")) {
        errors.add("At least one LLM provider must be configured")
    }
    
    // Check API keys
    config.providers.all().forEach { (name, provider) ->
        if (provider.enabled && provider is ApiKeyProvider && provider.apiKey.isBlank()) {
            errors.add("API key missing for provider: $name")
        }
    }
    
    // Check vector store
    if (config.vectorStore.all().isEmpty()) {
        errors.add("Warning: No vector store configured")
    }
    
    if (errors.isNotEmpty()) {
        throw ConfigurationException(errors.joinToString("\n"))
    }
}
```

## Advanced Configuration

### Custom Provider Implementation
```kotlin
// Define custom provider config
class CustomLLMConfig : ProviderConfig() {
    override val name = "custom-llm"
    override var enabled = true
    
    var endpoint: String = ""
    var authToken: String = ""
    var modelPath: String = ""
    var temperature: Float = 0.7f
}

// Register custom provider
fun registerCustomProvider() {
    SpiceConfig.update {
        providers {
            register("custom-llm", CustomLLMConfig().apply {
                endpoint = "https://my-llm-service.com"
                authToken = getApiKey("CUSTOM_LLM_TOKEN")
                modelPath = "/models/my-model"
            })
        }
    }
}

// Use custom provider
suspend fun useCustomProvider() {
    val config = SpiceConfig.current()
        .providers.getTyped<CustomLLMConfig>("custom-llm")
        ?: error("Custom LLM not configured")
    
    val agent = buildAgent {
        id = "custom-agent"
        
        handle { comm ->
            val response = callCustomLLM(
                endpoint = config.endpoint,
                token = config.authToken,
                model = config.modelPath,
                prompt = comm.content,
                temperature = config.temperature
            )
            comm.reply(response, id)
        }
    }
}
```

### Configuration Persistence
```kotlin
// Save configuration for offline use
interface ConfigPersistence {
    suspend fun save(config: SpiceConfig)
    suspend fun load(): SpiceConfig?
}

class FileConfigPersistence(private val path: String) : ConfigPersistence {
    override suspend fun save(config: SpiceConfig) {
        val json = Json.encodeToString(config.toMap())
        writeFile(path, json)
    }
    
    override suspend fun load(): SpiceConfig? {
        val json = readFile(path) ?: return null
        val map = Json.decodeFromString<Map<String, Any>>(json)
        return SpiceConfig.fromMap(map)
    }
}

// Usage
suspend fun initializeWithCache(persistence: ConfigPersistence) {
    // Try to load cached config first
    val cachedConfig = persistence.load()
    
    if (cachedConfig != null) {
        SpiceConfig.initialize(cachedConfig)
    } else {
        // Load from remote or use defaults
        initializeSpice()
        
        // Cache for next time
        persistence.save(SpiceConfig.current())
    }
}
```

## Platform-Specific Considerations

### Memory Management
```kotlin
// iOS - Manage memory carefully
fun configureForIOS() {
    SpiceConfig.update {
        engine {
            // Limit concurrent operations on mobile
            maxConcurrentAgents = 3
            
            // Shorter timeouts for mobile networks
            defaultTimeout = 15_000
            
            // Enable memory-conscious features
            enableMemoryOptimization = true
            maxCacheSize = 10 * 1024 * 1024  // 10MB
        }
    }
}
```

### Network Configuration
```kotlin
// Configure network settings per platform
expect fun getNetworkConfig(): NetworkConfig

// androidMain
actual fun getNetworkConfig(): NetworkConfig {
    return NetworkConfig(
        connectTimeout = 10_000,
        readTimeout = 30_000,
        proxy = if (BuildConfig.DEBUG) {
            ProxyConfig("10.0.2.2", 8888)  // Android emulator
        } else null
    )
}

// iosMain
actual fun getNetworkConfig(): NetworkConfig {
    return NetworkConfig(
        connectTimeout = 10_000,
        readTimeout = 30_000,
        allowsCellular = true
    )
}
```

## Best Practices

1. **Initialize Early**: Configure SpiceConfig as early as possible in your app lifecycle
2. **Use Type Safety**: Leverage the typed configuration classes instead of raw maps
3. **Environment Variables**: Keep sensitive data in environment variables, not in code
4. **Validate Configuration**: Always validate configuration on startup
5. **Platform Awareness**: Adjust settings based on platform capabilities
6. **Lazy Loading**: Use lazy initialization for expensive configurations
7. **Configuration as Code**: Version control your configuration structure
8. **Fail Fast**: Detect configuration errors early with validation
9. **Mock for Tests**: Always provide mock configurations for testing
10. **Document Settings**: Document all configuration options and their effects

## Troubleshooting

### Common Issues

1. **"SpiceConfig not initialized"**
   - Ensure `SpiceConfig.initialize()` is called before any usage
   - Check initialization order in your platform's entry point

2. **"Provider not found"**
   - Verify the provider is enabled in configuration
   - Check for typos in provider names
   - Ensure API keys are set correctly

3. **Platform-specific crashes**
   - Verify platform-specific implementations of expect functions
   - Check memory limits on mobile platforms
   - Ensure network permissions are granted

### Debug Configuration
```kotlin
// Enable detailed logging for troubleshooting
SpiceConfig.update {
    debug {
        enabled = true
        logLevel = LogLevel.VERBOSE
        logProviderCalls = true
        logConfiguration = true
        
        // Platform-specific debug settings
        when (getPlatform()) {
            Platform.ANDROID -> logToLogcat = true
            Platform.IOS -> logToConsole = true
            Platform.JVM -> logToFile = "spice-debug.log"
            Platform.JS -> logToBrowserConsole = true
        }
    }
}
```

## Next Steps

- [Agent Development Guide](agent-guide.md)
- [Tool Development](tool-development.md)
- [Platform-Specific APIs](platform-apis.md)