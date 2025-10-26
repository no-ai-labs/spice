# 🚀 Week 1-2: Core Engine Implementation

## 목표

**빠르게 MVP 만들기!** 기본 Graph 실행만 되면 OK.

## 구현할 것

### 1. Core Abstractions (`spice-core/src/main/kotlin/io/github/noailabs/spice/graph/`)

#### `Node.kt`
```kotlin
sealed interface Node {
  val id: String
  suspend fun run(ctx: NodeContext): NodeResult
}

data class NodeContext(
  val graphId: String,
  val state: MutableMap<String, Any>,
  val metadata: MutableMap<String, Any> = mutableMapOf()
)

data class NodeResult(
  val data: Any?,
  val metadata: Map<String, Any> = emptyMap(),
  val nextEdges: List<String> = emptyList()
)
```

#### `Graph.kt`
```kotlin
data class Graph(
  val id: String,
  val nodes: Map<String, Node>,
  val edges: List<Edge>,
  val entryPoint: String
)

data class Edge(
  val from: String,
  val to: String,
  val condition: (NodeResult) -> Boolean = { true }
)
```

#### `nodes/AgentNode.kt`
```kotlin
class AgentNode(
  override val id: String,
  val agent: Agent  // 기존 Spice Agent 재사용!
) : Node {
  override suspend fun run(ctx: NodeContext): NodeResult {
    val comm = Comm(
      content = ctx.state["input"]?.toString() ?: "",
      from = "system"
    )
    val response = agent.processComm(comm)
    return NodeResult(
      data = response.content,
      metadata = mapOf("agentId" to agent.id)
    )
  }
}
```

#### `nodes/ToolNode.kt`
```kotlin
class ToolNode(
  override val id: String,
  val tool: Tool  // 기존 Spice Tool 재사용!
) : Node {
  override suspend fun run(ctx: NodeContext): NodeResult {
    val params = ctx.state["params"] as? Map<String, Any> ?: emptyMap()
    val result = tool.execute(params)
    return NodeResult(data = result)
  }
}
```

#### `nodes/OutputNode.kt`
```kotlin
class OutputNode(
  override val id: String,
  val selector: (NodeContext) -> Any = { it.state["result"] }
) : Node {
  override suspend fun run(ctx: NodeContext): NodeResult {
    return NodeResult(data = selector(ctx))
  }
}
```

### 2. Graph Runner (`runner/GraphRunner.kt`)

```kotlin
interface GraphRunner {
  suspend fun run(
    graph: Graph,
    input: Map<String, Any>
  ): RunReport
}

class DefaultGraphRunner : GraphRunner {
  override suspend fun run(
    graph: Graph,
    input: Map<String, Any>
  ): RunReport {
    val ctx = NodeContext(
      graphId = graph.id,
      state = input.toMutableMap()
    )

    val nodeReports = mutableListOf<NodeReport>()
    var currentNodeId = graph.entryPoint

    while (currentNodeId != null) {
      val node = graph.nodes[currentNodeId]!!
      val startTime = Clock.System.now()

      val result = node.run(ctx)

      // Store result in context
      ctx.state[currentNodeId] = result.data

      nodeReports.add(
        NodeReport(
          nodeId = currentNodeId,
          duration = Clock.System.now() - startTime,
          status = NodeStatus.SUCCESS,
          output = result.data
        )
      )

      // Find next node
      currentNodeId = graph.edges
        .firstOrNull { it.from == currentNodeId && it.condition(result) }
        ?.to
    }

    return RunReport(
      graphId = graph.id,
      status = RunStatus.SUCCESS,
      result = ctx.state["output"],
      nodeReports = nodeReports
    )
  }
}

data class RunReport(
  val graphId: String,
  val status: RunStatus,
  val result: Any?,
  val nodeReports: List<NodeReport>
)

enum class RunStatus { SUCCESS, FAILED, CANCELLED }

data class NodeReport(
  val nodeId: String,
  val duration: Duration,
  val status: NodeStatus,
  val output: Any?
)

enum class NodeStatus { SUCCESS, FAILED, SKIPPED }
```

### 3. DSL Builder (`dsl/GraphBuilder.kt`)

```kotlin
fun graph(id: String, block: GraphBuilder.() -> Unit): Graph {
  return GraphBuilder(id).apply(block).build()
}

class GraphBuilder(val id: String) {
  private val nodes = mutableMapOf<String, Node>()
  private val edges = mutableListOf<Edge>()
  private var lastNodeId: String? = null

  fun agent(id: String, agent: Agent) {
    nodes[id] = AgentNode(id, agent)
    connectToPrevious(id)
    lastNodeId = id
  }

  fun tool(id: String, tool: Tool) {
    nodes[id] = ToolNode(id, tool)
    connectToPrevious(id)
    lastNodeId = id
  }

  fun output(id: String = "output") {
    nodes[id] = OutputNode(id)
    connectToPrevious(id)
    lastNodeId = id
  }

  private fun connectToPrevious(currentId: String) {
    lastNodeId?.let { prev ->
      edges.add(Edge(from = prev, to = currentId))
    }
  }

  fun build(): Graph {
    return Graph(
      id = id,
      nodes = nodes,
      edges = edges,
      entryPoint = nodes.keys.first()
    )
  }
}
```

## 테스트 코드

```kotlin
@Test
fun `test simple graph execution`() = runTest {
  // Given: Simple agent
  val testAgent = buildAgent {
    id = "test-agent"
    name = "Test Agent"
    handle { comm ->
      comm.reply("Hello, ${comm.content}!", id)
    }
  }

  // When: Create and run graph
  val graph = graph("test-graph") {
    agent("greeter", testAgent)
    output("result")
  }

  val runner = DefaultGraphRunner()
  val report = runner.run(graph, mapOf("input" to "World"))

  // Then: Check result
  assertEquals(RunStatus.SUCCESS, report.status)
  assertEquals("Hello, World!", report.result)
}
```

## 작업 순서

1. ✅ 폴더 생성: `spice-core/src/main/kotlin/io/github/noailabs/spice/graph/`
2. ✅ `Node.kt`, `Graph.kt` 작성
3. ✅ `nodes/AgentNode.kt`, `nodes/ToolNode.kt`, `nodes/OutputNode.kt` 작성
4. ✅ `runner/GraphRunner.kt` 작성
5. ✅ `dsl/GraphBuilder.kt` 작성
6. ✅ 테스트 작성 + 실행
7. ✅ PR 생성: "feat(graph): add core graph execution engine"

## 성공 기준

- [x] 간단한 Agent → Output 그래프가 실행됨
- [x] DSL이 직관적임 (`graph { agent("a", myAgent); output() }`)
- [x] 기존 Agent/Tool을 그대로 재사용 가능
- [x] 테스트 통과

## 나중에 할 것 (지금은 NO!)

- ❌ Middleware (Phase 2에)
- ❌ Checkpoint (Phase 2에)
- ❌ DecisionNode/ParallelNode (Phase 2에)
- ❌ Migration tool (Phase 3에)

---

**목표: MVP를 빠르게! 복잡한 건 나중에!** 🚀
