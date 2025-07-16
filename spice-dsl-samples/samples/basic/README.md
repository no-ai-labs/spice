# Basic DSL Samples

이 폴더는 Spice Core DSL의 기본 사용법을 보여주는 예제들을 포함합니다.

## 샘플 파일들

- `SimpleAgent.kt` - 가장 기본적인 Agent 생성과 사용법
- `SimpleTool.kt` - Tool 정의와 사용법  
- `SimpleFlow.kt` - Flow 생성과 실행법
- `AgentToolIntegration.kt` - Agent와 Tool을 연결하는 방법

## 실행 방법

```bash
# 프로젝트 루트에서
./gradlew :spice-dsl-samples:test --tests "*Basic*"
```

## 학습 순서

1. `SimpleAgent.kt` - Agent 생성의 기본
2. `SimpleTool.kt` - Tool 정의와 파라미터
3. `SimpleFlow.kt` - Flow를 통한 Agent 실행
4. `AgentToolIntegration.kt` - Agent와 Tool 연동 