package io.github.spice

import kotlinx.coroutines.runBlocking

/**
 * 🧩 Spice ToolChain System
 * 
 * 하나의 Tool이 다른 Tool을 호출할 수 있게 만드는 체인 구조
 * 복잡한 흐름 처리가 가능해집니다.
 */

/**
 * Tool 실행 컨텍스트
 */
data class ToolExecutionContext(
    val toolRunner: ToolChainRunner,
    val executionHistory: MutableList<ToolExecutionStep> = mutableListOf(),
    val maxDepth: Int = 10,
    val currentDepth: Int = 0,
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    
    /**
     * 실행 깊이 체크
     */
    fun canExecuteMore(): Boolean = currentDepth < maxDepth
    
    /**
     * 새로운 실행 레벨로 이동
     */
    fun nextLevel(): ToolExecutionContext = copy(currentDepth = currentDepth + 1)
    
    /**
     * 실행 기록 추가
     */
    fun addStep(step: ToolExecutionStep) {
        executionHistory.add(step)
    }
}

/**
 * Tool 실행 단계
 */
data class ToolExecutionStep(
    val toolName: String,
    val parameters: Map<String, Any>,
    val result: ToolResult,
    val executionTime: Long,
    val depth: Int,
    val parentTool: String? = null
)

/**
 * Tool 체인 실행기 인터페이스
 */
interface ToolChainRunner {
    /**
     * Tool 등록
     */
    fun registerTool(tool: Tool)
    
    /**
     * 등록된 Tool 조회
     */
    fun getTool(name: String): Tool?
    
    /**
     * Tool 실행
     */
    suspend fun execute(toolName: String, parameters: Map<String, Any>, context: ToolExecutionContext? = null): ToolResult
    
    /**
     * Tool 체인 실행
     */
    suspend fun executeChain(chain: ToolChain, initialParameters: Map<String, Any>): ToolChainResult
}

/**
 * 기존 ToolRunner를 확장한 Tool 체인 실행기 구현
 */
class DefaultToolChainRunner : ToolChainRunner {
    private val baseRunner = ToolRunner()
    
    override fun registerTool(tool: Tool) {
        baseRunner.registerTool(tool)
    }
    
    override fun getTool(name: String): Tool? = null  // 직접 접근하지 않음
    
    override suspend fun execute(toolName: String, parameters: Map<String, Any>, context: ToolExecutionContext?): ToolResult {
        val executionContext = context ?: ToolExecutionContext(this)
        
        // 재귀 깊이 체크
        if (!executionContext.canExecuteMore()) {
            return ToolResult.error("Maximum execution depth reached (${executionContext.maxDepth})")
        }
        
        val startTime = System.currentTimeMillis()
        
        return try {
            // 기존 ToolRunner 사용
            val result = baseRunner.executeTool(toolName, parameters)
            
            val executionTime = System.currentTimeMillis() - startTime
            
            // 실행 기록
            executionContext.addStep(ToolExecutionStep(
                toolName = toolName,
                parameters = parameters,
                result = result,
                executionTime = executionTime,
                depth = executionContext.currentDepth,
                parentTool = executionContext.metadata["currentTool"] as? String
            ))
            
            result
        } catch (e: Exception) {
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }
    
    override suspend fun executeChain(chain: ToolChain, initialParameters: Map<String, Any>): ToolChainResult {
        val context = ToolExecutionContext(this)
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = chain.execute(initialParameters, context)
            val totalTime = System.currentTimeMillis() - startTime
            
            ToolChainResult(
                success = result.success,
                finalResult = result,
                executionHistory = context.executionHistory,
                totalExecutionTime = totalTime,
                metadata = context.metadata.toMap()
            )
        } catch (e: Exception) {
            ToolChainResult(
                success = false,
                finalResult = ToolResult.error("Chain execution failed: ${e.message}"),
                executionHistory = context.executionHistory,
                totalExecutionTime = System.currentTimeMillis() - startTime,
                error = e.message
            )
        }
    }
}

/**
 * 체인 가능한 Tool 인터페이스
 */
interface ChainableTool : Tool {
    /**
     * 컨텍스트와 함께 실행
     */
    suspend fun executeWithContext(parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult
    
    /**
     * 다른 Tool 호출
     */
    suspend fun callTool(toolName: String, parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult {
        context.metadata["currentTool"] = this.name
        return context.toolRunner.execute(toolName, parameters, context)
    }
}

/**
 * 체인 가능한 기본 Tool 추상 클래스
 */
abstract class BaseChainableTool(
    override val name: String,
    override val description: String,
    override val schema: ToolSchema
) : ChainableTool {
    
    /**
     * 기본 execute는 컨텍스트 없이 실행
     */
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return executeWithContext(parameters, ToolExecutionContext(DefaultToolChainRunner()))
    }
    
    /**
     * 하위 클래스에서 구현할 체인 실행 로직
     */
    abstract override suspend fun executeWithContext(parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult
}

/**
 * Tool 체인 정의
 */
data class ToolChain(
    val name: String,
    val description: String,
    val steps: List<ToolChainStep>
) {
    
    /**
     * 체인 실행
     */
    suspend fun execute(initialParameters: Map<String, Any>, context: ToolExecutionContext): ToolResult {
        var currentParameters = initialParameters
        var lastResult: ToolResult? = null
        
        for ((index, step) in steps.withIndex()) {
            // 파라미터 매핑
            val stepParameters = mapParameters(currentParameters, step.parameterMapping, lastResult)
            
            // Tool 실행
            val result = context.toolRunner.execute(step.toolName, stepParameters, context)
            
            if (!result.success && step.required) {
                return ToolResult.error("Required step failed: ${step.toolName} - ${result.error}")
            }
            
            lastResult = result
            
            // 다음 단계를 위한 파라미터 준비
            if (step.outputMapping.isNotEmpty()) {
                currentParameters = currentParameters + mapOutputToParameters(result, step.outputMapping)
            }
        }
        
        return lastResult ?: ToolResult.error("No steps executed")
    }
    
    /**
     * 파라미터 매핑
     */
    private fun mapParameters(
        currentParams: Map<String, Any>,
        mapping: Map<String, String>,
        lastResult: ToolResult?
    ): Map<String, Any> {
        val mapped = mutableMapOf<String, Any>()
        
        mapping.forEach { (targetParam, sourceParam) ->
            when {
                sourceParam.startsWith("result.") -> {
                    // 이전 결과에서 추출
                    val resultKey = sourceParam.removePrefix("result.")
                    lastResult?.metadata?.get(resultKey)?.let { mapped[targetParam] = it }
                }
                currentParams.containsKey(sourceParam) -> {
                    // 현재 파라미터에서 복사
                    mapped[targetParam] = currentParams[sourceParam]!!
                }
            }
        }
        
        return mapped
    }
    
    /**
     * 출력을 파라미터로 매핑
     */
    private fun mapOutputToParameters(result: ToolResult, mapping: Map<String, String>): Map<String, Any> {
        val mapped = mutableMapOf<String, Any>()
        
        mapping.forEach { (outputKey, paramKey) ->
            when (outputKey) {
                "result" -> mapped[paramKey] = result.result
                "success" -> mapped[paramKey] = result.success
                else -> result.metadata[outputKey]?.let { mapped[paramKey] = it }
            }
        }
        
        return mapped
    }
}

/**
 * Tool 체인 단계
 */
data class ToolChainStep(
    val toolName: String,
    val parameterMapping: Map<String, String> = emptyMap(), // targetParam -> sourceParam
    val outputMapping: Map<String, String> = emptyMap(),    // outputKey -> nextParam
    val required: Boolean = true,
    val condition: ((Map<String, Any>) -> Boolean)? = null
)

/**
 * Tool 체인 실행 결과
 */
data class ToolChainResult(
    val success: Boolean,
    val finalResult: ToolResult,
    val executionHistory: List<ToolExecutionStep>,
    val totalExecutionTime: Long,
    val metadata: Map<String, Any> = emptyMap(),
    val error: String? = null
)

/**
 * === 실제 사용 예제 Tool들 ===
 */

/**
 * 데이터 처리 Tool (다른 Tool들을 체인으로 호출)
 */
class DataProcessingTool : BaseChainableTool(
    name = "data_processor",
    description = "Processes data through multiple transformation steps",
    schema = ToolSchema(
        name = "data_processor",
        description = "Data processing tool",
        parameters = mapOf(
            "input_data" to ParameterSchema("string", "Raw input data", required = true),
            "processing_steps" to ParameterSchema("array", "List of processing steps", required = true)
        )
    )
) {
    
    override suspend fun executeWithContext(parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult {
        val inputData = parameters["input_data"] as? String
            ?: return ToolResult.error("Missing input_data parameter")
        
        val steps = parameters["processing_steps"] as? List<String>
            ?: return ToolResult.error("Missing processing_steps parameter")
        
        var currentData = inputData
        val processedSteps = mutableListOf<String>()
        
        for (step in steps) {
            when (step) {
                "validate" -> {
                    val validationResult = callTool("data_validator", mapOf("data" to currentData), context)
                    if (!validationResult.success) {
                        return ToolResult.error("Validation failed: ${validationResult.error}")
                    }
                    processedSteps.add("validation completed")
                }
                "clean" -> {
                    val cleanResult = callTool("data_cleaner", mapOf("data" to currentData), context)
                    if (cleanResult.success) {
                        currentData = cleanResult.result
                        processedSteps.add("data cleaned")
                    }
                }
                "transform" -> {
                    val transformResult = callTool("data_transformer", mapOf("data" to currentData), context)
                    if (transformResult.success) {
                        currentData = transformResult.result
                        processedSteps.add("data transformed")
                    }
                }
                "analyze" -> {
                    val analysisResult = callTool("data_analyzer", mapOf("data" to currentData), context)
                    if (analysisResult.success) {
                        processedSteps.add("analysis: ${analysisResult.result}")
                    }
                }
            }
        }
        
        return ToolResult.success(
            result = currentData,
            metadata = mapOf(
                "processed_steps" to processedSteps.joinToString(", "),
                "step_count" to processedSteps.size.toString(),
                "original_data_length" to inputData.length.toString(),
                "final_data_length" to currentData.length.toString()
            )
        )
    }
}

/**
 * 데이터 검증 Tool
 */
class DataValidatorTool : BaseChainableTool(
    name = "data_validator",
    description = "Validates data format and content",
    schema = ToolSchema(
        name = "data_validator",
        description = "Data validation tool",
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to validate", required = true)
        )
    )
) {
    
    override suspend fun executeWithContext(parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult.error("Missing data parameter")
        
        val isValid = data.isNotBlank() && data.length >= 3
        val issues = mutableListOf<String>()
        
        if (data.isBlank()) issues.add("Data is empty")
        if (data.length < 3) issues.add("Data too short")
        
        return if (isValid) {
            ToolResult.success(
                result = "Data validation passed",
                metadata = mapOf(
                    "validation_status" to "passed",
                    "data_length" to data.length.toString()
                )
            )
        } else {
            ToolResult.error("Validation failed: ${issues.joinToString(", ")}")
        }
    }
}

/**
 * 데이터 정리 Tool
 */
class DataCleanerTool : BaseChainableTool(
    name = "data_cleaner",
    description = "Cleans and sanitizes data",
    schema = ToolSchema(
        name = "data_cleaner",
        description = "Data cleaning tool",
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to clean", required = true)
        )
    )
) {
    
    override suspend fun executeWithContext(parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult.error("Missing data parameter")
        
        // 데이터 정리 작업
        val cleanedData = data
            .trim()
            .replace(Regex("\\s+"), " ")  // 연속 공백 제거
            .replace(Regex("[^a-zA-Z0-9가-힣\\s.,!?]"), "") // 특수문자 제거
        
        return ToolResult.success(
            result = cleanedData,
            metadata = mapOf(
                "original_length" to data.length.toString(),
                "cleaned_length" to cleanedData.length.toString(),
                "cleaning_applied" to "whitespace_normalization,special_char_removal"
            )
        )
    }
}

/**
 * 데이터 변환 Tool
 */
class DataTransformerTool : BaseChainableTool(
    name = "data_transformer",
    description = "Transforms data format",
    schema = ToolSchema(
        name = "data_transformer",
        description = "Data transformation tool",
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to transform", required = true),
            "format" to ParameterSchema("string", "Target format", required = false)
        )
    )
) {
    
    override suspend fun executeWithContext(parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult.error("Missing data parameter")
        
        val format = parameters["format"] as? String ?: "uppercase"
        
        val transformedData = when (format) {
            "uppercase" -> data.uppercase()
            "lowercase" -> data.lowercase()
            "title_case" -> data.split(" ").joinToString(" ") { 
                it.replaceFirstChar { char -> char.uppercase() } 
            }
            "json" -> """{"data": "$data", "length": ${data.length}}"""
            else -> data
        }
        
        return ToolResult.success(
            result = transformedData,
            metadata = mapOf(
                "transformation_type" to format,
                "original_format" to "string",
                "transformation_applied" to "true"
            )
        )
    }
}

/**
 * 데이터 분석 Tool
 */
class DataAnalyzerTool : BaseChainableTool(
    name = "data_analyzer",
    description = "Analyzes data and provides insights",
    schema = ToolSchema(
        name = "data_analyzer",
        description = "Data analysis tool",
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to analyze", required = true)
        )
    )
) {
    
    override suspend fun executeWithContext(parameters: Map<String, Any>, context: ToolExecutionContext): ToolResult {
        val data = parameters["data"] as? String
            ?: return ToolResult.error("Missing data parameter")
        
        // 간단한 데이터 분석
        val wordCount = data.split("\\s+".toRegex()).size
        val charCount = data.length
        val sentenceCount = data.split("[.!?]".toRegex()).size
        val avgWordLength = if (wordCount > 0) charCount.toDouble() / wordCount else 0.0
        
        val analysis = "Analysis: $wordCount words, $charCount characters, $sentenceCount sentences. " +
                "Average word length: ${"%.1f".format(avgWordLength)} characters."
        
        return ToolResult.success(
            result = analysis,
            metadata = mapOf(
                "word_count" to wordCount.toString(),
                "char_count" to charCount.toString(),
                "sentence_count" to sentenceCount.toString(),
                "avg_word_length" to avgWordLength.toString(),
                "analysis_type" to "basic_text_analysis"
            )
        )
    }
}

/**
 * 편의 함수: ToolChain DSL
 */
fun buildToolChain(name: String, description: String, init: ToolChainBuilder.() -> Unit): ToolChain {
    val builder = ToolChainBuilder(name, description)
    builder.init()
    return builder.build()
}

/**
 * ToolChain 빌더
 */
class ToolChainBuilder(private val name: String, private val description: String) {
    private val steps = mutableListOf<ToolChainStep>()
    
    fun step(toolName: String, init: ToolChainStepBuilder.() -> Unit = {}) {
        val stepBuilder = ToolChainStepBuilder(toolName)
        stepBuilder.init()
        steps.add(stepBuilder.build())
    }
    
    internal fun build(): ToolChain {
        return ToolChain(name, description, steps)
    }
}

/**
 * ToolChain 단계 빌더
 */
class ToolChainStepBuilder(private val toolName: String) {
    private val parameterMapping = mutableMapOf<String, String>()
    private val outputMapping = mutableMapOf<String, String>()
    var required: Boolean = true
    var condition: ((Map<String, Any>) -> Boolean)? = null
    
    fun mapParameter(target: String, source: String) {
        parameterMapping[target] = source
    }
    
    fun mapOutput(output: String, nextParam: String) {
        outputMapping[output] = nextParam
    }
    
    internal fun build(): ToolChainStep {
        return ToolChainStep(
            toolName = toolName,
            parameterMapping = parameterMapping,
            outputMapping = outputMapping,
            required = required,
            condition = condition
        )
    }
} 