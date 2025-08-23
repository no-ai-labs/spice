package io.github.noailabs.spice.eventsourcing

import java.io.Serializable
import java.time.Instant

/**
 * Memento implementation for OrderAggregate
 * 
 * This demonstrates a concrete implementation of the Memento pattern
 * for a specific aggregate type
 */
data class OrderMemento(
    override val aggregateId: String,
    override val version: Long,
    override val timestamp: Instant,
    val customerId: String?,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val totalAmount: Money?
) : AggregateMemento, Serializable {
    
    override val aggregateType: String = "Order"
    
    override fun restoreState(aggregate: Aggregate) {
        require(aggregate is OrderAggregateWithMemento) {
            "Can only restore OrderMemento to OrderAggregate"
        }
        
        aggregate.restoreFromOrderMemento(this)
    }
}

/**
 * Enhanced OrderAggregate with Memento support
 */
class OrderAggregateWithMemento(
    override val aggregateId: String
) : MementoAggregate() {
    
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
                // Handle unknown events
                println("Warning: Unknown event type ${event.eventType} for Order aggregate")
            }
        }
    }
    
    // Memento implementation
    override fun doCreateMemento(): AggregateMemento {
        return OrderMemento(
            aggregateId = aggregateId,
            version = version,
            timestamp = Instant.now(),
            customerId = customerId,
            items = items.toList(), // Defensive copy
            status = status,
            totalAmount = totalAmount
        )
    }
    
    override fun doRestoreFromMemento(memento: AggregateMemento) {
        require(memento is OrderMemento) {
            "Expected OrderMemento but got ${memento::class.java.name}"
        }
        restoreFromOrderMemento(memento)
    }
    
    internal fun restoreFromOrderMemento(memento: OrderMemento) {
        this.customerId = memento.customerId
        this.items = memento.items.toMutableList() // Defensive copy
        this.status = memento.status
        this.totalAmount = memento.totalAmount
        // version is restored by parent class
    }
    
    private fun calculateTotal(): Money {
        val total = items.sumOf { it.quantity * it.price.amount }
        return Money(total, items.first().price.currency)
    }
}

/**
 * Example: Policy aggregate with memento
 */
data class PolicyMemento(
    override val aggregateId: String,
    override val version: Long,
    override val timestamp: Instant,
    val tenantId: String,
    val policyName: String,
    val rules: List<PolicyRule>,
    val status: PolicyStatus,
    val effectiveDate: Instant?,
    val expiryDate: Instant?
) : AggregateMemento, Serializable {
    
    override val aggregateType: String = "Policy"
    
    override fun restoreState(aggregate: Aggregate) {
        require(aggregate is PolicyAggregateWithMemento) {
            "Can only restore PolicyMemento to PolicyAggregate"
        }
        
        aggregate.restoreFromPolicyMemento(this)
    }
}

/**
 * Policy aggregate demonstrating memento pattern
 */
class PolicyAggregateWithMemento(
    override val aggregateId: String
) : MementoAggregate() {
    
    override val aggregateType = "Policy"
    
    // State
    var tenantId: String? = null
        private set
    var policyName: String? = null
        private set
    var rules: MutableList<PolicyRule> = mutableListOf()
        private set
    var status: PolicyStatus = PolicyStatus.DRAFT
        private set
    var effectiveDate: Instant? = null
        private set
    var expiryDate: Instant? = null
        private set
    
    // Event handling
    override fun apply(event: DomainEvent) {
        when (event) {
            is PolicyCreatedEvent -> {
                tenantId = event.tenantId
                policyName = event.policyName
                status = PolicyStatus.DRAFT
            }
            is PolicyRuleAddedEvent -> {
                rules.add(event.rule)
            }
            is PolicyActivatedEvent -> {
                status = PolicyStatus.ACTIVE
                effectiveDate = event.effectiveDate
                expiryDate = event.expiryDate
            }
            else -> {
                println("Warning: Unknown event type ${event.eventType} for Policy aggregate")
            }
        }
    }
    
    // Memento implementation
    override fun doCreateMemento(): AggregateMemento {
        return PolicyMemento(
            aggregateId = aggregateId,
            version = version,
            timestamp = Instant.now(),
            tenantId = tenantId ?: "",
            policyName = policyName ?: "",
            rules = rules.toList(),
            status = status,
            effectiveDate = effectiveDate,
            expiryDate = expiryDate
        )
    }
    
    override fun doRestoreFromMemento(memento: AggregateMemento) {
        require(memento is PolicyMemento) {
            "Expected PolicyMemento but got ${memento::class.java.name}"
        }
        restoreFromPolicyMemento(memento)
    }
    
    internal fun restoreFromPolicyMemento(memento: PolicyMemento) {
        this.tenantId = memento.tenantId
        this.policyName = memento.policyName
        this.rules = memento.rules.toMutableList()
        this.status = memento.status
        this.effectiveDate = memento.effectiveDate
        this.expiryDate = memento.expiryDate
    }
}

// Domain types used in mementos
data class PolicyRule(
    val id: String,
    val condition: String,
    val action: String,
    val priority: Int
) : Serializable

enum class PolicyStatus : Serializable {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    EXPIRED
}

// Policy events
class PolicyCreatedEvent(
    policyId: String,
    val tenantId: String,
    val policyName: String,
    timestamp: Instant = Instant.now(),
    createdBy: String = "system"
) : EntityCreatedEvent(
    aggregateId = policyId,
    aggregateType = "Policy",
    createdBy = createdBy,
    timestamp = timestamp
)

class PolicyRuleAddedEvent(
    policyId: String,
    val rule: PolicyRule,
    timestamp: Instant = Instant.now()
) : BaseDomainEvent(
    aggregateId = policyId,
    aggregateType = "Policy",
    eventType = "PolicyRuleAdded",
    timestamp = timestamp
) {
    override fun toProto(): ByteArray = "$eventType:$aggregateId:${rule.id}".toByteArray()
}

class PolicyActivatedEvent(
    policyId: String,
    val effectiveDate: Instant,
    val expiryDate: Instant?,
    timestamp: Instant = Instant.now()
) : StateTransitionEvent(
    aggregateId = policyId,
    aggregateType = "Policy",
    fromState = "DRAFT",
    toState = "ACTIVE",
    triggeredBy = "system",
    timestamp = timestamp
)