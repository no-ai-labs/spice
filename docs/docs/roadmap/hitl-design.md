---
sidebar_position: 4
---

# HITL (Human-in-the-Loop) Design

HITL (Human-in-the-Loop) 기능은 Graph 실행 중 사람의 개입이 필요한 시점을 정의하고, 승인/거부/수정을 받아 진행하는 시스템입니다.

Microsoft Agent Framework의 Human-in-the-Loop 패턴을 참고하여 Spice에 맞게 설계합니다.

## 핵심 개념

### 1. HumanNode
사람의 입력을 기다리는 특수한 Node 타입:

```kotlin
class HumanNode(
    override val id: String,
    val prompt: String,  // 사용자에게 보여줄 메시지
    val options: List<HumanOption> = emptyList(),  // 선택지 (optional)
    val timeout: Duration? = null,  // 응답 대기 시간 (optional)
    val validator: ((HumanResponse) -> Boolean)? = null  // 입력 검증 (optional)
) : Node

data class HumanOption(
    val id: String,
    val label: String,
    val description: String? = null
)

data class HumanResponse(
    val nodeId: String,
    val selectedOption: String? = null,  // 선택한 옵션 ID
    val text: String? = null,  // 자유 입력 텍스트
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Instant = Instant.now()
)
```

### 2. 실행 상태 관리

```kotlin
enum class GraphExecutionState {
    RUNNING,           // 정상 실행 중
    WAITING_FOR_HUMAN, // 사람 입력 대기 중
    COMPLETED,         // 완료
    FAILED,            // 실패
    CANCELLED          // 취소됨
}

data class HumanInteraction(
    val nodeId: String,
    val prompt: String,
    val options: List<HumanOption>,
    val pausedAt: Instant,
    val expiresAt: Instant? = null
)
```

### 3. GraphRunner 확장

```kotlin
interface GraphRunner {
    // 기존 메서드들...

    /**
     * Resume execution after receiving human input.
     */
    suspend fun resumeWithHumanResponse(
        graph: Graph,
        checkpointId: String,
        response: HumanResponse,
        store: CheckpointStore
    ): SpiceResult<RunReport>

    /**
     * Get current human interactions waiting for response.
     */
    suspend fun getPendingInteractions(
        checkpointId: String,
        store: CheckpointStore
    ): SpiceResult<List<HumanInteraction>>
}
```

## 사용 예시

### 1. 기본 승인/거부 패턴

```kotlin
val approvalGraph = graph("approval-workflow") {
    agent("draft", draftAgent)  // 초안 작성

    // 사람이 검토하고 승인/거부
    humanNode(
        id = "review",
        prompt = "초안을 검토해주세요",
        options = listOf(
            HumanOption("approve", "승인", "초안을 승인하고 계속 진행"),
            HumanOption("reject", "거부", "초안을 거부하고 재작성"),
            HumanOption("edit", "수정", "직접 수정")
        )
    )

    // 조건부 분기
    edge("review", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }
    edge("review", "draft") { result ->
        (result.data as? HumanResponse)?.selectedOption == "reject"
    }
    edge("review", "manual-edit") { result ->
        (result.data as? HumanResponse)?.selectedOption == "edit"
    }

    agent("publish", publishAgent)
    agent("manual-edit", editorAgent)
}

// 실행
val runner = DefaultGraphRunner()
val checkpointStore = InMemoryCheckpointStore()

// 1단계: Graph 실행 시작 (HumanNode에서 멈춤)
val initialResult = runner.runWithCheckpoint(
    graph = approvalGraph,
    input = mapOf("content" to "Initial draft"),
    store = checkpointStore
).getOrThrow()

// 2단계: 대기 중인 인터랙션 확인
val pending = runner.getPendingInteractions(
    checkpointId = initialResult.checkpointId,
    store = checkpointStore
).getOrThrow()

println("대기 중: ${pending.first().prompt}")

// 3단계: 사람의 응답 제공
val humanResponse = HumanResponse(
    nodeId = "review",
    selectedOption = "approve"
)

// 4단계: 재개
val finalResult = runner.resumeWithHumanResponse(
    graph = approvalGraph,
    checkpointId = initialResult.checkpointId,
    response = humanResponse,
    store = checkpointStore
).getOrThrow()
```

### 2. 자유 입력 패턴

```kotlin
val inputGraph = graph("data-collection") {
    agent("explain", explainerAgent)  // 설명 제공

    // 자유 입력 받기
    humanNode(
        id = "get-input",
        prompt = "추가 정보를 입력해주세요",
        validator = { response ->
            response.text?.length?.let { it >= 10 } ?: false
        }
    )

    agent("process", processorAgent)  // 입력 처리
}
```

### 3. 타임아웃 패턴

```kotlin
val timeoutGraph = graph("urgent-approval") {
    agent("create-request", requestAgent)

    // 30분 내 응답 필요
    humanNode(
        id = "urgent-review",
        prompt = "긴급 요청 검토 (30분 제한)",
        timeout = Duration.ofMinutes(30),
        options = listOf(
            HumanOption("approve", "승인"),
            HumanOption("reject", "거부")
        )
    )

    // 타임아웃 시 자동 거부
    edge("urgent-review", "auto-reject") { result ->
        (result.data as? HumanResponse) == null  // timeout = null response
    }
    edge("urgent-review", "approved") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }
}
```

## 구현 계획

### Phase 1: 기본 HITL 지원
- [ ] `HumanNode` 구현
- [ ] `HumanResponse` 데이터 모델
- [ ] Checkpoint 통합 (HITL 대기 상태 저장)
- [ ] `resumeWithHumanResponse()` 구현
- [ ] 기본 테스트

### Phase 2: 고급 기능
- [ ] Timeout 지원
- [ ] Validator 지원
- [ ] Multiple choice vs Free text 구분
- [ ] `getPendingInteractions()` 구현
- [ ] 통합 테스트

### Phase 3: UI/UX 통합
- [ ] REST API for HITL interactions
- [ ] WebSocket 실시간 알림
- [ ] Dashboard 예제
- [ ] 문서 및 가이드

## 기술적 고려사항

### 1. Checkpoint 구조
```kotlin
data class Checkpoint(
    val id: String,
    val runId: String,
    val graphId: String,
    val currentNodeId: String,
    val state: Map<String, Any?>,
    val agentContext: AgentContext?,
    val timestamp: Instant,

    // HITL 지원
    val executionState: GraphExecutionState = GraphExecutionState.RUNNING,
    val pendingInteraction: HumanInteraction? = null
)
```

### 2. Thread Safety
- HumanNode 실행은 suspend 함수
- Checkpoint 저장은 atomic
- 동시성 고려 (여러 사람이 동시에 응답하는 경우)

### 3. Error Handling
- Timeout 처리
- 잘못된 응답 처리
- Checkpoint 복원 실패 처리

### 4. AgentContext 연계
```kotlin
// Context에 사람 응답 기록
val enrichedContext = ctx.agentContext?.copy(
    metadata = ctx.agentContext.metadata + mapOf(
        "human_response_${nodeId}" to response,
        "reviewed_by" to response.metadata["user_id"],
        "reviewed_at" to response.timestamp
    )
)
```

## 예상 사용 사례

1. **문서 승인 워크플로우**: 초안 → 검토 → 승인/거부 → 출판
2. **데이터 검증**: AI 분석 → 사람 확인 → 최종 결정
3. **위험 작업 승인**: 요청 생성 → 관리자 승인 → 실행
4. **협업 워크플로우**: AI 제안 → 사람 수정 → AI 재처리
5. **긴급 대응**: 자동 감지 → 사람 판단 → 조치 실행

## Microsoft AF vs Spice HITL

| 기능 | Microsoft AF | Spice |
|------|-------------|-------|
| Human Node | ✅ Built-in | ✅ 계획됨 |
| Checkpoint | ✅ | ✅ 이미 구현 |
| Resume | ✅ | ✅ 확장 필요 |
| Timeout | ✅ | 🔜 Phase 2 |
| Validation | ✅ | 🔜 Phase 2 |
| UI Integration | ✅ Dashboard | 🔜 Phase 3 |
| Multi-tenant | ❌ | ✅ AgentContext 통합 |

## 다음 단계

HITL 기능은 3단계로 구현 예정:

1. **Phase 1** (핵심): HumanNode, Resume 구현 → MVP
2. **Phase 2** (고급): Timeout, Validation → Production-ready
3. **Phase 3** (통합): REST API, UI 예제 → User-friendly

각 Phase는 독립적으로 테스트 가능하며, 점진적으로 기능을 추가합니다.
