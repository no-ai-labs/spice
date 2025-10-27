# Installation

Get Spice Framework up and running in your project.

## Requirements

- **Kotlin**: 1.9.0 or higher
- **JVM**: Java 11 or higher
- **Build Tool**: Gradle (recommended) or Maven

## Installation via JitPack

Spice Framework is distributed via JitPack, making it easy to integrate into your project.

[![](https://jitpack.io/v/no-ai-labs/spice-framework.svg)](https://jitpack.io/#no-ai-labs/spice-framework)

### Step 1: Add JitPack Repository

Add the JitPack repository to your build configuration:

#### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

#### Gradle (Groovy)

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositoriesMode = FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2: Add Dependencies

Add the Spice Framework dependencies to your project:

#### Core Module (Required)

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:Tag")
}
```

Replace `Tag` with the latest version from JitPack: [![](https://jitpack.io/v/no-ai-labs/spice-framework.svg)](https://jitpack.io/#no-ai-labs/spice-framework)

#### Optional Modules

Add additional modules based on your needs:

```kotlin
dependencies {
    // Core framework (required)
    implementation("com.github.no-ai-labs.spice-framework:spice-core:Tag")

    // Spring Boot integration (optional)
    implementation("com.github.no-ai-labs.spice-framework:spice-springboot:Tag")

    // Event sourcing module (optional)
    implementation("com.github.no-ai-labs.spice-framework:spice-eventsourcing:Tag")
}
```

## Verify Installation

Create a simple test file to verify your installation:

```kotlin
// src/main/kotlin/Test.kt
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val agent = buildAgent {
        id = "test-agent"
        name = "Test Agent"
        description = "Testing Spice installation"

        handle { comm ->
            comm.reply("Hello from Spice! 🌶️", id)
        }
    }

    val response = agent.processComm(
        Comm(content = "Hello", from = "user")
    )

    println(response.content) // Output: Hello from Spice! 🌶️
}
```

Run this file:

```bash
./gradlew run
```

If you see "Hello from Spice! 🌶️", congratulations! Spice is successfully installed.

## Module Overview

| Module | Description | When to Use |
|--------|-------------|-------------|
| **spice-core** | Core framework with agents, tools, DSL | Always required |
| **spice-springboot** | Spring Boot auto-configuration | When using Spring Boot |
| **spice-eventsourcing** | Event sourcing and CQRS support | For event-driven architectures |

## Next Steps

Now that you have Spice installed, let's create your first agent!

[Create Your First Agent →](./first-agent)

## Troubleshooting

### JitPack Build Issues

If JitPack shows a build error, wait a few minutes for the artifact to build. First-time builds can take 5-10 minutes.

### Kotlin Version Conflicts

Ensure your project uses Kotlin 1.9.0 or higher:

```kotlin
// build.gradle.kts
kotlin {
    jvmToolchain(11)
}
```

### Dependency Resolution

If you encounter dependency resolution issues, try:

```bash
./gradlew clean build --refresh-dependencies
```

## Getting Help

- **GitHub Issues**: [Report installation problems](https://github.com/no-ai-labs/spice/issues)
- **Discussions**: [Ask the community](https://github.com/no-ai-labs/spice/discussions)
