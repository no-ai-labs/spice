# Spice Framework v0.8.0 Release Notes

**Released:** November 11, 2025
**Type:** Minor Release
**Breaking Changes:** None

## 🎉 Overview

Version 0.8.0 introduces **DynamicHumanNode**, a powerful new node type that enables runtime-generated prompts in Human-in-the-Loop workflows. This release also includes critical bug fixes for checkpoint/resume scenarios and enhancements to context propagation.

## ✨ New Features

### 🎭 DynamicHumanNode - Runtime Dynamic Prompts

A new `HumanNode` variant that reads prompts from execution context at runtime, enabling agents to generate dynamic menus and messages based on their processing results.

**Key Capabilities:**
- ✅ Read prompts from `NodeContext.state` or `NodeContext.context`
- ✅ Configurable `promptKey` and `fallbackPrompt`
- ✅ Seamless checkpoint/resume support
- ✅ Perfect for agent-generated selection menus

**Example:**
```kotlin
val workflowGraph = graph("reservation-workflow") {
    // Agent lists reservations and generates menu
    agent("list-reservations", listAgent)

    // DynamicHumanNode reads menu from state["menu_text"]
    dynamicHumanNode(
        id = "select-reservation",
        promptKey = "menu_text",
        fallbackPrompt = "Please make a selection"
    )

    // Agent processes user selection
    agent("cancel-reservation", cancelAgent)
}
```

**Use Cases:**
- Dynamic selection menus (e.g., "Select a reservation to cancel")
- Context-aware approval prompts
- Agent-generated follow-up questions
- Personalized user interactions

**Documentation:** [HITL (Human-in-the-Loop)](/docs/orchestration/graph-hitl#2-dynamichumannode-added-in-080)

### 📦 New Classes

```kotlin
// Core DynamicHumanNode
class DynamicHumanNode(
    override val id: String,
    private val promptKey: String = "menu_text",
    private val fallbackPrompt: String = "사용자 입력을 기다립니다...",
    val options: List<HumanOption> = emptyList(),
    val timeout: Duration? = null,
    val validator: ((HumanResponse) -> Boolean)? = null,
    val allowFreeText: Boolean = options.isEmpty()
) : Node

// DSL Extension
fun GraphBuilder.dynamicHumanNode(
    id: String,
    promptKey: String = "menu_text",
    fallbackPrompt: String = "사용자 입력을 기다립니다...",
    options: List<HumanOption> = emptyList(),
    timeout: Duration? = null,
    validator: ((HumanResponse) -> Boolean)? = null,
    allowFreeText: Boolean = options.isEmpty()
)
```

## 🐛 Bug Fixes

### Critical: Checkpoint Resume Data Loss

**Issue:** When resuming from a checkpoint after `DynamicHumanNode` pause, metadata from `ExecutionContext` was not propagated to subsequent nodes, causing data loss in agent workflows.

**Root Cause:**
1. `KAIBaseAgent.toThinkingContext()` only read from `comm.data`, ignoring `comm.context`
2. `WorkflowAgent.restoreJsonDataFromSession()` checked SessionState before ExecutionContext

**Fixed:**
- ✅ `KAIBaseAgent` now merges `comm.context + comm.data` when creating `ThinkingContext`
- ✅ `WorkflowAgent` prioritizes `comm.context` over SessionState for data restoration
- ✅ `AgentNode` propagates `comm.data` entries to metadata for non-AgentNode access

**Impact:** Resolved data loss in multi-turn HITL workflows with agent-generated menus.

**Files Modified:**
- `/kai-core/src/main/kotlin/team/kai/agents/KAIBaseAgent.kt`
- `/kai-core/src/main/kotlin/team/kai/agents/WorkflowAgent.kt`

## 🔧 Enhancements

### ExecutionContext Merging in KAIBaseAgent

Enhanced `Comm.toThinkingContext()` to merge ExecutionContext data:

```kotlin
// Before (0.7.x)
metadata = data  // Only comm.data

// After (0.8.0)
val mergedMetadata = buildMap<String, String> {
    // Add all from ExecutionContext (if present)
    context?.toMap()?.forEach { (key, value) ->
        put(key, value.toString())
    }
    // Add all from comm.data (legacy/direct metadata)
    putAll(data)
}
metadata = mergedMetadata  // Full context!
```

**Benefit:** Agents now receive complete execution context from checkpointed workflows.

### Prioritized Data Restoration in WorkflowAgent

Updated `restoreJsonDataFromSession()` to check sources in priority order:

1. **Priority 1:** `comm.context` (from Checkpoint ExecutionContext)
2. **Priority 2:** SessionState.workflowData (fallback)

**Benefit:** Correct data restoration in checkpoint resume scenarios.

## 📚 Documentation

### New Documentation

- **DynamicHumanNode Section** in [HITL Documentation](/docs/orchestration/graph-hitl#2-dynamichumannode-added-in-080)
  - Overview and key differences
  - Agent-generated menu example
  - Prompt resolution order
  - Checkpoint resume support

### Updated Documentation

- [Graph Nodes](/docs/orchestration/graph-nodes) - Added DynamicHumanNode reference
- [HITL (Human-in-the-Loop)](/docs/orchestration/graph-hitl) - Renumbered sections

## 🔄 Migration Guide

### From 0.7.x to 0.8.0

**No Breaking Changes** - This is a minor release with backward compatibility.

#### Adopting DynamicHumanNode

If you have workflows with agent-generated selection menus:

**Before (0.7.x):**
```kotlin
// Workaround: Store menu in state and use static HumanNode
agent("list-items", listAgent)
humanNode(
    id = "select",
    prompt = "Select an item"  // ❌ Static prompt, no menu
)
```

**After (0.8.0):**
```kotlin
// Native support: Agent stores menu, DynamicHumanNode displays it
agent("list-items", listAgent)
dynamicHumanNode(
    id = "select",
    promptKey = "menu_text",  // ✅ Reads dynamic menu from agent
    fallbackPrompt = "Please make a selection"
)
```

#### No Changes Required

- Existing `HumanNode` usage continues to work unchanged
- Checkpoint/resume workflows automatically benefit from bug fixes
- No API changes to existing classes

## 📊 Statistics

- **Lines Changed:** ~150
- **New Classes:** 1 (DynamicHumanNode)
- **Bug Fixes:** 2 critical fixes
- **Documentation Pages:** 1 major update

## 🙏 Acknowledgments

Special thanks to the kai-core team for identifying the checkpoint resume data loss issue during production testing!

## 🔗 Links

- [Full Documentation](/docs/intro)
- [HITL Guide](/docs/orchestration/graph-hitl)
- [GitHub Repository](https://github.com/no-ai-labs/spice)
- [Migration Guides](/docs/roadmap/migrations)

## 🚀 What's Next

See our [Roadmap](/docs/roadmap/overview) for upcoming features:

- **v0.9.0**: Enhanced parallel execution with nested graphs
- **v1.0.0**: Production-ready release with stability guarantees

---

**Questions or feedback?** Join our community discussions or open an issue on GitHub!
