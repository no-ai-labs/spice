# DSL API

Domain-Specific Language for building agents, tools, and multi-agent systems in Spice Framework.

## Overview

Spice Framework provides an intuitive, type-safe DSL for:

- **Building agents** - Create LLM-powered and custom agents
- **Defining tools** - Inline tool definitions with automatic validation
- **Creating swarms** - Multi-agent coordination systems
- **Managing communications** - Type-safe message construction
- **Configuring systems** - Declarative configuration

The DSL emphasizes:
- ✅ Type safety and compile-time validation
- ✅ Intuitive builder patterns
- ✅ Minimal boilerplate
- ✅ Clear, readable code

## Agent DSL

### buildAgent { }

Create agents with LLM providers:

```kotlin
val agent = buildAgent {
    // Identity
    name = "Research Assistant"
    description = "AI-powered research assistant"
    capabilities = listOf("research", "analysis", "summarization")

    // LLM provider
    llm = anthropic(apiKey = env["ANTHROPIC_API_KEY"]!!) {
        model = "claude-3-5-sonnet-20241022"
        temperature = 0.7
        maxTokens = 4096
    }

    // Tools
    tools {
        tool("web_search", "Search the web") {
            parameter("query", "string", "Search query", required = true)
            execute(fun(params: Map<String, Any>): String {
                return searchEngine.search(params["query"] as String)
            })
        }
    }

    // Instructions (system prompt)
    instructions = """
        You are a research assistant that helps users find and analyze information.

        Guidelines:
        - Always cite your sources with URLs
        - Be thorough and accurate
        - Ask clarifying questions when needed
    """.trimIndent()
}
```

**Parameters:**

- `name: String` - Agent name (required)
- `description: String` - Agent description
- `capabilities: List<String>` - List of capabilities
- `llm: LLM` - LLM provider configuration
- `tools { }` - Tools block (inline tool definitions)
- `instructions: String` - System prompt / instructions

**LLM Providers:**

```kotlin
// Anthropic Claude
llm = anthropic(apiKey = "...") {
    model = "claude-3-5-sonnet-20241022"  // or haiku, opus
    temperature = 0.7
    maxTokens = 4096
    topP = 1.0
    topK = -1
}

// OpenAI
llm = openai(apiKey = "...") {
    model = "gpt-4"
    temperature = 0.7
    maxTokens = 2048
    topP = 1.0
    frequencyPenalty = 0.0
    presencePenalty = 0.0
}

// Custom LLM
llm = customLLM(
    provider = myCustomProvider,
    model = "my-model"
)
```

**Complete Example:**

```kotlin
val researchAgent = buildAgent {
    name = "Research Bot"
    description = "Comprehensive research assistant"
    capabilities = listOf(
        "web-search",
        "data-analysis",
        "report-generation"
    )

    llm = anthropic(apiKey = env["ANTHROPIC_API_KEY"]!!) {
        model = "claude-3-5-sonnet-20241022"
        temperature = 0.7
        maxTokens = 4096
    }

    tools {
        // Web search tool
        tool("search", "Search the internet") {
            parameter("query", "string", "Search query", required = true)
            parameter("limit", "number", "Max results", required = false)

            execute(fun(params: Map<String, Any>): String {
                val query = params["query"] as String
                val limit = (params["limit"] as? Number)?.toInt() ?: 10
                return searchApi.search(query, limit).joinToString("\n")
            })
        }

        // Document analysis tool
        tool("analyze_document", "Analyze document content") {
            parameter("url", "string", "Document URL", required = true)

            execute(fun(params: Map<String, Any>): String {
                val url = params["url"] as String
                val content = httpClient.get(url)
                return analyzer.analyze(content)
            })
        }
    }

    instructions = """
        You are a professional research assistant specializing in comprehensive information gathering and analysis.

        Your workflow:
        1. Use 'search' to find relevant sources
        2. Use 'analyze_document' to extract key information
        3. Synthesize findings into clear, well-cited responses

        Always:
        - Cite sources with full URLs
        - Present multiple perspectives on controversial topics
        - Indicate confidence levels in your findings
        - Ask clarifying questions when the request is ambiguous
    """.trimIndent()
}

// Use the agent
val result = researchAgent.processComm(Comm(
    content = "Research the latest developments in quantum computing",
    from = "user"
))
```

### buildSwarmAgent { }

Create multi-agent coordination systems:

```kotlin
val swarm = buildSwarmAgent {
    // Identity
    name = "Research Team"
    description = "Multi-agent research team with specialized roles"

    // Shared tools accessible to all members
    swarmTools {
        tool("database_query", "Query shared database") {
            parameter("query", "string", required = true)
            execute(fun(params: Map<String, Any>): String {
                return database.query(params["query"] as String)
            })
        }

        // Built-in coordination tools
        aiConsensus(scoringAgent = anthropic(...))
        resultAggregator()
        qualityAssessor(scoringAgent = anthropic(...))
    }

    // Define team members (quick setup)
    quickSwarm {
        specialist("researcher", "Researcher", "information gathering")
        specialist("analyst", "Data Analyst", "data analysis")
        specialist("writer", "Technical Writer", "report writing")
    }

    // Or use existing agents
    members {
        agent(researchAgent)
        agent(analysisAgent)
        agent(writingAgent)
    }

    // Configuration
    config {
        debugEnabled = true
        maxConcurrentOperations = 5
        timeoutMs = 60000
    }
}
```

**Parameters:**

- `name: String` - Swarm name (required)
- `description: String` - Swarm description
- `swarmTools { }` - Shared tools block
- `quickSwarm { }` - Quick member setup
- `members { }` - Add existing agents
- `config { }` - Swarm configuration

**Quick Swarm Setup:**

```kotlin
quickSwarm {
    // Create specialists with templates
    specialist("researcher", "Researcher", "research")
    specialist("analyst", "Analyst", "analysis")
    specialist("validator", "Validator", "validation")

    // Specify LLM model
    specialist("writer", "Writer", "writing", model = "claude-3-5-sonnet-20241022")
}
```

**Advanced Member Configuration:**

```kotlin
members {
    // Add pre-configured agents
    agent(existingAgent1)
    agent(existingAgent2)

    // Create agents inline
    agent {
        name = "Custom Specialist"
        llm = anthropic(...) {
            model = "claude-3-5-haiku-20241022"
        }
        tools {
            tool("custom_tool") { /* ... */ }
        }
    }
}
```

**Swarm Configuration:**

```kotlin
config {
    debugEnabled = true
    maxConcurrentOperations = 10
    timeoutMs = 30000
    retryAttempts = 3
}
```

**Swarm Execution Strategies:**

Swarms automatically select execution strategies based on task content:

- **PARALLEL** - Execute all agents simultaneously (default)
- **SEQUENTIAL** - Execute in sequence, passing results forward
- **CONSENSUS** - Build consensus through discussion
- **COMPETITION** - Select best result from multiple agents
- **HIERARCHICAL** - Hierarchical delegation

```kotlin
val swarm = buildSwarmAgent {
    name = "Decision Team"

    quickSwarm {
        specialist("expert1", "Expert 1", "analysis")
        specialist("expert2", "Expert 2", "evaluation")
        specialist("expert3", "Expert 3", "recommendation")
    }
}

// Task containing "compare" triggers CONSENSUS strategy
val result = swarm.processComm(Comm(
    content = "Compare these three approaches and recommend the best one",
    from = "user"
))

// Task containing "step" triggers SEQUENTIAL strategy
val result2 = swarm.processComm(Comm(
    content = "Process this data step by step through validation, transformation, and storage",
    from = "user"
))
```

**Complete Swarm Example:**

```kotlin
val dataProcessingSwarm = buildSwarmAgent {
    name = "Data Processing Pipeline"
    description = "Multi-stage data processing with validation, transformation, and storage"

    swarmTools {
        // Validation tool
        tool("validate", "Validate data format") {
            parameter("data", "string", required = true)
            execute(fun(params: Map<String, Any>): String {
                val data = params["data"] as String
                return if (isValidJson(data)) "valid" else "invalid"
            })
        }

        // Transformation tool
        tool("transform", "Transform data") {
            parameter("data", "string", required = true)
            parameter("format", "string", required = true)
            execute(fun(params: Map<String, Any>): String {
                val data = params["data"] as String
                val format = params["format"] as String
                return transformData(data, format)
            })
        }

        // Storage tool
        tool("store", "Store processed data") {
            parameter("data", "string", required = true)
            execute(fun(params: Map<String, Any>): String {
                database.store(params["data"] as String)
                return "stored"
            })
        }
    }

    members {
        agent(buildAgent {
            name = "Validator"
            description = "Validates incoming data"
            llm = anthropic(...) { model = "claude-3-5-haiku-20241022" }
            instructions = "Validate all data using the validate tool"
        })

        agent(buildAgent {
            name = "Transformer"
            description = "Transforms data to target format"
            llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
            instructions = "Transform data using the transform tool"
        })

        agent(buildAgent {
            name = "Archiver"
            description = "Stores processed data"
            llm = anthropic(...) { model = "claude-3-5-haiku-20241022" }
            instructions = "Store data using the store tool"
        })
    }

    config {
        debugEnabled = true
        maxConcurrentOperations = 3
        timeoutMs = 60000
    }
}

// Process data through pipeline (automatic SEQUENTIAL strategy)
val result = dataProcessingSwarm.processComm(Comm(
    content = "Process this JSON data: {\"user\": \"Alice\", \"action\": \"login\"}",
    from = "data-source"
))
```

## Tool DSL

### tool() - Inline Tool Definition

Define tools within agent builders:

```kotlin
buildAgent {
    name = "Calculator"

    tools {
        // Simple tool with automatic validation
        tool("calculate", "Performs arithmetic operations") {
            parameter("a", "number", "First number", required = true)
            parameter("b", "number", "Second number", required = true)
            parameter("operation", "string", "Operation (+, -, *, /)", required = true)

            execute(fun(params: Map<String, Any>): String {
                val a = (params["a"] as Number).toDouble()
                val b = (params["b"] as Number).toDouble()
                val op = params["operation"] as String

                return when (op) {
                    "+" -> (a + b).toString()
                    "-" -> (a - b).toString()
                    "*" -> (a * b).toString()
                    "/" -> if (b != 0.0) (a / b).toString()
                           else throw ArithmeticException("Division by zero")
                    else -> throw IllegalArgumentException("Unknown operation")
                }
            })
        }

        // Tool with custom validation
        tool("create_user", "Creates a new user") {
            parameter("email", "string", "Email address", required = true)
            parameter("age", "number", "Age in years", required = true)

            canExecute { params ->
                val email = params["email"] as? String ?: return@canExecute false
                val age = (params["age"] as? Number)?.toInt() ?: return@canExecute false

                email.contains("@") && age in 18..120
            }

            execute(fun(params: Map<String, Any>): String {
                val email = params["email"] as String
                val age = (params["age"] as Number).toInt()

                val user = database.createUser(email, age)
                return "User created: ${user.id}"
            })
        }
    }
}
```

**Tool DSL Functions:**

- `parameter(name, type, description, required)` - Define parameter
- `execute(fun(params) -> result)` - Simple execution (automatic validation & error handling)
- `execute { params -> SpiceResult<ToolResult> }` - Advanced execution (full control)
- `canExecute { params -> Boolean }` - Custom validation logic
- `description(string)` - Set tool description

**Parameter Types:**

```kotlin
parameter("name", "string", "Description", required = true)
parameter("count", "number", "Description", required = true)
parameter("active", "boolean", "Description", required = false)
parameter("tags", "array", "Description", required = false)
parameter("metadata", "object", "Description", required = false)
```

### swarmTools { } - Shared Tools

Define tools accessible to all swarm members:

```kotlin
swarmTools {
    // Inline tool
    tool("shared_calculator", "Calculator for all agents") {
        parameter("expression", "string", required = true)
        execute(fun(params: Map<String, Any>): String {
            return evaluateExpression(params["expression"] as String)
        })
    }

    // Add existing tool
    tool(myExistingTool)

    // Add multiple tools
    tools(tool1, tool2, tool3)

    // Built-in coordination tools
    aiConsensus(scoringAgent = llm)
    conflictResolver()
    qualityAssessor(scoringAgent = llm)
    resultAggregator()
    strategyOptimizer()
}
```

**Built-in Coordination Tools:**

```kotlin
swarmTools {
    // AI-powered consensus building
    aiConsensus(scoringAgent = anthropic(...) {
        model = "claude-3-5-sonnet-20241022"
    })

    // Resolve conflicts between agent responses
    conflictResolver()

    // Quality assessment with AI scoring
    qualityAssessor(scoringAgent = anthropic(...) {
        model = "claude-3-5-haiku-20241022"
    })

    // Intelligent result aggregation
    resultAggregator()

    // Strategy optimization
    strategyOptimizer()
}
```

## Communication DSL

### comm { } - Communication Builder

Create communications with rich metadata:

```kotlin
val message = comm("Hello, agent!") {
    from("user-123")
    to("agent-456")
    type(CommType.TEXT)
    role(CommRole.USER)

    // Metadata
    data("session_id", "sess-789")
    data("user_name" to "Alice", "user_tier" to "premium")

    // Features
    mention("@agent1", "@agent2")
    priority(Priority.HIGH)
    encrypt()
    expires(60000) // 60 seconds TTL

    // Media
    image("photo.jpg", "https://example.com/photo.jpg", caption = "Profile photo")
    document("report.pdf", "https://example.com/report.pdf")
}
```

**DSL Functions:**

- `from(id)` - Set sender
- `to(id)` - Set recipient
- `type(CommType)` - Set message type
- `role(CommRole)` - Set role
- `data(key, value)` - Add metadata
- `data(vararg pairs)` - Add multiple metadata entries
- `mention(vararg ids)` - Add mentions
- `priority(Priority)` - Set priority
- `urgent()` - Mark as urgent
- `critical()` - Mark as critical
- `lowPriority()` - Mark as low priority
- `encrypt()` - Enable encryption
- `expires(ttlMs)` - Set TTL
- `image(filename, url, size, caption, metadata)` - Add image
- `document(filename, url, size, metadata)` - Add document
- `audio(filename, url, size, metadata)` - Add audio
- `video(filename, url, size, metadata)` - Add video

### quickComm() - Quick Communication

Create simple communications:

```kotlin
// Basic message
val message = quickComm(
    content = "Hello!",
    from = "user",
    to = "agent"
)

// With type and role
val message = quickComm(
    content = "Process this data",
    from = "user",
    to = "agent",
    type = CommType.TEXT,
    role = CommRole.USER
)
```

### systemComm() - System Message

Create system messages:

```kotlin
val systemMsg = systemComm(
    content = "Agent initialized successfully",
    to = "user"
)
// type = CommType.SYSTEM, role = CommRole.SYSTEM
```

### errorComm() - Error Message

Create error messages:

```kotlin
val errorMsg = errorComm(
    error = "Processing failed: Invalid input",
    to = "user"
)
// type = CommType.ERROR, role = CommRole.SYSTEM
```

## Configuration DSL

### LLM Configuration

Configure LLM providers with builder pattern:

```kotlin
// Anthropic
val llm = anthropic(apiKey = "...") {
    model = "claude-3-5-sonnet-20241022"
    temperature = 0.7
    maxTokens = 4096
    topP = 1.0
    topK = -1
}

// OpenAI
val llm = openai(apiKey = "...") {
    model = "gpt-4"
    temperature = 0.7
    maxTokens = 2048
    topP = 1.0
    frequencyPenalty = 0.0
    presencePenalty = 0.0
}
```

### Agent Configuration

Configure agent behavior:

```kotlin
val agent = buildAgent {
    name = "Configured Agent"

    config {
        maxConcurrentRequests = 10
        requestTimeoutMs = 30000

        retryPolicy {
            maxRetries = 3
            initialDelayMs = 1000
            maxDelayMs = 10000
            backoffMultiplier = 2.0
        }

        rateLimiting {
            maxRequestsPerMinute = 100
            maxBurstSize = 20
        }

        monitoring {
            enableMetrics = true
            enableTracing = true
            enableLogging = true
            logSlowRequests = true
            slowRequestThresholdMs = 5000
        }
    }
}
```

### Swarm Configuration

Configure swarm behavior:

```kotlin
val swarm = buildSwarmAgent {
    name = "Configured Swarm"

    config {
        debugEnabled = true
        maxConcurrentOperations = 10
        timeoutMs = 30000
        retryAttempts = 3
    }
}
```

## Patterns & Best Practices

### 1. Layered Agent Design

Build agents with clear separation of concerns:

```kotlin
// ✅ Good - Layered design
val agent = buildAgent {
    name = "Professional Assistant"

    // Core configuration
    llm = anthropic(...) {
        model = "claude-3-5-sonnet-20241022"
        temperature = 0.7
    }

    // Capabilities layer
    tools {
        tool("research") { /* ... */ }
        tool("analyze") { /* ... */ }
        tool("summarize") { /* ... */ }
    }

    // Behavior layer
    instructions = """
        You are a professional assistant.
        Use tools systematically and explain your reasoning.
    """.trimIndent()
}

// ❌ Bad - Mixed concerns
val agent = buildAgent {
    name = "Messy Agent"
    llm = anthropic(...) { /* ... */ }
    instructions = "Do stuff"  // Too vague
    tools { /* Too many tools, unclear purpose */ }
}
```

### 2. Reusable Tool Definitions

Extract common tools for reuse:

```kotlin
// ✅ Good - Reusable tool builder
fun createCalculatorTool() = tool("calculate", "Calculator") {
    parameter("a", "number", required = true)
    parameter("b", "number", required = true)
    parameter("op", "string", required = true)

    execute(fun(params: Map<String, Any>): String {
        val a = (params["a"] as Number).toDouble()
        val b = (params["b"] as Number).toDouble()
        val op = params["op"] as String

        return when (op) {
            "+" -> (a + b).toString()
            "-" -> (a - b).toString()
            "*" -> (a * b).toString()
            "/" -> (a / b).toString()
            else -> throw IllegalArgumentException("Unknown operation")
        }
    })
}

// Use in multiple agents
val agent1 = buildAgent {
    name = "Math Agent 1"
    tools { tool(createCalculatorTool()) }
}

val agent2 = buildAgent {
    name = "Math Agent 2"
    tools { tool(createCalculatorTool()) }
}
```

### 3. Swarm Tool Sharing

Use swarm tools for shared capabilities:

```kotlin
// ✅ Good - Shared tools at swarm level
val swarm = buildSwarmAgent {
    name = "Analysis Team"

    // Tools all agents need
    swarmTools {
        tool("database_query") { /* ... */ }
        tool("data_validator") { /* ... */ }
        tool("result_formatter") { /* ... */ }
    }

    // Agent-specific tools defined per agent
    members {
        agent(buildAgent {
            name = "SQL Specialist"
            tools {
                tool("optimize_query") { /* SQL-specific */ }
            }
        })

        agent(buildAgent {
            name = "Data Scientist"
            tools {
                tool("statistical_analysis") { /* Stats-specific */ }
            }
        })
    }
}

// ❌ Bad - Duplicating tools across agents
val swarm = buildSwarmAgent {
    members {
        agent(buildAgent {
            tools {
                tool("database_query") { /* duplicate */ }
                tool("data_validator") { /* duplicate */ }
            }
        })

        agent(buildAgent {
            tools {
                tool("database_query") { /* duplicate */ }
                tool("data_validator") { /* duplicate */ }
            }
        })
    }
}
```

### 4. Progressive Instructions

Build instructions progressively:

```kotlin
// ✅ Good - Clear, structured instructions
val agent = buildAgent {
    name = "Research Assistant"

    instructions = """
        # Role
        You are a professional research assistant specializing in academic research.

        # Capabilities
        - Web search using 'search' tool
        - Document analysis using 'analyze' tool
        - Citation generation using 'cite' tool

        # Workflow
        1. Understand the research question
        2. Search for relevant sources
        3. Analyze source credibility
        4. Synthesize findings
        5. Generate proper citations

        # Guidelines
        - Always cite sources with URLs
        - Present multiple perspectives on controversial topics
        - Indicate confidence levels
        - Ask clarifying questions for ambiguous requests

        # Examples
        Good: "Based on Smith et al. (2023), I found that..."
        Bad: "Some people say that..."
    """.trimIndent()
}

// ❌ Bad - Vague instructions
val agent = buildAgent {
    name = "Research Agent"
    instructions = "You help with research. Be helpful."
}
```

### 5. Type-Safe Communication

Use DSL for type-safe communication creation:

```kotlin
// ✅ Good - Type-safe with DSL
val message = comm("Analyze this data") {
    from("user-123")
    to("analyzer")
    type(CommType.TEXT)
    role(CommRole.USER)

    data(
        "dataset_id" to "ds-456",
        "analysis_type" to "statistical",
        "priority" to "high"
    )

    priority(Priority.HIGH)
}

// ❌ Bad - Manual construction (error-prone)
val message = Comm(
    content = "Analyze this data",
    from = "user-123",
    to = "analyzer",
    type = CommType.TEXT,  // Could forget this
    role = CommRole.USER,  // Could forget this
    data = mapOf(
        "dataset_id" to "ds-456",
        "analysis_type" to "statistical"
        // Forgot priority in data!
    ),
    priority = Priority.NORMAL  // Wrong priority!
)
```

## Real-World Examples

### Complete Research Agent

```kotlin
val researchAgent = buildAgent {
    name = "Academic Researcher"
    description = "AI assistant for academic research and literature review"
    capabilities = listOf(
        "literature-search",
        "citation-analysis",
        "paper-summarization"
    )

    llm = anthropic(apiKey = env["ANTHROPIC_API_KEY"]!!) {
        model = "claude-3-5-sonnet-20241022"
        temperature = 0.7
        maxTokens = 4096
    }

    tools {
        tool("search_papers", "Search academic papers") {
            parameter("query", "string", "Search query", required = true)
            parameter("year_from", "number", "Start year", required = false)
            parameter("year_to", "number", "End year", required = false)

            execute(fun(params: Map<String, Any>): String {
                val query = params["query"] as String
                val yearFrom = (params["year_from"] as? Number)?.toInt()
                val yearTo = (params["year_to"] as? Number)?.toInt()

                val papers = scholarApi.search(query, yearFrom, yearTo)
                return papers.joinToString("\n") { paper ->
                    "${paper.title} (${paper.year}) - ${paper.authors.joinToString(", ")}"
                }
            })
        }

        tool("get_paper_details", "Get full paper details") {
            parameter("paper_id", "string", "Paper ID", required = true)

            execute(fun(params: Map<String, Any>): String {
                val paperId = params["paper_id"] as String
                val paper = scholarApi.getPaper(paperId)

                return """
                    Title: ${paper.title}
                    Authors: ${paper.authors.joinToString(", ")}
                    Year: ${paper.year}
                    Citations: ${paper.citationCount}
                    Abstract: ${paper.abstract}
                    URL: ${paper.url}
                """.trimIndent()
            })
        }

        tool("analyze_citations", "Analyze citation network") {
            parameter("paper_id", "string", "Paper ID", required = true)
            parameter("depth", "number", "Citation depth", required = false)

            execute(fun(params: Map<String, Any>): String {
                val paperId = params["paper_id"] as String
                val depth = (params["depth"] as? Number)?.toInt() ?: 2

                val network = citationAnalyzer.analyze(paperId, depth)
                return network.summary()
            })
        }
    }

    instructions = """
        You are an expert academic research assistant helping with literature reviews and research synthesis.

        Your capabilities:
        - Search academic papers using 'search_papers'
        - Get detailed paper information using 'get_paper_details'
        - Analyze citation networks using 'analyze_citations'

        Research workflow:
        1. Understand the research question and scope
        2. Search for relevant papers (recent 5 years by default)
        3. Analyze key papers and their citations
        4. Identify research trends and gaps
        5. Synthesize findings with proper citations

        Best practices:
        - Always cite papers in APA format: (Author, Year)
        - Note citation counts as indicators of impact
        - Identify seminal papers in the field
        - Point out conflicting findings in the literature
        - Suggest areas for future research

        Example response:
        "Based on recent literature, Chen et al. (2023) demonstrated that...
        This finding was corroborated by Smith (2024) who showed...
        However, Johnson et al. (2023) found conflicting evidence suggesting..."
    """.trimIndent()
}

// Use the agent
val result = researchAgent.processComm(Comm(
    content = "Review recent advances in transformer architectures for NLP",
    from = "researcher"
))
```

### Complete Multi-Agent Swarm

```kotlin
val softwareDevSwarm = buildSwarmAgent {
    name = "Software Development Team"
    description = "Multi-agent team for software development tasks"

    swarmTools {
        // Code analysis tool
        tool("analyze_code", "Analyze code quality") {
            parameter("code", "string", "Code to analyze", required = true)
            parameter("language", "string", "Programming language", required = true)

            execute(fun(params: Map<String, Any>): String {
                val code = params["code"] as String
                val language = params["language"] as String

                val analysis = codeAnalyzer.analyze(code, language)
                return analysis.report()
            })
        }

        // Test generation tool
        tool("generate_tests", "Generate unit tests") {
            parameter("code", "string", "Code to test", required = true)
            parameter("framework", "string", "Test framework", required = false)

            execute(fun(params: Map<String, Any>): String {
                val code = params["code"] as String
                val framework = params["framework"] as? String ?: "junit"

                val tests = testGenerator.generate(code, framework)
                return tests
            })
        }

        // Documentation tool
        tool("generate_docs", "Generate documentation") {
            parameter("code", "string", "Code to document", required = true)
            parameter("format", "string", "Doc format", required = false)

            execute(fun(params: Map<String, Any>): String {
                val code = params["code"] as String
                val format = params["format"] as? String ?: "markdown"

                val docs = docGenerator.generate(code, format)
                return docs
            })
        }

        // Built-in coordination
        aiConsensus(scoringAgent = anthropic(...) {
            model = "claude-3-5-sonnet-20241022"
        })
    }

    members {
        // Backend developer
        agent(buildAgent {
            name = "Backend Developer"
            description = "Specializes in backend development"
            capabilities = listOf("api-design", "database-modeling", "backend-logic")

            llm = anthropic(...) {
                model = "claude-3-5-sonnet-20241022"
                temperature = 0.7
            }

            instructions = """
                You are a senior backend developer specializing in scalable API design.
                Focus on clean architecture, proper error handling, and performance.
            """.trimIndent()
        })

        // Frontend developer
        agent(buildAgent {
            name = "Frontend Developer"
            description = "Specializes in frontend development"
            capabilities = listOf("ui-design", "react", "accessibility")

            llm = anthropic(...) {
                model = "claude-3-5-sonnet-20241022"
                temperature = 0.7
            }

            instructions = """
                You are a senior frontend developer specializing in modern web applications.
                Focus on user experience, accessibility, and performance.
            """.trimIndent()
        })

        // QA engineer
        agent(buildAgent {
            name = "QA Engineer"
            description = "Quality assurance and testing"
            capabilities = listOf("test-design", "automation", "quality-review")

            llm = anthropic(...) {
                model = "claude-3-5-haiku-20241022"
                temperature = 0.3
            }

            instructions = """
                You are a QA engineer focused on comprehensive testing.
                Design test cases covering edge cases and error conditions.
            """.trimIndent()
        })

        // Tech lead
        agent(buildAgent {
            name = "Tech Lead"
            description = "Technical leadership and architecture"
            capabilities = listOf("architecture", "code-review", "mentoring")

            llm = anthropic(...) {
                model = "claude-3-5-sonnet-20241022"
                temperature = 0.5
            }

            instructions = """
                You are a technical lead responsible for architecture decisions.
                Review solutions for scalability, maintainability, and best practices.
            """.trimIndent()
        })
    }

    config {
        debugEnabled = true
        maxConcurrentOperations = 4
        timeoutMs = 120000
    }
}

// Use the swarm for code review
val result = softwareDevSwarm.processComm(Comm(
    content = """
        Review this code for a user authentication API:

        ```kotlin
        fun authenticateUser(username: String, password: String): User {
            val user = database.findByUsername(username)
            return if (user.password == password) user else throw AuthException()
        }
        ```

        Provide feedback on security, error handling, and best practices.
    """.trimIndent(),
    from = "developer"
))

// Swarm automatically coordinates reviews from all team members
```

## Next Steps

- [Agent API](./agent) - Learn about agent interface and implementations
- [Tool API](./tool) - Deep dive into tool creation
- [Comm API](./comm) - Master communication system
- [Swarm Documentation](../orchestration/swarm) - Multi-agent coordination
- [Creating Custom Tools](../tools-extensions/creating-tools) - Tool development guide
