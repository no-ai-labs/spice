# 🌶️ Spice

**The Agent Runtime for the JVM.**  
Multi-agent orchestration, message-driven thinking, and LLM interoperability — built for Kotlin, inspired by flow.

---

## ✨ What is Spice?

Spice is a **JVM-native multi-agent orchestration framework**, designed for intelligent, message-based workflows across local and cloud-hosted LLMs.

Spice provides the foundation to build agent systems that talk, think, and act — all while being modular, testable, and Spring Boot-ready.

> 💬 Think AutoGen, but with structure.  
> ☕ Think LangChain, but with Kotlin.  
> 🔁 Think Spice Flow, and let it run.

---

## 🌌 Key Features

- 🧠 **Multi-Agent Conversations** — Agents work together in teams with different flow strategies
- 🔀 **Smart Message Routing** — Intelligent message routing based on content, confidence, and agent capabilities
- 🎭 **Agent Personas** — Give your agents personality and communication styles
- 🧩 **Tool Chains** — Connect tools together for complex workflows
- 🔌 **Plugin System** — Extend functionality with dynamic plugin loading
- ⚙️ **Modular LLM Integration** — Supports OpenAI, vLLM, Anthropic, Vertex AI and more
- ♻️ **Spice Flow Graph Execution** — Message-based DAG-style routing
- ☕️ **Spring Boot AutoConfiguration** — Ready to drop into your Spring ecosystem
- 🔐 **Async-Ready, Production-Friendly** — Built with coroutines and extension points

---

## 🚀 Quick Start

### 1. Add Dependency

```kotlin
// build.gradle.kts
implementation("io.github.spice:spice-core:1.0.0")
implementation("io.github.spice:spice-springboot:1.0.0") // Optional for Spring Boot
```

### 2. Create Your First Agent

```kotlin
import io.github.spice.*

val engine = AgentEngine()

// Register a simple agent
engine.registerAgent(OpenAIAgent("summarizer", 
    name = "Document Summarizer",
    description = "Summarizes documents and articles",
    capabilities = listOf("summarization", "text_analysis")
))

// Send a message
val result = engine.receive(
    Message(
        sender = "user",
        receiver = "summarizer",
        content = "Summarize this paragraph about Kotlin coroutines..."
    )
)

println(result.response.content)
```

### 3. Multi-Agent Team Work

```kotlin
// Create a team of agents
val agents = listOf(
    OpenAIAgent("researcher", ...),
    OpenAIAgent("writer", ...),
    OpenAIAgent("reviewer", ...)
)

// Create a swarm agent that coordinates the team
val swarmAgent = SwarmAgent(
    id = "content-team",
    name = "Content Creation Team",
    agents = agents,
    strategy = FlowStrategy.PIPELINE
)

// Process with team coordination
val result = swarmAgent.processMessage(
    Message(content = "Create a blog post about Kotlin coroutines")
)
```

### 4. Add Personality to Your Agents

```kotlin
// Give your agent a friendly personality
val friendlyAgent = agent.withPersona(PersonaLibrary.FRIENDLY_BUDDY)

// Or create a custom persona
val customPersona = buildPersona("Custom Assistant") {
    personalityType = PersonalityType.PROFESSIONAL
    communicationStyle = CommunicationStyle.TECHNICAL
    trait(PoliteTrait(0.9))
    trait(ConciseTrait(0.7))
}
```

### 5. Connect Tools Together

```kotlin
// Create a data processing pipeline
val processor = DataProcessingTool()
val result = processor.execute(mapOf(
    "input_data" to "Raw data here...",
    "processing_steps" to listOf("validate", "clean", "transform", "analyze")
))
```

### 6. Smart Message Routing

```kotlin
// Set up intelligent routing
val routingManager = RoutingPolicyManager()
    .addRule(KeywordRoutingPolicy("code", "developer-agent"))
    .addRule(ConfidenceRoutingPolicy(0.8))
    .addRule(LoadBalancedRoutingPolicy())
    
// Messages will be automatically routed to the best agent
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
| `spice-core`       | Agent interface, engine, message model       | ✅ Ready |
| `spice-springboot` | AutoConfiguration + property-based registration | ✅ Ready |
| `spice-runtime`    | LLM client wrappers (vLLM, OpenAI, etc.)     | 🚧 Planned |
| `spice-tools`      | Tool interface + example tools               | 🚧 Planned |
| `spice-examples`   | Ready-to-run demos                           | 🚧 Planned |

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

- [ ] Enhanced LLM provider support
- [ ] Visual flow builder
- [ ] Monitoring and observability
- [ ] Advanced tool marketplace
- [ ] Performance optimizations

---

## 💬 Authors

Built by No AI Labs with ❤️ and lots of ☕

---

> **The Spice must flow.**
