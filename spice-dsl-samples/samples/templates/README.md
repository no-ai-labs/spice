# DSL Templates Examples

**Spice DSL의 템플릿 함수들을 활용한 빠른 개발 예제**

이 폴더는 Spice Framework에서 제공하는 다양한 템플릿 함수들을 사용하여 빠르게 Agent, Tool, Flow를 생성하는 방법을 보여줍니다.

## 🚀 템플릿의 장점

- **빠른 프로토타이핑**: 몇 줄의 코드로 완전한 기능 구현
- **모범 사례**: 검증된 패턴과 구조 제공
- **일관성**: 표준화된 구성과 명명 규칙
- **확장성**: 템플릿을 기반으로 추가 기능 개발

## 📂 파일 구조

| 파일 | 설명 |
|------|------|
| `TemplateUsageExamples.kt` | 모든 템플릿 사용법 종합 예제 |

## 🔧 제공되는 템플릿

### Agent 템플릿

```kotlin
// 기본 응답 에이전트
val agent = defaultAgent("My Agent")

// 메시지 에코 에이전트  
val echo = echoAgent("Echo Bot")

// 로깅 에이전트
val logger = loggingAgent("Logger", "[INFO]")

// 메시지 변환 에이전트
val transformer = transformAgent("Transformer") { input -> 
    input.uppercase() 
}
```

### Tool 템플릿

```kotlin
// 스트리밍 처리 도구
val processor = streamingTool("processor") { input ->
    "Processed: $input"
}

// 계산기 도구
val calculator = calculatorTool()

// 텍스트 처리 도구
val textProcessor = textProcessorTool()
```

### Flow 템플릿

```kotlin
// 로깅 플로우
val loggingFlow = loggingFlow("Log Flow", agentIds)

// 파이프라인 플로우
val pipeline = pipelineFlow("Pipeline", agentIds)

// 조건부 라우팅 플로우
val router = conditionalRoutingFlow("Router", conditions, defaultAgent)
```

### 완성된 시나리오 템플릿

```kotlin
// 고객 서비스 시스템
val customerService = createCustomerServiceTemplate()
customerService.registerAll()

// 데이터 처리 파이프라인
val dataProcessing = createDataProcessingTemplate()
dataProcessing.registerAll()
```

## 💡 사용법

### 1. 개별 템플릿 사용

```kotlin
// 1단계: 템플릿으로 생성
val myAgent = defaultAgent("Customer Support")

// 2단계: 등록
AgentRegistry.register(myAgent)

// 3단계: 사용
val response = myAgent.processMessage(Message(content = "Hello", sender = "user"))
```

### 2. 완성된 시나리오 사용

```kotlin
// 1단계: 시나리오 템플릿 생성
val customerService = createCustomerServiceTemplate()

// 2단계: 모든 구성 요소 등록
customerService.registerAll()

// 3단계: 플로우 실행
val result = customerService.flow.execute(
    Message(
        content = "I'm frustrated with your service!", 
        sender = "customer",
        metadata = mapOf("customer" to "user@email.com")
    )
)

// 결과: 감정 분석 → 응답 생성 → 필요시 티켓 생성
```

### 3. 빠른 프로토타이핑

```kotlin
// 1분 안에 챗봇 만들기
val chatbot = defaultAgent("FAQ Bot")
val faqTool = streamingTool("faq") { question ->
    when {
        question.contains("hello") -> "Hello! How can I help?"
        question.contains("help") -> "I'm here to assist!"
        else -> "Could you be more specific?"
    }
}

val smartBot = buildAgent {
    id = "smart-bot"
    tools("faq")
    handle { message ->
        val response = faqTool.execute(mapOf("input" to message.content))
        Message(content = response.result, sender = id)
    }
}
```

## 🎯 실습 과제

### 초급자

1. **기본 에이전트 만들기**
   - `defaultAgent`로 인사 에이전트 생성
   - 다양한 메시지로 테스트

2. **도구 활용하기**
   - `calculatorTool`로 계산기 만들기
   - `textProcessorTool`로 텍스트 변환기 만들기

### 중급자

3. **플로우 구성하기**
   - 3개 에이전트로 파이프라인 만들기
   - 조건부 라우팅 구현하기

4. **커스텀 템플릿 만들기**
   - 자신만의 `emailAgent` 템플릿 함수 작성
   - 특정 도메인용 도구 템플릿 개발

### 고급자

5. **완성된 시나리오 개발**
   - 주문 처리 시스템 템플릿 만들기
   - 콘텐츠 모더레이션 워크플로우 구현

6. **템플릿 확장하기**
   - 기존 `CustomerServiceTemplate`에 기능 추가
   - 새로운 시나리오 템플릿 설계

## 📖 학습 리소스

- [DSL Summary 기능](../../src/main/kotlin/io/github/spice/dsl/DSLSummary.kt) - 생성된 구성 요소 문서화
- [Core DSL 기초](../basic/) - 기본 DSL 사용법
- [Flow 예제](../flow/) - 플로우 패턴 이해

## 🔍 디버깅 팁

```kotlin
// 1. 템플릿 목록 확인
printAvailableTemplates()

// 2. 생성된 구성 요소 확인
val agent = defaultAgent("Test")
println(agent.describe())

// 3. 전체 환경 상태 확인
println(describeAllComponents())

// 4. 건강 상태 체크
val issues = checkDSLHealth()
if (issues.isNotEmpty()) {
    println("Issues found: ${issues.joinToString()}")
}
```

## 🚀 빠른 시작

```bash
# 템플릿 예제 실행
./gradlew :spice-dsl-samples:test --tests "*Template*"

# 또는 직접 실행
kotlin spice-dsl-samples/samples/templates/TemplateUsageExamples.kt
```

---

**💡 Tip**: 템플릿은 학습과 프로토타이핑을 위한 도구입니다. 프로덕션 환경에서는 요구사항에 맞게 커스터마이징하여 사용하세요! 