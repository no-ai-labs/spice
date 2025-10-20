# Chatbot Example

Build a chatbot with Spice.

```kotlin
val chatbot = buildAgent {
    id = "chatbot"
    name = "Friendly Chatbot"

    tool("greet") {
        execute { ToolResult.success("Hello!") }
    }

    handle { comm ->
        comm.reply("How can I help?", id)
    }
}
```
