package io.github.noailabs.spice.config

import io.github.noailabs.spice.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class SpiceConfigTest {
    
    @BeforeEach
    fun setup() {
        // Reset config before each test
        SpiceConfig.reset()
    }
    
    @Test
    fun `test basic configuration creation`() {
        val config = spiceConfig {
            providers {
                openai {
                    apiKey = "test-key"
                    model = "gpt-4"
                    temperature = 0.8
                }
            }
            
            engine {
                maxAgents = 50
                enableHealthCheck = false
            }
            
            debug {
                enabled = true
                prefix = "[TEST]"
            }
        }
        
        assertNotNull(config)
        
        val openAIConfig = config.providers.getTyped<OpenAIConfig>("openai")
        assertNotNull(openAIConfig)
        assertEquals("test-key", openAIConfig.apiKey)
        assertEquals("gpt-4", openAIConfig.model)
        assertEquals(0.8, openAIConfig.temperature)
        
        assertEquals(50, config.engine.maxAgents)
        assertFalse(config.engine.enableHealthCheck)
        
        assertTrue(config.debug.enabled)
        assertEquals("[TEST]", config.debug.prefix)
    }
    
    @Test
    fun `test global configuration initialization`() {
        assertFalse(SpiceConfig.isInitialized())
        
        SpiceConfig.initialize {
            providers {
                anthropic {
                    apiKey = "claude-key"
                    model = "claude-3-sonnet"
                }
            }
        }
        
        assertTrue(SpiceConfig.isInitialized())
        
        val config = SpiceConfig.current()
        val anthropicConfig = config.providers.getTyped<AnthropicConfig>("anthropic")
        assertNotNull(anthropicConfig)
        assertEquals("claude-key", anthropicConfig.apiKey)
    }
    
    @Test
    fun `test accessing config before initialization throws exception`() {
        assertThrows<IllegalStateException> {
            SpiceConfig.current()
        }
    }
    
    @Test
    fun `test provider validation`() {
        // Test OpenAI validation
        assertThrows<IllegalArgumentException> {
            val config = OpenAIConfig().apply {
                apiKey = ""
            }
            config.validate()
        }
        
        // Test valid configuration
        val validConfig = OpenAIConfig().apply {
            apiKey = "valid-key"
            temperature = 0.5
        }
        validConfig.validate() // Should not throw
        
        // Test temperature bounds
        assertThrows<IllegalArgumentException> {
            val config = OpenAIConfig().apply {
                apiKey = "key"
                temperature = 2.5 // Out of bounds
            }
            config.validate()
        }
    }
    
    @Test
    fun `test custom provider configuration`() {
        val config = spiceConfig {
            providers {
                custom("cohere") {
                    property("apiKey", "cohere-key")
                    property("maxTokens", 2048)
                    property("enabled", true)
                }
            }
        }
        
        val cohereConfig = config.providers.getTyped<CustomProviderConfig>("cohere")
        assertNotNull(cohereConfig)
        assertEquals("cohere-key", cohereConfig.getProperty<String>("apiKey"))
        assertEquals(2048, cohereConfig.getProperty<Int>("maxTokens"))
        assertTrue(cohereConfig.getProperty<Boolean>("enabled") ?: false)
    }
    
    @Test
    fun `test vector store configuration`() {
        val config = spiceConfig {
            vectorStore {
                defaultProvider = "qdrant"
                qdrant = VectorStoreSettings.QdrantConfig(
                    host = "vector.example.com",
                    port = 6334,
                    apiKey = "qdrant-key",
                    useTls = true
                )
                pinecone = VectorStoreSettings.PineconeConfig(
                    apiKey = "pinecone-key",
                    environment = "us-west-2",
                    indexName = "test-index"
                )
            }
        }
        
        assertEquals("qdrant", config.vectorStore.defaultProvider)
        
        val qdrantConfig = config.vectorStore.qdrant
        assertNotNull(qdrantConfig)
        assertEquals("vector.example.com", qdrantConfig.host)
        assertEquals(6334, qdrantConfig.port)
        assertEquals("qdrant-key", qdrantConfig.apiKey)
        assertTrue(qdrantConfig.useTls)
        
        val pineconeConfig = config.vectorStore.pinecone
        assertNotNull(pineconeConfig)
        assertEquals("pinecone-key", pineconeConfig.apiKey)
        assertEquals("us-west-2", pineconeConfig.environment)
        assertEquals("test-index", pineconeConfig.indexName)
    }
    
    @Test
    fun `test configuration reset`() {
        SpiceConfig.initialize {
            providers {
                openai {
                    apiKey = "test-key"
                }
            }
        }
        
        assertTrue(SpiceConfig.isInitialized())
        
        SpiceConfig.reset()
        
        assertFalse(SpiceConfig.isInitialized())
        assertThrows<IllegalStateException> {
            SpiceConfig.current()
        }
    }
    
    @Test
    fun `test multiple provider configuration`() {
        val config = spiceConfig {
            providers {
                openai {
                    apiKey = "openai-key"
                    enabled = true
                }
                
                anthropic {
                    apiKey = "anthropic-key"
                    enabled = false
                }
                
                vertex {
                    projectId = "my-project"
                    location = "europe-west1"
                    enabled = true
                }
                
                vllm {
                    baseUrl = "http://localhost:8080"
                    model = "llama2"
                    enabled = true
                }
            }
        }
        
        assertTrue(config.providers.has("openai"))
        assertTrue(config.providers.has("anthropic"))
        assertTrue(config.providers.has("vertex"))
        assertTrue(config.providers.has("vllm"))
        assertFalse(config.providers.has("unknown"))
        
        val allProviders = config.providers.all()
        assertEquals(4, allProviders.size)
    }
    
    @Test
    fun `test debug configuration`() {
        val config = spiceConfig {
            debug {
                enabled = true
                prefix = "[CUSTOM]"
                logLevel = DebugConfig.LogLevel.DEBUG
                logHttpRequests = true
                logAgentCommunication = true
            }
        }
        
        assertTrue(config.debug.enabled)
        assertEquals("[CUSTOM]", config.debug.prefix)
        assertEquals(DebugConfig.LogLevel.DEBUG, config.debug.logLevel)
        assertTrue(config.debug.logHttpRequests)
        assertTrue(config.debug.logAgentCommunication)
    }
}