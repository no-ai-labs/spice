# Getting Started with Event Sourcing

Build event-sourced applications.

## Create Aggregate

```kotlin
class OrderAggregate(override val aggregateId: String) : Aggregate() {
    override val aggregateType = "Order"

    var items: List<OrderItem> = emptyList()

    suspend fun create(items: List<OrderItem>) {
        raiseEvent(OrderCreatedEvent(aggregateId, items))
    }

    override fun apply(event: DomainEvent) {
        when (event) {
            is OrderCreatedEvent -> this.items = event.items
        }
    }
}
```
