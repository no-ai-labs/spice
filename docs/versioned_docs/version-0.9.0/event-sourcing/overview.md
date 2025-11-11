# Event Sourcing

Complete event sourcing module with Kafka integration.

## Features

- Event Store (Kafka + PostgreSQL)
- Aggregate Root pattern
- Saga pattern
- Memento pattern
- Projections

## Quick Start

```kotlin
val eventStore = EventStoreFactory.kafkaWithPostgres(
    kafkaCommHub = kafkaHub,
    dataSource = dataSource
)
```
