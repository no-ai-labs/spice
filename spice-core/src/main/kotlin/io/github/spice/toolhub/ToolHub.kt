package io.github.spice.toolhub

import io.github.spice.Tool

/**
 * 🧰 ToolHub - 통합 도구 관리 시스템
 * 
 * 여러 Tool을 통합 관리하고, 공통된 상태 및 리소스를 공유하며,
 * MCP 등 외부 실행 환경에서도 기록이 보존되도록 설계된 도구 허브입니다.
 * 
 * Autogen의 Workbench와 비슷한 역할을 수행하지만,
 * Spice의 구조(Tool, ToolChain 등)와 자연스럽게 통합됩니다.
 */
interface ToolHub {
    /**
     * 등록된 모든 도구 목록 조회
     */
    suspend fun listTools(): List<Tool>
    
    /**
     * 도구 실행
     * @param name 도구 이름
     * @param parameters 실행 파라미터
     * @param context 실행 컨텍스트
     * @return 실행 결과
     */
    suspend fun callTool(
        name: String,
        parameters: Map<String, Any>,
        context: ToolContext = ToolContext()
    ): ToolResult
    
    /**
     * ToolHub 시작 (리소스 초기화)
     */
    suspend fun start()
    
    /**
     * ToolHub 종료 (리소스 정리)
     */
    suspend fun stop()
    
    /**
     * ToolHub 상태 초기화
     */
    suspend fun reset()
    
    /**
     * 현재 상태 저장
     */
    suspend fun saveState(): Map<String, Any>
    
    /**
     * 저장된 상태 로드
     */
    suspend fun loadState(state: Map<String, Any>)
}

/**
 * 🗂️ ToolContext - 도구 실행 컨텍스트
 * 
 * 도구 실행 시 공유되는 메타데이터와 실행 히스토리를 관리합니다.
 */
data class ToolContext(
    /**
     * 실행 메타데이터 (공유 상태)
     */
    val metadata: MutableMap<String, Any> = mutableMapOf(),
    
    /**
     * 도구 실행 히스토리
     */
    val callHistory: MutableList<ToolExecutionLog> = mutableListOf()
) {
    /**
     * 메타데이터 설정
     */
    fun setMetadata(key: String, value: Any) {
        metadata[key] = value
    }
    
    /**
     * 메타데이터 조회
     */
    fun getMetadata(key: String): Any? = metadata[key]
    
    /**
     * 실행 히스토리 추가
     */
    fun addExecutionLog(log: ToolExecutionLog) {
        callHistory.add(log)
    }
    
    /**
     * 마지막 실행 결과 조회
     */
    fun getLastResult(): ToolResult? = callHistory.lastOrNull()?.result
    
    /**
     * 특정 도구의 실행 히스토리 조회
     */
    fun getExecutionHistory(toolName: String): List<ToolExecutionLog> {
        return callHistory.filter { it.toolName == toolName }
    }
}

/**
 * 📝 ToolExecutionLog - 도구 실행 로그
 */
data class ToolExecutionLog(
    val toolName: String,
    val parameters: Map<String, Any>,
    val result: ToolResult,
    val timestamp: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0
) {
    /**
     * 실행 성공 여부
     */
    val isSuccess: Boolean get() = result.success
    
    /**
     * 실행 시간을 포함한 요약 정보
     */
    fun getSummary(): String {
        val status = if (isSuccess) "SUCCESS" else "FAILED"
        return "[$status] $toolName (${executionTimeMs}ms) - ${if (isSuccess) result.result else result.error}"
    }
}

/**
 * 🔄 ToolResult - 향상된 도구 실행 결과
 * 
 * 기존 ToolResult를 sealed class로 확장하여 더 명확한 결과 타입을 제공합니다.
 */
sealed class ToolResult {
    abstract val success: Boolean
    abstract val metadata: Map<String, Any>
    
    /**
     * 성공 결과
     */
    data class Success(
        val output: Any,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success: Boolean = true
        
        // 기존 ToolResult 호환성을 위한 속성들
        val result: String get() = output.toString()
        val error: String get() = ""
    }
    
    /**
     * 실패 결과
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success: Boolean = false
        
        // 기존 ToolResult 호환성을 위한 속성들
        val result: String get() = ""
        val error: String get() = message
    }
    
    /**
     * 재시도 요청
     */
    data class Retry(
        val reason: String,
        val retryAfterMs: Long = 1000,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success: Boolean = false
        
        // 기존 ToolResult 호환성을 위한 속성들
        val result: String get() = ""
        val error: String get() = "Retry requested: $reason"
    }
    
    companion object {
        /**
         * 성공 결과 생성
         */
        fun success(output: Any, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return Success(output, metadata)
        }
        
        /**
         * 에러 결과 생성
         */
        fun error(message: String, cause: Throwable? = null, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return Error(message, cause, metadata)
        }
        
        /**
         * 재시도 결과 생성
         */
        fun retry(reason: String, retryAfterMs: Long = 1000, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return Retry(reason, retryAfterMs, metadata)
        }
    }
}

/**
 * 🔧 기존 ToolResult와의 호환성을 위한 확장 함수
 */
fun io.github.spice.ToolResult.toEnhancedResult(): ToolResult {
    return if (this.success) {
        ToolResult.success(this.result, this.metadata)
    } else {
        ToolResult.error(this.error, metadata = this.metadata)
    }
}

/**
 * 🔄 Enhanced ToolResult를 기존 ToolResult로 변환
 */
fun ToolResult.toLegacyResult(): io.github.spice.ToolResult {
    return when (this) {
        is ToolResult.Success -> io.github.spice.ToolResult.success(this.result, this.metadata.mapValues { it.value.toString() })
        is ToolResult.Error -> io.github.spice.ToolResult.error(this.error, this.metadata.mapValues { it.value.toString() })
        is ToolResult.Retry -> io.github.spice.ToolResult.error(this.error, this.metadata.mapValues { it.value.toString() })
    }
} 