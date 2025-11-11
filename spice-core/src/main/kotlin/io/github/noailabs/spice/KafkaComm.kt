package io.github.noailabs.spice

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.serialization.*
import java.util.Properties
import java.time.Duration

/**
 * 🚀 KafkaComm - Kafka-based Communication for Spice Framework
 * 
 * Extends Spice's communication capabilities with distributed messaging.
 * Enables agent communication across processes and services.
 */

// =====================================
// KAFKA CONFIGURATION
// =====================================

/**
 * Kafka configuration for Spice
 */
data class KafkaConfig(
    val bootstrapServers: String = "localhost:9092",
    val groupId: String = "spice-agents",
    val enableAutoCommit: Boolean = false,
    val autoOffsetReset: String = "earliest",
    val keySerializer: String = StringSerializer::class.java.name,
    val valueSerializer: String = StringSerializer::class.java.name,
    val keyDeserializer: String = StringDeserializer::class.java.name,
    val valueDeserializer: String = StringDeserializer::class.java.name
) {
    fun toProducerProps(): Properties = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.RETRIES_CONFIG, 3)
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
    }
    
    fun toConsumerProps(groupId: String = this.groupId): Properties = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset)
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit)
    }
}

// =====================================
// KAFKA TOPICS
// =====================================

/**
 * Standard Kafka topics for Spice
 */
object SpiceKafkaTopics {
    const val AGENT_MESSAGES = "spice.agent.messages"
    const val AGENT_BROADCASTS = "spice.agent.broadcasts"
    const val TOOL_CALLS = "spice.tool.calls"
    const val TOOL_RESULTS = "spice.tool.results"
    const val SYSTEM_EVENTS = "spice.system.events"
    const val WORKFLOW_EVENTS = "spice.workflow.events"
    
    fun topicForAgent(agentId: String) = "spice.agent.$agentId"
    fun topicForWorkflow(workflowId: String) = "spice.workflow.$workflowId"
}

// =====================================
// KAFKA COMM HUB
// =====================================

/**
 * 💫 Kafka-enabled Communication Hub
 * 
 * Drop-in replacement for CommHub with Kafka support.
 * Maintains backward compatibility while adding distributed capabilities.
 */
class KafkaCommHub(
    private val config: KafkaConfig = KafkaConfig(),
    private val json: Json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = false
    }
) {
    private val producer: KafkaProducer<String, String> by lazy {
        KafkaProducer(config.toProducerProps())
    }
    
    private val consumers = mutableMapOf<String, KafkaConsumer<String, String>>()
    private val consumerJobs = mutableMapOf<String, Job>()
    
    // Local fallback (for backward compatibility)
    private val localHub = CommHub
    
    // ===== Sending Messages =====
    
    /**
     * Send a message via Kafka
     */
    suspend fun send(comm: Comm): CommResult = coroutineScope {
        try {
            val topic = when {
                comm.to != null -> SpiceKafkaTopics.topicForAgent(comm.to!!)
                comm.type == CommType.SYSTEM -> SpiceKafkaTopics.SYSTEM_EVENTS
                comm.type == CommType.TOOL_CALL -> SpiceKafkaTopics.TOOL_CALLS
                comm.type == CommType.TOOL_RESULT -> SpiceKafkaTopics.TOOL_RESULTS
                else -> SpiceKafkaTopics.AGENT_MESSAGES
            }
            
            val key = comm.from
            val value = json.encodeToString(comm)
            
            val record = ProducerRecord(topic, key, value)
            
            val future = producer.send(record)
            val metadata = future.get() // Wait for confirmation
            
            println("📤 [Kafka] ${comm.from} → ${comm.to ?: "broadcast"}: ${comm.content.take(50)}...")
            println("   → Topic: $topic, Partition: ${metadata.partition()}, Offset: ${metadata.offset()}")
            
            CommResult.success(comm.id, listOfNotNull(comm.to))
        } catch (e: Exception) {
            println("❌ [Kafka] Send failed: ${e.message}")
            // Fallback to local
            localHub.send(comm)
        }
    }
    
    /**
     * Broadcast to all agents
     */
    suspend fun broadcast(comm: Comm): CommResult {
        val broadcastComm = comm.copy(to = null)
        val topic = SpiceKafkaTopics.AGENT_BROADCASTS
        
        val record = ProducerRecord(topic, comm.from, json.encodeToString(broadcastComm))
        
        return try {
            producer.send(record).get()
            println("📢 [Kafka] Broadcast from ${comm.from}: ${comm.content.take(50)}...")
            CommResult.success(comm.id, emptyList())
        } catch (e: Exception) {
            println("❌ [Kafka] Broadcast failed: ${e.message}")
            return localHub.broadcastAll(comm).first()
        }
    }
    
    // ===== Receiving Messages =====
    
    /**
     * Subscribe agent to receive messages
     */
    fun subscribeAgent(agentId: String, handler: suspend (Comm) -> Unit) {
        val topics = listOf(
            SpiceKafkaTopics.topicForAgent(agentId),
            SpiceKafkaTopics.AGENT_BROADCASTS,
            SpiceKafkaTopics.SYSTEM_EVENTS
        )
        
        val consumer = KafkaConsumer<String, String>(
            config.toConsumerProps("spice-agent-$agentId")
        )
        consumer.subscribe(topics)
        
        consumers[agentId] = consumer
        
        // Start consumer coroutine
        val job = GlobalScope.launch {
            println("🔌 [Kafka] Agent $agentId subscribed to topics: $topics")
            
            while (isActive) {
                try {
                    val records = consumer.poll(Duration.ofMillis(100))
                    
                    for (record in records) {
                        try {
                            val comm = json.decodeFromString<Comm>(record.value())
                            
                            // Skip if it's our own broadcast
                            if (comm.from == agentId && comm.to == null) continue
                            
                            println("📥 [Kafka] $agentId received from ${comm.from}: ${comm.content.take(50)}...")
                            handler(comm)
                            
                            consumer.commitSync()
                        } catch (e: Exception) {
                            println("❌ [Kafka] Error processing message: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        println("❌ [Kafka] Consumer error for $agentId: ${e.message}")
                        delay(1000) // Back off before retry
                    }
                }
            }
        }
        
        consumerJobs[agentId] = job
    }
    
    /**
     * Unsubscribe agent
     */
    fun unsubscribeAgent(agentId: String) {
        consumerJobs[agentId]?.cancel()
        consumerJobs.remove(agentId)
        
        consumers[agentId]?.close()
        consumers.remove(agentId)
        
        println("🔌 [Kafka] Agent $agentId unsubscribed")
    }
    
    // ===== Tool Communication =====
    
    /**
     * Send tool call via Kafka
     */
    suspend fun sendToolCall(
        toolName: String, 
        params: Map<String, Any>,
        from: String,
        requestId: String = "tool-${System.currentTimeMillis()}"
    ): String {
        val comm = Comm(
            content = "Tool call: $toolName",
            from = from,
            type = CommType.TOOL_CALL,
            role = CommRole.TOOL,
            data = mapOf(
                "tool_name" to toolName,
                "tool_params" to json.encodeToString(params),
                "request_id" to requestId
            )
        )
        
        send(comm)
        return requestId
    }
    
    /**
     * Listen for tool results
     */
    fun subscribeToToolResults(handler: suspend (String, String, String) -> Unit) {
        val consumer = KafkaConsumer<String, String>(
            config.toConsumerProps("spice-tool-results")
        )
        consumer.subscribe(listOf(SpiceKafkaTopics.TOOL_RESULTS))
        
        GlobalScope.launch {
            while (isActive) {
                try {
                    val records = consumer.poll(Duration.ofMillis(100))
                    
                    for (record in records) {
                        val comm = json.decodeFromString<Comm>(record.value())
                        val requestId = comm.data["request_id"]?.toString() ?: continue
                        val toolName = comm.data["tool_name"]?.toString() ?: continue

                        handler(requestId, toolName, comm.content)
                        consumer.commitSync()
                    }
                } catch (e: Exception) {
                    println("❌ [Kafka] Tool result consumer error: ${e.message}")
                }
            }
        }
    }
    
    // ===== Workflow Events =====
    
    /**
     * Publish workflow event
     */
    suspend fun publishWorkflowEvent(
        workflowId: String,
        event: String,
        data: Map<String, Any> = emptyMap()
    ): CommResult {
        val comm = Comm(
            content = "Workflow event: $event",
            from = "workflow-$workflowId",
            type = CommType.WORKFLOW_START,
            data = mapOf(
                "workflow_id" to workflowId,
                "event" to event,
                "event_data" to json.encodeToString(data)
            )
        )
        
        val topic = SpiceKafkaTopics.topicForWorkflow(workflowId)
        val record = ProducerRecord(topic, workflowId, json.encodeToString(comm))
        
        return try {
            producer.send(record).get()
            println("🔄 [Kafka] Workflow $workflowId: $event")
            CommResult.success(comm.id, emptyList())
        } catch (e: Exception) {
            CommResult.failure(e.message ?: "Failed to publish workflow event")
        }
    }
    
    // ===== Cleanup =====
    
    /**
     * Close all resources
     */
    fun close() {
        consumerJobs.values.forEach { it.cancel() }
        consumers.values.forEach { it.close() }
        producer.close()
        
        println("🔌 [Kafka] KafkaCommHub closed")
    }
}

// =====================================
// KAFKA-ENABLED AGENT
// =====================================

/**
 * Kafka-enabled agent wrapper
 */
class KafkaAgent(
    val agent: SmartAgent,
    private val kafkaHub: KafkaCommHub,
    private val messageHandler: suspend (Comm) -> Unit = {}
) {
    val id = agent.id
    val name = agent.name
    
    init {
        // Auto-subscribe to Kafka
        kafkaHub.subscribeAgent(id) { comm ->
            GlobalScope.launch {
                handleKafkaMessage(comm)
            }
        }
    }
    
    /**
     * Handle incoming Kafka messages
     */
    suspend fun handleKafkaMessage(comm: Comm) {
        when (comm.type) {
            CommType.TEXT, CommType.PROMPT -> {
                // Delegate to custom handler or use default
                messageHandler(comm)
            }
            CommType.TOOL_CALL -> {
                handleToolCall(comm)
            }
            else -> {
                // Let custom handler deal with it
                messageHandler(comm)
            }
        }
    }
    
    /**
     * Handle tool calls
     */
    private suspend fun handleToolCall(comm: Comm) {
        val toolName = comm.data["tool_name"]?.toString() ?: return
        val params = comm.data["tool_params"]?.let {
            Json.decodeFromString<Map<String, Any>>(it.toString())
        } ?: emptyMap()

        // Use agent's tool if available
        val result = agent.useTool(toolName, params)
        if (result != null) {
            kafkaHub.send(comm.toolResult(result.toString(), id))
        }
    }
    
    /**
     * Send message via Kafka
     */
    suspend fun send(comm: Comm): CommResult {
        return kafkaHub.send(comm.copy(from = id))
    }
    
    /**
     * Send text message
     */
    suspend fun send(content: String, to: String? = null): CommResult {
        return kafkaHub.send(
            Comm(
                content = content,
                from = id,
                to = to,
                type = CommType.TEXT,
                role = CommRole.AGENT
            )
        )
    }
    
    /**
     * Cleanup on destroy
     */
    fun cleanup() {
        kafkaHub.unsubscribeAgent(id)
    }
}

// =====================================
// EXTENSION FUNCTIONS
// =====================================

/**
 * Enable Kafka for existing agent
 */
fun SmartAgent.withKafka(
    kafkaHub: KafkaCommHub,
    messageHandler: suspend (Comm) -> Unit = { comm ->
        // Default handler
        when (comm.type) {
            CommType.TEXT, CommType.PROMPT -> {
                kafkaHub.send(
                    comm.reply(
                        content = "Received: ${comm.content}",
                        from = id
                    )
                )
            }
            else -> {
                // Ignore other types
            }
        }
    }
): KafkaAgent {
    return KafkaAgent(this, kafkaHub, messageHandler)
}

/**
 * Quick Kafka setup
 */
fun setupKafkaComm(
    bootstrapServers: String = "localhost:9092",
    groupId: String = "spice-agents"
): KafkaCommHub {
    val config = KafkaConfig(
        bootstrapServers = bootstrapServers,
        groupId = groupId
    )
    return KafkaCommHub(config)
}