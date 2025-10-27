# Context Propagation Guide

Practical guide to using context propagation in real-world applications.

## Overview

For detailed technical information about how context propagation works, see [Context Propagation Deep Dive](../advanced/context-propagation.md).

This guide focuses on practical patterns for using context in your applications.

## Quick Start

### Setting Context

```kotlin
withAgentContext(
    "tenantId" to "ACME",
    "userId" to "user-123"
) {
    // All operations have context!
    agent.processComm(comm)
}
```

### Using Context in Tools

```kotlin
val tool = contextAwareTool("my_tool") {
    execute { params, context ->
        val tenantId = context.tenantId!!
        // Use tenantId for scoped operations
    }
}
```

## See Also

- [Context-Aware Tools](../dsl-guide/context-aware-tools.md) - Build context-aware tools
- [Context Propagation Deep Dive](../advanced/context-propagation.md) - Technical details
- [Multi-Tenancy](../security/multi-tenancy.md) - Multi-tenant patterns
