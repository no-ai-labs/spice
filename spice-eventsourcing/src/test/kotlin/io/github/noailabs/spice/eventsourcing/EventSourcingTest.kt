package io.github.noailabs.spice.eventsourcing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.noailabs.spice.KafkaCommHub
import io.github.noailabs.spice.KafkaConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventSourcingTest {
    
    private lateinit var kafkaContainer: KafkaContainer
    private lateinit var postgresContainer: PostgreSQLContainer<*>
    private lateinit var dataSource: HikariDataSource
    private lateinit var kafkaCommHub: KafkaCommHub
    private lateinit var eventStore: KafkaEventStore
    
    @BeforeAll
    fun setup() {
        // Start containers
        kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        kafkaContainer.start()
        
        postgresContainer = PostgreSQLContainer(DockerImageName.parse("postgres:15"))
        postgresContainer.start()
        
        // Setup database connection
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = postgresContainer.jdbcUrl
            username = postgresContainer.username
            password = postgresContainer.password
            driverClassName = "org.postgresql.Driver"
        }
        dataSource = HikariDataSource(hikariConfig)
        
        // Setup Kafka
        val kafkaConfig = KafkaConfig(
            bootstrapServers = kafkaContainer.bootstrapServers,
            groupId = "test-group"
        )
        
        kafkaCommHub = KafkaCommHub(kafkaConfig)
        eventStore = KafkaEventStore(kafkaCommHub, dataSource)
    }
    
    @AfterAll
    fun teardown() {
        dataSource.close()
        kafkaCommHub.close()
        kafkaContainer.stop()
        postgresContainer.stop()
    }
    
    @Test
    fun `test event store basic operations`() = runBlocking {
        val streamId = "test-stream-1"
        val events = listOf(
            TestEvent("Event1", streamId, mapOf("data" to "test1")),
            TestEvent("Event2", streamId, mapOf("data" to "test2"))
        )
        
        // Append events
        val version = eventStore.append(streamId, events, -1)
        assertEquals(2, version)
        
        // Read events
        val readEvents = eventStore.readStream(streamId, 0)
        assertEquals(2, readEvents.size)
        
        // Get stream version
        val currentVersion = eventStore.getStreamVersion(streamId)
        assertEquals(2, currentVersion)
    }
    
    @Test
    fun `test concurrency control`() = runBlocking {
        val streamId = "test-stream-2"
        val event1 = TestEvent("Event1", streamId, mapOf("data" to "test1"))
        
        // First append should succeed
        val version1 = eventStore.append(streamId, listOf(event1), -1)
        assertEquals(1, version1)
        
        // Second append with wrong expected version should fail
        assertThrows<ConcurrencyException> {
            runBlocking {
                eventStore.append(streamId, listOf(event1), 0)
            }
        }
    }
    
    @Test
    fun `test snapshots`() = runBlocking {
        val streamId = "test-stream-3"
        val snapshotData = "test-snapshot-data".toByteArray()
        
        // Save snapshot
        val snapshot = Snapshot(
            streamId = streamId,
            version = 10,
            timestamp = Instant.now(),
            data = snapshotData,
            metadata = mapOf("test" to "metadata")
        )
        
        eventStore.saveSnapshot(streamId, snapshot, 10)
        
        // Get snapshot
        val retrieved = eventStore.getLatestSnapshot(streamId)
        assertNotNull(retrieved)
        assertEquals(10, retrieved!!.version)
        assertArrayEquals(snapshotData, retrieved.data)
    }
    
    @Test
    fun `test event sourcing agent`() = runBlocking {
        val agent = CounterAgent(eventStore)
        
        // Initialize agent
        agent.initialize()
        
        // Process increment
        val incrementComm = io.github.noailabs.spice.Comm(
            from = "test",
            to = agent.id,
            content = "increment"
        )
        
        val response1 = agent.processComm(incrementComm)
        assertTrue(response1.content.contains("1"))
        
        // Process another increment
        val response2 = agent.processComm(incrementComm)
        assertTrue(response2.content.contains("2"))
        
        // Process decrement
        val decrementComm = io.github.noailabs.spice.Comm(
            from = "test",
            to = agent.id,
            content = "decrement"
        )
        
        val response3 = agent.processComm(decrementComm)
        assertTrue(response3.content.contains("1"))
    }
    
    @Test
    fun `test aggregate pattern`() = runBlocking {
        val repository = AggregateRepository(eventStore)
        val orderId = "order-123"
        
        // Create new order
        val order = OrderAggregate(orderId)
        order.create("customer-456")
        order.addItem("product-1", 2, Money(1000, "USD"))
        order.addItem("product-2", 1, Money(2000, "USD"))
        order.submit()
        
        // Save aggregate
        repository.save(order)
        
        // Load aggregate
        val loadedOrder = repository.load(orderId) { OrderAggregate(orderId) }
        assertEquals("customer-456", loadedOrder.customerId)
        assertEquals(OrderStatus.SUBMITTED, loadedOrder.status)
        assertEquals(2, loadedOrder.items.size)
    }
    
    @Test
    fun `test projection`() = runBlocking {
        // Create some order events
        val order1Id = "order-proj-1"
        val order2Id = "order-proj-2"
        
        val events = listOf(
            OrderCreatedEvent(order1Id, "customer-1", Instant.now()),
            OrderItemAddedEvent(order1Id, "product-1", 2, Money(1000, "USD"), Instant.now()),
            OrderSubmittedEvent(order1Id, Money(2000, "USD"), Instant.now()),
            
            OrderCreatedEvent(order2Id, "customer-1", Instant.now()),
            OrderCancelledEvent(order2Id, "Out of stock", Instant.now())
        )
        
        // Append events to store
        eventStore.append("Order-$order1Id", events.take(3), -1)
        eventStore.append("Order-$order2Id", events.takeLast(2), -1)
        
        // Create and run projection
        val projection = OrderSummaryProjection()
        
        // Process events manually (in real scenario, ProjectionManager would handle this)
        events.forEach { event ->
            projection.handle(event)
        }
        
        // Query projection
        val summary1 = projection.getOrderSummary(order1Id)
        assertNotNull(summary1)
        assertEquals("SUBMITTED", summary1!!.status)
        assertEquals(1, summary1.itemCount)
        
        val customerOrders = projection.getOrdersByCustomer("customer-1")
        assertEquals(2, customerOrders.size)
    }
    
    @Test
    fun `test saga pattern`() = runBlocking {
        val sagaManager = SagaManager(eventStore, InMemorySagaStore())
        val orderId = "order-saga-1"
        val sagaId = "saga-$orderId"
        
        val saga = OrderFulfillmentSaga(sagaId, orderId)
        val context = SagaContext()
        
        // Start saga
        sagaManager.startSaga(saga, context)
        
        // Wait for saga to complete
        Thread.sleep(1000)
        
        // Check saga state
        val status = sagaManager.getSagaStatus(sagaId)
        assertNotNull(status)
        
        // Check events were created
        val sagaEvents = eventStore.readStream("saga-$sagaId", 0)
        assertTrue(sagaEvents.isNotEmpty())
    }
}

// Test event implementation
data class TestEvent(
    override val eventType: String,
    override val streamId: String,
    val data: Map<String, String>,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val version: Long = -1,
    override val timestamp: Instant = Instant.now(),
    override val metadata: EventMetadata = EventMetadata(userId = "test", correlationId = java.util.UUID.randomUUID().toString())
) : Event {
    override fun toProto(): ByteArray = eventType.toByteArray()
}