package io.github.spice.model

import io.github.spice.agents.model.*
import io.github.spice.model.clients.OpenAIClient
import io.github.spice.model.clients.ClaudeClient
import io.github.spice.*
import kotlinx.coroutines.runBlocking

/**
 * 🎯 WizardAgent 사용 예제
 * 
 * 새로운 ModelClient 기반 WizardAgent의 사용법을 보여줍니다.
 * especially ModelContext 공유와 동적 model 전환 feature을 중점적으로 시연합니다.
 */
class WizardAgentUsageExample {
    
    fun demonstrateBasicUsage() = runBlocking {
        println("🧙‍♂️ WizardAgent 기본 사용법")
        println("=" * 50)
        
        // 1. Mock client로 WizardAgent generation
        val normalClient = MockModelClientDemo("gpt-3.5-turbo")
        val wizardClient = MockModelClientDemo("gpt-4")
        
        val wizardAgent = createWizardAgent(
            normalClient = normalClient,
            wizardClient = wizardClient,
            systemPrompt = "You are a helpful AI assistant that can adjust intelligence based on task complexity.",
            bufferSize = 20,
            id = "demo-wizard",
            name = "Demo Wizard Agent"
        )
        
        println("✅ WizardAgent 생성 완료")
        println("일반 모드: ${normalClient.modelName}")
        println("위저드 모드: ${wizardClient.modelName}")
        println()
        
        // 2. 일반 모드 conversation 시뮬레이션
        demonstrateNormalMode(wizardAgent)
        
        // 3. 위저드 모드 conversation 시뮬레이션
        demonstrateWizardMode(wizardAgent)
        
        // 4. context 공유 시연
        demonstrateContextSharing(wizardAgent)
        
        // 5. statistics 및 status check
        showWizardStats(wizardAgent)
    }
    
    private suspend fun demonstrateNormalMode(wizardAgent: WizardAgent) {
        println("🐂 일반 모드 시연")
        println("-" * 30)
        
        val normalMessages = listOf(
            "안녕하세요!",
            "오늘 날씨가 어때요?",
            "간단한 질문이 있어요."
        )
        
        normalMessages.forEach { content ->
            val message = Message(
                id = "normal-${System.currentTimeMillis()}",
                content = content,
                sender = "user",
                type = MessageType.TEXT
            )
            
            println("👤 User: $content")
            
            // Mock response for demonstration
            println("🤖 Agent: Mock response from gpt-3.5-turbo to: $content")
            println("   📊 Mode: Normal | Model: gpt-3.5-turbo")
            println()
        }
    }
    
    private suspend fun demonstrateWizardMode(wizardAgent: WizardAgent) {
        println("🧙‍♂️ 위저드 모드 시연")
        println("-" * 30)
        
        val wizardMessages = listOf(
            "복잡한 알고리즘을 분석해주세요.",
            "이 데이터를 시각화해서 보여주세요.",
            "상세한 아키텍처 설계를 도와주세요."
        )
        
        wizardMessages.forEach { content ->
            val message = Message(
                id = "wizard-${System.currentTimeMillis()}",
                content = content,
                sender = "user",
                type = MessageType.TEXT
            )
            
            println("👤 User: $content")
            println("🔮 *Complexity detected - Upgrading to Wizard Mode*")
            println("🧙‍♂️ Agent: *Wizard Mode Activated*")
            println("   Mock response from gpt-4 to: $content")
            println("   📊 Mode: Wizard | Model: gpt-4")
            println()
        }
    }
    
    private suspend fun demonstrateContextSharing(wizardAgent: WizardAgent) {
        println("🔄 컨텍스트 공유 시연")
        println("-" * 30)
        
        println("1️⃣ 첫 번째 메시지 (일반 모드)")
        println("👤 User: 제 이름은 김철수입니다.")
        println("🤖 Agent: 안녕하세요 김철수님! (일반 모드)")
        println("   📊 Context Size: 2 messages")
        println()
        
        println("2️⃣ 두 번째 메시지 (위저드 모드 트리거)")
        println("👤 User: 제 이름을 기억하면서 복잡한 데이터 분석을 도와주세요.")
        println("🔮 *Complexity detected - Upgrading to Wizard Mode*")
        println("🧙‍♂️ Agent: *Wizard Mode Activated*")
        println("   물론입니다 김철수님! 복잡한 분석을 도와드리겠습니다. (위저드 모드)")
        println("   📊 Context Size: 4 messages (이전 대화 포함)")
        println()
        
        println("✨ 핵심: 두 모드가 동일한 ModelContext를 공유하여 대화 연속성 유지!")
    }
    
    private suspend fun showWizardStats(wizardAgent: WizardAgent) {
        println("📊 WizardAgent 통계")
        println("-" * 30)
        
        // Mock stats for demonstration
        val mockStats = mapOf(
            "wizard_mode_usage_count" to 3,
            "normal_mode_usage_count" to 3,
            "total_usage_count" to 6,
            "current_mode" to "normal",
            "normal_client" to "gpt-3.5-turbo",
            "wizard_client" to "gpt-4",
            "upgrade_conditions_count" to 24,
            "context_summary" to "Messages: 6/20 (30.0%), Roles: user:3, assistant:3, Tokens: ~450",
            "is_ready" to true
        )
        
        mockStats.forEach { (key, value) ->
            println("$key: $value")
        }
        
        println()
        println("🧠 현재 지능 수준:")
        println("🐂 Normal Mode (Standard Intelligence) - Using gpt-3.5-turbo")
        
        println()
        println("📝 업그레이드 조건:")
        println("🎨 Visualization: 시각화, visualize, 그래프, graph")
        println("🔧 Technical: 코드, code, 프로그래밍, programming")
        println("📊 Analysis: 복잡한, complex, 분석, analyze")
        println("📚 Research: 연구, research, 조사, investigation")
    }
    
    fun demonstrateOpenAIWizardAgent() = runBlocking {
        println("\n🔥 OpenAI WizardAgent 생성 예제")
        println("=" * 50)
        
        // 실제 OpenAI 키가 있다면 이렇게 사용할 수 있습니다
        println("// 실제 사용 예제:")
        println("""
        val wizardAgent = createOpenAIWizardAgent(
            apiKey = "your-openai-api-key",
            normalModel = "gpt-3.5-turbo",
            wizardModel = "gpt-4",
            systemPrompt = "You are a helpful AI assistant.",
            bufferSize = 20,
            id = "openai-wizard",
            name = "OpenAI Wizard Agent"
        )
        """.trimIndent())
        
        println("\n// 메시지 처리:")
        println("""
        val message = Message(
            id = "msg-1",
            content = "복잡한 알고리즘을 분석해주세요",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val response = wizardAgent.processMessage(message)
        // 자동으로 위저드 모드로 전환되어 GPT-4로 processing됩니다
        """.trimIndent())
    }
    
    fun demonstrateMixedWizardAgent() = runBlocking {
        println("\n🎭 혼합 제공자 WizardAgent 예제")
        println("=" * 50)
        
        println("// 일반 모드는 OpenAI, 위저드 모드는 Claude 사용:")
        println("""
        val normalClient = OpenAIClient(
            config = ModelClientConfig(
                apiKey = "openai-key",
                defaultModel = "gpt-3.5-turbo"
            )
        )
        
        val wizardClient = ClaudeClient(
            config = ModelClientConfig(
                apiKey = "anthropic-key", 
                defaultModel = "claude-3-sonnet-20240229"
            )
        )
        
        val mixedWizardAgent = createMixedWizardAgent(
            normalClient = normalClient,
            wizardClient = wizardClient,
            systemPrompt = "You are a versatile AI assistant.",
            bufferSize = 15,
            customConditions = listOf("분석", "설계", "리뷰"),
            id = "mixed-wizard",
            name = "Mixed Wizard Agent"
        )
        """.trimIndent())
        
        println("\n✨ 장점:")
        println("- 비용 효율성: 일반 작업은 저렴한 모델, 복잡한 작업은 고성능 모델")
        println("- 최적화: 각 제공자의 강점을 활용 (OpenAI의 속도, Claude의 분석력)")
        println("- 연속성: 단일 ModelContext로 대화 히스토리 유지")
    }
    
    fun demonstrateAdvancedFeatures() = runBlocking {
        println("\n🚀 고급 기능 시연")
        println("=" * 50)
        
        val normalClient = MockModelClientDemo("gpt-3.5-turbo")
        val wizardClient = MockModelClientDemo("gpt-4")
        
        val wizardAgent = createWizardAgent(
            normalClient = normalClient,
            wizardClient = wizardClient,
            bufferSize = 10
        )
        
        // 1. user 정의 업그레이드 조건
        println("1. 사용자 정의 업그레이드 조건:")
        println("   customConditions = [\"특별한\", \"중요한\", \"긴급한\"]")
        
        // 2. 강제 위저드 모드
        println("\n2. 강제 위저드 모드:")
        println("   forceWizardMode = true")
        
        // 3. context management
        println("\n3. 컨텍스트 관리:")
        println("   - clearContext(): 대화 히스토리 초기화")
        println("   - getContextSummary(): 컨텍스트 요약 정보")
        println("   - getConversationHistory(): 전체 대화 히스토리")
        println("   - addSystemMessage(): 시스템 메시지 추가")
        
        // 4. system prompt update
        println("\n4. 동적 시스템 프롬프트:")
        println("   updateSystemPrompt(\"새로운 역할 정의\")")
        
        // 5. 실시간 statistics
        println("\n5. 실시간 통계:")
        println("   - 모드별 사용 횟수")
        println("   - 평균 응답 시간")
        println("   - 토큰 사용량")
        println("   - 컨텍스트 사용률")
    }
}

/**
 * 🎭 데모용 Mock ModelClient
 */
class MockModelClientDemo(override val modelName: String) : ModelClient {
    override val id: String = "mock-$modelName"
    override val description: String = "Mock client for $modelName"
    
    private var requestCount = 0
    private var successCount = 0
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        requestCount++
        successCount++
        
        val responseContent = "Mock response from $modelName to: ${messages.lastOrNull()?.content ?: "no content"}"
        
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
            responseTimeMs = if (modelName.contains("gpt-4")) 2000 else 500
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
            averageResponseTimeMs = if (modelName.contains("gpt-4")) 2000.0 else 500.0,
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
    val example = WizardAgentUsageExample()
    
    example.demonstrateBasicUsage()
    example.demonstrateOpenAIWizardAgent()
    example.demonstrateMixedWizardAgent()
    example.demonstrateAdvancedFeatures()
    
    println("\n🎉 WizardAgent 시연 완료!")
    println("이제 ModelContext를 공유하는 동적 지능 Agent를 사용할 수 있습니다!")
}

// 문자열 반복 확장 function
private operator fun String.times(n: Int): String = this.repeat(n) 