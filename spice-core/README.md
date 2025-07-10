# 🌶️ Spice Framework

> **The Spice of Workflows** - JVM-based Multi-Agent Orchestration Framework

Spice는 JVM 생태계를 위한 혁신적인 멀티 에이전트 오케스트레이션 프레임워크입니다. 
복잡한 워크플로우를 **Message 기반 아키텍처**로 간단하고 우아하게 처리합니다.

## 🚀 핵심 특징

### 🎯 **Message-First Architecture**
- 모든 Agent 간 통신은 `Message`를 통해 이루어집니다
- 선언적 라우팅 규칙으로 복잡한 워크플로우 제약조건을 해결
- 타입 안전한 메시지 전달과 메타데이터 기반 라우팅

### 🔧 **Interface-First Design**
- `Agent`, `Tool`, `Orchestrator` 등 핵심 인터페이스 중심 설계
- 확장 가능한 도구 생태계
- 비즈니스 로직과 인프라의 완전한 분리

### 🌱 **JVM Native**
- Kotlin 코루틴 기반 비동기 처리
- Spring Boot 생태계와 완벽 통합
- Enterprise-ready 아키텍처

### 🎨 **Spice Flow Graph**
- 기존 DAG의 한계를 넘어선 사이클 허용 그래프
- 시각적 워크플로우 설계와 코드 실행의 완벽한 분리
- 실시간 피드백 루프 지원

## 📦 설치

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("io.github.spice:spice-core:0.1.0-SNAPSHOT")
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.spice</groupId>
    <artifactId>spice-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 🔥 빠른 시작

### 1. 간단한 Agent 생성
```kotlin
import io.github.spice.*

val promptAgent = PromptAgent(
    id = "summarizer",
    name = "문서 요약기",
    prompt = "다음 문서를 3줄로 요약해주세요:"
)

val resultAgent = ResultAgent(
    id = "formatter",
    name = "결과 포맷터"
)
```

### 2. 워크플로우 구성
```kotlin
val workflow = WorkflowBuilder()
    .addAgent(promptAgent)
    .addAgent(resultAgent)
    .addRoutingRule(MessageRoutingRule(
        sourceType = MessageType.PROMPT,
        targetType = MessageType.RESULT
    ))
    .build()
```

### 3. 실행
```kotlin
val initialMessage = Message(
    content = "긴 문서 내용...",
    sender = "user",
    type = MessageType.PROMPT
)

workflow.execute(initialMessage).collect { message ->
    println("${message.sender}: ${message.content}")
}
```

## 🎯 고급 기능

### Message 라우팅 규칙
```kotlin
// 복잡한 조건부 라우팅
val conditionalRule = MessageRoutingRule(
    sourceType = MessageType.DATA,
    targetType = MessageType.SYSTEM,
    condition = { message -> 
        message.metadata["targetType"] == "RESULT" 
    },
    transformer = { message ->
        message.withMetadata("autoInserted", "dataToResult")
               .withMetadata("nextTarget", "RESULT")
    }
)
```

### 사용자 정의 Tool
```kotlin
class CustomTool : BaseTool(
    name = "custom_processor",
    description = "사용자 정의 처리기",
    schema = ToolSchema(
        name = "custom_processor",
        description = "커스텀 처리 도구",
        parameters = mapOf(
            "input" to ParameterSchema("string", "입력 데이터", required = true)
        )
    )
) {
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val input = parameters["input"] as String
        val processed = processCustomLogic(input)
        return ToolResult.success(processed)
    }
}
```

### Spice Flow Graph 변환
```kotlin
// 멘타트 Spice Flow Graph JSON에서 워크플로우 생성
val workflow = WorkflowBuilder
    .fromMentatSpiceFlow(spiceFlowJson)
    .build()
```

## 🏗️ 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Agent A       │    │   Message       │    │   Agent B       │
│                 │───▶│   Router        │───▶│                 │
│ - processMessage│    │                 │    │ - processMessage│
│ - tools         │    │ - route()       │    │ - tools         │
│ - capabilities  │    │ - rules         │    │ - capabilities  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Tool          │    │   Orchestrator  │    │   SpiceWorkflow │
│                 │    │                 │    │                 │
│ - execute()     │    │ - orchestrate() │    │ - execute()     │
│ - schema        │    │ - getStatus()   │    │ - agents        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🤝 기여하기

Spice는 오픈소스 프로젝트입니다! 기여를 환영합니다.

1. Fork this repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 🌟 로드맵

- [ ] **v0.1.0**: 핵심 Message 아키텍처 완성
- [ ] **v0.2.0**: Spring Boot Auto-configuration 지원
- [ ] **v0.3.0**: 멘타트 Spice Flow Graph 완전 통합
- [ ] **v0.4.0**: 웹 UI 대시보드
- [ ] **v0.5.0**: 클러스터링 및 분산 처리 지원
- [ ] **v1.0.0**: Production Ready 릴리스

## 🙏 감사의 말

Spice는 [Microsoft AutoGen](https://github.com/microsoft/autogen)의 Message 아키텍처에서 영감을 받았습니다. 
JVM 생태계에서 더 나은 멀티 에이전트 경험을 제공하기 위해 탄생했습니다.

---

**Made with ❤️ by Spice Framework Team**

🌶️ **Add some spice to your workflows!** 