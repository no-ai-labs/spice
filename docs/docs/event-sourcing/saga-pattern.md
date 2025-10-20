# Saga Pattern

Distributed transactions with compensating actions.

## Define Saga

```kotlin
class OrderSaga(override val sagaId: String) : Saga() {
    override val sagaType = "OrderFulfillment"

    override suspend fun execute(context: SagaContext) {
        executeStep(ReserveInventory(), context)
        executeStep(ProcessPayment(), context)
        executeStep(CreateShipment(), context)
    }
}
```
