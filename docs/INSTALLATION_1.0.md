# 📦 Spice Framework 1.0.0 - Installation Guide

Complete installation guide for all Spice Framework modules.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Module Overview](#module-overview)
- [Gradle Installation](#gradle-installation)
- [Maven Installation](#maven-installation)
- [Module-Specific Setup](#module-specific-setup)
- [Configuration](#configuration)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required
- **Kotlin**: 2.2.0 or higher
- **JDK**: 17 or higher (LTS recommended)
- **Build Tool**: Gradle 8.0+ or Maven 3.8+

### Recommended
- **IDE**: IntelliJ IDEA 2024.1+ or Android Studio
- **Kotlin Plugin**: Latest version
- **Memory**: 4GB RAM minimum, 8GB recommended

### Optional (for specific modules)
- **Spring Boot**: 3.2+ (for spice-springboot modules)
- **Redis**: 6.0+ (for distributed checkpoints)
- **PostgreSQL**: 12+ (for event sourcing)
- **Kafka**: 3.0+ (for event bus integration)

---

## Module Overview

Spice Framework 1.0.0 consists of several modules:

| Module | Artifact ID | Description | Dependencies |
|--------|------------|-------------|--------------|
| **Core** | `spice-core` | Core framework (SpiceMessage, Graph, Agent, Tool) | None |
| **Agents** | `spice-agents` | Pre-built agents (OpenAI, Anthropic, etc.) | spice-core |
| **Spring Boot** | `spice-springboot` | Spring Boot integration | spice-core, Spring Boot 3.x |
| **Spring AI** | `spice-springboot-ai` | Spring AI integration | spice-springboot |
| **State Machine** | `spice-springboot-statemachine` | Checkpoint/resume with Spring State Machine | spice-springboot |

### Module Dependency Graph

```
spice-core (Required)
    ↑
    ├── spice-agents (Optional)
    └── spice-springboot (Optional)
            ↑
            ├── spice-springboot-ai (Optional)
            └── spice-springboot-statemachine (Optional)
```

---

## Gradle Installation

### Step 1: Add Repository

Add the Spice repository to your `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()

        // Spice Framework repository
        maven {
            url = uri("http://218.232.94.139:8081/repository/maven-releases/")
            credentials {
                username = project.findProperty("nexusUsername") as String?
                    ?: System.getenv("NEXUS_USERNAME")
                password = project.findProperty("nexusPassword") as String?
                    ?: System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}
```

### Step 2: Configure Credentials

Create or edit `gradle.properties` in your project root or `~/.gradle/gradle.properties`:

```properties
# Spice Nexus credentials
nexusUsername=admin
nexusPassword=your_password_here
```

**Alternative**: Use environment variables:

```bash
export NEXUS_USERNAME=admin
export NEXUS_PASSWORD=your_password_here
```

### Step 3: Add Dependencies

#### Minimal Setup (Core Only)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.2.0"
}

group = "com.example"
version = "1.0.0"

dependencies {
    // Spice Core
    implementation("io.github.noailabs:spice-core:1.0.0")

    // Kotlin Coroutines (Required)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Kotlin DateTime (Required)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

    // Kotlin Serialization (Required)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

kotlin {
    jvmToolchain(17)
}
```

#### Full Setup (All Modules)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

dependencies {
    // Spice Framework
    implementation("io.github.noailabs:spice-core:1.0.0")
    implementation("io.github.noailabs:spice-agents:1.0.0")
    implementation("io.github.noailabs:spice-springboot:1.0.0")
    implementation("io.github.noailabs:spice-springboot-ai:1.0.0")
    implementation("io.github.noailabs:spice-springboot-statemachine:1.0.0")

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
```

---

## Maven Installation

### Step 1: Add Repository

Add to your `pom.xml`:

```xml
<project>
    <!-- ... -->

    <repositories>
        <repository>
            <id>noai-nexus</id>
            <name>NoAI Labs Nexus Repository</name>
            <url>http://218.232.94.139:8081/repository/maven-releases/</url>
        </repository>
    </repositories>

    <servers>
        <server>
            <id>noai-nexus</id>
            <username>${env.NEXUS_USERNAME}</username>
            <password>${env.NEXUS_PASSWORD}</password>
        </server>
    </servers>
</project>
```

### Step 2: Configure Credentials

Add to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>noai-nexus</id>
            <username>admin</username>
            <password>your_password_here</password>
        </server>
    </servers>
</settings>
```

### Step 3: Add Dependencies

#### Minimal Setup

```xml
<dependencies>
    <!-- Spice Core -->
    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-core</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Kotlin -->
    <dependency>
        <groupId>org.jetbrains.kotlinx</groupId>
        <artifactId>kotlinx-coroutines-core</artifactId>
        <version>1.7.3</version>
    </dependency>

    <dependency>
        <groupId>org.jetbrains.kotlinx</groupId>
        <artifactId>kotlinx-datetime-jvm</artifactId>
        <version>0.4.1</version>
    </dependency>

    <dependency>
        <groupId>org.jetbrains.kotlinx</groupId>
        <artifactId>kotlinx-serialization-json</artifactId>
        <version>1.6.0</version>
    </dependency>
</dependencies>
```

#### Full Setup

```xml
<dependencies>
    <!-- Spice Framework -->
    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-core</artifactId>
        <version>1.0.0</version>
    </dependency>

    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-agents</artifactId>
        <version>1.0.0</version>
    </dependency>

    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-springboot</artifactId>
        <version>1.0.0</version>
    </dependency>

    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-springboot-ai</artifactId>
        <version>1.0.0</version>
    </dependency>

    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-springboot-statemachine</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Kotlin & Coroutines -->
    <dependency>
        <groupId>org.jetbrains.kotlinx</groupId>
        <artifactId>kotlinx-coroutines-core</artifactId>
        <version>1.7.3</version>
    </dependency>

    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.2.0</version>
    </dependency>
</dependencies>
```

---

## Module-Specific Setup

### spice-core

**No additional setup required.** This is the foundation module.

**Verify installation:**

```kotlin
import io.github.noailabs.spice.*

fun main() {
    val message = SpiceMessage.create("Test", "user")
    println("Spice Core installed: ${message.id}")
}
```

---

### spice-agents

Provides pre-built agents for popular LLM providers.

**Additional dependencies** (choose based on providers you need):

```kotlin
dependencies {
    // For OpenAI
    implementation("com.aallam.openai:openai-client:3.6.0")

    // For Anthropic
    implementation("com.anthropic:anthropic-sdk-kotlin:1.0.0")

    // For HTTP clients
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
}
```

**Usage example:**

```kotlin
import io.github.noailabs.spice.agents.*

val openAIAgent = OpenAIAgent(
    id = "gpt4",
    name = "GPT-4 Agent",
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4"
)
```

---

### spice-springboot

Spring Boot integration for Spice Framework.

**Prerequisites:**
- Spring Boot 3.2+
- Kotlin Spring Plugin

**Configuration** (`application.yml`):

```yaml
spice:
  enabled: true
  graph:
    registry:
      enabled: true
    execution:
      timeout-ms: 60000
      max-retries: 3
```

**Spring Boot Application:**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import io.github.noailabs.spice.*

@SpringBootApplication
class SpiceApplication {

    @Bean
    fun myAgent(): Agent {
        return object : Agent {
            override val id = "spring-agent"
            override val name = "Spring Agent"
            override val description = "Agent managed by Spring"
            override val capabilities = listOf("chat")

            override suspend fun processMessage(message: SpiceMessage) =
                SpiceResult.success(message.reply("Hello from Spring!", id))
        }
    }
}

fun main(args: Array<String>) {
    runApplication<SpiceApplication>(*args)
}
```

---

### spice-springboot-ai

Spring AI integration for Spice Framework.

**Prerequisites:**
- spice-springboot
- Spring AI

**Additional dependencies:**

```kotlin
dependencies {
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0")
}
```

**Configuration** (`application.yml`):

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7

spice:
  spring-ai:
    enabled: true
    default-provider: openai
```

---

### spice-springboot-statemachine

State machine integration with checkpoint/resume support.

**Prerequisites:**
- spice-springboot
- Redis (for distributed checkpoints) or in-memory storage

**Additional dependencies:**

```kotlin
dependencies {
    // For Redis checkpoints
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
}
```

**Configuration** (`application.yml`):

```yaml
spice:
  statemachine:
    enabled: true
    persistence:
      type: REDIS  # or IN_MEMORY
      redis:
        host: localhost
        port: 6379
        key-prefix: "spice:checkpoints:"
        ttl-seconds: 3600
    retry:
      enabled: true
      max-attempts: 3
      backoff-ms: 1000
    events:
      enabled: true
```

**Usage:**

```kotlin
import io.github.noailabs.spice.statemachine.*
import org.springframework.stereotype.Service

@Service
class WorkflowService(
    private val engine: StateMachineWorkflowEngine
) {
    suspend fun executeWorkflow(message: SpiceMessage): SpiceMessage {
        val result = engine.execute(myGraph, message)
        return result.getOrThrow()
    }

    suspend fun resumeWorkflow(checkpointId: String, response: SpiceMessage): SpiceMessage {
        val result = engine.resume(checkpointId, response)
        return result.getOrThrow()
    }
}
```

---

## Configuration

### Environment Variables

Common environment variables:

```bash
# LLM API Keys
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."

# Nexus Repository (for builds)
export NEXUS_USERNAME="admin"
export NEXUS_PASSWORD="your_password"

# Redis (for checkpoints)
export REDIS_HOST="localhost"
export REDIS_PORT="6379"

# Database (for event sourcing)
export DB_URL="jdbc:postgresql://localhost:5432/spice"
export DB_USERNAME="spice_user"
export DB_PASSWORD="secure_password"
```

### Application Configuration

**Minimal** (`application.yml`):

```yaml
spice:
  enabled: true
```

**Full Configuration** (`application.yml`):

```yaml
spice:
  enabled: true

  # Core settings
  core:
    debug: false
    log-level: INFO

  # Graph execution
  graph:
    registry:
      enabled: true
    execution:
      timeout-ms: 60000
      max-retries: 3
      parallel-execution: true

  # State machine
  statemachine:
    enabled: true
    persistence:
      type: REDIS
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
        database: 0
        key-prefix: "spice:checkpoints:"
        ttl-seconds: 3600
    retry:
      enabled: true
      max-attempts: 3
      backoff-ms: 1000
    events:
      enabled: true

  # Spring AI
  spring-ai:
    enabled: true
    default-provider: openai
    providers:
      openai:
        model: gpt-4
        temperature: 0.7
      anthropic:
        model: claude-3-opus-20240229

  # Observability
  observability:
    enabled: true
    tracing:
      enabled: true
      sample-rate: 1.0
    metrics:
      enabled: true

# Spring settings
spring:
  application:
    name: spice-app

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
```

---

## Verification

### Test Core Installation

Create `TestSpiceCore.kt`:

```kotlin
import io.github.noailabs.spice.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== Spice Core Verification ===")

    // Test 1: Create message
    val message = SpiceMessage.create("Hello, Spice!", "user")
    println("✓ Message created: ${message.id}")

    // Test 2: Message helpers
    val reply = message.reply("Response", "agent")
    println("✓ Reply created: ${reply.content}")

    // Test 3: Data operations
    val withData = message.withData(mapOf("test" to "value"))
    val value = withData.getData<String>("test")
    println("✓ Data operations: $value")

    // Test 4: State transitions
    val running = message.transitionTo(ExecutionState.RUNNING, "Test")
    println("✓ State transition: ${running.state}")

    println("\n✅ Spice Core verification complete!")
}
```

Run: `./gradlew run` or `mvn exec:java`

### Test Graph Execution

Create `TestGraphExecution.kt`:

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.graph.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== Spice Graph Verification ===")

    // Create simple agent
    val testAgent = object : Agent {
        override val id = "test"
        override val name = "Test Agent"
        override val description = "Test"
        override val capabilities = listOf("test")

        override suspend fun processMessage(message: SpiceMessage) =
            SpiceResult.success(message.reply("Processed: ${message.content}", id))
    }

    // Create graph
    val graph = graph("test-graph") {
        agent("process", testAgent)
        output("result") { it.content }
        edge("process", "result")
    }

    // Execute
    val runner = DefaultGraphRunner()
    val message = SpiceMessage.create("Test input", "user")
    val result = runner.execute(graph, message)

    when (result) {
        is SpiceResult.Success -> {
            println("✓ Graph executed successfully")
            println("✓ Result: ${result.value.content}")
        }
        is SpiceResult.Failure -> {
            println("✗ Graph execution failed: ${result.error.message}")
        }
    }

    println("\n✅ Graph verification complete!")
}
```

### Test Spring Boot Integration

Create `SpiceHealthController.kt`:

```kotlin
import org.springframework.web.bind.annotation.*
import io.github.noailabs.spice.*

@RestController
@RequestMapping("/health")
class SpiceHealthController(
    private val agents: List<Agent>
) {
    @GetMapping
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "spice_version" to "1.0.0",
            "agents_loaded" to agents.size,
            "agents" to agents.map { it.id }
        )
    }
}
```

Test: `curl http://localhost:8080/health`

Expected response:
```json
{
  "status": "UP",
  "spice_version": "1.0.0",
  "agents_loaded": 1,
  "agents": ["spring-agent"]
}
```

---

## Troubleshooting

### Issue: Repository Authentication Failed

**Error:**
```
Could not resolve io.github.noailabs:spice-core:1.0.0
401 Unauthorized
```

**Solution:**
1. Verify credentials in `gradle.properties` or `~/.m2/settings.xml`
2. Check environment variables: `echo $NEXUS_USERNAME`
3. Try accessing repository in browser: http://218.232.94.139:8081

---

### Issue: Kotlin Version Mismatch

**Error:**
```
Module was compiled with an incompatible version of Kotlin
```

**Solution:**
Update Kotlin to 2.2.0+:

```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
}
```

---

### Issue: Coroutines Not Found

**Error:**
```
Unresolved reference: runBlocking
```

**Solution:**
Add coroutines dependency:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

---

### Issue: Redis Connection Failed (State Machine)

**Error:**
```
Unable to connect to Redis at localhost:6379
```

**Solution:**
1. Start Redis: `docker run -p 6379:6379 redis:latest`
2. Or use in-memory checkpoints:

```yaml
spice:
  statemachine:
    persistence:
      type: IN_MEMORY
```

---

### Issue: Spring Boot Autoconfiguration Failed

**Error:**
```
No qualifying bean of type 'Agent'
```

**Solution:**
Ensure `@Bean` or `@Component` annotations on your agents:

```kotlin
@Component
class MyAgent : Agent {
    // ...
}
```

---

## Next Steps

Now that installation is complete:

1. **[Quick Start Guide](QUICK_START_1.0.md)** - Build your first agent
2. **[Architecture Overview](ARCHITECTURE_1.0.md)** - Understand the design
3. **[Migration Guide](MIGRATION_0.x_TO_1.0.md)** - Upgrade from 0.x
4. **[API Reference](wiki/api-reference.md)** - Detailed API documentation

---

## Getting Help

- **Documentation**: [GitHub Wiki](https://github.com/no-ai-labs/spice/wiki)
- **Issues**: [Report problems](https://github.com/no-ai-labs/spice/issues)
- **Discussions**: [Ask questions](https://github.com/no-ai-labs/spice/discussions)

---

**Installation complete! Happy coding! 🌶️**
