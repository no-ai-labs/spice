package io.github.spice.dsl

import io.github.spice.*
import io.github.spice.dsl.experimental.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * 🎯 Simplified DSL Examples
 * 
 * Demonstrates the new simplified Agent > Flow > Tool structure
 * with optional experimental extensions.
 */
class SimplifiedDSLExamples {
    
    @Test
    fun basicAgentFlowToolExample() = runBlocking {
        println("🌶️ === Basic Agent > Flow > Tool Example ===")
        
        // 1. Create tools
        tool("greeting") {
            description = "Generate personalized greetings"
            param("name", "string")
            param("language", "string")
            
            execute { params ->
                val name = params["name"] as? String ?: "World"
                val language = params["language"] as? String ?: "en"
                
                val greeting = when (language) {
                    "ko" -> "안녕하세요, ${name}님!"
                    "en" -> "Hello, $name!"
                    "ja" -> "こんにちは、${name}さん！"
                    else -> "Hello, $name!"
                }
                
                ToolResult.success(greeting)
            }
        }
        
        tool("analysis") {
            description = "Analyze text content"
            param("text", "string")
            
            execute { params ->
                val text = params["text"] as? String ?: ""
                val words = text.split("\\s+".toRegex()).size
                val chars = text.length
                
                ToolResult.success(
                    "Analysis: $words words, $chars characters",
                    metadata = mapOf(
                        "word_count" to words.toString(),
                        "char_count" to chars.toString()
                    )
                )
            }
        }
        
        // 2. Create agents
        val greetingAgent = buildAgent {
            id = "greeting-agent"
            name = "Greeting Agent"
            description = "Handles greetings and welcomes"
            
            tool("greeting")
            
            handle { message ->
                // Extract name from message
                val name = message.content.substringAfter("Hello ").substringBefore(" ").takeIf { it.isNotEmpty() } ?: "World"
                
                val result = executeTool("greeting", mapOf(
                    "name" to name,
                    "language" to "ko"
                ))
                
                message.createReply(
                    content = result.result ?: result.error ?: "Greeting failed",
                    sender = id
                )
            }
        }
        
        val analysisAgent = buildAgent {
            id = "analysis-agent"
            name = "Analysis Agent"
            description = "Analyzes text content"
            
            tool("analysis")
            
            handle { message ->
                val result = executeTool("analysis", mapOf("text" to message.content))
                
                message.createReply(
                    content = result.result ?: result.error ?: "Analysis failed",
                    sender = id,
                    metadata = result.metadata
                )
            }
        }
        
        // 3. Create flow
        val welcomeFlow = flow {
            id = "welcome-flow"
            name = "Welcome Processing Flow"
            description = "Greets user and analyzes their message"
            
            step("greeting-agent")
            step("analysis-agent")
        }
        
        // 4. Execute
        val testMessage = Message(
            content = "Hello Alice how are you today?",
            sender = "user"
        )
        
        val result = welcomeFlow.execute(testMessage)
        println("✅ Result: ${result.content}")
        println("📊 Metadata: ${result.metadata}")
    }
    
    @Test
    fun pluginToolsAndChainsExample() = runBlocking {
        println("\n🔧 === Plugin Tools and Tool Chains Example ===")
        
        // 1. Create plugin tool with input/output mapping
        pluginTool("weather-service", "external-weather-plugin") {
            description = "Get weather information from external service"
            
            // Map user-friendly names to plugin-specific parameters
            mapInput("city", "location_name")
            mapInput("units", "temperature_unit")
            mapOutput("temp", "temperature")
            mapOutput("condition", "weather_condition")
            
            execute { params ->
                // Mock weather service call
                val location = params["location_name"] as? String ?: "Unknown"
                val unit = params["temperature_unit"] as? String ?: "celsius"
                
                ToolResult.success(
                    "Weather for $location: 22°C, Sunny",
                    metadata = mapOf(
                        "temp" to "22",
                        "condition" to "sunny",
                        "unit" to unit
                    )
                )
            }
        }
        
        // 2. Create tool chain
        toolChain("weather-report-chain") {
            description = "Complete weather report generation"
            
            step("validate-input", "weather-service", mapOf(
                "location_name" to "city",
                "temperature_unit" to "units"
            ))
            
            step("format-output", "analysis", mapOf(
                "text" to "result"
            ))
        }
        
        // 3. Create agent that uses the chain
        val weatherAgent = buildAgent {
            id = "weather-agent"
            name = "Weather Agent"
            description = "Provides weather reports"
            
            handle { message ->
                // Extract city from message
                val city = message.content.substringAfter("weather in ").substringBefore(" ").takeIf { it.isNotEmpty() } ?: "Seoul"
                
                val result = executeToolChain("weather-report-chain", mapOf(
                    "city" to city,
                    "units" to "celsius"
                ))
                
                message.createReply(
                    content = if (result.success) {
                        "🌤️ ${result.result}"
                    } else {
                        "❌ Weather service unavailable: ${result.error}"
                    },
                    sender = id,
                    metadata = result.metadata
                )
            }
        }
        
        // 4. Test
        val weatherMessage = Message(
            content = "What's the weather in Tokyo today?",
            sender = "user"
        )
        
        val weatherResult = weatherAgent.processMessage(weatherMessage)
        println("🌤️ Weather Result: ${weatherResult.content}")
    }
    
    @Test
    fun experimentalDSLExtensionsExample() = runBlocking {
        println("\n🧪 === Experimental DSL Extensions Example ===")
        
        // 1. Conditional flow
        val conditionalProcessor = experimental {
            conditional {
                whenThen(
                    condition = { message -> message.content.contains("urgent") },
                    action = { message ->
                        message.createReply(
                            content = "🚨 URGENT: ${message.content}",
                            sender = "urgent-processor"
                        )
                    }
                )
                
                whenThen(
                    condition = { message -> message.content.length > 100 },
                    action = { message ->
                        message.createReply(
                            content = "📄 Long message: ${message.content.take(50)}...",
                            sender = "long-processor"
                        )
                    }
                )
                
                otherwise { message ->
                    message.createReply(
                        content = "✅ Standard: ${message.content}",
                        sender = "standard-processor"
                    )
                }
            }
        }.conditional { /* recreate for testing */ }
        
        // 2. Reactive processing
        val baseAgent = buildAgent {
            id = "reactive-base"
            name = "Reactive Base Agent"
            
            handle { message ->
                message.createReply(
                    content = "Reactively processed: ${message.content}",
                    sender = id
                )
            }
        }
        
        val reactiveAgent = experimental { reactive(baseAgent) }.reactive(baseAgent)
        
        // 3. Agent composition
        val composedAgent = experimental {
            composition {
                agent(baseAgent)
                strategy(CompositionBuilder.CompositionStrategy.SEQUENTIAL)
            }
        }.composition {
            agent(baseAgent)
            strategy(CompositionBuilder.CompositionStrategy.SEQUENTIAL)
        }
        
        // Test results
        val urgentMessage = Message(content = "urgent: system failure", sender = "user")
        val normalMessage = Message(content = "normal message", sender = "user")
        
        println("🧪 Experimental features demonstrated successfully!")
    }
    
    @Test
    fun realWorldCustomerServiceExample() = runBlocking {
        println("\n🤖 === Real World Example: Customer Service Bot ===")
        
        // 1. Create tools
        tool("sentiment-analysis") {
            description = "Analyze customer sentiment"
            param("text", "string")
            
            execute { params ->
                val text = params["text"] as? String ?: ""
                val sentiment = when {
                    text.contains("angry") || text.contains("frustrated") -> "negative"
                    text.contains("happy") || text.contains("great") -> "positive"
                    else -> "neutral"
                }
                
                ToolResult.success(sentiment, metadata = mapOf("confidence" to "0.85"))
            }
        }
        
        tool("ticket-creation") {
            description = "Create support ticket"
            param("issue", "string")
            param("priority", "string")
            
            execute { params ->
                val issue = params["issue"] as? String ?: "Unknown issue"
                val priority = params["priority"] as? String ?: "normal"
                val ticketId = "TICKET-${System.currentTimeMillis()}"
                
                ToolResult.success(
                    "Ticket created: $ticketId",
                    metadata = mapOf(
                        "ticket_id" to ticketId,
                        "priority" to priority,
                        "status" to "open"
                    )
                )
            }
        }
        
        // 2. Create specialized agents
        val sentimentAgent = buildAgent {
            id = "sentiment-agent"
            name = "Sentiment Analyzer"
            tool("sentiment-analysis")
            
            handle { message ->
                val result = executeTool("sentiment-analysis", mapOf("text" to message.content))
                
                message.createReply(
                    content = "Sentiment: ${result.result}",
                    sender = id,
                    metadata = result.metadata
                )
            }
        }
        
        val ticketAgent = buildAgent {
            id = "ticket-agent"
            name = "Ticket Creator"
            tool("ticket-creation")
            
            handle { message ->
                val priority = if (message.metadata["sentiment"] == "negative") "high" else "normal"
                
                val result = executeTool("ticket-creation", mapOf(
                    "issue" to message.content,
                    "priority" to priority
                ))
                
                message.createReply(
                    content = result.result ?: "Ticket creation failed",
                    sender = id,
                    metadata = result.metadata
                )
            }
        }
        
        // 3. Create customer service flow
        val customerServiceFlow = flow {
            id = "customer-service-flow"
            name = "Customer Service Processing"
            description = "Analyzes sentiment and creates tickets for customer issues"
            
            step("sentiment-step", "sentiment-agent")
            step("ticket-step", "ticket-agent", condition = { message ->
                // Only create ticket if sentiment is negative or issue is complex
                message.metadata["sentiment"] == "negative" || message.content.length > 50
            })
        }
        
        // 4. Test with different scenarios
        val scenarios = listOf(
            "I'm really frustrated with your service, it's not working at all!",
            "Thanks for the great support, everything is working perfectly!",
            "I have a complex technical issue with multiple components not integrating properly and need detailed assistance to resolve this critical problem."
        )
        
        scenarios.forEachIndexed { index, scenario ->
            println("\n--- Scenario ${index + 1} ---")
            println("Customer: $scenario")
            
            val customerMessage = Message(content = scenario, sender = "customer-${index + 1}")
            val result = customerServiceFlow.execute(customerMessage)
            
            println("System: ${result.content}")
            if (result.metadata.containsKey("ticket_id")) {
                println("🎫 Ticket ID: ${result.metadata["ticket_id"]}")
                println("⚡ Priority: ${result.metadata["priority"]}")
            }
        }
    }
}

/**
 * 📋 Summary of Simplified DSL Structure
 */
fun dslSummary() {
    println("""
    🌶️ Spice Simplified DSL Structure:
    
    ✅ CORE DSL (Simple & Essential):
    
    1️⃣ AGENT:
       buildAgent {
         id = "my-agent"
         name = "My Agent"
         tool("tool-name")
         handle { message -> /* process */ }
       }
    
    2️⃣ FLOW:
       flow {
         id = "my-flow"
         name = "My Flow"
         step("agent-id")
         step("another-agent", condition = { /* when */ })
       }
    
    3️⃣ TOOL:
       tool("my-tool") {
         description = "Does something"
         param("input", "string")
         execute { params -> ToolResult.success("result") }
       }
    
    🧪 EXPERIMENTAL DSL (Opt-in):
    
    experimental {
       conditional { whenThen(...) }
       reactive(agent)
       composition { agent(...) }
       workflow("id") { agent(...) }
    }
    
    🔧 PLUGIN TOOLS (Hidden Complexity):
    
    pluginTool("name", "plugin-id") {
       mapInput("from", "to")
       execute { /* plugin logic */ }
    }
    
    toolChain("chain-name") {
       step("step1", "tool1")
       step("step2", "tool2")
    }
    
    💡 Benefits:
    - Simple 3-level hierarchy
    - Optional complexity
    - Hidden internals
    - Easy to learn and use
    """.trimIndent())
} 