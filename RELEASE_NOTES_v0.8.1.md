# Spice Framework v0.8.1 Release Notes

**Released:** November 11, 2025
**Type:** Patch Release
**Breaking Changes:** None

## 🎉 Overview

Version 0.8.1 is a critical patch release that fixes a major context propagation bug in Human-in-the-Loop (HITL) workflows. This release ensures that `HumanResponse.metadata` properly propagates to ExecutionContext during checkpoint resume, preventing data loss in multi-agent workflows.

## 🐛 Critical Bug Fixes

### HumanResponse Metadata Propagation

**Issue:** When resuming a graph from checkpoint after a `HumanNode` pause, `HumanResponse.metadata` was not merged into `ExecutionContext`. This caused the next agent node to lose access to user selection data, breaking workflows that relied on passing user input metadata to subsequent agents.

**Impact:**
- ❌ AgentNode could not access HumanResponse metadata via `comm.context`
- ❌ User selection data (e.g., `selected_item_id`, `user_notes`) was lost
- ❌ Multi-stage HITL workflows with data handoffs failed silently

**Root Cause:**
In `GraphRunner.resumeWithHumanResponse()`, the `HumanResponse` was stored in `NodeContext.state` only. The metadata field was never merged into `NodeContext.context` (ExecutionContext), breaking the standard metadata propagation pattern used by `AgentNode` and other nodes.

**Fix:**
- Modified `GraphRunner.resumeWithHumanResponse()` to merge `HumanResponse.metadata` into ExecutionContext
- Added `NodeResult.fromHumanResponse()` helper function for consistent HumanResponse → NodeResult conversion
- Aligned with AgentNode's pattern: context data → `comm.context` and `comm.data`

**Code Changes:**
```kotlin
// GraphRunner.kt (lines 794-807)
val humanMetadata = response.metadata.mapValues { it.value as Any }
nodeContext = nodeContext
    .withState(checkpoint.currentNodeId, response)
    .withState("_previous", response)
    .withContext(nodeContext.context.plusAll(humanMetadata))  // 🔥 NEW!

val currentResult = NodeResult.fromContext(
    ctx = nodeContext,
    data = response,
    additional = humanMetadata  // 🔥 Explicitly propagated
)
```

**Verification:**
- ✅ New test: `test HumanResponse metadata propagates to next AgentNode via ExecutionContext`
- ✅ Validates Agent → HumanNode → Agent workflow with metadata preservation
- ✅ Confirms ExecutionContext contains original context + HumanResponse metadata

## 🆕 New APIs

### NodeResult.fromHumanResponse()

New factory method for creating `NodeResult` from `HumanResponse` with automatic metadata propagation:

```kotlin
/**
 * Factory specifically for creating NodeResult from HumanResponse during checkpoint resume.
 * Automatically propagates HumanResponse.metadata to ensure it's available in ExecutionContext
 * for subsequent nodes (especially AgentNode).
 */
fun NodeResult.Companion.fromHumanResponse(
    ctx: NodeContext,
    response: HumanResponse
): NodeResult
```

**Usage:**
```kotlin
// Recommended pattern in resume flows
val result = NodeResult.fromHumanResponse(ctx, response)

// Equivalent to:
val humanMetadata = response.metadata.mapValues { it.value as Any }
val result = NodeResult.fromContext(ctx, response, additional = humanMetadata)
```

## 📚 Documentation Updates

### Updated Documentation
- **docs/orchestration/graph-hitl.md**: Added "Metadata Propagation" section with examples
- **CLAUDE.md**: Added "HumanResponse Metadata 전파 (0.8.1+)" section with patterns

### New Documentation
- Added comprehensive test example showing metadata flow through checkpoint/resume cycle
- Documented `NodeResult.fromHumanResponse()` helper function

## 🔍 Implementation Details

### Affected Files
1. **GraphRunner.kt**: Fixed `resumeWithHumanResponse()` to merge metadata into ExecutionContext
2. **Node.kt**: Added `NodeResult.fromHumanResponse()` helper
3. **HumanNodeTest.kt**: Added metadata propagation test (Agent → HumanNode → Agent)
4. **CLAUDE.md**: Added metadata propagation patterns and guidelines

### Metadata Flow (After Fix)

```
┌─────────────────────────────────────────────────────┐
│ 1. Agent generates data                              │
│    comm.data = {"items_json": "[...]",              │
│                 "session_id": "SESSION123"}         │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ 2. NodeResult.fromContext()                          │
│    result.metadata = ctx.context + comm.data        │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ 3. GraphRunner merges into ExecutionContext          │
│    ctx.context = ctx.context.plusAll(result.metadata)│
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ 4. Checkpoint saves ExecutionContext                 │
│    checkpoint.metadata = ctx.context.toMap()        │
│    // Contains: items_json, session_id              │
└─────────────────────────────────────────────────────┘
                      ↓ [PAUSE]
                      ↓
┌─────────────────────────────────────────────────────┐
│ 5. Resume: Restore ExecutionContext                  │
│    ctx.context = ExecutionContext.of(checkpoint.metadata)│
│    // Restored: items_json, session_id              │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ 6. Merge HumanResponse.metadata (NEW!)               │
│    humanMetadata = response.metadata                │
│    ctx.context = ctx.context.plusAll(humanMetadata) │
│    // Now has: items_json, session_id,              │
│    //          + selected_id, user_comment          │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ 7. Next AgentNode receives complete context         │
│    comm.context = ctx.context                       │
│    // ✅ Can access ALL data!                       │
└─────────────────────────────────────────────────────┘
```

## ✅ Testing

### Test Coverage
- ✅ **HumanNodeTest**: 11 tests, all passing
- ✅ **DynamicHumanNodeTest**: 10 tests, all passing
- ✅ **CheckpointIntegrationTest**: 6 tests, all passing
- ✅ **New test**: Metadata propagation through Agent → HumanNode → Agent flow

### Test Case
```kotlin
@Test
fun `test HumanResponse metadata propagates to next AgentNode via ExecutionContext`()
```

Validates:
1. First agent generates data with `comm.data`
2. Graph pauses at HumanNode, checkpoint saves context
3. Resume with HumanResponse containing metadata
4. Next agent receives **both** original context and HumanResponse metadata via `comm.context`

## 🚀 Migration Guide

### No Breaking Changes
This is a patch release with **no breaking changes**. Existing code continues to work as before.

### Recommended Updates

If you're using HumanResponse with custom metadata, update to the new pattern:

```kotlin
// Before (still works, but manual)
val response = HumanResponse(
    nodeId = "select",
    selectedOption = "item-2",
    metadata = mapOf("selected_id" to "ITEM002")
)
// Next node: manually extract from state

// After (recommended, automatic)
val response = HumanResponse(
    nodeId = "select",
    selectedOption = "item-2",
    metadata = mapOf(
        "selected_id" to "ITEM002",
        "user_comment" to "Looks good!"
    )
)
// Next node: automatically available in comm.context
val processAgent = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val selectedId = comm.context?.get("selected_id")  // ✅ Works!
        val comment = comm.context?.get("user_comment")    // ✅ Works!
        return processItem(selectedId, comment)
    }
}
```

## 🙏 Acknowledgments

- Bug discovered during reservation cancellation workflow testing
- Fix implements AgentNode's existing metadata propagation pattern
- Aligns with Spice 0.6.0 ExecutionContext design principle: "단일 소스로 사용"

## 📦 Upgrade Instructions

### Gradle (JitPack)
```kotlin
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:0.8.1")
}
```

### Maven
```xml
<dependency>
    <groupId>com.github.no-ai-labs.spice-framework</groupId>
    <artifactId>spice-core</artifactId>
    <version>0.8.1</version>
</dependency>
```

## 📄 Full Changelog

### Added
- `NodeResult.fromHumanResponse()` helper function for HumanResponse → NodeResult conversion

### Fixed
- **Critical**: HumanResponse.metadata now properly propagates to ExecutionContext during checkpoint resume
- AgentNode after HumanNode can now access user selection data via `comm.context`

### Changed
- GraphRunner.resumeWithHumanResponse() now merges HumanResponse.metadata into ExecutionContext
- Aligned resume flow with standard metadata propagation pattern

### Documentation
- Added "Metadata Propagation (0.8.1+)" section to graph-hitl.md
- Added "HumanResponse Metadata 전파 (0.8.1+)" section to CLAUDE.md
- Added comprehensive test example for metadata flow

## 🔗 Related Issues

- Fixes context loss in HITL workflows with data handoffs
- Implements missing metadata propagation in resumeWithHumanResponse()
- Aligns with 0.6.0 ExecutionContext unification architecture

---

**Full Documentation:** [docs.spice-framework.com](https://github.com/no-ai-labs/spice/tree/main/docs)
**GitHub:** [github.com/no-ai-labs/spice](https://github.com/no-ai-labs/spice)
