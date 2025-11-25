# Redis Client 추상화 로드맵

> **작성일**: 2025-11-19  
> **작성자**: Codex  
> **목표**: Jedis에 직결된 Spice 런타임 의존성을 추상화하여 Redisson/Lettuce 등의 대체 구현을 플러그인 형태로 교체 가능하도록 만드는 로드맵.

## 1. 배경 및 문제

현재 Spice Core 및 dogfooding 프로젝트(kai-core, melange)는 Jedis 기반 컴포넌트를 사용한다:

- CheckpointStore (InMemory/Redis)
- ToolCallEventBus (Redis Streams)
- DeadLetterQueue (Redis 기반)
- HITL Resume 경로에서 쓰는 checkpoint 로딩/갱신 로직

장점: 프레임워크와 애플리케이션이 동일한 경로를 밟아 버그 재현이 빠름.  
단점: 클라이언트 바꿔야 할 때 전체 모듈을 갈아엎어야 하고, Lettuce/Redisson/Jedis 를 병행 검증하기 어렵다.

## 2. 요구사항

1. Envelope, EventBus, DLQ, Checkpoint, HITL Resume 등 Redis 의존 모듈에 “클라이언트 추상화” 계층 도입.
2. Jedis 구현을 기본(default)으로 제공하되, 같은 인터페이스로 Lettuce/Redisson 구현을 작성할 수 있도록 설계.
3. Spring Boot starter 에서는 `spice.redis.client=jodis/lettuce/redisson` 같은 설정으로 구현을 선택.
4. 기존 kai-core/melange 코드 변경 없이도 새로운 구현을 스위칭 가능하게 유지 (Bean wiring/Factory 패턴 활용).
5. 추상화 레이어 도입 후에도 dogfooding 시 Jedis를 기본값으로 사용하여 회귀 없이 진행.

## 3. 단계별 로드맵

### Phase 1 – 인터페이스 추출
- [ ] `CheckpointStore`, `DeadLetterQueue`, `ToolCallEventBus` 등에서 Redis-specific 코드를 Helper로 분리.
- [ ] “Connection abstraction” (예: `RedisConnectionAdapter`) 설계: get/set, hash, stream, pub/sub 등 필요한 연산만 노출.
- [ ] Jedis 구현(`JedisConnectionAdapter`) 작성, 기존 코드가 adapter만 바라보게 리팩터링.

### Phase 2 – Spring Boot 선택지 제공
- [ ] Starter AutoConfiguration에서 `@ConditionalOnProperty` 또는 profile 로 Jedis/Lettuce 구현 선택 가능하게 함.
- [ ] LettuceConnectionAdapter (Spring Data Redis 기반) 프로토타입 작성, unit test 로 커버.
- [ ] 문서에 `spice.redis.client` 설정 방법 추가.

### Phase 3 – 고급 기능
- [ ] Redisson 기반 구현 (분산락/Reactive 등 필요한 경우).
- [ ] 모듈별로 “동일한 API로 다른 백엔드” 전략 확장 (예: EventBus → Redis Streams / Kafka / InMemory).
- [ ] 모니터링/메트릭 인터페이스 통합 (pool 상태, 명령 지연 등).

### Phase 4 – Dogfooding & Migration
- [ ] melange 에서 Lettuce/Redisson 구현 실험, 성능/안정성 비교.
- [ ] kai-core 에서 특정 환경(예: Async/HITL-heavy) 에 Lettuce 구현 적용하여 필드 데이터 수집.
- [ ] 피드백 반영 후 1.1.x 릴리스에서 “Redis Client Plugin” 기능 공식화.

## 4. 리스크 및 고려 사항

- **추상화 과도 복잡도**: Redis Streams, Lua, Pub/Sub 등 서로 다른 기능을 맞추기 까다로움 → 최소 필요 연산만 추출.
- **레거시와 호환**: Jedis-specific API (Pipeline, Script) 를 사용하던 코드가 있다면 래퍼 추가 필요.
- **테스트 부채**: 백엔드별 통합 테스트를 CI에서 돌릴 수 있도록 Docker Compose / Testcontainers 기반 환경 준비.

## 5. Next Steps

1. Phase 1 착수: `RedisConnectionAdapter` 스펙 정의 및 Jedis 버전 구현.
2. 문서(`redis-client-selection.md`)에 로드맵 링크 추가.
3. kai-core/melange roadmap 문서에도 “Redis Client abstraction” 항목을 넣어 추적.

