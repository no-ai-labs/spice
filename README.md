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

- 🧠 **Agent-to-Agent Messaging** — Structured agent interface powered by typed messages
- 🔌 **Modular LLM Integration** — Supports OpenAI, vLLM, Anthropic, Vertex AI and more
- ⚙️ **Pluggable Tool System** — Agents can use and expose tools declaratively
- ♻️ **Spice Flow Graph Execution** — Experimental message-based DAG-style routing
- ☕️ **Spring Boot AutoConfiguration** — Ready to drop into your Spring ecosystem
- 🔐 **Async-Ready, Production-Friendly** — Built with coroutines and extension points

---

## 🚀 Getting Started

```kotlin
val engine = AgentEngine()
engine.registerAgent(OpenAIAgent("summarizer", ...))
engine.registerAgent(OpenAIAgent("critic", ...))

val result = engine.send(
    Message(
        sender = "user",
        receiver = "summarizer",
        content = "Summarize this paragraph about Kotlin coroutines..."
    )
)
```

---

## 📦 Modules (Planned)

| Module             | Description                                  |
|--------------------|----------------------------------------------|
| `spice-core`       | Agent interface, engine, message model       |
| `spice-springboot` | AutoConfiguration + property-based registration |
| `spice-runtime`    | LLM client wrappers (vLLM, OpenAI, etc.)     |
| `spice-tools`      | Tool interface + example tools               |
| `spice-examples`   | Ready-to-run demos                           |

---

## 📜 License

MIT License. Use freely. Share wildly. Build something spicy. 🌶️

---

## 💬 Authors

Built by No AI Labs

---

> **The Spice must flow.**
