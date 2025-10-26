# Spice 0.5.0 Roadmap: Agent Framework Revolution

## Overview

Spice 0.5.0 represents a **major architectural overhaul** inspired by Microsoft Agent Framework. We're transitioning from scattered execution models (Flow/Swarm/Pipeline) to a **unified graph-based runtime** with enterprise-grade observability, checkpointing, and middleware.

:::caution Breaking Changes Ahead
**0.5.0 is a breaking release.** The existing 0.4.x API will not be compatible. Migration tools and comprehensive guides will be provided.
:::

## Why Now?

### The Right Timing
- **Small user base** (8 stars, 4 forks) → minimal disruption  
- **Pre-1.0** → breaking changes expected in 0.x
- **Already proven** → 0.3.0 had breaking changes (CoreFlow removal)
- **Architectural debt** → Can't innovate with 3 execution models

### The AF-Style Solution
Unified graph runtime with nodes, middleware, checkpoints, and OpenTelemetry-native observability.

## Core Innovations

1. **Graph Runtime** - Everything is a DAG of nodes
2. **Middleware Pipeline** - Auth/logging/policy in one place
3. **Checkpoint & Time-Travel** - Resume from any node
4. **Enhanced Observability** - OpenTelemetry native
5. **Typed Edges & Validation** - Schema validation on edges

## Timeline

| Phase | Duration | Focus |
|-------|----------|-------|
| **Week 1-2** | Core Engine | Node/Graph/Runner + Middleware |
| **Week 3-4** | Checkpoint | CheckpointStore + Resume API |
| **Week 5** | Migration | Auto-migration tool + guide |
| **Week 6** | Validation | Port all examples to 0.5.0 |
| **Week 7** | RFC | Public feedback period |
| **Week 8** | Beta | 0.5.0-beta release |
| **Week 10** | GA | 0.5.0 stable release |

## Next Steps

1. Read: [AF-Style Architecture Spec](./af-architecture.md)
2. Read: [Migration Guide](./migration-guide.md)
3. Read: [Implementation Timeline](./implementation-timeline.md)

**Let's build the future of agent orchestration together!** 🚀
