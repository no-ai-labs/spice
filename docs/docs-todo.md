# 📚 Documentation TODO

List of documentation improvements needed for Spice Framework.

## 🔴 High Priority

All high-priority items have been completed! 🎉

---

## 🟡 Medium Priority

### 4. Agent Lifecycle Guide

**Location:** `docs/core-concepts/agent-lifecycle.md` (new file)

**What's missing:**
- Detailed lifecycle stages
- State management
- Resource cleanup
- Coroutine scope management

---

### 5. Observability Patterns

**Location:** `docs/observability/patterns.md` (new file)

**What's missing:**
- Custom metrics creation
- Span attribute patterns
- Cost tracking implementation
- Performance profiling techniques

---

### 6. Vector Store Deep Dive

**Location:** `docs/tools-extensions/vector-stores-advanced.md` (new file)

**What's missing:**
- Chunking strategies
- Embedding optimization
- Hybrid search patterns
- Production deployment tips

---

### 7. Tool Creation Patterns

**Location:** `docs/tools-extensions/tool-patterns.md` (new file)

**What's missing:**
- Advanced tool patterns
- Stateful tools
- Tool composition
- Error handling in tools

---

## 🟢 Low Priority

### 8. Testing Guide

**Location:** `docs/testing/overview.md` (new file)

**What's missing:**
- Unit testing agents
- Integration testing swarms
- Mocking LLM responses
- Property-based testing

---

### 9. Performance Tuning

**Location:** `docs/advanced/performance.md` (new file)

**What's missing:**
- Caching strategies
- Batching patterns
- Connection pooling
- Memory optimization

---

### 10. Migration Guides

**Location:** `docs/migration/` (new directory)

**What's missing:**
- From v0.1.x to v0.2.x
- From legacy Message to Comm
- From other frameworks (LangChain, etc.)

---

## ✅ Recently Completed

- [x] **Swarm Strategies Deep Dive** (2024-10-22) - Complete guide to all 5 execution strategies with real code flows, performance analysis, decision matrix, and 8 detailed examples
- [x] **Inline Functions & catchingSuspend Guide** (2024-10-22) - Comprehensive explanation of non-local returns, 4 solution patterns, 10+ examples, and common pitfalls
- [x] **Error Context Enrichment Patterns** (2024-10-22) - Multi-layer context pattern, request tracking, timing/retry patterns, OpenTelemetry integration, and structured logging
- [x] **SpiceResult Operator Guide** (2024-10-22) - Added comprehensive "Choosing the Right Operator" section with decision tree and patterns
- [x] **Comm API Complete Reference** (2024-10-22) - Expanded from 18 lines to 750+ lines with full examples
- [x] **MDX Compilation Fix** (2024-10-22) - Fixed generic type syntax in documentation

---

## 📝 Quick Reference: Documentation Standards

When writing documentation:

1. **Start with Overview** - What is it? Why use it?
2. **Show Code First** - Examples before explanations
3. **Include Anti-Patterns** - Show ❌ bad and ✅ good
4. **Real-World Examples** - Not just toy examples
5. **Decision Trees** - Help users choose between options
6. **Link Related Docs** - Connect concepts together
7. **Keep Updated** - Update when code changes

---

## 🤝 Contributing

To claim a TODO item:
1. Create an issue linking to this TODO
2. PR with the new documentation
3. Update this file marking it complete
4. Add to "Recently Completed" section

---

**Last Updated:** 2024-10-22
**Maintainer:** @spice-team
