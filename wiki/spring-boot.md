# Spring Boot Integration

## Getting Started with Spring Boot

The Spice Framework provides seamless integration with Spring Boot through auto-configuration and Spring-friendly APIs.

### Dependencies
```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.spice:spice-springboot:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

### Basic Setup
```kotlin
@SpringBootApplication
@EnableSpice  // Enable Spice Framework
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

## Auto-Configuration

### Default Configuration
Spice Spring Boot starter automatically configures:
- Agent registry as Spring bean
- Tool registry as Spring bean
- CommHub as Spring bean
- Built-in tools registration
- Default agents (if enabled)

### Application Properties
```yaml
# application.yml
spice:
  enabled: true
  agents:
    auto-register: true
    scan-packages:
      - com.example.agents
      - com.example.tools
  tools:
    register-builtins: true
    namespaces:
      - default
      - custom
  comm-hub:
    history-size: 1000
    enable-analytics: true
  debug:
    enabled: false
    prefix: "[SPICE]"
    
  # Provider Configuration (NEW)
  openai:
    enabled: true
    api-key: ${OPENAI_API_KEY}
    base-url: https://api.openai.com/v1
    model: gpt-4o-mini
    temperature: 0.7
    max-tokens: 2000
    
  anthropic:
    enabled: false
    api-key: ${ANTHROPIC_API_KEY}
    base-url: https://api.anthropic.com
    model: claude-3-opus-20240229
    
  # Vector Store Configuration (NEW)
  vectorstore:
    qdrant:
      host: ${QDRANT_HOST:localhost}
      port: ${QDRANT_PORT:6333}
      api-key: ${QDRANT_API_KEY:}
      use-tls: false
    pinecone:
      environment: ${PINECONE_ENV:us-east-1}
      api-key: ${PINECONE_API_KEY:}
```

## SpiceConfig Integration (NEW)

### Automatic Configuration
SpiceConfig is automatically initialized from application properties:

```kotlin
@Service
class MyService(
    private val spiceConfig: SpiceConfig  // Auto-injected
) {
    fun useOpenAI() {
        val openAIConfig = spiceConfig.providers.getTyped<OpenAIConfig>("openai")
        println("Using model: ${openAIConfig?.model}")
    }
}
```

### Creating Agents with SpiceConfig
```kotlin
@Configuration
class AgentConfiguration {
    
    @Bean
    fun openAIAgent(): Agent {
        // Automatically uses SpiceConfig settings
        return SpiceConfig.current().createOpenAIAgent()
    }
    
    @Bean 
    fun anthropicAgent(): Agent {
        // Create with custom settings
        return SpiceConfig.current().createAnthropicAgent {
            id = "custom-claude"
            name = "Custom Claude Agent"
            systemPrompt = "You are a helpful assistant"
        }
    }
}
```

### Accessing Provider Settings
```kotlin
@Service
class AIService {
    
    fun processWithOpenAI(prompt: String): String {
        val config = SpiceConfig.current().providers.getTyped<OpenAIConfig>("openai")
            ?: throw IllegalStateException("OpenAI not configured")
            
        // Use config.apiKey, config.model, etc.
        return callOpenAI(config, prompt)
    }
    
    fun listAvailableProviders(): List<String> {
        return SpiceConfig.current().providers.all().keys.toList()
    }
}
```

### Vector Store Configuration
```kotlin
@Component
class VectorSearchService {
    
    private val vectorStore: VectorStore by lazy {
        val qdrantConfig = SpiceConfig.current().vectorStore.qdrant
            ?: throw IllegalStateException("Qdrant not configured")
            
        QdrantVectorStore(
            host = qdrantConfig.host,
            port = qdrantConfig.port,
            apiKey = qdrantConfig.apiKey
        )
    }
    
    suspend fun search(query: String): List<SearchResult> {
        // Vector store is configured from SpiceConfig
        return vectorStore.search(query)
    }
}
```

### Custom Configuration Extension
```kotlin
@Configuration
class CustomSpiceConfiguration {
    
    @Bean
    @Primary
    fun customSpiceConfig(properties: SpiceProperties): SpiceConfig {
        return SpiceConfig.builder()
            .providers {
                // Add custom provider
                custom("my-llm") {
                    endpoint = "https://my-llm.com/api"
                    apiKey = properties.customLlm.apiKey
                    headers = mapOf("X-Custom" to "value")
                }
            }
            .engine {
                // Custom engine settings
                maxAgents = 100
                defaultTimeout = 60_000
            }
            .build()
    }
}
```

## Spring Bean Integration

### Agents as Spring Beans
```kotlin
@Component
class GreetingAgent : Agent {
    override val id = "greeting-agent"
    override val name = "Greeting Agent"
    
    @Value("\${app.greeting.template}")
    private lateinit var greetingTemplate: String
    
    override suspend fun process(comm: Comm): Comm {
        val greeting = greetingTemplate.replace("{name}", comm.data["name"] as? String ?: "User")
        return comm.reply(greeting, id)
    }
}

@Component
class WeatherAgent(
    private val weatherService: WeatherService  // Inject Spring service
) : Agent {
    override val id = "weather-agent"
    override val name = "Weather Agent"
    
    override suspend fun process(comm: Comm): Comm {
        val location = comm.content
        val weather = weatherService.getWeather(location)
        return comm.reply("Weather in $location: $weather", id)
    }
}
```

### Tools as Spring Beans
```kotlin
@Component
@Tool("database-query")
class DatabaseQueryTool(
    private val jdbcTemplate: JdbcTemplate
) : Tool {
    override val name = "database-query"
    override val description = "Query application database"
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val query = params["query"] as String
            val results = jdbcTemplate.queryForList(query)
            ToolResult.success(
                "Found ${results.size} results",
                mapOf("data" to results)
            )
        } catch (e: Exception) {
            ToolResult.error("Query failed: ${e.message}")
        }
    }
}
```

## REST API Integration

### Agent Controller
```kotlin
@RestController
@RequestMapping("/api/agents")
class AgentController(
    private val agentRegistry: AgentRegistry,
    private val commHub: CommHub
) {
    
    @GetMapping
    fun listAgents(): List<AgentInfo> {
        return agentRegistry.list().map { agent ->
            AgentInfo(
                id = agent.id,
                name = agent.name,
                description = agent.description
            )
        }
    }
    
    @PostMapping("/{agentId}/process")
    suspend fun processMessage(
        @PathVariable agentId: String,
        @RequestBody request: MessageRequest
    ): MessageResponse {
        val agent = agentRegistry.get(agentId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found")
        
        val comm = comm(request.content) {
            from(request.from ?: "api")
            type(CommType.valueOf(request.type ?: "TEXT"))
            request.data?.forEach { (key, value) ->
                data(key, value)
            }
        }
        
        val response = agent.process(comm)
        
        return MessageResponse(
            content = response.content,
            from = response.from,
            type = response.type.name,
            data = response.data
        )
    }
}

data class MessageRequest(
    val content: String,
    val from: String? = null,
    val type: String? = null,
    val data: Map<String, Any>? = null
)

data class MessageResponse(
    val content: String,
    val from: String?,
    val type: String,
    val data: Map<String, Any>
)
```

### WebSocket Support
```kotlin
@Configuration
@EnableWebSocket
class WebSocketConfig : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(AgentWebSocketHandler(), "/ws/agents")
            .setAllowedOrigins("*")
    }
}

@Component
class AgentWebSocketHandler(
    private val commHub: CommHub
) : TextWebSocketHandler() {
    
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    
    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        
        // Subscribe to CommHub events
        commHub.subscribe(session.id) { comm ->
            session.sendMessage(TextMessage(
                Json.encodeToString(comm)
            ))
        }
    }
    
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val request = Json.decodeFromString<MessageRequest>(message.payload)
        
        // Process through CommHub
        val comm = comm(request.content) {
            from(session.id)
        }
        
        runBlocking {
            commHub.broadcast(comm)
        }
    }
    
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session.id)
        commHub.unsubscribe(session.id)
    }
}
```

## Configuration Classes

### Custom Configuration
```kotlin
@Configuration
class SpiceConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    fun customAgentRegistry(): AgentRegistry {
        return AgentRegistry("custom").apply {
            // Custom initialization
        }
    }
    
    @Bean
    fun llmAgentConfiguration(): LLMAgentConfig {
        return LLMAgentConfig(
            defaultModel = "gpt-4",
            apiKey = System.getenv("OPENAI_API_KEY"),
            timeout = 30_000
        )
    }
    
    @Bean
    @Profile("development")
    fun debugAgent(): Agent {
        return buildAgent {
            id = "debug"
            name = "Debug Agent"
            debugMode(true)
            
            handle { comm ->
                println("Debug: Received ${comm.content}")
                comm.reply("Debug: Processed", id)
            }
        }
    }
}
```

### Conditional Beans
```kotlin
@Configuration
@ConditionalOnProperty(
    prefix = "spice.swarm",
    name = ["enabled"],
    havingValue = "true"
)
class SwarmConfiguration {
    
    @Bean
    fun swarmCoordinator(
        agentRegistry: AgentRegistry
    ): SwarmCoordinator {
        return SwarmCoordinator(agentRegistry).apply {
            // Configure swarm settings
            config.minWorkers = 3
            config.maxWorkers = 10
            config.consensusThreshold = 0.7
        }
    }
    
    @Bean
    fun analysisSwarm(
        coordinator: SwarmCoordinator,
        @Qualifier("sentimentAgent") sentiment: Agent,
        @Qualifier("summaryAgent") summary: Agent
    ): SwarmAgent {
        return SwarmAgent("analysis-swarm").apply {
            addWorker(sentiment)
            addWorker(summary)
            setCoordinator(coordinator)
        }
    }
}
```

## Service Integration

### Agent Service
```kotlin
@Service
class AgentService(
    private val agentRegistry: AgentRegistry,
    private val commHub: CommHub,
    private val metricsCollector: MetricsCollector
) {
    
    suspend fun processRequest(
        agentId: String,
        content: String,
        context: Map<String, Any> = emptyMap()
    ): ProcessingResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val agent = agentRegistry.get(agentId)
                ?: throw AgentNotFoundException(agentId)
            
            val comm = comm(content) {
                from("service")
                context.forEach { (key, value) ->
                    data(key, value)
                }
            }
            
            val response = agent.process(comm)
            
            val duration = System.currentTimeMillis() - startTime
            metricsCollector.recordProcessing(agentId, duration, true)
            
            ProcessingResult.success(response, duration)
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            metricsCollector.recordProcessing(agentId, duration, false)
            
            ProcessingResult.error(e.message ?: "Unknown error", duration)
        }
    }
    
    fun getAgentHealth(agentId: String): AgentHealth {
        val metrics = metricsCollector.getMetrics(agentId)
        return AgentHealth(
            agentId = agentId,
            status = if (metrics.errorRate < 0.1) "healthy" else "degraded",
            averageLatency = metrics.averageLatency,
            errorRate = metrics.errorRate,
            requestCount = metrics.totalRequests
        )
    }
}
```

### Scheduled Tasks
```kotlin
@Component
class AgentMaintenanceTasks(
    private val agentRegistry: AgentRegistry,
    private val healthChecker: AgentHealthChecker
) {
    
    @Scheduled(fixedDelay = 60000) // Every minute
    fun checkAgentHealth() {
        agentRegistry.list().forEach { agent ->
            val health = healthChecker.check(agent)
            if (!health.isHealthy) {
                log.warn("Agent ${agent.id} is unhealthy: ${health.issues}")
            }
        }
    }
    
    @Scheduled(cron = "0 0 * * * *") // Every hour
    fun cleanupInactiveAgents() {
        val inactive = agentRegistry.list()
            .filter { healthChecker.getLastActivity(it) > 3600000 }
        
        inactive.forEach { agent ->
            log.info("Removing inactive agent: ${agent.id}")
            agentRegistry.unregister(agent.id)
        }
    }
}
```

## Testing with Spring Boot

### Integration Tests
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class AgentIntegrationTest {
    
    @Autowired
    lateinit var mockMvc: MockMvc
    
    @Autowired
    lateinit var agentRegistry: AgentRegistry
    
    @Test
    fun `test agent REST endpoint`() {
        // Register test agent
        val testAgent = buildAgent {
            id = "test"
            handle { comm ->
                comm.reply("Test response", id)
            }
        }
        agentRegistry.register(testAgent)
        
        // Test API
        mockMvc.perform(
            post("/api/agents/test/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "Test message"}""")
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.content").value("Test response"))
        .andExpect(jsonPath("$.from").value("test"))
    }
}

@TestConfiguration
class TestAgentConfiguration {
    
    @Bean
    @Primary
    fun mockWeatherService(): WeatherService {
        return mockk<WeatherService> {
            every { getWeather(any()) } returns "Sunny, 25°C"
        }
    }
}
```

## Monitoring and Metrics

### Actuator Integration
```kotlin
@Component
@ConditionalOnClass(name = ["org.springframework.boot.actuate.endpoint.annotation.Endpoint"])
@Endpoint(id = "spice-agents")
class SpiceAgentEndpoint(
    private val agentRegistry: AgentRegistry,
    private val commHub: CommHub
) {
    
    @ReadOperation
    fun agents(): Map<String, Any> {
        return mapOf(
            "count" to agentRegistry.size(),
            "agents" to agentRegistry.list().map { agent ->
                mapOf(
                    "id" to agent.id,
                    "name" to agent.name,
                    "type" to agent::class.simpleName
                )
            }
        )
    }
    
    @ReadOperation
    fun commHubStats(): Map<String, Any> {
        val analytics = commHub.getAnalytics()
        return mapOf(
            "totalMessages" to analytics.totalComms,
            "activeAgents" to analytics.activeAgents,
            "messageRate" to analytics.messageRate,
            "queueSize" to analytics.queueSize
        )
    }
}
```

## Best Practices

1. **Use Spring profiles** for environment-specific agent configurations
2. **Leverage dependency injection** for agent dependencies
3. **Implement health checks** for critical agents
4. **Use @Async** for non-blocking agent operations
5. **Configure connection pools** for external service agents
6. **Monitor agent performance** with Spring Boot Actuator
7. **Use @ConfigurationProperties** for type-safe configuration
8. **Implement circuit breakers** for resilient agent communication
9. **Cache agent responses** with Spring Cache abstraction
10. **Document API endpoints** with SpringDoc/Swagger

## Next: [API Reference](api-reference.md)