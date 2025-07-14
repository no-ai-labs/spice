package io.github.spice.model

import io.github.spice.model.clients.OpenAIClient
import io.github.spice.model.clients.ClaudeClient
import io.github.spice.agents.model.createGPTAgent
import io.github.spice.agents.model.createClaudeAgent
import io.github.spice.agents.model.GPTAgent
import io.github.spice.agents.model.ClaudeAgent
import io.github.spice.*
import kotlinx.coroutines.runBlocking

/**
 * 🎯 ModelClient system 사용 예제
 * 
 * 새로운 ModelClient architecture를 사용하여 context-aware Agent를 
 * 구성하고 사용하는 방법을 보여줍니다.
 */
class ModelClientUsageExample {
    
    fun demonstrateBasicUsage() = runBlocking {
        println("🚀 ModelClient 시스템 기본 사용법")
        println("=" * 50)
        
        // 1. OpenAI client setting
        val openaiConfig = ModelClientConfig(
            apiKey = "your-openai-api-key",
            defaultModel = "gpt-3.5-turbo"
        )
        val openaiClient = OpenAIClient(openaiConfig)
        
        // 2. Claude client setting
        val claudeConfig = ModelClientConfig(
            apiKey = "your-anthropic-api-key",
            defaultModel = "claude-3-sonnet-20240229"
        )
        val claudeClient = ClaudeClient(claudeConfig)
        
        // 3. GPT Agent generation
        val gptAgent = createGPTAgent(
            client = openaiClient,
            systemPrompt = "You are a helpful coding assistant. Be concise and practical.",
            bufferSize = 15,
            id = "coding-gpt",
            name = "Coding GPT Assistant"
        )
        
        // 4. Claude Agent generation
        val claudeAgent = createClaudeAgent(
            client = claudeClient,
            systemPrompt = "You are Claude, an AI assistant specialized in analysis and reasoning.",
            bufferSize = 20,
            id = "analysis-claude",
            name = "Analysis Claude Assistant"
        )
        
        println("✅ Agent 생성 완료")
        println("GPT Agent: ${gptAgent.name} (${gptAgent.id})")
        println("Claude Agent: ${claudeAgent.name} (${claudeAgent.id})")
        println()
        
        // 5. conversation 시뮬레이션
        simulateConversation(gptAgent, claudeAgent)
    }
    
    private suspend fun simulateConversation(gptAgent: GPTAgent, claudeAgent: ClaudeAgent) {
        println("💬 대화 시뮬레이션 시작")
        println("-" * 30)
        
        // GPT Agent와 conversation
        println("🤖 GPT Agent와 대화:")
        val gptMessages = listOf(
            "Hello! Can you help me with Python coding?",
            "How do I create a simple web scraper?",
            "What about error handling in the scraper?"
        )
        
        gptMessages.forEach { content ->
            val message = Message(
                id = "msg-${System.currentTimeMillis()}",
                content = content,
                sender = "user",
                type = MessageType.TEXT
            )
            
            println("👤 User: $content")
            
            // Mock response for demonstration
            val response = Message(
                id = "resp-${System.currentTimeMillis()}",
                content = "Mock GPT response to: $content",
                sender = gptAgent.id,
                type = MessageType.TEXT
            )
            
            println("🤖 GPT: ${response.content}")
            println()
        }
        
        // Claude Agent와 conversation
        println("🧠 Claude Agent와 대화:")
        val claudeMessages = listOf(
            "Can you analyze this data trend?",
            "What are the implications of this analysis?",
            "How would you summarize the key findings?"
        )
        
        claudeMessages.forEach { content ->
            println("👤 User: $content")
            
            // Mock response for demonstration
            val response = Message(
                id = "resp-${System.currentTimeMillis()}",
                content = "Mock Claude response to: $content",
                sender = claudeAgent.id,
                type = MessageType.TEXT
            )
            
            println("🧠 Claude: ${response.content}")
            println()
        }
        
        // Agent status check
        showAgentStatus(gptAgent, claudeAgent)
    }
    
    private suspend fun showAgentStatus(gptAgent: GPTAgent, claudeAgent: ClaudeAgent) {
        println("📊 Agent 상태 정보")
        println("-" * 30)
        
        val gptStatus = gptAgent.getAgentStatus()
        val claudeStatus = claudeAgent.getAgentStatus()
        
        println("🤖 GPT Agent 상태:")
        gptStatus.forEach { (key: String, value: Any) ->
            println("  $key: $value")
        }
        println()
        
        println("🧠 Claude Agent 상태:")
        claudeStatus.forEach { (key: String, value: Any) ->
            println("  $key: $value")
        }
        println()
        
        // context summary
        val gptContextSummary = gptAgent.getContextSummary()
        val claudeContextSummary = claudeAgent.getContextSummary()
        
        println("📝 컨텍스트 요약:")
        println("GPT: ${gptContextSummary.getSummaryText()}")
        println("Claude: ${claudeContextSummary.getSummaryText()}")
    }
    
    fun demonstrateAdvancedFeatures() = runBlocking {
        println("\n🔧 고급 기능 시연")
        println("=" * 50)
        
        // Mock client로 시연
        val mockClient = MockModelClientDemo()
        val agent = createGPTAgent(client = mockClient)
        
        // 1. context management
        println("1. 컨텍스트 관리:")
        agent.addSystemMessage("You are now in debug mode.")
        agent.updateSystemPrompt("You are a debugging assistant.")
        
        val contextSummary = agent.getContextSummary()
        println("   ${contextSummary.getSummaryText()}")
        
        // 2. conversation history 조회
        println("\n2. 대화 히스토리:")
        val history = agent.getConversationHistory()
        history.forEach { message ->
            println("   ${message.role}: ${message.content}")
        }
        
        // 3. client status check
        println("\n3. 클라이언트 상태:")
        val clientStatus = agent.getClientStatus()
        println("   ${clientStatus.getSummary()}")
        
        // 4. context 초기화
        println("\n4. 컨텍스트 초기화:")
        agent.clearContext()
        val clearedSummary = agent.getContextSummary()
        println("   초기화 후: ${clearedSummary.getSummaryText()}")
    }
    
    fun demonstrateClaudeSpecialFeatures() = runBlocking {
        println("\n🎯 Claude 특화 기능 시연")
        println("=" * 50)
        
        val mockClient = MockModelClientDemo()
        val claudeAgent = createClaudeAgent(client = mockClient)
        
        // 1. code analytical
        println("1. 코드 분석:")
        val codeToAnalyze = """
            def fibonacci(n):
                if n <= 1:
                    return n
                return fibonacci(n-1) + fibonacci(n-2)
        """.trimIndent()
        
        val codeAnalysis = claudeAgent.analyzeContent(codeToAnalyze, "code")
        println("   분석 결과: $codeAnalysis")
        
        // 2. 텍스트 summary
        println("\n2. 텍스트 요약:")
        val textToSummarize = """
            The ModelClient architecture provides a clean separation between 
            Agent logic and LLM communication. This allows for better testing,
            easier model switching, and improved maintainability. The context
            management system ensures that conversation history is preserved
            within configurable buffer limits.
        """.trimIndent()
        
        val summary = claudeAgent.summarizeContent(textToSummarize, "bullet")
        println("   요약 결과: $summary")
        
        // 3. 일반 analytical
        println("\n3. 일반 분석:")
        val dataToAnalyze = "Sales: Q1=100, Q2=150, Q3=200, Q4=180"
        val dataAnalysis = claudeAgent.analyzeContent(dataToAnalyze, "data")
        println("   분석 결과: $dataAnalysis")
    }
    
    fun demonstrateModelComparison() = runBlocking {
        println("\n⚖️ 모델 비교 시연")
        println("=" * 50)
        
        val mockClient = MockModelClientDemo()
        val gptAgent = createGPTAgent(client = mockClient, name = "GPT Assistant")
        val claudeAgent = createClaudeAgent(client = mockClient, name = "Claude Assistant")
        
        val testPrompt = "Explain the benefits of functional programming"
        
        println("📝 테스트 프롬프트: $testPrompt")
        println()
        
        // GPT response
        val gptMessage = Message(
            id = "comparison-gpt",
            content = testPrompt,
            sender = "user",
            type = MessageType.TEXT
        )
        
        println("🤖 GPT 응답:")
        // Mock response would be generated here
        println("   Mock GPT response to: $testPrompt")
        
        // Claude response
        val claudeMessage = Message(
            id = "comparison-claude",
            content = testPrompt,
            sender = "user",
            type = MessageType.TEXT
        )
        
        println("\n🧠 Claude 응답:")
        // Mock response would be generated here
        println("   Mock Claude response to: $testPrompt")
        
        // performance 비교
        println("\n📊 성능 비교:")
        val gptClientStatus = gptAgent.getClientStatus()
        val claudeClientStatus = claudeAgent.getClientStatus()
        
        println("GPT Client: ${gptClientStatus.getSummary()}")
        println("Claude Client: ${claudeClientStatus.getSummary()}")
    }
}

/**
 * 🎭 Mock ModelClient for demonstration (Usage Example)
 */
class MockModelClientDemo : ModelClient {
    override val id: String = "mock-client"
    override val modelName: String = "mock-model"
    override val description: String = "Mock client for demonstration"
    
    private var requestCount = 0
    private var successCount = 0
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        requestCount++
        successCount++
        
        val responseContent = "Mock response to: ${messages.lastOrNull()?.content ?: "no content"}"
        
        return ModelResponse.success(
            content = responseContent,
            usage = TokenUsage(
                inputTokens = messages.sumOf { it.content.length },
                outputTokens = responseContent.length
            ),
            metadata = mapOf(
                "provider" to "mock",
                "model" to modelName
            ),
            responseTimeMs = 100
        )
    }
    
    override suspend fun chatStream(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ) = kotlinx.coroutines.flow.flow {
        val response = chat(messages, systemPrompt, metadata)
        emit(ModelStreamChunk(
            content = response.content,
            isComplete = true,
            index = 0
        ))
    }
    
    override fun isReady(): Boolean = true
    
    override suspend fun getStatus(): ClientStatus {
        return ClientStatus(
            clientId = id,
            status = "READY",
            totalRequests = requestCount,
            successfulRequests = successCount,
            averageResponseTimeMs = 100.0,
            metadata = mapOf(
                "model" to modelName,
                "provider" to "mock"
            )
        )
    }
}

/**
 * 🚀 execution possible 메인 function
 */
fun main() {
    val example = ModelClientUsageExample()
    
    example.demonstrateBasicUsage()
    example.demonstrateAdvancedFeatures()
    example.demonstrateClaudeSpecialFeatures()
    example.demonstrateModelComparison()
    
    println("\n🎉 ModelClient 시스템 시연 완료!")
    println("이제 Autogen 스타일의 context-aware Agent를 사용할 수 있습니다!")
}

// 문자열 반복 확장 function
private operator fun String.times(n: Int): String = this.repeat(n) 