# Spice Framework v0.9.3 Release Notes

**Release Date:** 2025-11-12
**Type:** Bug Fix & Enhancement

---

## 🎯 Overview

Version 0.9.3 addresses a **critical consistency issue** in the Graph execution pipeline where metadata was not being properly propagated to node state, causing data loss in Agent → HumanNode workflows.

---

## 🔥 Critical Fix

### Metadata → State Auto-Propagation

**Issue:** Agent nodes would set data in `Comm.data` which was copied to `result.metadata`, but this metadata was **not** automatically propagated to the next node's state. This caused `DynamicHumanNode` and other nodes to be unable to access data from previous agent executions.

**Example of the problem:**
```kotlin
// AgentNode returns
comm.copy(data = mapOf("menu_text" to "1. Hotel A\n2. Hotel B"))
// → Copied to result.metadata

// DynamicHumanNode tries to read
ctx.state["menu_text"]  // ❌ Not found! Data was lost
```

**Solution:** `GraphRunner` now automatically copies all `result.metadata` to `state` after each node execution:

```kotlin
// GraphRunner.kt (Line 230-239, 592-601)
val stateUpdates = mutableMapOf<String, Any?>(
    nodeId to result.data,
    "_previous" to result.data
)
// 🔥 Add all metadata to state
result.metadata.forEach { (key, value) ->
    stateUpdates[key] = value
}
```

**Impact:**
- ✅ **Agent → HumanNode workflows now work seamlessly**
- ✅ **DynamicHumanNode can access agent-generated prompts**
- ✅ **All metadata from any node is now accessible in subsequent nodes**
- ✅ **Improved workflow consistency and developer experience**

---

## 📝 Changes

### Core Framework

**GraphRunner.kt**
- **Enhanced**: `runValidatedGraph()` - Added automatic metadata → state propagation
- **Enhanced**: `executeGraphWithCheckpoint()` - Added automatic metadata → state propagation
- **Impact**: All graph executions now have consistent state management

### Tests

**DynamicHumanNodeTest.kt**
- **Fixed**: Updated edge conditions to use `result.metadata` instead of `result.data`
- **Reason**: `AgentNode` returns `response.content` (String) as `result.data`, not the full data map
- **All tests passing**: 3/3 DynamicHumanNode tests, plus all integration tests

---

## 🔄 Migration Guide

**No breaking changes!** This is a purely additive enhancement.

### Before (0.9.2)
```kotlin
// Edge conditions had to check metadata explicitly
edge("agent", "human") { result ->
    // ❌ This didn't work because result.data is a String
    (result.data as? Map<*, *>)?.get("needs_input") == "true"
}

// DynamicHumanNode couldn't access agent data from state
```

### After (0.9.3)
```kotlin
// Now works correctly - check metadata
edge("agent", "human") { result ->
    result.metadata["needs_input"] == "true"  // ✅ Correct
}

// DynamicHumanNode can now access agent data from state
ctx.state["menu_text"]  // ✅ Works! Auto-propagated from metadata
```

---

## 🧪 Testing

All tests passing:
- ✅ **DynamicHumanNodeTest**: 3/3 tests
- ✅ **GraphIntegrationTest**: All tests passing
- ✅ **CheckpointIntegrationTest**: All tests passing
- ✅ **HumanNodeTest**: All tests passing (including metadata propagation tests)

---

## 📦 Dependencies

No dependency changes.

---

## 🙏 Acknowledgments

This fix improves the developer experience for complex multi-node workflows, especially those involving dynamic human interactions and agent-generated content.

---

## 📚 Documentation

Updated documentation:
- Graph DSL patterns
- DynamicHumanNode usage examples
- Metadata propagation behavior

---

**Full Changelog:** [v0.9.2...v0.9.3](https://github.com/no-ai-labs/spice/compare/v0.9.2...v0.9.3)
