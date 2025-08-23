package io.github.noailabs.spice.eventsourcing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Base class for event-sourced aggregates
 * 
 * Aggregates are the core building blocks of domain-driven design.
 * They ensure consistency boundaries and business invariants.
 */
abstract class Aggregate {
    
    /**
     * Unique identifier for this aggregate instance
     */
    abstract val aggregateId: String
    
    /**
     * Type of this aggregate (e.g., "Order", "Policy")
     */
    abstract val aggregateType: String
    
    /**
     * Current version of the aggregate
     */
    var version: Long = -1
        protected set
    
    /**
     * Uncommitted events that haven't been persisted yet
     */
    private val uncommittedEvents = mutableListOf<DomainEvent>()
    
    /**
     * Mutex for thread-safe operations
     */
    private val mutex = Mutex()
    
    /**
     * Apply an event to update the aggregate state
     */
    protected abstract fun apply(event: DomainEvent)
    
    /**
     * Validate that an event can be applied in the current state
     */
    protected open fun validate(event: DomainEvent) {
        // Default: no validation
    }
    
    /**
     * Raise a new event
     */
    protected suspend fun raiseEvent(event: DomainEvent) = mutex.withLock {
        validate(event)
        apply(event)
        uncommittedEvents.add(event)
    }
    
    /**
     * Get and clear uncommitted events
     */
    suspend fun getUncommittedEvents(): List<DomainEvent> = mutex.withLock {
        val events = uncommittedEvents.toList()
        uncommittedEvents.clear()
        events
    }
    
    /**
     * Mark events as committed and update version
     */
    suspend fun markEventsAsCommitted(newVersion: Long) = mutex.withLock {
        version = newVersion
        uncommittedEvents.clear()
    }
    
    /**
     * Load aggregate from historical events
     */
    suspend fun loadFromHistory(events: List<DomainEvent>) = mutex.withLock {
        uncommittedEvents.clear()
        events.forEach { event ->
            apply(event)
            version = event.version
        }
    }
    
    /**
     * Get the stream ID for this aggregate
     */
    fun getStreamId(): String = "$aggregateType-$aggregateId"
}

/**
 * Repository for loading and saving aggregates
 */
class AggregateRepository(
    private val eventStore: EventStore
) {
    
    /**
     * Load an aggregate from the event store
     */
    suspend fun <T : Aggregate> load(
        aggregateId: String,
        aggregateFactory: () -> T
    ): T {
        val aggregate = aggregateFactory()
        val streamId = aggregate.getStreamId()
        
        // Load events from store
        val events = eventStore.readStream(streamId, 0)
            .filterIsInstance<DomainEvent>()
        
        // Rebuild aggregate state
        aggregate.loadFromHistory(events)
        
        return aggregate
    }
    
    /**
     * Save an aggregate's uncommitted events
     */
    suspend fun save(aggregate: Aggregate) {
        val events = aggregate.getUncommittedEvents()
        if (events.isEmpty()) return
        
        val streamId = aggregate.getStreamId()
        val newVersion = eventStore.append(streamId, events, aggregate.version)
        
        aggregate.markEventsAsCommitted(newVersion)
    }
    
    /**
     * Load aggregate at a specific version
     */
    suspend fun <T : Aggregate> loadAtVersion(
        aggregateId: String,
        version: Long,
        aggregateFactory: () -> T
    ): T {
        val aggregate = aggregateFactory()
        val streamId = aggregate.getStreamId()
        
        // Load events up to specified version
        val events = eventStore.readStream(streamId, 0, version)
            .filterIsInstance<DomainEvent>()
        
        // Rebuild aggregate state
        aggregate.loadFromHistory(events)
        
        return aggregate
    }
    
    /**
     * Check if an aggregate exists
     */
    suspend fun exists(aggregateType: String, aggregateId: String): Boolean {
        val streamId = "$aggregateType-$aggregateId"
        return eventStore.getStreamVersion(streamId) >= 0
    }
}

/**
 * Example: Order aggregate using DomainEvents
 */
class OrderAggregate(
    override val aggregateId: String
) : Aggregate() {
    
    override val aggregateType = "Order"
    
    // State
    var customerId: String? = null
        private set
    var status: OrderStatus = OrderStatus.DRAFT
        private set
    var items: MutableList<OrderItem> = mutableListOf()
        private set
    var totalAmount: Money? = null
        private set
    
    // Business operations
    
    suspend fun create(customerId: String) {
        if (this.customerId != null) {
            throw IllegalStateException("Order already created")
        }
        
        raiseEvent(OrderCreatedEvent(
            orderId = aggregateId,
            customerId = customerId,
            timestamp = Instant.now()
        ))
    }
    
    suspend fun addItem(productId: String, quantity: Int, price: Money) {
        if (status != OrderStatus.DRAFT) {
            throw IllegalStateException("Cannot add items to $status order")
        }
        
        raiseEvent(OrderItemAddedEvent(
            orderId = aggregateId,
            productId = productId,
            quantity = quantity,
            price = price,
            timestamp = Instant.now()
        ))
    }
    
    suspend fun submit() {
        if (status != OrderStatus.DRAFT) {
            throw IllegalStateException("Cannot submit $status order")
        }
        
        if (items.isEmpty()) {
            throw IllegalStateException("Cannot submit empty order")
        }
        
        raiseEvent(OrderSubmittedEvent(
            orderId = aggregateId,
            totalAmount = calculateTotal(),
            timestamp = Instant.now()
        ))
    }
    
    suspend fun cancel(reason: String) {
        if (status in listOf(OrderStatus.CANCELLED, OrderStatus.DELIVERED)) {
            throw IllegalStateException("Cannot cancel $status order")
        }
        
        raiseEvent(OrderCancelledEvent(
            orderId = aggregateId,
            reason = reason,
            timestamp = Instant.now()
        ))
    }
    
    // Event handling
    
    override fun apply(event: DomainEvent) {
        when (event) {
            is OrderCreatedEvent -> {
                customerId = event.customerId
                status = OrderStatus.DRAFT
            }
            is OrderItemAddedEvent -> {
                items.add(OrderItem(
                    productId = event.productId,
                    quantity = event.quantity,
                    price = event.price
                ))
            }
            is OrderSubmittedEvent -> {
                status = OrderStatus.SUBMITTED
                totalAmount = event.totalAmount
            }
            is OrderCancelledEvent -> {
                status = OrderStatus.CANCELLED
            }
            else -> {
                // Handle unknown events - this could be events from newer versions
                // Log warning but don't fail
                println("Warning: Unknown event type ${event.eventType} for Order aggregate")
            }
        }
    }
    
    private fun calculateTotal(): Money {
        val total = items.sumOf { it.quantity * it.price.amount }
        return Money(total, items.first().price.currency)
    }
}

// Domain events for Order aggregate
class OrderCreatedEvent(
    orderId: String,
    val customerId: String,
    timestamp: Instant = Instant.now(),
    createdBy: String = "system"
) : EntityCreatedEvent(
    aggregateId = orderId,
    aggregateType = "Order",
    createdBy = createdBy,
    timestamp = timestamp
) {
    val orderId: String get() = aggregateId
}

class OrderItemAddedEvent(
    orderId: String,
    val productId: String,
    val quantity: Int,
    val price: Money,
    timestamp: Instant = Instant.now()
) : BaseDomainEvent(
    aggregateId = orderId,
    aggregateType = "Order",
    eventType = "OrderItemAdded",
    timestamp = timestamp
) {
    val orderId: String get() = aggregateId
    
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$orderId:$productId:$quantity".toByteArray()
    }
}

class OrderSubmittedEvent(
    orderId: String,
    val totalAmount: Money,
    timestamp: Instant = Instant.now()
) : StateTransitionEvent(
    aggregateId = orderId,
    aggregateType = "Order",
    fromState = "DRAFT",
    toState = "SUBMITTED",
    triggeredBy = "system",
    timestamp = timestamp
) {
    val orderId: String get() = aggregateId
    
    override val eventType: String = "OrderSubmitted"
    
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$orderId:${totalAmount.amount}".toByteArray()
    }
}

class OrderCancelledEvent(
    orderId: String,
    val reason: String,
    timestamp: Instant = Instant.now(),
    cancelledBy: String = "system"
) : StateTransitionEvent(
    aggregateId = orderId,
    aggregateType = "Order",
    fromState = "*", // Can be cancelled from any state
    toState = "CANCELLED",
    triggeredBy = cancelledBy,
    timestamp = timestamp
) {
    val orderId: String get() = aggregateId
    
    override val eventType: String = "OrderCancelled"
    
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$orderId:$reason".toByteArray()
    }
}

// Value objects
data class OrderItem(
    val productId: String,
    val quantity: Int,
    val price: Money
)

data class Money(
    val amount: Long,
    val currency: String
)

enum class OrderStatus {
    DRAFT,
    SUBMITTED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}