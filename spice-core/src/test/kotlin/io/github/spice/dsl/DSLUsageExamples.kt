package io.github.spice.dsl

import io.github.spice.*
import io.github.spice.dsl.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * 🎯 DSL Usage Examples
 * 
 * Demonstrates the enhanced DSL capabilities of Spice Framework
 */
class DSLUsageExamples {
    
    @Test
    fun `workflow DSL example`() = runBlocking {
        // Create agents
        val textAgent = agent {
            id = "text-processor"
            name = "Text Processor"
            description = "Processes text messages"
            
            messageHandler { message ->
                message.createReply(
                    content = "Processed: ${message.content}",
                    sender = id
                )
            }
        }
        
        val dataAgent = agent {
            id = "data-analyzer"
            name = "Data Analyzer"
            description = "Analyzes data"
            
            messageHandler { message ->
                message.createReply(
                    content = "Analyzed: ${message.content.length} characters",
                    sender = id
                )
            }
        }
        
        // Build workflow
        val workflowDef = workflow("sample-workflow", "Sample Processing Workflow") {
            description("A workflow that processes text and then analyzes the result")
            
            // Define nodes
            agent("text-node", textAgent)
            agent("data-node", dataAgent)
            
            transform("uppercase") { message ->
                message.copy(content = message.content.uppercase())
            }
            
            sink("final-output") { message ->
                message.copy(
                    content = "Final result: ${message.content}",
                    metadata = message.metadata + ("workflow_complete" to "true")
                )
            }
            
            // Define flow
            flow("text-node", "uppercase", "data-node", "final-output")
        }
        
        // Execute workflow
        val executor = WorkflowExecutor()
        val input = Message(
            content = "Hello Spice Framework!",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val result = executor.execute(workflowDef, input)
        println("🌊 Workflow Result: ${result.content}")
    }
    
    @Test
    fun `conditional flow DSL example`() = runBlocking {
        // Create conditional flow
        val conditionalFlow = conditionalFlow {
            // Route based on content length
            whenThen(
                ContentConditions.lengthGreaterThan(50)
            ) { message ->
                message.createReply(
                    content = "Long message processed: ${message.content.take(20)}...",
                    sender = "long-processor"
                )
            }
            
            // Route based on content pattern
            whenThen(
                ContentConditions.contains("urgent", ignoreCase = true)
            ) { message ->
                message.createReply(
                    content = "URGENT: ${message.content}",
                    sender = "urgent-processor"
                )
            }
            
            // Route based on sender
            whenThen(
                SenderConditions.senderContains("admin")
            ) { message ->
                message.createReply(
                    content = "Admin message: ${message.content}",
                    sender = "admin-processor"
                )
            }
            
            // Default case
            otherwise { message ->
                message.createReply(
                    content = "Standard processing: ${message.content}",
                    sender = "default-processor"
                )
            }
        }
        
        // Test different message types
        val messages = listOf(
            Message(content = "This is a very long message that should trigger the length condition", sender = "user"),
            Message(content = "URGENT: System failure!", sender = "user"),
            Message(content = "Hello from admin", sender = "admin-user"),
            Message(content = "Normal message", sender = "user")
        )
        
        messages.forEach { message ->
            val result = conditionalFlow.execute(message)
            println("🔀 Conditional Result: ${result.content}")
        }
    }
    
    @Test
    fun `composition DSL example`() = runBlocking {
        // Create base agents
        val validator = agent {
            id = "validator"
            name = "Input Validator"
            messageHandler { message ->
                if (message.content.isBlank()) {
                    message.createReply(
                        content = "Error: Empty message",
                        sender = id,
                        type = MessageType.ERROR
                    )
                } else {
                    message.createReply(
                        content = "Valid: ${message.content}",
                        sender = id
                    )
                }
            }
        }
        
        val processor = agent {
            id = "processor"
            name = "Message Processor"
            messageHandler { message ->
                message.createReply(
                    content = "Processed: ${message.content.uppercase()}",
                    sender = id
                )
            }
        }
        
        val logger = agent {
            id = "logger"
            name = "Message Logger"
            messageHandler { message ->
                println("📝 Logged: ${message.content}")
                message
            }
        }
        
        // Create composable agents
        val composableValidator = validator.toComposable()
        val composableProcessor = processor.toComposable()
        val composableLogger = logger.toComposable()
        
        // Sequential composition
        val sequentialFlow = composableValidator
            .then(composableProcessor)
            .then(composableLogger)
        
        // Parallel composition with fallback
        val parallelFlow = composableProcessor
            .parallel(composableLogger)
            .or(composableValidator) // Fallback
        
        // Conditional composition
        val conditionalFlow = composableValidator.conditional(
            condition = { message -> message.content.startsWith("validate") },
            ifTrue = composableProcessor,
            ifFalse = composableLogger
        )
        
        // Test sequential flow
        val testMessage = Message(content = "test message", sender = "user")
        val sequentialResult = sequentialFlow.processMessage(testMessage)
        println("🔗 Sequential Result: ${sequentialResult.content}")
        
        // Test parallel flow
        val parallelResult = parallelFlow.processMessage(testMessage)
        println("⚡ Parallel Result: ${parallelResult.content}")
    }
    
    @Test
    fun `reactive streams DSL example`() = runBlocking {
        // Create reactive agents
        val textAgent = agent {
            id = "reactive-text"
            name = "Reactive Text Agent"
            messageHandler { message ->
                message.createReply(
                    content = "Reactively processed: ${message.content}",
                    sender = id
                )
            }
        }.reactive()
        
        val filterAgent = agent {
            id = "reactive-filter"
            name = "Reactive Filter Agent"
            messageHandler { message ->
                if (message.content.contains("important")) {
                    message.createReply(
                        content = "Important message flagged: ${message.content}",
                        sender = id
                    )
                } else {
                    message
                }
            }
        }.reactive()
        
        // Create message stream
        val messageStream = messageStream {
            fromMessages(
                Message(content = "Hello reactive world", sender = "user1"),
                Message(content = "This is important information", sender = "user2"),
                Message(content = "Normal message", sender = "user3"),
                Message(content = "Another important update", sender = "user4")
            )
        }
        
                 // Create stream composition
         val streamProcessor = streamComposition {
             filterByType(MessageType.TEXT)
             addMetadata(mapOf("processed_by" to "reactive_system"))
             throttle(1.seconds)
         }
        
        // Create reactive multi-agent system
        val reactiveSystem = reactiveSystem(
            agents = listOf(textAgent, filterAgent),
            config = StreamConfig(bufferSize = 32, parallelism = 2)
        )
        
        // Process stream through composition and agents
        val processedStream = messageStream
            .let(streamProcessor)
            .processWithAgents(textAgent, filterAgent)
        
        // Collect and display results
        processedStream.collect { message ->
            println("🌊 Reactive Result: ${message.content}")
        }
    }
    
    @Test
    fun `complex DSL integration example`() = runBlocking {
        // Create sophisticated processing pipeline
        
        // 1. Create agents with different capabilities
        val validationAgent = agent {
            id = "validator"
            name = "Validation Agent"
            capabilities = setOf("validation", "input-checking")
            
            messageHandler { message ->
                when {
                    message.content.isBlank() -> message.createReply(
                        content = "Validation failed: Empty content",
                        sender = id,
                        type = MessageType.ERROR
                    )
                    message.content.length > 1000 -> message.createReply(
                        content = "Validation failed: Content too long",
                        sender = id,
                        type = MessageType.ERROR
                    )
                    else -> message.createReply(
                        content = "Validation passed",
                        sender = id,
                        metadata = mapOf("validated" to "true")
                    )
                }
            }
        }
        
        val processingAgent = agent {
            id = "processor"
            name = "Processing Agent"
            capabilities = setOf("processing", "transformation")
            
            messageHandler { message ->
                if (message.metadata["validated"] == "true") {
                    message.createReply(
                        content = "PROCESSED: ${message.content.uppercase()}",
                        sender = id,
                        metadata = mapOf("processed" to "true")
                    )
                } else {
                    message.createReply(
                        content = "Cannot process unvalidated message",
                        sender = id,
                        type = MessageType.ERROR
                    )
                }
            }
        }
        
        val analysisAgent = agent {
            id = "analyzer"
            name = "Analysis Agent"
            capabilities = setOf("analysis", "reporting")
            
            messageHandler { message ->
                val wordCount = message.content.split("\\s+".toRegex()).size
                val charCount = message.content.length
                
                message.createReply(
                    content = "Analysis: $wordCount words, $charCount characters",
                    sender = id,
                    metadata = mapOf(
                        "word_count" to wordCount.toString(),
                        "char_count" to charCount.toString()
                    )
                )
            }
        }
        
        // 2. Create workflow that combines all techniques
        val complexWorkflow = workflow("complex-pipeline", "Complex Processing Pipeline") {
            description("Validates, processes, and analyzes messages with conditional routing")
            
            // Add agents as nodes
            agent("validation", validationAgent)
            agent("processing", processingAgent)
            agent("analysis", analysisAgent)
            
            // Add conditional routing
            condition("validation-check") { message ->
                message.metadata["validated"] == "true"
            }
            
            condition("processing-check") { message ->
                message.metadata["processed"] == "true"
            }
            
            // Add data transformation
            transform("add-timestamp") { message ->
                message.copy(
                    metadata = message.metadata + ("processed_at" to System.currentTimeMillis().toString())
                )
            }
            
            // Define complex flow with conditionals
            start("validation")
            edge("validation", "validation-check")
            edge("validation-check", "processing", condition = { it.metadata["validated"] == "true" })
            edge("validation-check", "analysis", condition = { it.metadata["validated"] != "true" })
            edge("processing", "processing-check")
            edge("processing-check", "add-timestamp", condition = { it.metadata["processed"] == "true" })
            edge("add-timestamp", "analysis")
            end("analysis")
        }
        
        // 3. Create reactive stream with conditional processing
        val conditionalProcessor = conditionalFlow {
            whenThen(
                ContentConditions.lengthGreaterThan(100) and 
                MetadataConditions.hasKey("priority")
            ) { message ->
                // High priority, long messages get expedited processing
                message.copy(metadata = message.metadata + ("expedited" to "true"))
            }
            
            whenThen(
                TypeConditions.isType(MessageType.ERROR)
            ) { message ->
                // Error messages get logged and flagged
                println("🚨 Error detected: ${message.content}")
                message.copy(metadata = message.metadata + ("error_logged" to "true"))
            }
            
            otherwise { message -> message }
        }
        
                 // 4. Create composable agent chain
         val composableChain = validationAgent.toComposable()
             .withTimeout(5000L)
             .then(processingAgent.toComposable())
             .then(analysisAgent.toComposable())
        
        // 5. Test with various message types
        val testMessages = listOf(
            Message(content = "Simple test", sender = "user"),
            Message(content = "", sender = "user"), // Will fail validation
            Message(content = "Priority message with lots of content to trigger length check", 
                   sender = "admin", metadata = mapOf("priority" to "high")),
            Message(content = "Error test", sender = "system", type = MessageType.ERROR)
        )
        
        // Process through workflow
        val executor = WorkflowExecutor()
        testMessages.forEach { message ->
            println("\n🎯 Processing: ${message.content.take(20)}...")
            
            // Apply conditional processing first
            val conditionedMessage = conditionalProcessor.execute(message)
            
            // Execute through workflow
            val workflowResult = executor.execute(complexWorkflow, conditionedMessage)
            println("   Workflow result: ${workflowResult.content}")
            
            // Also test composable chain
            val composableResult = composableChain.processMessage(conditionedMessage)
            println("   Composable result: ${composableResult.content}")
        }
    }
} 