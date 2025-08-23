package io.github.noailabs.spice.eventsourcing

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Base interface for projections
 * 
 * Projections build read models from events for efficient querying
 */
interface Projection {
    /**
     * Unique identifier for this projection
     */
    val projectionId: String
    
    /**
     * Event types this projection is interested in
     */
    val subscribedEventTypes: Set<String>
    
    /**
     * Handle an event and update the projection
     */
    suspend fun handle(event: Event)
    
    /**
     * Get the current position of this projection
     */
    suspend fun getPosition(): ProjectionPosition
    
    /**
     * Save the current position
     */
    suspend fun savePosition(position: ProjectionPosition)
    
    /**
     * Reset the projection to rebuild from beginning
     */
    suspend fun reset()
}

/**
 * Position tracking for projections
 */
data class ProjectionPosition(
    val streamId: String,
    val version: Long,
    val timestamp: Instant
)

/**
 * Base class for projections with common functionality
 */
abstract class BaseProjection(
    override val projectionId: String,
    override val subscribedEventTypes: Set<String>
) : Projection {
    
    protected val logger = LoggerFactory.getLogger(this::class.java)
    
    override suspend fun handle(event: Event) {
        try {
            if (event.eventType in subscribedEventTypes) {
                handleEvent(event)
                savePosition(ProjectionPosition(
                    streamId = event.streamId,
                    version = event.version,
                    timestamp = event.timestamp
                ))
            }
        } catch (e: Exception) {
            logger.error("Error handling event ${event.eventId}: ${e.message}", e)
            throw ProjectionException("Failed to handle event", e)
        }
    }
    
    protected abstract suspend fun handleEvent(event: Event)
}

/**
 * Projection manager that coordinates multiple projections
 */
class ProjectionManager(
    private val eventStore: EventStore,
    private val positionStore: ProjectionPositionStore
) {
    
    private val logger = LoggerFactory.getLogger(ProjectionManager::class.java)
    private val projections = mutableMapOf<String, Projection>()
    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Register a projection
     */
    fun register(projection: Projection) {
        projections[projection.projectionId] = projection
        logger.info("Registered projection: ${projection.projectionId}")
    }
    
    /**
     * Start all registered projections
     */
    fun startAll() {
        projections.values.forEach { start(it) }
    }
    
    /**
     * Start a specific projection
     */
    fun start(projection: Projection) {
        if (jobs.containsKey(projection.projectionId)) {
            logger.warn("Projection ${projection.projectionId} is already running")
            return
        }
        
        val job = scope.launch {
            logger.info("Starting projection: ${projection.projectionId}")
            
            try {
                // Subscribe to events
                eventStore.subscribeToTypes(*projection.subscribedEventTypes.toTypedArray())
                    .catch { e ->
                        logger.error("Error in projection ${projection.projectionId}: ${e.message}", e)
                    }
                    .collect { event ->
                        projection.handle(event)
                    }
            } catch (e: CancellationException) {
                logger.info("Projection ${projection.projectionId} cancelled")
                throw e
            } catch (e: Exception) {
                logger.error("Projection ${projection.projectionId} failed: ${e.message}", e)
            }
        }
        
        jobs[projection.projectionId] = job
    }
    
    /**
     * Stop a specific projection
     */
    fun stop(projectionId: String) {
        jobs[projectionId]?.cancel()
        jobs.remove(projectionId)
        logger.info("Stopped projection: $projectionId")
    }
    
    /**
     * Stop all projections
     */
    fun stopAll() {
        scope.cancel()
        jobs.clear()
        logger.info("Stopped all projections")
    }
    
    /**
     * Rebuild a projection from the beginning
     */
    suspend fun rebuild(projectionId: String) {
        val projection = projections[projectionId]
            ?: throw IllegalArgumentException("Projection $projectionId not found")
        
        logger.info("Rebuilding projection: $projectionId")
        
        // Stop if running
        stop(projectionId)
        
        // Reset projection
        projection.reset()
        
        // Process all historical events
        val eventTypes = projection.subscribedEventTypes
        
        // This would need to be optimized for large event stores
        // In practice, you'd process in batches
        var processedCount = 0
        
        for (eventType in eventTypes) {
            // Read all events of this type
            // Note: This is simplified - real implementation would paginate
            val events = eventStore.subscribeToTypes(eventType)
            
            events.collect { event ->
                projection.handle(event)
                processedCount++
                
                if (processedCount % 1000 == 0) {
                    logger.info("Processed $processedCount events for projection $projectionId")
                }
            }
        }
        
        logger.info("Rebuilt projection $projectionId with $processedCount events")
        
        // Restart live processing
        start(projection)
    }
    
    /**
     * Get projection status
     */
    suspend fun getStatus(projectionId: String): ProjectionStatus {
        val projection = projections[projectionId]
            ?: return ProjectionStatus.NOT_FOUND
        
        val job = jobs[projectionId]
        
        return when {
            job == null -> ProjectionStatus.STOPPED
            job.isActive -> ProjectionStatus.RUNNING
            job.isCancelled -> ProjectionStatus.STOPPED
            job.isCompleted -> ProjectionStatus.FAILED
            else -> ProjectionStatus.UNKNOWN
        }
    }
}

/**
 * Status of a projection
 */
enum class ProjectionStatus {
    RUNNING,
    STOPPED,
    FAILED,
    NOT_FOUND,
    UNKNOWN
}

/**
 * Store for projection positions
 */
interface ProjectionPositionStore {
    suspend fun getPosition(projectionId: String): ProjectionPosition?
    suspend fun savePosition(projectionId: String, position: ProjectionPosition)
    suspend fun deletePosition(projectionId: String)
}

/**
 * In-memory implementation of position store
 */
class InMemoryProjectionPositionStore : ProjectionPositionStore {
    private val positions = ConcurrentHashMap<String, ProjectionPosition>()
    
    override suspend fun getPosition(projectionId: String): ProjectionPosition? {
        return positions[projectionId]
    }
    
    override suspend fun savePosition(projectionId: String, position: ProjectionPosition) {
        positions[projectionId] = position
    }
    
    override suspend fun deletePosition(projectionId: String) {
        positions.remove(projectionId)
    }
}

/**
 * Exception thrown by projections
 */
class ProjectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Example: Order summary projection
 */
class OrderSummaryProjection(
    private val summaryStore: MutableMap<String, OrderSummary> = mutableMapOf()
) : BaseProjection(
    projectionId = "order-summary",
    subscribedEventTypes = setOf(
        "OrderCreated",
        "OrderSubmitted",
        "OrderCancelled",
        "OrderItemAdded"
    )
) {
    
    private var lastPosition: ProjectionPosition? = null
    
    override suspend fun handleEvent(event: Event) {
        when (event) {
            is OrderCreatedEvent -> {
                summaryStore[event.orderId] = OrderSummary(
                    orderId = event.orderId,
                    customerId = event.customerId,
                    status = "DRAFT",
                    itemCount = 0,
                    totalAmount = null,
                    createdAt = event.timestamp,
                    updatedAt = event.timestamp
                )
            }
            is OrderItemAddedEvent -> {
                summaryStore[event.orderId]?.let { summary ->
                    summaryStore[event.orderId] = summary.copy(
                        itemCount = summary.itemCount + 1,
                        updatedAt = event.timestamp
                    )
                }
            }
            is OrderSubmittedEvent -> {
                summaryStore[event.orderId]?.let { summary ->
                    summaryStore[event.orderId] = summary.copy(
                        status = "SUBMITTED",
                        totalAmount = event.totalAmount,
                        updatedAt = event.timestamp
                    )
                }
            }
            is OrderCancelledEvent -> {
                summaryStore[event.orderId]?.let { summary ->
                    summaryStore[event.orderId] = summary.copy(
                        status = "CANCELLED",
                        updatedAt = event.timestamp
                    )
                }
            }
        }
    }
    
    override suspend fun getPosition(): ProjectionPosition {
        return lastPosition ?: ProjectionPosition(
            streamId = "",
            version = -1,
            timestamp = Instant.EPOCH
        )
    }
    
    override suspend fun savePosition(position: ProjectionPosition) {
        lastPosition = position
    }
    
    override suspend fun reset() {
        summaryStore.clear()
        lastPosition = null
    }
    
    // Query methods
    fun getOrderSummary(orderId: String): OrderSummary? = summaryStore[orderId]
    
    fun getOrdersByCustomer(customerId: String): List<OrderSummary> {
        return summaryStore.values.filter { it.customerId == customerId }
    }
    
    fun getOrdersByStatus(status: String): List<OrderSummary> {
        return summaryStore.values.filter { it.status == status }
    }
}

/**
 * Read model for order summary
 */
data class OrderSummary(
    val orderId: String,
    val customerId: String,
    val status: String,
    val itemCount: Int,
    val totalAmount: Money?,
    val createdAt: Instant,
    val updatedAt: Instant
)