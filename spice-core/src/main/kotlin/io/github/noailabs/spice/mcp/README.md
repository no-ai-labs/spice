# MCP Integration

This module provides Model Context Protocol (MCP) integration for the Spice framework.

## Overview

Spice acts as an MCP **client** to communicate with MCP servers like mnemo for:
- Saving PSI structures of agents, tools, and flows
- Recording execution patterns and context injections  
- Detecting vibe coding anti-patterns
- Getting intelligent recommendations

## Architecture

```
┌─────────────┐     MCP Protocol     ┌──────────────┐
│   Spice     │ ──────────────────> │    mnemo     │
│ (MCP Client)│                      │ (MCP Server) │
└─────────────┘                      └──────────────┘
      │                                      │
      └── Sends PSI structures ──────────────┘
      └── Queries for patterns ──────────────┘
      └── Gets recommendations ──────────────┘
```

## Components

### MCPClient
Low-level MCP protocol client using Ktor HTTP client.

### MnemoMCPAdapter  
High-level adapter specifically for mnemo integration:
- `saveAgent()` - Save agent PSI structure
- `saveFlowExecution()` - Record flow executions
- `findSimilarAgents()` - Search for similar patterns
- `detectVibeCoding()` - Find anti-patterns
- `getRecommendedTools()` - Get tool suggestions

## Usage

```kotlin
val adapter = MnemoMCPAdapter("http://localhost:8080")

// Save agent structure
agent.savePSIToMnemo(adapter)

// Check for vibe coding
val issues = agents.checkVibeCoding(adapter)
```

## MCP Tools Used

### mnemo MCP Tools
- `mcp_mnemo_remember` - Store memories
- `mcp_mnemo_search` - Search for patterns
- `mcp_mnemo_find_pattern` - Find specific patterns
- `mcp_mnemo_remember_code_pattern` - Store code patterns

## Configuration

Set environment variables:
- `MNEMO_URL` - MCP server URL (default: http://localhost:8080)
- `MNEMO_API_KEY` - Optional API key for authentication