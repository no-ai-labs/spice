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

### 🔧 **Simplified DSL Architecture**
- **Agent > Flow > Tool** — Clean 3-tier hierarchy for intuitive development
- **Core DSL** — `buildAgent{}`, `flow{}`, `tool{}` for 90% of use cases
- **Experimental DSL** — Advanced features in `experimental{}` wrapper for opt-in usage
- **Hidden Complexity** — Plugin tools and ToolChains work transparently behind simple interfaces
- **Progressive Disclosure** — Learn 3 concepts to start, access advanced features when needed

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
- **OpenRouter Multi-Provider** — Single API key for 50+ models from multiple providers
- **Unified Interface** — Switch providers without changing code

### 🧙‍♂️ **Intelligent Agent Patterns**
- **WizardAgent** — One-shot intelligence upgrade for complex tasks
- **Dynamic Model Switching** — Runtime model selection based on task complexity
- **Cost Optimization** — Automatic model selection for optimal price/performance
- **Context Preservation** — Seamless intelligence scaling without losing conversation state

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

### 2. Core DSL - Simple Agent Creation

```kotlin
import io.github.spice.dsl.*

// Simple agent with Core DSL
val greetingAgent = buildAgent {
    id = "greeter"
    name = "Greeting Agent"
    description = "Friendly greeting agent"
    tools = listOf("greeting-tool")
    
    handle { message ->
        Message(
            content = "Hello! ${message.content}",
            sender = id,
            receiver = message.sender
        )
    }
}

// Simple tool definition
val greetingTool = tool("greeting-tool") {
    parameter("name", "string", "User's name")
    description = "Generates personalized greetings"
    
    execute { params ->
        "Hello, ${params["name"]}! Welcome to Spice!"
    }
}

// Simple flow creation
val greetingFlow = flow {
    step("greeter")
}

// Execute the flow
val message = Message(content = "World", sender = "user")
val result = greetingFlow.execute(message)
println(result.content) // "Hello! World"
```

### 3. Plugin Tools - External Service Integration

```kotlin
// Plugin tool with input/output mapping
val weatherTool = pluginTool("weather", "weather-service") {
    mapInput("location") { message -> message.content }
    mapOutput { response -> "Weather: $response" }
    parameter("location", "string", required = true)
}

// Tool chain for complex workflows  
val analysisChain = toolChain("sentiment-analysis") {
    step("text-preprocessor") {
        mapParameter("text", "input_text")
        mapOutput("clean_text", "processed_text")
    }
    step("sentiment-classifier") {
        mapParameter("text", "processed_text")
        mapOutput("sentiment", "final_sentiment")
    }
}

// Agent using plugin tools
val weatherAgent = buildAgent {
    id = "weather-bot"
    name = "Weather Assistant"
    tools = listOf("weather", "sentiment-analysis")
    
    handle { message ->
        val weather = getToolResult("weather", mapOf("location" to message.content))
        val sentiment = getToolResult("sentiment-analysis", mapOf("input_text" to weather))
        Message(content = "$weather (Sentiment: $sentiment)", sender = id)
    }
}
```

### 4. Experimental DSL - Advanced Features

```kotlin
import io.github.spice.dsl.experimental.*

// Advanced features in experimental wrapper
experimental {
    // Conditional flows with pattern matching
    val smartFlow = conditional {
        whenThen(
            condition = { message -> message.content.contains("weather") },
            thenAgent = "weather-agent"
        )
        whenThen(
            condition = { message -> message.content.contains("help") },
            thenAgent = "support-agent"
        )
        otherwise("general-agent")
    }
    
    // Reactive processing
    val reactiveAgent = reactive(weatherAgent) {
        filter { message -> message.content.isNotEmpty() }
        map { message -> message.copy(content = message.content.uppercase()) }
        buffer(size = 10, timeWindow = 5000)
    }
    
    // Agent composition
    val composedAgent = composition {
        sequential("weather-agent", "sentiment-agent")
        parallel("translator-1", "translator-2")
        merge { responses -> responses.joinToString("; ") }
    }
    
    // Type-safe messaging
    val typedAgent = typedAgent<TextContent, DataContent>("typed-agent") {
        handle { input: TextContent ->
            DataContent(mapOf("processed" to input.text.length))
        }
    }
    
    // Simple workflow
    val workflow = workflow {
        agent("processor") { input -> process(input) }
        transform { result -> result.uppercase() }
        agent("formatter") { processed -> format(processed) }
    }
}
```

### 5. Legacy LLM Agents (Still Supported)

```kotlin
// OpenRouter for multi-provider access
val multiModelAgent = OpenRouterAgent(
    id = "multi-agent",
    name = "Multi-Provider Agent",
    apiKey = "your-openrouter-key",
    model = "anthropic/claude-3.5-sonnet",
    maxTokens = 1000
)

// WizardAgent for intelligence scaling
val wizardAgent = WizardAgent(
    id = "shape-shifter",
    name = "Shape-Shifting Agent",
    normalAgent = OpenRouterAgent(apiKey, "google/bison-001"),
    wizardAgent = OpenRouterAgent(apiKey, "anthropic/claude-3.5-sonnet")
)

// SwarmAgent for team coordination
val contentTeam = SwarmAgent(
    id = "content-team",
    name = "Content Creation Team"
).apply {
    addToPool(OpenAIAgent("researcher", "Research Agent", apiKey, "gpt-4"))
    addToPool(OpenAIAgent("writer", "Content Writer", apiKey, "gpt-4"))
    addToPool(AnthropicAgent("reviewer", "Content Reviewer", claudeKey, "claude-3-5-sonnet"))
}
```

### 6. Real-World Example - Customer Service Bot

```kotlin
// Complete customer service system with new DSL
fun createCustomerServiceBot() {
    // Tools
    val sentimentTool = tool("sentiment-analysis") {
        parameter("text", "string", "Text to analyze")
        execute { params ->
            val text = params["text"] as String
            when {
                text.contains("angry", "frustrated") -> "negative"
                text.contains("happy", "great") -> "positive"
                else -> "neutral"
            }
        }
    }
    
    val ticketTool = pluginTool("create-ticket", "ticketing-system") {
        mapInput("issue") { message -> message.content }
        mapInput("priority") { message -> 
            when (message.metadata["sentiment"]) {
                "negative" -> "high"
                else -> "normal"
            }
        }
        mapOutput { response -> "Ticket created: $response" }
    }
    
    // Main service agent
    val serviceAgent = buildAgent {
        id = "customer-service"
        name = "Customer Service Agent"
        description = "Handles customer inquiries with sentiment analysis"
        tools = listOf("sentiment-analysis", "create-ticket")
        
        handle { message ->
            val sentiment = getToolResult("sentiment-analysis", mapOf("text" to message.content))
            val response = when (sentiment) {
                "negative" -> {
                    val ticket = getToolResult("create-ticket", mapOf("issue" to message.content))
                    "I understand your frustration. $ticket"
                }
                "positive" -> "Thank you for your feedback! How else can I help?"
                else -> "I'm here to help. Could you provide more details?"
            }
            
            Message(
                content = response,
                sender = id,
                receiver = message.sender,
                metadata = mapOf("sentiment" to sentiment)
            )
        }
    }
    
    // Optional: Advanced routing with experimental DSL
    experimental {
        val smartRouter = conditional {
            whenThen(
                condition = { message -> message.metadata["priority"] == "high" },
                thenAgent = "escalation-agent"
            )
            otherwise("customer-service")
        }
    }
}
```

---

## 📖 Core Concepts

### 🏗️ **DSL Architecture**

Spice uses a **3-tier progressive disclosure design**:

1. **Core DSL** (`buildAgent{}`, `flow{}`, `tool{}`) - 90% of use cases
2. **Plugin Tools** (`pluginTool{}`, `toolChain{}`) - External service integration  
3. **Experimental DSL** (`experimental{}`) - Advanced features for power users

```kotlin
// Tier 1: Core DSL - Simple and intuitive
buildAgent { id = "agent"; handle { ... } }
flow { step("agent-id") }
tool("name") { execute { ... } }

// Tier 2: Plugin Tools - External services
pluginTool("weather", "weather-service") { mapInput(...) }
toolChain("pipeline") { step("tool1"); step("tool2") }

// Tier 3: Experimental - Advanced features
experimental {
    conditional { whenThen(...) }
    reactive(agent) { filter(...); map(...) }
    composition { sequential(...); parallel(...) }
}
```

### **Agents**
Agents are the core units of work in Spice. They receive messages, process them, and send responses.

### **Messages**
Structured communication between agents with typed content and metadata.

### **Tools**
Reusable functions that agents can use to perform specific tasks.

### **Routing**
Intelligent message routing based on agent capabilities, content analysis, and load balancing.

### **Personas**
Personality and communication styles that can be applied to agents.

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

### DSL Layer Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPICE DSL ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────┤
│  TIER 1: Core DSL (90% of use cases)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ buildAgent{}│  │   flow{}    │  │   tool()    │             │
│  │    Agent    │──│    Flow     │──│    Tool     │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
├─────────────────────────────────────────────────────────────────┤
│  TIER 2: Plugin Tools (External Integration)                  │
│  ┌─────────────┐  ┌─────────────┐                              │
│  │pluginTool() │  │ toolChain() │                              │
│  │  External   │──│  Pipeline   │                              │
│  │  Services   │  │ Execution   │                              │
│  └─────────────┘  └─────────────┘                              │
├─────────────────────────────────────────────────────────────────┤
│  TIER 3: Experimental DSL (Advanced Features)                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │conditional{}│  │ reactive()  │  │composition{}│             │
│  │   Pattern   │  │  Reactive   │  │   Agent     │             │
│  │  Matching   │  │ Processing  │  │ Composition │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

### System Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Core DSL      │────│  Agent Registry │────│ Tool Registry   │
│   Builders      │    │   Management    │    │  Management     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Agents      │    │      Flows      │    │     Tools       │
│  - Core DSL     │    │  - Sequential   │    │  - Core Tools   │
│  - Legacy LLM   │    │  - Conditional  │    │  - Plugin Tools │
│  - Experimental │    │  - Reactive     │    │  - Tool Chains  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Message Bus   │    │   Experimental  │    │   Integration   │
│  - Type Safety  │    │   Extensions    │    │  - Spring Boot  │
│  - Metadata     │    │  - Conditional  │    │  - Plugin Mgmt  │
│  - Routing      │    │  - Reactive     │    │  - External API │
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
├── dsl/                   # New DSL Architecture
│   ├── CoreDSL.kt         # Core 3-tier DSL (Agent/Flow/Tool)
│   ├── PluginTools.kt     # Plugin tool integration
│   └── experimental/      # Advanced features
│       └── ExperimentalDSL.kt # Conditional, reactive, composition
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
│   ├── VLLMAgent.kt       # vLLM local deployment
│   ├── WizardAgent.kt     # Intelligence scaling agent
│   └── PlanningAgent.kt   # Planning and orchestration
└── Tool.kt                # Function calling interface
```

---

## ⚠️ Breaking Changes (v0.1.0)

### DSL Architecture Refactoring

The DSL has been completely restructured for better usability and maintainability:

#### **What's New**
- **Core DSL**: Simple `buildAgent{}`, `flow{}`, `tool{}` for common use cases
- **Plugin Tools**: `pluginTool{}`, `toolChain{}` for external service integration  
- **Experimental DSL**: Advanced features moved to `experimental{}` wrapper

#### **Migration Required**
- Complex DSL features now require `experimental { ... }` wrapper
- Legacy agent creation patterns still work but new DSL is recommended
- Plugin tools use new registration system with enhanced capabilities

#### **Compatibility**
- All existing agents (OpenAI, Anthropic, etc.) work unchanged
- Legacy DSL patterns still supported but deprecated
- Spring Boot integration remains the same

#### **Example Migration**

```kotlin
// OLD (still works, but deprecated)
val agent = OpenAIAgent("id", "name", apiKey, "gpt-4")

// NEW (recommended)
val agent = buildAgent {
    id = "modern-agent"
    name = "Modern Agent"
    description = "Uses new DSL"
    tools = listOf("my-tool")
    
    handle { message ->
        // Enhanced message handling
        Message(content = "Processed: ${message.content}", sender = id)
    }
}

// Complex features now in experimental
experimental {
    val conditionalFlow = conditional {
        whenThen(condition = { ... }, thenAgent = "agent1")
        otherwise("default-agent")
    }
}
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
- [x] **DSL Simplification** - Agent > Flow > Tool 3-tier architecture
- [x] **Core DSL** - `buildAgent{}`, `flow{}`, `tool{}` for intuitive usage
- [x] **Experimental DSL** - Advanced features in `experimental{}` wrapper
- [x] **Plugin Tools** - External service integration with transparent complexity
- [x] **Progressive Disclosure** - 90% of use cases with 3 simple concepts
- [x] Multi-agent orchestration with SwarmAgent
- [x] Production-ready MultiAgentFlow with 4 strategies
- [x] OpenAI, Anthropic, Vertex AI, vLLM agent implementations
- [x] Vector store integration with Qdrant
- [x] Spring Boot starter with autoconfiguration
- [x] Comprehensive test coverage (90%+)
- [x] Thread-safe concurrent execution
- [x] Rich metadata and observability

### 🚧 **In Progress (v0.2.0)**
- [ ] Enhanced tool execution engine for new DSL
- [ ] RAG workflow templates with Core DSL
- [ ] Advanced routing policies in experimental package
- [ ] Performance benchmarking suite for DSL overhead
- [ ] Migration guide from legacy to new DSL
- [ ] Documentation and examples for all DSL tiers

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
