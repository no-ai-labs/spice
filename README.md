# 🌶️ Spice Framework

<p align="center">
  <strong>Modern Multi-LLM Orchestration Framework for Kotlin</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2.0-blue.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/Coroutines-1.7.3-green.svg" alt="Coroutines">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

---

## 🎯 What is Spice?

Spice Framework is a modern, type-safe, coroutine-first framework for building AI-powered applications in Kotlin. It provides a clean DSL for creating agents, managing tools, and orchestrating complex AI workflows with multiple LLM providers.

### Why Spice?

- **🚀 Simple yet Powerful** - Get started in minutes, scale to complex multi-agent systems
- **🔧 Type-Safe** - Leverage Kotlin's type system for compile-time safety
- **🌊 Async-First** - Built on coroutines for efficient concurrent operations
- **🎨 Clean DSL** - Intuitive API that reads like natural language
- **🔌 Extensible** - Easy to add custom agents, tools, and integrations

## ✨ Features

### Core Features
- **Unified Communication** - Single `Comm` type for all agent interactions
- **Generic Registry System** - Type-safe, thread-safe component management
- **Progressive Disclosure** - Simple things simple, complex things possible
- **Tool System** - Built-in tools and easy custom tool creation
- **JSON Serialization** - Production-grade JSON conversion for all components

### Advanced Features
- **Multi-LLM Support** - OpenAI, Anthropic, Google Vertex AI, and more
- **Swarm Intelligence** - Coordinate multiple agents for complex tasks
- **Vector Store Integration** - Built-in RAG support with multiple providers
- **MCP Protocol** - External tool integration via Model Context Protocol
- **Spring Boot Starter** - Seamless Spring Boot integration
- **JSON Schema Support** - Export tools as standard JSON Schema for GUI/API integration
- **PSI (Program Structure Interface)** - Convert DSL to LLM-friendly tree structures

### Enterprise Features (New! 🎉)
- **Multi-Tenant Support** - Full tenant isolation with ThreadLocal context propagation
- **Dynamic Platform Management** - Register and manage platforms at runtime
- **Policy Versioning** - Version control for policies with rollback capability
- **Event Sourcing** - Complete event sourcing module with Kafka integration
- **Event-Driven Architecture** - Kafka integration for distributed systems
- **Advanced Monitoring** - Built-in metrics and performance tracking

## 🚀 Quick Start

### Installation

#### Using JitPack

[![](https://jitpack.io/v/no-ai-labs/spice-framework.svg)](https://jitpack.io/#no-ai-labs/spice-framework)

Add JitPack repository to your build file:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:Tag")
    
    // Optional modules
    implementation("com.github.no-ai-labs.spice-framework:spice-springboot:Tag")
    implementation("com.github.no-ai-labs.spice-framework:spice-eventsourcing:Tag")
}
```

Replace `Tag` with the latest version: [![](https://jitpack.io/v/no-ai-labs/spice-framework.svg)](https://jitpack.io/#no-ai-labs/spice-framework)

### Your First Agent

```kotlin
import io.github.spice.*
import io.github.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create a simple agent
    val assistant = buildAgent {
        id = "assistant-1"
        name = "AI Assistant"
        description = "A helpful AI assistant"
        
        // Add an inline tool
        tool("greet") {
            description = "Greet someone"
            parameter("name", "string", "Person's name")
            execute { params ->
                "Hello, ${params["name"]}! How can I help you today?"
            }
        }
        
        // Define message handling
        handle { comm ->
            when {
                comm.content.startsWith("greet ") -> {
                    val name = comm.content.removePrefix("greet ").trim()
                    val result = run("greet", mapOf("name" to name))
                    comm.reply(result.result.toString(), id)
                }
                else -> comm.reply("Say 'greet NAME' to get a greeting!", id)
            }
        }
    }
    
    // Use the agent
    val response = assistant.processComm(
        Comm(content = "greet Alice", from = "user")
    )
    println(response.content) // "Hello, Alice! How can I help you today?"
}
```

### Using LLM Providers

```kotlin
// OpenAI Integration
val gptAgent = buildOpenAIAgent {
    id = "gpt-4"
    name = "GPT-4 Assistant"
    apiKey = System.getenv("OPENAI_API_KEY")
    model = "gpt-4"
    systemPrompt = "You are a helpful coding assistant."
}

// Anthropic Integration
val claudeAgent = buildClaudeAgent {
    id = "claude-3"
    name = "Claude Assistant"
    apiKey = System.getenv("ANTHROPIC_API_KEY")
    model = "claude-3-opus-20240229"
}

// Use them just like any other agent
val response = gptAgent.processComm(
    Comm(content = "Explain coroutines in Kotlin", from = "user")
)
```

## 🏗️ Architecture

### Core Components

```
┌─────────────────────────────────────────────────┐
│                Your Application                  │
├─────────────────────────────────────────────────┤
│                  Spice DSL                      │
│         buildAgent { } • buildFlow { }          │
├─────────────────────────────────────────────────┤
│                 Core Layer                      │
│    Agent • Comm • Tool • Registry System        │
├─────────────────────────────────────────────────┤
│              Integration Layer                  │
│    LLMs • Vector Stores • MCP • Spring Boot    │
└─────────────────────────────────────────────────┘
```

### Key Design Patterns

1. **Registry Pattern** - Centralized management of agents, tools, and flows
2. **Builder Pattern** - Intuitive DSL for creating components
3. **Strategy Pattern** - Pluggable LLM providers and tool implementations
4. **Observer Pattern** - Event-driven agent communication

### Component Overview

- **`Agent`** - Base interface for all intelligent agents
- **`Comm`** - Universal communication unit (replaces legacy Message system)
- **`Tool`** - Reusable functions agents can execute
- **`Registry<T>`** - Generic, thread-safe component registry
- **`SmartCore`** - Next-generation agent system
- **`CommHub`** - Central message routing system

## 📚 Documentation

Comprehensive documentation is available in our [GitHub Wiki](https://github.com/no-ai-labs/spice/wiki):

- 📖 **[Getting Started Guide](https://github.com/no-ai-labs/spice/wiki/Getting-Started)** - Installation and first steps
- 🎯 **[Core Concepts](https://github.com/no-ai-labs/spice/wiki/Core-Concepts)** - Understanding the fundamentals
- 🏗️ **[Architecture Overview](https://github.com/no-ai-labs/spice/wiki/Architecture)** - System design and patterns
- 📚 **[Examples](https://github.com/no-ai-labs/spice/wiki/Examples)** - Learn by example
- 🔧 **[API Reference](https://github.com/no-ai-labs/spice/wiki/API-Reference)** - Detailed API documentation

## 🛠️ Advanced Usage

### Multi-Agent Collaboration

```kotlin
// Create specialized agents
val researcher = buildAgent {
    id = "researcher"
    name = "Research Agent"
    // ... configuration
}

val analyzer = buildAgent {
    id = "analyzer"
    name = "Analysis Agent"
    // ... configuration
}

// Register them
AgentRegistry.register(researcher)
AgentRegistry.register(analyzer)

// Create a workflow
val researchFlow = buildFlow {
    id = "research-flow"
    name = "Research and Analyze"
    
    step("research", "researcher")
    step("analyze", "analyzer") { comm ->
        // Only analyze if research found something
        comm.content.isNotEmpty()
    }
}
```

### Vector Store Integration

```kotlin
val ragAgent = buildAgent {
    id = "rag-agent"
    name = "RAG Assistant"
    
    // Configure vector store
    vectorStore("knowledge") {
        provider("qdrant")
        connection("localhost", 6333)
        collection("documents")
    }
    
    handle { comm ->
        // Automatic vector search tool available
        val results = run("search-knowledge", mapOf(
            "query" to comm.content,
            "topK" to 5
        ))
        // Process results...
    }
}
```

### JSON Serialization

Spice provides unified JSON serialization for all components:

```kotlin
import io.github.noailabs.spice.serialization.SpiceSerializer.toJson
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonSchema

// Serialize any component to JSON
val agentJson = myAgent.toJson()
val toolJson = myTool.toJson()
val vectorStoreJson = myVectorStore.toJson()

// Export tool as JSON Schema
val schema = myAgentTool.toJsonSchema()

// Handle complex metadata properly
val metadata = mapOf(
    "tags" to listOf("ai", "agent"),
    "config" to mapOf("timeout" to 30, "retries" to 3)
)
// Preserves structure instead of toString()!
val jsonMetadata = SpiceSerializer.toJsonMetadata(metadata)
```

### Multi-Tenant Support

Spice now includes enterprise-grade multi-tenant support:

```kotlin
import io.github.noailabs.spice.tenant.*

// Set tenant context
TenantContext.set("tenant-123", mapOf("tier" to "premium"))

// Use tenant-aware runtime
val runtime = TenantAwareAgentRuntime(
    context = agentContext { 
        this["locale"] = "en-US"
    },
    tenantId = "tenant-123"
)

// Create tenant-aware agent
class MyTenantAgent : TenantAwareAgent(
    id = "my-agent",
    name = "Multi-Tenant Agent"
) {
    override suspend fun processCommInternal(comm: Comm): Comm {
        val tenantId = requireTenantId() // Automatically gets current tenant
        // Process with tenant isolation
        return comm.reply("Processing for tenant: $tenantId", id)
    }
}

// Tenant context propagates across coroutines
coroutineScope {
    launch(coroutineContext.withTenant("tenant-456")) {
        // This coroutine sees tenant-456
        val agent = agentRegistry.get("my-agent")
        agent.processComm(comm) // Processes in tenant-456 context
    }
}
```

### Platform Management

Dynamic platform registration and management:

```kotlin
import io.github.noailabs.spice.platform.*

// Create platform manager
val platformManager = DefaultPlatformManager()

// Register a platform
platformManager.registerPlatform(
    PlatformInfo(
        id = "shopify",
        name = "Shopify",
        type = PlatformType.MARKETPLACE,
        capabilities = setOf("orders", "inventory", "customers"),
        metadata = mapOf("apiVersion" to "2024-01")
    )
)

// Tenant-specific platform configuration
val tenantPlatformManager = DefaultTenantPlatformManager(platformManager)

tenantPlatformManager.registerPlatformForTenant(
    tenantId = "tenant-123",
    platformId = "shopify",
    config = PlatformConfig(
        platformId = "shopify",
        credentials = mapOf("apiKey" to "xxx", "apiSecret" to "yyy"),
        endpoints = mapOf("base" to "https://mystore.myshopify.com"),
        rateLimits = RateLimitConfig(
            requestsPerMinute = 40,
            burstSize = 10
        )
    )
)

// Check platform health
val health = platformManager.checkPlatformHealth("shopify")
println("Platform status: ${health.status}")
```

### Policy Versioning

Version control for configuration and policies:

```kotlin
import io.github.noailabs.spice.policy.*
import kotlinx.serialization.json.*

// Create policy manager
val policyManager = DefaultPolicyManager()

// Save a policy with version tracking
val v1 = policyManager.savePolicy(
    policyId = "refund-policy",
    content = buildJsonObject {
        put("maxRefundDays", 30)
        put("autoApprove", true)
        put("maxAmount", 1000)
    },
    createdBy = "admin",
    comment = "Initial refund policy"
)

// Update policy (creates v2)
val v2 = policyManager.savePolicy(
    policyId = "refund-policy",
    content = buildJsonObject {
        put("maxRefundDays", 60)  // Changed
        put("autoApprove", false) // Changed
        put("maxAmount", 1000)
    },
    createdBy = "admin",
    comment = "Extended refund period, manual approval required"
)

// View history
val history = policyManager.getPolicyHistory("refund-policy")
history.forEach { entry ->
    println("v${entry.version}: ${entry.comment} by ${entry.createdBy}")
}

// Rollback to v1
val v3 = policyManager.rollbackPolicy(
    policyId = "refund-policy",
    targetVersion = 1,
    rolledBackBy = "admin",
    comment = "Reverting to original policy"
)
```

### Event Sourcing

Spice now includes a complete event sourcing module with Kafka integration:

```kotlin
import io.github.noailabs.spice.eventsourcing.*
import javax.sql.DataSource

// Create event store with Kafka and PostgreSQL
val eventStore = EventStoreFactory.kafkaWithPostgres(
    kafkaCommHub = kafkaCommHub,
    dataSource = dataSource,
    config = EventStoreConfig(
        topicPrefix = "events",
        snapshotFrequency = 100
    )
)

// Or use in-memory for testing
val testEventStore = EventStoreFactory.inMemory(dataSource)

// Or use custom event publisher (e.g., RabbitMQ)
val customEventStore = EventStoreFactory.withCustomPublisher(
    dataSource = dataSource,
    eventPublisher = MyRabbitMQEventPublisher()
)

// Define domain events
class OrderCreatedEvent(
    orderId: String,
    val customerId: String,
    val items: List<OrderItem>
) : EntityCreatedEvent(
    aggregateId = orderId,
    aggregateType = "Order"
)

// Create event-sourced aggregate
class OrderAggregate(
    override val aggregateId: String
) : Aggregate() {
    
    override val aggregateType = "Order"
    
    // State
    var customerId: String? = null
    var items: MutableList<OrderItem> = mutableListOf()
    var status: OrderStatus = OrderStatus.DRAFT
    
    // Business operations
    suspend fun create(customerId: String, items: List<OrderItem>) {
        raiseEvent(OrderCreatedEvent(aggregateId, customerId, items))
    }
    
    // Apply events to update state
    override fun apply(event: DomainEvent) {
        when (event) {
            is OrderCreatedEvent -> {
                this.customerId = event.customerId
                this.items.addAll(event.items)
                this.status = OrderStatus.CREATED
            }
        }
    }
}

// Use repository for loading/saving
val repository = AggregateRepository(eventStore)

// Create and save aggregate
val order = OrderAggregate("order-123")
order.create("customer-456", items)
repository.save(order)

// Load aggregate from events
val loadedOrder = repository.load("order-123") { OrderAggregate(it) }
```

### Memento Pattern for Snapshots

Efficiently manage aggregate snapshots with the Memento pattern:

```kotlin
import io.github.noailabs.spice.eventsourcing.*

// Define a memento for your aggregate
data class CartMemento(
    override val aggregateId: String,
    override val version: Long,
    override val timestamp: Instant,
    val customerId: String,
    val items: List<CartItem>,
    val totalAmount: Money,
    val appliedCoupons: List<String>
) : AggregateMemento, Serializable {
    
    override val aggregateType = "ShoppingCart"
    
    override fun restoreState(aggregate: Aggregate) {
        require(aggregate is ShoppingCartAggregate)
        aggregate.restoreFromMemento(this)
    }
}

// Create aggregate with memento support
class ShoppingCartAggregate(
    override val aggregateId: String
) : MementoAggregate() {
    
    override val aggregateType = "ShoppingCart"
    
    // State
    private var customerId: String? = null
    private val items = mutableListOf<CartItem>()
    private var totalAmount = Money(0, "USD")
    private val appliedCoupons = mutableListOf<String>()
    
    // Create memento
    override fun doCreateMemento(): AggregateMemento {
        return CartMemento(
            aggregateId = aggregateId,
            version = version,
            timestamp = Instant.now(),
            customerId = customerId ?: "",
            items = items.toList(),
            totalAmount = totalAmount,
            appliedCoupons = appliedCoupons.toList()
        )
    }
    
    // Restore from memento
    override fun doRestoreFromMemento(memento: AggregateMemento) {
        require(memento is CartMemento)
        this.customerId = memento.customerId
        this.items.clear()
        this.items.addAll(memento.items)
        this.totalAmount = memento.totalAmount
        this.appliedCoupons.clear()
        this.appliedCoupons.addAll(memento.appliedCoupons)
    }
    
    // Event handling...
    override fun apply(event: DomainEvent) {
        // Apply events to update state
    }
}

// Use memento-aware repository
val repository = MementoAggregateRepository(
    eventStore = eventStore,
    snapshotStore = MementoSnapshotStoreFactory.inMemory(),
    snapshotFrequency = 50  // Create snapshot every 50 events
)

// Load aggregate (uses snapshot if available)
val cart = repository.load("cart-123") { ShoppingCartAggregate(it) }

// Save aggregate (creates snapshot automatically if needed)
repository.save(cart)
```

### Saga Pattern Support

Implement distributed transactions with compensating actions:

```kotlin
import io.github.noailabs.spice.eventsourcing.*

// Define a saga for order fulfillment
class OrderFulfillmentSaga(
    override val sagaId: String,
    private val orderId: String
) : Saga() {
    
    override val sagaType = "OrderFulfillment"
    
    override suspend fun execute(context: SagaContext) {
        // Step 1: Reserve inventory
        executeStep(ReserveInventoryStep(orderId), context)
        
        // Step 2: Process payment
        executeStep(ProcessPaymentStep(orderId), context)
        
        // Step 3: Create shipment
        executeStep(CreateShipmentStep(orderId), context)
        
        // Step 4: Send confirmation
        executeStep(SendConfirmationStep(orderId), context)
    }
}

// Define saga steps with compensation logic
class ProcessPaymentStep(private val orderId: String) : SagaStep {
    override val name = "ProcessPayment"
    
    override suspend fun execute(context: SagaContext) {
        // Process payment
        val paymentId = paymentService.charge(orderId)
        context.set("paymentId", paymentId)
    }
    
    override suspend fun compensate(context: SagaContext) {
        // Refund if saga fails
        val paymentId = context.get<String>("paymentId")
        paymentService.refund(paymentId)
    }
}

// Execute saga
val sagaManager = SagaManager(eventStore, InMemorySagaStore())
val saga = OrderFulfillmentSaga("saga-123", "order-123")
sagaManager.startSaga(saga, SagaContext())
```

## 🌱 Spring Boot Integration

```kotlin
@SpringBootApplication
@EnableSpice
class MyApplication

@Component
class MyService(
    @Autowired private val agentRegistry: AgentRegistry
) {
    fun processRequest(message: String): String {
        val agent = agentRegistry.get("my-agent")
        val response = runBlocking {
            agent?.processComm(Comm(content = message, from = "user"))
        }
        return response?.content ?: "No response"
    }
}
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

```bash
# Clone the repository
git clone https://github.com/spice-framework/spice.git
cd spice-framework

# Build the project
./gradlew build

# Run tests
./gradlew test
```

## 📊 Project Status

- ✅ Core Agent System
- ✅ Generic Registry System
- ✅ Unified Communication (Comm)
- ✅ Tool Management System
- ✅ LLM Integrations (OpenAI, Anthropic)
- ✅ Spring Boot Starter
- ✅ JSON Serialization System
- ✅ PSI (Program Structure Interface) - DSL to tree conversion
- ✅ Swarm Intelligence (Multi-agent coordination with 5 strategies)
- ✅ MCP Protocol Support (Model Context Protocol integration)
- ✅ Multi-Tenant Support with ThreadLocal context propagation
- ✅ Dynamic Platform Management System
- ✅ Policy Versioning with rollback capability
- ✅ Tenant-aware storage abstractions
- ✅ Event Sourcing Module with Kafka and PostgreSQL
- ✅ Saga Pattern for distributed transactions
- 🚧 Vector Store Integrations (Qdrant implemented, others in progress)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with ❤️ using Kotlin and Coroutines
- Inspired by modern AI agent architectures
- Special thanks to all contributors

## 📬 Contact

- **GitHub Issues**: [Report bugs or request features](https://github.com/no-ai-labs/spice/issues)
- **Discussions**: [Ask questions and share ideas](https://github.com/no-ai-labs/spice/discussions)
- **Wiki**: [Comprehensive documentation](https://github.com/no-ai-labs/spice/wiki)

---

<p align="center">
  <strong>Ready to spice up your AI applications? 🌶️</strong>
</p>

<p align="center">
  <a href="https://github.com/no-ai-labs/spice/wiki/getting-started.md">Get Started</a> •
  <a href="https://github.com/no-ai-labs/spice/blob/main/spice-dsl-samples/README.md">View Examples</a> •
  <a href="https://github.com/no-ai-labs/spice/wiki">Read Docs</a>
</p>
