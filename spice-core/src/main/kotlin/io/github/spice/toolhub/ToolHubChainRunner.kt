package io.github.spice.toolhub

import io.github.spice.*

/**
 * 🔗 ToolHubChainRunner - ToolHub를 사용하는 ToolChain 실행기
 * 
 * 기존 ToolChainRunner를 대체하여 ToolHub를 통해 도구를 실행하는 체인 실행기입니다.
 * 
 * 사용 예시:
 * ```kotlin
 * val toolHub = staticToolHub {
 *     addTool(DataValidationTool())
 *     addTool(DataCleaningTool())
 * }
 * 
 * val runner = ToolHubChainRunner(toolHub)
 * val result = runner.executeChain(chainDefinition)
 * ```
 */
class ToolHubChainRunner(
    private val toolHub: ToolHub
) {
    
    /**
     * 🔗 ToolChain 실행
     */
    suspend fun executeChain(
        definition: ToolChainDefinition,
        initialParameters: Map<String, Any> = emptyMap()
    ): ToolHubChainResult {
        val context = ToolContext()
        context.setMetadata("chain_id", definition.name)
        context.setMetadata("chain_description", definition.description)
        
        // 초기 파라미터를 컨텍스트에 설정
        initialParameters.forEach { (key, value) ->
            context.setMetadata(key, value)
        }
        
        val stepResults = mutableListOf<ToolResult>()
        val executionLogs = mutableListOf<ChainStepLog>()
        
        println("🔗 Starting ToolChain execution: ${definition.name}")
        
        for ((index, step) in definition.steps.withIndex()) {
            val stepStartTime = System.currentTimeMillis()
            
            // 조건 확인
            if (step.condition != null && !step.condition.invoke(createLegacyContext(context))) {
                println("   ⏭️  Skipping step '${step.name}' due to condition")
                continue
            }
            
            // 스텝 파라미터 빌드
            val stepParameters = buildStepParameters(step, context)
            
            println("   🔧 Executing step ${index + 1}/${definition.steps.size}: ${step.name} (${step.toolName})")
            
            // 도구 실행
            val result = toolHub.callTool(step.toolName, stepParameters, context)
            stepResults.add(result)
            
            val stepExecutionTime = System.currentTimeMillis() - stepStartTime
            
            // 실행 로그 기록
            val stepLog = ChainStepLog(
                stepName = step.name,
                toolName = step.toolName,
                parameters = stepParameters,
                result = result,
                executionTimeMs = stepExecutionTime,
                stepIndex = index
            )
            executionLogs.add(stepLog)
            
            // 결과 처리
            if (result.success) {
                println("   ✅ Step '${step.name}' completed successfully (${stepExecutionTime}ms)")
                
                // 성공 콜백 실행
                step.onSuccess?.invoke(result.toLegacyResult(), createLegacyContext(context))
                
                // 결과 데이터를 컨텍스트에 저장
                when (result) {
                    is ToolResult.Success -> {
                        context.setMetadata("${step.name}_result", result.output)
                        context.setMetadata("${step.name}_success", true)
                    }
                    else -> {
                        context.setMetadata("${step.name}_result", result.toString())
                        context.setMetadata("${step.name}_success", true)
                    }
                }
                
            } else {
                println("   ❌ Step '${step.name}' failed (${stepExecutionTime}ms): ${getErrorMessage(result)}")
                
                // 실패 콜백 실행
                step.onFailure?.invoke(result.toLegacyResult(), createLegacyContext(context))
                
                // 체인 중단
                context.setMetadata("${step.name}_success", false)
                context.setMetadata("${step.name}_error", getErrorMessage(result))
                break
            }
        }
        
        val chainSuccess = stepResults.all { it.success }
        val totalExecutionTime = executionLogs.sumOf { it.executionTimeMs }
        
        println("🔗 ToolChain execution completed: ${definition.name} (${totalExecutionTime}ms) - ${if (chainSuccess) "SUCCESS" else "FAILED"}")
        
        return ToolHubChainResult(
            chainName = definition.name,
            success = chainSuccess,
            stepResults = stepResults,
            executionLogs = executionLogs,
            finalContext = context,
            totalExecutionTimeMs = totalExecutionTime
        )
    }
    
    /**
     * 🔧 스텝 파라미터 빌드
     */
    private fun buildStepParameters(step: ToolChainStep, context: ToolContext): Map<String, Any> {
        val parameters = step.parameters.toMutableMap()
        
        // 파라미터 매핑 적용
        step.parameterMapping.forEach { (source, target) ->
            when {
                // 컨텍스트에서 값 가져오기
                source.startsWith("context.") -> {
                    val contextKey = source.removePrefix("context.")
                    context.getMetadata(contextKey)?.let { value ->
                        parameters[target] = value
                    }
                }
                // 이전 결과에서 값 가져오기
                source.startsWith("result.") -> {
                    val resultKey = source.removePrefix("result.")
                    context.getLastResult()?.metadata?.get(resultKey)?.let { value ->
                        parameters[target] = value
                    }
                }
                // 직접 컨텍스트에서 값 가져오기
                else -> {
                    context.getMetadata(source)?.let { value ->
                        parameters[target] = value
                    }
                }
            }
        }
        
        return parameters
    }
    
    /**
     * 🔄 ToolContext를 기존 ToolChainContext로 변환
     */
    private fun createLegacyContext(context: ToolContext): ToolChainContext {
        return ToolChainContext(
            chainId = context.getMetadata("chain_id") as? String ?: "unknown",
            currentDepth = 0,
            maxDepth = 10,
            executionHistory = context.callHistory.map { log ->
                ToolExecutionRecord(
                    toolName = log.toolName,
                    parameters = log.parameters,
                    result = log.result.toLegacyResult(),
                    executionTime = log.timestamp,
                    depth = 0
                )
            }.toMutableList(),
            sharedData = context.metadata.toMutableMap()
        )
    }
    
    /**
     * 🚨 에러 메시지 추출
     */
    private fun getErrorMessage(result: ToolResult): String {
        return when (result) {
            is ToolResult.Error -> result.message
            is ToolResult.Retry -> "Retry requested: ${result.reason}"
            else -> "Unknown error"
        }
    }
}

/**
 * 📊 ToolHub 체인 실행 결과
 */
data class ToolHubChainResult(
    val chainName: String,
    val success: Boolean,
    val stepResults: List<ToolResult>,
    val executionLogs: List<ChainStepLog>,
    val finalContext: ToolContext,
    val totalExecutionTimeMs: Long
) {
    /**
     * 성공한 스텝 수
     */
    val successfulSteps: Int get() = stepResults.count { it.success }
    
    /**
     * 실패한 스텝 수
     */
    val failedSteps: Int get() = stepResults.count { !it.success }
    
    /**
     * 전체 스텝 수
     */
    val totalSteps: Int get() = stepResults.size
    
    /**
     * 성공률
     */
    val successRate: Double get() = if (totalSteps > 0) successfulSteps.toDouble() / totalSteps * 100 else 0.0
    
    /**
     * 실행 요약
     */
    fun getSummary(): String {
        return "Chain '$chainName': $successfulSteps/$totalSteps steps succeeded (${String.format("%.1f", successRate)}%) in ${totalExecutionTimeMs}ms"
    }
    
    /**
     * 상세 로그
     */
    fun getDetailedLog(): String {
        val sb = StringBuilder()
        sb.appendLine("=== ToolChain Execution Log: $chainName ===")
        sb.appendLine("Status: ${if (success) "SUCCESS" else "FAILED"}")
        sb.appendLine("Total Time: ${totalExecutionTimeMs}ms")
        sb.appendLine("Steps: $successfulSteps/$totalSteps succeeded")
        sb.appendLine()
        
        executionLogs.forEach { log ->
            val status = if (log.isSuccess) "✅" else "❌"
            sb.appendLine("$status Step ${log.stepIndex + 1}: ${log.stepName} (${log.toolName}) - ${log.executionTimeMs}ms")
            if (!log.isSuccess) {
                sb.appendLine("   Error: ${getErrorMessage(log.result)}")
            }
        }
        
        return sb.toString()
    }
}

/**
 * 📝 체인 스텝 실행 로그
 */
data class ChainStepLog(
    val stepName: String,
    val toolName: String,
    val parameters: Map<String, Any>,
    val result: ToolResult,
    val executionTimeMs: Long,
    val stepIndex: Int
) {
    /**
     * 실행 성공 여부
     */
    val isSuccess: Boolean get() = result.success
    
    /**
     * 로그 요약
     */
    fun getSummary(): String {
        val status = if (isSuccess) "SUCCESS" else "FAILED"
        return "[$status] $stepName ($toolName) - ${executionTimeMs}ms"
    }
}

/**
 * 🔧 ToolChainDefinition 확장 함수 - ToolHub로 실행
 */
suspend fun ToolChainDefinition.executeWith(
    toolHub: ToolHub,
    initialParameters: Map<String, Any> = emptyMap()
): ToolHubChainResult {
    val runner = ToolHubChainRunner(toolHub)
    return runner.executeChain(this, initialParameters)
}

/**
 * 🎯 ToolHub 체인 실행을 위한 DSL
 */
suspend fun executeToolChain(
    toolHub: ToolHub,
    chainName: String,
    initialParameters: Map<String, Any> = emptyMap(),
    init: ToolChainBuilder.() -> Unit
): ToolHubChainResult {
    val definition = toolChain(chainName, init)
    return definition.executeWith(toolHub, initialParameters)
} 