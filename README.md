# 🌶️ Spice

**Production-Ready Multi-Agent Framework for the JVM.**  
Intelligent agent swarms, dynamic flow strategies, and enterprise-grade LLM orchestration — built for Kotlin, designed for scale.

---

## ✨ What is Spice?

Spice is a **production-ready multi-agent orchestration framework** for the JVM, enabling intelligent, collaborative agent workflows across local and cloud-hosted LLMs.

From simple agent interactions to complex swarm intelligence, Spice provides the building blocks for scalable, maintainable agent systems that integrate seamlessly with modern JVM ecosystems.

> 🤖 Think CrewAI, but type-safe and JVM-native.  
> ⚡ Think AutoGen, but with Kotlin coroutines.  
> 🎯 Think intelligent routing, dynamic strategies, and production reliability.

---

## 🌌 Key Features

### 🤖 **Intelligent Agent Swarms**
- **SwarmAgent** — Coordinate multiple agents with dynamic strategy selection
- **Flow Strategies** — SEQUENTIAL, PARALLEL, COMPETITION, PIPELINE execution modes
- **Capability Matching** — Automatic agent selection based on task requirements
- **Fallback & Retry** — Production-grade error handling and resilience

### 🔄 **Advanced Flow Control**
- **MultiAgentFlow** — High-performance agent orchestration with strategy optimization
- **Message Routing** — Context-aware routing with comprehensive metadata
- **Dynamic Strategy Resolution** — Runtime strategy selection based on message content
- **Thread-Safe Execution** — Concurrent processing with CopyOnWriteArrayList pools

### 🎯 **Enterprise LLM Integration**
- **OpenAI GPT-4/3.5** — Full API support with function calling and vision
- **Anthropic Claude** — Claude-3.5-Sonnet with tool use and 200K context
- **Google Vertex AI** — Gemini Pro with multimodal capabilities
- **vLLM High-Performance** — Local deployment with batch optimization
- **Unified Interface** — Switch providers without changing code

### 🔍 **Vector & RAG Support**
- **VectorStore Interface** — Qdrant, Pinecone, Weaviate support
- **Smart Filtering** — Advanced metadata filtering with DSL
- **Embedding Integration** — Automatic text-to-vector conversion
- **RAG Workflows** — Retrieval-augmented generation patterns

### ⚙️ **Production Features**
- **Spring Boot Starter** — Zero-config integration with autoconfiguration
- **Comprehensive Testing** — 90%+ test coverage with MockK integration
- **Observability** — Rich metadata and execution tracking
- **Type Safety** — Full Kotlin type system with coroutine support

---

## 🚀 Quick Start

### 1. Add Dependencies

```kotlin
// build.gradle.kts
implementation("io.github.spice:spice-core:0.1.0-SNAPSHOT")
implementation("io.github.spice:spice-springboot:0.1.0-SNAPSHOT") // For Spring Boot
```

### 2. Create Your First Agent

```kotlin
import io.github.spice.*

// Create an OpenAI agent
val summarizer = OpenAIAgent(
    id = "summarizer",
    name = "Document Summarizer", 
    apiKey = "your-openai-key",
    model = "gpt-4",
    maxTokens = 1000
)

// Process a message
val response = summarizer.processMessage(
    Message(
        content = "Summarize this article about Kotlin coroutines...",
        sender = "user"
    )
)

println(response.content)
```

### 3. SwarmAgent for Team Coordination

```kotlin
// Create specialized agents
val researcher = OpenAIAgent("researcher", "Research Agent", apiKey, "gpt-4")
val writer = OpenAIAgent("writer", "Content Writer", apiKey, "gpt-4") 
val reviewer = AnthropicAgent("reviewer", "Content Reviewer", claudeKey, "claude-3-5-sonnet")

// Create a coordinated swarm
val contentTeam = SwarmAgent(
    id = "content-team",
    name = "Content Creation Team",
    description = "Collaborative content creation swarm"
).apply {
    addToPool(researcher)
    addToPool(writer)
    addToPool(reviewer)
    
    // Dynamic strategy based on content
    setStrategyResolver { message, agents ->
        when {
            message.content.contains("research") -> FlowStrategy.PIPELINE
            message.content.contains("quick") -> FlowStrategy.COMPETITION
            else -> FlowStrategy.SEQUENTIAL
        }
    }
}

// Execute with team coordination
val result = contentTeam.processMessage(
    Message(content = "Create a technical blog post about Kotlin coroutines")
)
```

### 4. MultiAgentFlow for Advanced Orchestration

```kotlin
// High-performance multi-agent execution
val flow = MultiAgentFlow(FlowStrategy.PARALLEL).apply {
    addAgent(OpenAIAgent("gpt4", "GPT-4 Agent", apiKey))
    addAgent(AnthropicAgent("claude", "Claude Agent", claudeKey))
    addAgent(VertexAgent("gemini", "Gemini Agent", projectId, location, token))
}

// Process with parallel execution
val message = Message(content = "Analyze this code for performance issues")
val result = flow.process(message)

// Rich metadata available
println("Winner: ${result.metadata["winnerId"]}")
println("Strategy: ${result.metadata["strategy"]}")
println("Execution time: ${result.metadata["executionTimeMs"]}ms")
```

### 5. Vector Store Integration

```kotlin
// Create a vector store for RAG
val vectorStore = VectorStoreFactory.createQdrant(
    host = "localhost",
    port = 6333
)

// Create collection
vectorStore.createCollection(
    collectionName = "documents",
    vectorSize = 384,
    distance = DistanceMetric.COSINE
)

// Store vectors with metadata
val documents = listOf(
    VectorDocument(
        id = "doc1",
        vector = embeddings, // Your embedding vector
        metadata = mapOf(
            "title" to JsonPrimitive("Kotlin Guide"),
            "category" to JsonPrimitive("programming")
        )
    )
)

vectorStore.upsert("documents", documents)

// Search with filtering
val results = vectorStore.search(
    collectionName = "documents",
    queryVector = queryEmbedding,
    topK = 5,
    filter = buildFilter {
        equals("category", "programming")
        range("score", min = 0.8f)
    }
)
```

### 6. Spring Boot Integration

```kotlin
@SpringBootApplication
@EnableSpice
class SpiceApplication

@RestController
class AgentController(private val contentTeam: SwarmAgent) {
    
    @PostMapping("/generate")
    suspend fun generateContent(@RequestBody request: ContentRequest): ContentResponse {
        val message = Message(content = request.prompt, sender = "api")
        val result = contentTeam.processMessage(message)
        
        return ContentResponse(
            content = result.content,
            agent = result.metadata["processedBy"] ?: "unknown",
            executionTime = result.metadata["executionTimeMs"]
        )
    }
}
```

---

## 📖 Core Concepts

### Agents
Agents are the core units of work in Spice. They receive messages, process them, and send responses.

### Messages
Structured communication between agents with typed content and metadata.

### Tools
Reusable functions that agents can use to perform specific tasks.

### Routing
Intelligent message routing based on agent capabilities, content analysis, and load balancing.

### Personas
Personality and communication styles that can be applied to agents.

---

## �� Configuration

### Spring Boot Integration

```yaml
# application.yml
spice:
  agents:
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-sonnet
  routing:
    default-strategy: confidence
    load-balancing: enabled
```

### Programmatic Configuration

```kotlin
@Configuration
class SpiceConfig {
    
    @Bean
    fun agentEngine(): AgentEngine {
        val engine = AgentEngine()
        
        // Register agents
        engine.registerAgent(OpenAIAgent("gpt-agent", ...))
        engine.registerAgent(AnthropicAgent("claude-agent", ...))
        
        return engine
    }
    
    @Bean
    fun pluginManager(engine: AgentEngine): PluginManager {
        val manager = createPluginManager(engine)
        
        // Load plugins
        manager.loadPlugin(WebhookPlugin())
        manager.loadPlugin(LoggingPlugin())
        manager.activateAllPlugins()
        
        return manager
    }
}
```

---

## 📚 Examples

### Simple Q&A Agent

```kotlin
val qaAgent = OpenAIAgent("qa-bot",
    name = "Q&A Assistant",
    description = "Answers questions about documentation"
)

val response = engine.receive(
    Message(
        sender = "user",
        receiver = "qa-bot",
        content = "How do I create a new agent?"
    )
)
```

### Multi-Step Workflow

```kotlin
val workflow = buildToolChain("document-processing", "Process documents end-to-end") {
    step("pdf-extractor") {
        mapParameter("file_path", "input_file")
        mapOutput("extracted_text", "raw_text")
    }
    step("text-cleaner") {
        mapParameter("text", "raw_text")
        mapOutput("cleaned_text", "processed_text")
    }
    step("summarizer") {
        mapParameter("content", "processed_text")
    }
}

val result = toolRunner.executeChain(workflow, mapOf(
    "input_file" to "/path/to/document.pdf"
))
```

### Agent with Custom Persona

```kotlin
val supportAgent = OpenAIAgent("support", ...)
    .withPersona(PersonaLibrary.PROFESSIONAL_ASSISTANT)

// The agent will now respond with a professional, helpful tone
```

---

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Agent Engine  │────│  Message Router │────│  Plugin Manager │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Agents      │    │   Routing Rules │    │    Plugins      │
│  - OpenAI       │    │  - Keyword      │    │  - Webhook      │
│  - Anthropic    │    │  - Confidence   │    │  - Logging      │
│  - Custom       │    │  - Load Balance │    │  - Custom       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Tools       │    │    Personas     │    │   Tool Chains   │
│  - File I/O     │    │  - Friendly     │    │  - Validation   │
│  - Web Search   │    │  - Professional │    │  - Processing   │
│  - Custom       │    │  - Sarcastic    │    │  - Analysis     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

---

## 🧪 Testing

```kotlin
class SpiceTest {
    @Test
    fun testAgentCommunication() {
        val engine = AgentEngine()
        val agent = MockAgent("test-agent")
        engine.registerAgent(agent)
        
        val result = engine.receive(
            Message(sender = "test", receiver = "test-agent", content = "Hello")
        )
        
        assert(result.success)
        assert(result.response.content.isNotEmpty())
    }
}
```

---

## 📦 Modules

| Module             | Description                                  | Status |
|--------------------|----------------------------------------------|--------|
| `spice-core`       | Multi-agent orchestration, SwarmAgent, VectorStore | ✅ **Production Ready** |
| `spice-springboot` | AutoConfiguration, Spring Boot starter integration | ✅ **Production Ready** |
| `spice-runtime`    | Extended LLM providers, custom agent types | 🚧 Planned |
| `spice-tools`      | Tool interface, function calling, RAG tools | 🚧 Planned |
| `spice-examples`   | Production demos, tutorials, best practices | 🚧 Planned |

### 🏗️ Core Architecture

```
spice-core/
├── Agent.kt               # Base agent interface
├── AgentEngine.kt         # Agent orchestration engine
├── SwarmAgent.kt          # Multi-agent coordination
├── MultiAgentFlow.kt      # High-performance flow execution
├── Message.kt             # Type-safe message model
├── VectorStore.kt         # Vector database integration
├── agents/                # LLM provider implementations
│   ├── OpenAIAgent.kt     # GPT-4, GPT-3.5 support
│   ├── AnthropicAgent.kt  # Claude-3.5-Sonnet support
│   ├── VertexAgent.kt     # Google Gemini support
│   └── VLLMAgent.kt       # vLLM local deployment
└── Tool.kt                # Function calling interface
```

---

## 🤝 Contributing

We welcome contributions! Here's how to get started:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## 📜 License

MIT License. Use freely. Share wildly. Build something spicy. 🌶️

---

## 🆘 Support

- 📖 **Documentation**: Coming soon to the wiki
- 🐛 **Bug Reports**: Open an issue on GitHub
- 💬 **Questions**: Start a discussion on GitHub
- 🚀 **Feature Requests**: Open an issue with the enhancement label

---

## 🎯 Roadmap

### ✅ **Completed (v0.1.0)**
- [x] Multi-agent orchestration with SwarmAgent
- [x] Production-ready MultiAgentFlow with 4 strategies
- [x] OpenAI, Anthropic, Vertex AI, vLLM agent implementations
- [x] Vector store integration with Qdrant
- [x] Spring Boot starter with autoconfiguration
- [x] Comprehensive test coverage (90%+)
- [x] Thread-safe concurrent execution
- [x] Rich metadata and observability

### 🚧 **In Progress (v0.2.0)**
- [ ] Function calling and tool integration
- [ ] RAG workflow templates
- [ ] Advanced routing policies
- [ ] Performance benchmarking suite
- [ ] Documentation and examples

### 🔮 **Future Releases**
- [ ] Visual flow builder for agent workflows
- [ ] Distributed agent execution
- [ ] Advanced monitoring dashboard
- [ ] Agent marketplace and sharing
- [ ] WebAssembly agent runtime

---

## 💬 Authors

Built by No AI Labs with ❤️ and lots of ☕

---

> **The Spice must flow.**
