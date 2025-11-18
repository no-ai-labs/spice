# 📚 Spice Framework 1.0.0 Documentation

Welcome to the Spice Framework 1.0.0 documentation! This guide will help you get started and master the framework.

---

## 🎯 What is Spice 1.0.0?

Spice Framework 1.0.0 is a **revolutionary update** that introduces a unified message architecture for building AI-powered applications in Kotlin. The framework provides a clean DSL for creating agents, managing tools, and orchestrating complex AI workflows.

### Key Features

- ✅ **Single Message Type** - SpiceMessage replaces all 0.x message types
- ✅ **Built-in State Machine** - READY → RUNNING → WAITING → COMPLETED/FAILED
- ✅ **Graph DSL** - Powerful workflow orchestration with nodes and edges
- ✅ **HITL Support** - Human-in-the-Loop with automatic checkpoint/resume
- ✅ **Tool Standardization** - OpenAI/Anthropic compatible tool calls
- ✅ **Type Safety** - Kotlin-first design with coroutines
- ✅ **Spring Boot Integration** - Seamless enterprise integration

---

## 📖 Documentation Structure

### Foundation Guides (Start Here!)

1. **[Quick Start Guide](QUICK_START_1.0.md)** ⭐ **Start here!**
   - Installation in 5 minutes
   - Your first agent
   - Building workflows
   - HITL examples
   - Common patterns

2. **[Installation Guide](INSTALLATION_1.0.md)**
   - Prerequisites
   - All modules (core, agents, springboot)
   - Gradle and Maven setup
   - Configuration
   - Troubleshooting

3. **[Architecture Overview](ARCHITECTURE_1.0.md)**
   - Core philosophy
   - Visual diagrams
   - SpiceMessage deep dive
   - Component architecture
   - Data flow patterns
   - Design patterns

### Migration Guides

4. **[Migration Guide (0.x → 1.0)](MIGRATION_0.x_TO_1.0.md)** ⚠️ **Upgrading from 0.x?**
   - Breaking changes summary
   - Step-by-step migration
   - Side-by-side code examples
   - Common pitfalls
   - Testing strategy

5. **[Concept Comparison](CONCEPT_COMPARISON_1.0.md)**
   - Quick reference tables
   - API comparisons
   - Before/after examples
   - All changes documented

---

## 🚀 Quick Navigation

### I want to...

#### Get Started
- **Build my first agent** → [Quick Start Guide](QUICK_START_1.0.md#your-first-agent)
- **Install Spice** → [Installation Guide](INSTALLATION_1.0.md#gradle-installation)
- **See examples** → [Quick Start Guide](QUICK_START_1.0.md#common-patterns)

#### Understand the Architecture
- **Learn core concepts** → [Architecture Overview](ARCHITECTURE_1.0.md#core-philosophy)
- **See data flow** → [Architecture Overview](ARCHITECTURE_1.0.md#data-flow-patterns)
- **Understand SpiceMessage** → [Architecture Overview](ARCHITECTURE_1.0.md#spicemessage-the-foundation)

#### Migrate from 0.x
- **Start migration** → [Migration Guide](MIGRATION_0.x_TO_1.0.md#migration-checklist)
- **Compare APIs** → [Concept Comparison](CONCEPT_COMPARISON_1.0.md#detailed-comparisons)
- **See breaking changes** → [Migration Guide](MIGRATION_0.x_TO_1.0.md#breaking-changes-summary)

#### Build Advanced Features
- **Create workflows** → [Quick Start Guide](QUICK_START_1.0.md#building-workflows-with-graph-dsl)
- **Implement HITL** → [Quick Start Guide](QUICK_START_1.0.md#human-in-the-loop-hitl)
- **Use Spring Boot** → [Installation Guide](INSTALLATION_1.0.md#spice-springboot)

---

## 📊 Quick Reference

### SpiceMessage in 30 Seconds

```kotlin
// Create message
val message = SpiceMessage.create("Hello", "user")
    .withData(mapOf("key" to "value"))
    .withMetadata(mapOf("userId" to "123"))

// Reply
val reply = message.reply("Response", "agent-id")

// Access data
val data = message.getData<String>("key")
val userId = message.getMetadata<String>("userId")

// State transitions
val running = message.transitionTo(ExecutionState.RUNNING, "Processing")
```

### Agent in 30 Seconds

```kotlin
class MyAgent : Agent {
    override val id = "my-agent"
    override val name = "My Agent"
    override val description = "Agent description"
    override val capabilities = listOf("chat")

    override suspend fun processMessage(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        return SpiceResult.success(
            message.reply("Processed: ${message.content}", id)
        )
    }
}
```

### Graph in 30 Seconds

```kotlin
val graph = graph("workflow") {
    agent("step1", agent1)
    tool("step2", tool1) { msg -> mapOf("param" to msg.content) }
    human("step3", "Confirm?")
    output("result") { it.content }

    edge("step1", "step2")
    edge("step2", "step3")
    edge("step3", "result")
}

val runner = DefaultGraphRunner()
val result = runner.execute(graph, initialMessage)
```

---

## 🎓 Learning Path

### Beginner
1. Read [Quick Start Guide](QUICK_START_1.0.md) (30 min)
2. Build your first agent (15 min)
3. Try examples in Quick Start (30 min)

### Intermediate
1. Study [Architecture Overview](ARCHITECTURE_1.0.md) (1 hour)
2. Build a multi-agent workflow (1 hour)
3. Implement HITL with checkpoints (45 min)

### Advanced
1. Master Graph DSL patterns (1 hour)
2. Spring Boot integration (1 hour)
3. Production deployment (2 hours)

### Migrating from 0.x
1. Read [Migration Guide](MIGRATION_0.x_TO_1.0.md) introduction (15 min)
2. Review [Concept Comparison](CONCEPT_COMPARISON_1.0.md) (30 min)
3. Follow [Migration Checklist](MIGRATION_0.x_TO_1.0.md#migration-checklist) (varies)

---

## 🆚 0.x vs 1.0.0 at a Glance

| Feature | 0.x | 1.0.0 |
|---------|-----|-------|
| **Message Types** | Comm, NodeContext, NodeResult (3 types) | SpiceMessage (1 type) |
| **State Management** | Manual | Built-in state machine |
| **HITL** | Manual checkpoint | Automatic checkpoint/resume |
| **Tool Calls** | Custom format | OpenAI/Anthropic compatible |
| **Graph DSL** | Flow-based | Node/edge-based |
| **Type Safety** | Manual casts | Generic type accessors |
| **Metadata** | Mixed with data | Separated |

**Result:**
- ✅ 50% less boilerplate code
- ✅ 70% fewer type conversions
- ✅ 100% better HITL support

---

## 🔧 Module Overview

| Module | Description | Documentation |
|--------|-------------|---------------|
| **spice-core** | Core framework | [Installation](INSTALLATION_1.0.md#spice-core) |
| **spice-agents** | Pre-built agents | [Installation](INSTALLATION_1.0.md#spice-agents) |
| **spice-springboot** | Spring Boot integration | [Installation](INSTALLATION_1.0.md#spice-springboot) |
| **spice-springboot-ai** | Spring AI integration | [Installation](INSTALLATION_1.0.md#spice-springboot-ai) |
| **spice-springboot-statemachine** | Checkpoint/resume | [Installation](INSTALLATION_1.0.md#spice-springboot-statemachine) |

---

## 💡 Examples

### Example 1: Simple Agent
```kotlin
val agent = object : Agent {
    override val id = "echo"
    override val name = "Echo Agent"
    override val description = "Echoes input"
    override val capabilities = listOf("chat")

    override suspend fun processMessage(message: SpiceMessage) =
        SpiceResult.success(message.reply("Echo: ${message.content}", id))
}

val result = agent.processMessage(SpiceMessage.create("Hello", "user"))
```

### Example 2: Multi-Step Workflow
```kotlin
val graph = graph("workflow") {
    agent("analyze", analyzerAgent)
    agent("transform", transformAgent)
    agent("validate", validatorAgent)
    output("result")

    edge("analyze", "transform")
    edge("transform", "validate")
    edge("validate", "result")
}

val runner = DefaultGraphRunner()
val result = runner.execute(graph, initialMessage)
```

### Example 3: HITL with Checkpoint
```kotlin
val graph = graph("booking") {
    agent("search", searchAgent)
    human("confirm", "Confirm booking?")
    tool("book", bookingTool)
    output("result")
    // edges...
}

// Execute (pauses at human node)
val result1 = runner.executeWithCheckpoint(graph, message, checkpointStore)

if (result1.getOrNull()?.state == ExecutionState.WAITING) {
    val checkpointId = result1.getOrNull()?.runId!!

    // Resume with user response
    val result2 = runner.resumeFromCheckpoint(
        graph, checkpointId, userResponse, checkpointStore
    )
}
```

More examples in [Quick Start Guide](QUICK_START_1.0.md).

---

## 🐛 Troubleshooting

### Common Issues

1. **Repository Authentication Failed**
   - Check credentials in gradle.properties
   - See [Installation Guide - Troubleshooting](INSTALLATION_1.0.md#troubleshooting)

2. **Kotlin Version Mismatch**
   - Upgrade to Kotlin 2.2.0+
   - See [Installation Guide - Prerequisites](INSTALLATION_1.0.md#prerequisites)

3. **Migration Errors**
   - Review [Common Pitfalls](MIGRATION_0.x_TO_1.0.md#common-pitfalls)
   - Check [Concept Comparison](CONCEPT_COMPARISON_1.0.md)

---

## 📚 Additional Resources

### Official Documentation
- **[CLAUDE.md](../CLAUDE.md)** - AI assistant coding guide
- **[GitHub Wiki](https://github.com/no-ai-labs/spice/wiki)** - Community docs
- **[API Reference](../wiki/api-reference.md)** - Detailed API docs

### Community
- **[GitHub Issues](https://github.com/no-ai-labs/spice/issues)** - Report bugs
- **[Discussions](https://github.com/no-ai-labs/spice/discussions)** - Ask questions
- **[Examples](../spice-dsl-samples)** - Sample projects

### Version History
- **[0.10.0 Migration](MIGRATION-0.10.0.md)** - 0.9.x → 0.10.0
- **[0.9.0 Release Notes](V0.9.0_RELEASE_NOTES.md)**
- **[0.8.0 Release Notes](V0.8.0_RELEASE_NOTES.md)**

---

## 🗺️ Documentation Roadmap

### ✅ Available Now
- Quick Start Guide
- Installation Guide
- Architecture Overview
- Migration Guide
- Concept Comparison

### 🚧 Coming Soon
- Advanced Patterns Guide
- Performance Optimization Guide
- Production Deployment Guide
- Spring Boot Best Practices
- Testing Strategies Guide

---

## 📝 Document Versions

| Document | Version | Last Updated |
|----------|---------|--------------|
| Quick Start Guide | 1.0 | 2025-01-19 |
| Installation Guide | 1.0 | 2025-01-19 |
| Architecture Overview | 1.0 | 2025-01-19 |
| Migration Guide | 1.0 | 2025-01-19 |
| Concept Comparison | 1.0 | 2025-01-19 |

---

## 🤝 Contributing

We welcome contributions! If you find errors or have suggestions:

1. **Documentation Errors**: [Open an issue](https://github.com/no-ai-labs/spice/issues)
2. **Improvements**: Submit a PR with updates
3. **Examples**: Share your examples in discussions

---

## 📞 Getting Help

### Quick Help
- **Installation issues** → [Installation Guide - Troubleshooting](INSTALLATION_1.0.md#troubleshooting)
- **Migration questions** → [Migration Guide](MIGRATION_0.x_TO_1.0.md)
- **API questions** → [Concept Comparison](CONCEPT_COMPARISON_1.0.md#api-reference-quick-lookup)

### Community Help
- **[GitHub Discussions](https://github.com/no-ai-labs/spice/discussions)** - Best for questions
- **[GitHub Issues](https://github.com/no-ai-labs/spice/issues)** - For bug reports
- **[Wiki](https://github.com/no-ai-labs/spice/wiki)** - Community knowledge

---

## 🎉 Ready to Get Started?

Choose your path:

- **New to Spice?** → Start with [Quick Start Guide](QUICK_START_1.0.md)
- **Upgrading from 0.x?** → Start with [Migration Guide](MIGRATION_0.x_TO_1.0.md)
- **Need installation help?** → Start with [Installation Guide](INSTALLATION_1.0.md)
- **Want to understand deeply?** → Start with [Architecture Overview](ARCHITECTURE_1.0.md)

---

**Welcome to Spice 1.0.0! Let's build amazing AI applications! 🌶️**
