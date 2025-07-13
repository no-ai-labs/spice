package io.github.spice.toolhub

import io.github.spice.Tool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 🏗️ StaticToolHub - 정적 도구 허브 구현체
 * 
 * 고정된 도구 목록을 관리하는 기본 ToolHub 구현체입니다.
 * 
 * 사용 시나리오:
 * - LLM 기반 Agent에 도구를 제공할 때
 * - 도구 구성이 고정되어 있고 별도의 상태를 가지지 않는 경우
 * - 예: FileTool, WebSearchTool, NotionTool 등
 */
class StaticToolHub(
    private val tools: List<Tool>
) : ToolHub {
    
    private val toolMap = tools.associateBy { it.name }
    private val executionMutex = Mutex()
    private val globalState = ConcurrentHashMap<String, Any>()
    private var isStarted = false
    
    /**
     * 🔧 도구 목록 조회
     */
    override suspend fun listTools(): List<Tool> = tools
    
    /**
     * 🚀 도구 실행
     */
    override suspend fun callTool(
        name: String,
        parameters: Map<String, Any>,
        context: ToolContext
    ): ToolResult {
        if (!isStarted) {
            return ToolResult.error("ToolHub not started. Call start() first.")
        }
        
        val tool = toolMap[name]
            ?: return ToolResult.error("Tool '$name' not found. Available tools: ${toolMap.keys}")
        
        return executionMutex.withLock {
            val startTime = System.currentTimeMillis()
            
            try {
                // 도구 실행 전 검증
                if (!tool.canExecute(parameters)) {
                    return ToolResult.error("Tool '$name' cannot execute with provided parameters")
                }
                
                // 도구 실행
                val legacyResult = tool.execute(parameters)
                val enhancedResult = legacyResult.toEnhancedResult()
                
                val executionTime = System.currentTimeMillis() - startTime
                
                // 실행 로그 기록
                val executionLog = ToolExecutionLog(
                    toolName = name,
                    parameters = parameters,
                    result = enhancedResult,
                    timestamp = startTime,
                    executionTimeMs = executionTime
                )
                
                context.addExecutionLog(executionLog)
                
                // 성공 시 결과를 컨텍스트에 저장
                if (enhancedResult.success) {
                    context.setMetadata("${name}_last_result", enhancedResult)
                    context.setMetadata("${name}_last_execution_time", executionTime)
                }
                
                println("🔧 Tool executed: $name (${executionTime}ms) - ${if (enhancedResult.success) "SUCCESS" else "FAILED"}")
                
                enhancedResult
                
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                val errorResult = ToolResult.error("Tool execution failed: ${e.message}", e)
                
                val executionLog = ToolExecutionLog(
                    toolName = name,
                    parameters = parameters,
                    result = errorResult,
                    timestamp = startTime,
                    executionTimeMs = executionTime
                )
                
                context.addExecutionLog(executionLog)
                
                println("🔧 Tool execution failed: $name (${executionTime}ms) - ${e.message}")
                
                errorResult
            }
        }
    }
    
    /**
     * 🏁 ToolHub 시작
     */
    override suspend fun start() {
        if (isStarted) {
            println("🧰 ToolHub already started")
            return
        }
        
        println("🧰 Starting StaticToolHub with ${tools.size} tools...")
        
        // 도구 검증
        val duplicateNames = tools.groupBy { it.name }.filter { it.value.size > 1 }
        if (duplicateNames.isNotEmpty()) {
            throw IllegalStateException("Duplicate tool names found: ${duplicateNames.keys}")
        }
        
        // 도구 준비 상태 확인
        tools.forEach { tool ->
            println("   - ${tool.name}: ${tool.description}")
        }
        
        isStarted = true
        println("🧰 StaticToolHub started successfully")
    }
    
    /**
     * 🛑 ToolHub 종료
     */
    override suspend fun stop() {
        if (!isStarted) {
            println("🧰 ToolHub already stopped")
            return
        }
        
        println("🧰 Stopping StaticToolHub...")
        
        // 상태 정리
        globalState.clear()
        isStarted = false
        
        println("🧰 StaticToolHub stopped")
    }
    
    /**
     * 🔄 ToolHub 상태 초기화
     */
    override suspend fun reset() {
        println("🧰 Resetting StaticToolHub...")
        
        globalState.clear()
        
        println("🧰 StaticToolHub reset completed")
    }
    
    /**
     * 💾 현재 상태 저장
     */
    override suspend fun saveState(): Map<String, Any> {
        return mapOf(
            "hub_type" to "static",
            "tool_count" to tools.size,
            "tool_names" to tools.map { it.name },
            "is_started" to isStarted,
            "global_state" to globalState.toMap(),
            "saved_at" to System.currentTimeMillis()
        )
    }
    
    /**
     * 📂 저장된 상태 로드
     */
    override suspend fun loadState(state: Map<String, Any>) {
        println("🧰 Loading StaticToolHub state...")
        
        // 글로벌 상태 복원
        val savedGlobalState = state["global_state"] as? Map<String, Any> ?: emptyMap()
        globalState.clear()
        globalState.putAll(savedGlobalState)
        
        // 시작 상태 복원
        val wasStarted = state["is_started"] as? Boolean ?: false
        if (wasStarted && !isStarted) {
            start()
        }
        
        val savedAt = state["saved_at"] as? Long ?: 0
        println("🧰 StaticToolHub state loaded (saved at: ${java.time.Instant.ofEpochMilli(savedAt)})")
    }
    
    /**
     * 📊 도구 실행 통계 조회
     */
    fun getExecutionStats(context: ToolContext): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        // 전체 실행 횟수
        stats["total_executions"] = context.callHistory.size
        
        // 도구별 실행 횟수
        val toolExecutionCounts = context.callHistory.groupBy { it.toolName }
            .mapValues { it.value.size }
        stats["tool_execution_counts"] = toolExecutionCounts
        
        // 도구별 평균 실행 시간
        val toolAvgExecutionTimes = context.callHistory.groupBy { it.toolName }
            .mapValues { entries ->
                entries.value.map { it.executionTimeMs }.average()
            }
        stats["tool_avg_execution_times"] = toolAvgExecutionTimes
        
        // 성공률
        val successRate = if (context.callHistory.isNotEmpty()) {
            context.callHistory.count { it.isSuccess }.toDouble() / context.callHistory.size * 100
        } else {
            0.0
        }
        stats["success_rate"] = successRate
        
        return stats
    }
    
    /**
     * 🔍 도구 검색
     */
    fun findTool(name: String): Tool? = toolMap[name]
    
    /**
     * 📋 도구 이름 목록
     */
    fun getToolNames(): List<String> = toolMap.keys.toList()
    
    /**
     * ✅ 도구 존재 여부 확인
     */
    fun hasTool(name: String): Boolean = toolMap.containsKey(name)
    
    /**
     * 🏃 ToolHub 상태 확인
     */
    fun isStarted(): Boolean = isStarted
}

/**
 * 🏗️ StaticToolHub 빌더
 */
class StaticToolHubBuilder {
    private val tools = mutableListOf<Tool>()
    
    /**
     * 도구 추가
     */
    fun addTool(tool: Tool): StaticToolHubBuilder {
        tools.add(tool)
        return this
    }
    
    /**
     * 여러 도구 추가
     */
    fun addTools(vararg tools: Tool): StaticToolHubBuilder {
        this.tools.addAll(tools)
        return this
    }
    
    /**
     * 도구 목록 추가
     */
    fun addTools(tools: List<Tool>): StaticToolHubBuilder {
        this.tools.addAll(tools)
        return this
    }
    
    /**
     * StaticToolHub 빌드
     */
    fun build(): StaticToolHub {
        return StaticToolHub(tools.toList())
    }
}

/**
 * 🎯 StaticToolHub 생성을 위한 DSL 함수
 */
fun staticToolHub(init: StaticToolHubBuilder.() -> Unit): StaticToolHub {
    val builder = StaticToolHubBuilder()
    builder.init()
    return builder.build()
}

/**
 * 🔧 편의 함수 - 도구 목록으로 StaticToolHub 생성
 */
fun createStaticToolHub(tools: List<Tool>): StaticToolHub {
    return StaticToolHub(tools)
}

/**
 * 🔧 편의 함수 - 가변 인자로 StaticToolHub 생성
 */
fun createStaticToolHub(vararg tools: Tool): StaticToolHub {
    return StaticToolHub(tools.toList())
} 