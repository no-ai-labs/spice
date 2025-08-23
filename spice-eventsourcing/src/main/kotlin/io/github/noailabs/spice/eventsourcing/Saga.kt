package io.github.noailabs.spice.eventsourcing

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for implementing the Saga pattern
 * 
 * Sagas manage distributed transactions across multiple aggregates/services
 * using compensating actions for rollback
 */
abstract class Saga {
    
    protected val logger = LoggerFactory.getLogger(this::class.java)
    
    /**
     * Unique identifier for this saga instance
     */
    abstract val sagaId: String
    
    /**
     * Type of this saga
     */
    abstract val sagaType: String
    
    /**
     * Current state of the saga
     */
    var state: SagaState = SagaState.NOT_STARTED
        protected set
    
    /**
     * Steps that have been executed
     */
    protected val executedSteps = mutableListOf<SagaStep>()
    
    /**
     * Error that caused the saga to fail
     */
    var error: Throwable? = null
        protected set
    
    /**
     * Start the saga
     */
    suspend fun start(context: SagaContext) {
        if (state != SagaState.NOT_STARTED) {
            throw IllegalStateException("Saga already started")
        }
        
        state = SagaState.RUNNING
        
        try {
            execute(context)
            state = SagaState.COMPLETED
        } catch (e: Exception) {
            logger.error("Saga $sagaId failed: ${e.message}", e)
            error = e
            state = SagaState.COMPENSATING
            
            try {
                compensate(context)
                state = SagaState.COMPENSATED
            } catch (ce: Exception) {
                logger.error("Saga $sagaId compensation failed: ${ce.message}", ce)
                state = SagaState.COMPENSATION_FAILED
                throw SagaCompensationException("Compensation failed", ce)
            }
            
            throw e
        }
    }
    
    /**
     * Execute the saga steps
     */
    protected abstract suspend fun execute(context: SagaContext)
    
    /**
     * Compensate for executed steps in reverse order
     */
    protected open suspend fun compensate(context: SagaContext) {
        executedSteps.asReversed().forEach { step ->
            try {
                logger.info("Compensating step: ${step.name}")
                step.compensate(context)
            } catch (e: Exception) {
                logger.error("Failed to compensate step ${step.name}: ${e.message}", e)
                throw e
            }
        }
    }
    
    /**
     * Execute a saga step
     */
    protected suspend fun executeStep(
        step: SagaStep,
        context: SagaContext
    ) {
        logger.info("Executing step: ${step.name}")
        step.execute(context)
        executedSteps.add(step)
    }
}

/**
 * State of a saga
 */
enum class SagaState {
    NOT_STARTED,
    RUNNING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    COMPENSATION_FAILED
}

/**
 * Context passed through saga execution
 */
class SagaContext {
    private val data = ConcurrentHashMap<String, Any>()
    
    fun <T> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? T
    }
    
    fun set(key: String, value: Any) {
        data[key] = value
    }
    
    fun remove(key: String) {
        data.remove(key)
    }
    
    fun getAll(): Map<String, Any> = data.toMap()
}

/**
 * A step in a saga
 */
interface SagaStep {
    val name: String
    
    /**
     * Execute the step
     */
    suspend fun execute(context: SagaContext)
    
    /**
     * Compensate for this step
     */
    suspend fun compensate(context: SagaContext)
}

/**
 * Simple implementation of SagaStep
 */
class SimpleSagaStep(
    override val name: String,
    private val executeAction: suspend (SagaContext) -> Unit,
    private val compensateAction: suspend (SagaContext) -> Unit
) : SagaStep {
    
    override suspend fun execute(context: SagaContext) {
        executeAction(context)
    }
    
    override suspend fun compensate(context: SagaContext) {
        compensateAction(context)
    }
}

/**
 * Saga manager that coordinates saga execution
 */
class SagaManager(
    private val eventStore: EventStore,
    private val sagaStore: SagaStore
) {
    
    private val logger = LoggerFactory.getLogger(SagaManager::class.java)
    private val runningScopes = ConcurrentHashMap<String, CoroutineScope>()
    
    /**
     * Start a new saga
     */
    suspend fun <T : Saga> startSaga(
        saga: T,
        context: SagaContext
    ): T {
        // Save initial saga state
        sagaStore.save(saga)
        
        // Create event
        val event = SagaStartedEvent(
            sagaId = saga.sagaId,
            sagaType = saga.sagaType,
            context = context.getAll()
        )
        
        eventStore.append(
            streamId = "saga-${saga.sagaId}",
            events = listOf(event),
            expectedVersion = -1
        )
        
        // Execute saga
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        runningScopes[saga.sagaId] = scope
        
        scope.launch {
            try {
                saga.start(context)
                
                // Save completed state
                sagaStore.save(saga)
                
                // Create completion event
                val completionEvent = SagaCompletedEvent(
                    sagaId = saga.sagaId,
                    sagaType = saga.sagaType
                )
                
                eventStore.append(
                    streamId = "saga-${saga.sagaId}",
                    events = listOf(completionEvent),
                    expectedVersion = 0
                )
                
            } catch (e: Exception) {
                // Save failed state
                sagaStore.save(saga)
                
                // Create failure event
                val failureEvent = SagaFailedEvent(
                    sagaId = saga.sagaId,
                    sagaType = saga.sagaType,
                    error = e.message ?: "Unknown error",
                    compensated = saga.state == SagaState.COMPENSATED
                )
                
                eventStore.append(
                    streamId = "saga-${saga.sagaId}",
                    events = listOf(failureEvent),
                    expectedVersion = 0
                )
            } finally {
                runningScopes.remove(saga.sagaId)
            }
        }
        
        return saga
    }
    
    /**
     * Get saga status
     */
    suspend fun getSagaStatus(sagaId: String): SagaState? {
        return sagaStore.load(sagaId)?.state
    }
    
    /**
     * Cancel a running saga
     */
    fun cancelSaga(sagaId: String) {
        runningScopes[sagaId]?.cancel()
        runningScopes.remove(sagaId)
    }
}

/**
 * Store for saga state
 */
interface SagaStore {
    suspend fun save(saga: Saga)
    suspend fun load(sagaId: String): Saga?
    suspend fun delete(sagaId: String)
}

/**
 * In-memory saga store
 */
class InMemorySagaStore : SagaStore {
    private val sagas = ConcurrentHashMap<String, Saga>()
    
    override suspend fun save(saga: Saga) {
        sagas[saga.sagaId] = saga
    }
    
    override suspend fun load(sagaId: String): Saga? {
        return sagas[sagaId]
    }
    
    override suspend fun delete(sagaId: String) {
        sagas.remove(sagaId)
    }
}

// Saga events
data class SagaStartedEvent(
    val sagaId: String,
    val sagaType: String,
    val context: Map<String, Any>,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val eventType: String = "SagaStarted",
    override val streamId: String = "saga-$sagaId",
    override val version: Long = -1,
    override val timestamp: Instant = Instant.now(),
    override val metadata: EventMetadata = EventMetadata(userId = "system", correlationId = sagaId)
) : Event {
    override fun toProto(): ByteArray = eventType.toByteArray()
}

data class SagaCompletedEvent(
    val sagaId: String,
    val sagaType: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val eventType: String = "SagaCompleted",
    override val streamId: String = "saga-$sagaId",
    override val version: Long = -1,
    override val timestamp: Instant = Instant.now(),
    override val metadata: EventMetadata = EventMetadata(userId = "system", correlationId = sagaId)
) : Event {
    override fun toProto(): ByteArray = eventType.toByteArray()
}

data class SagaFailedEvent(
    val sagaId: String,
    val sagaType: String,
    val error: String,
    val compensated: Boolean,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val eventType: String = "SagaFailed",
    override val streamId: String = "saga-$sagaId",
    override val version: Long = -1,
    override val timestamp: Instant = Instant.now(),
    override val metadata: EventMetadata = EventMetadata(userId = "system", correlationId = sagaId)
) : Event {
    override fun toProto(): ByteArray = eventType.toByteArray()
}

/**
 * Exception thrown when saga compensation fails
 */
class SagaCompensationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Example: Order fulfillment saga
 */
class OrderFulfillmentSaga(
    override val sagaId: String,
    private val orderId: String
) : Saga() {
    
    override val sagaType = "OrderFulfillment"
    
    override suspend fun execute(context: SagaContext) {
        // Step 1: Reserve inventory
        executeStep(ReserveInventoryStep(orderId), context)
        
        // Step 2: Process payment
        executeStep(ProcessPaymentStep(orderId), context)
        
        // Step 3: Create shipment
        executeStep(CreateShipmentStep(orderId), context)
        
        // Step 4: Send confirmation
        executeStep(SendConfirmationStep(orderId), context)
    }
}

// Example saga steps
class ReserveInventoryStep(private val orderId: String) : SagaStep {
    override val name = "ReserveInventory"
    
    override suspend fun execute(context: SagaContext) {
        // Reserve inventory logic
        logger.info("Reserving inventory for order $orderId")
        // Store reservation ID in context for compensation
        context.set("reservationId", "RES-${java.util.UUID.randomUUID()}")
    }
    
    override suspend fun compensate(context: SagaContext) {
        val reservationId = context.get<String>("reservationId")
        logger.info("Releasing inventory reservation $reservationId")
        // Release inventory logic
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(ReserveInventoryStep::class.java)
    }
}

class ProcessPaymentStep(private val orderId: String) : SagaStep {
    override val name = "ProcessPayment"
    
    override suspend fun execute(context: SagaContext) {
        logger.info("Processing payment for order $orderId")
        // Process payment logic
        context.set("paymentId", "PAY-${java.util.UUID.randomUUID()}")
    }
    
    override suspend fun compensate(context: SagaContext) {
        val paymentId = context.get<String>("paymentId")
        logger.info("Refunding payment $paymentId")
        // Refund logic
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(ProcessPaymentStep::class.java)
    }
}

class CreateShipmentStep(private val orderId: String) : SagaStep {
    override val name = "CreateShipment"
    
    override suspend fun execute(context: SagaContext) {
        logger.info("Creating shipment for order $orderId")
        // Create shipment logic
        context.set("shipmentId", "SHIP-${java.util.UUID.randomUUID()}")
    }
    
    override suspend fun compensate(context: SagaContext) {
        val shipmentId = context.get<String>("shipmentId")
        logger.info("Cancelling shipment $shipmentId")
        // Cancel shipment logic
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(CreateShipmentStep::class.java)
    }
}

class SendConfirmationStep(private val orderId: String) : SagaStep {
    override val name = "SendConfirmation"
    
    override suspend fun execute(context: SagaContext) {
        logger.info("Sending confirmation for order $orderId")
        // Send confirmation logic
    }
    
    override suspend fun compensate(context: SagaContext) {
        logger.info("Sending cancellation notification for order $orderId")
        // Send cancellation notification
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(SendConfirmationStep::class.java)
    }
}