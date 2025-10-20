# Spring Boot Integration

Seamless Spring Boot integration with auto-configuration.

## Setup

```kotlin
@SpringBootApplication
@EnableSpice
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

## Features

- Auto-configuration
- Property-based setup
- Dependency injection
- Actuator support
