package io.github.spice.toolhub

import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ToolHubChainTest {
    
    private lateinit var toolHub: StaticToolHub
    private lateinit var validationTool: Tool
    private lateinit var transformTool: Tool
    private lateinit var analysisTool: Tool
    
    @BeforeEach
    fun setup() {
        // 데이터 검증 도구
        validationTool = object : BaseTool(
            name = "data_validator",
            description = "Validates input data",
            schema = ToolSchema(
                name = "data_validator",
                description = "Data validation tool",
                parameters = mapOf(
                    "data" to ParameterSchema("string", "Data to validate", required = true)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val data = parameters["data"] as? String ?: ""
                
                return if (data.isNotBlank() && data.length >= 3) {
                    io.github.spice.ToolResult.success(
                        result = data,
                        metadata = mapOf(
                            "validation_status" to "valid",
                            "data_length" to data.length.toString()
                        )
                    )
                } else {
                    io.github.spice.ToolResult.error(
                        error = "Validation failed: data too short or empty",
                        metadata = mapOf("validation_status" to "invalid")
                    )
                }
            }
        }
        
        // 데이터 변환 도구
        transformTool = object : BaseTool(
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
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val data = parameters["data"] as? String ?: ""
                val format = parameters["format"] as? String ?: "uppercase"
                
                val transformed = when (format) {
                    "uppercase" -> data.uppercase()
                    "lowercase" -> data.lowercase()
                    "reverse" -> data.reversed()
                    "json" -> """{"data": "$data"}"""
                    else -> data
                }
                
                return io.github.spice.ToolResult.success(
                    result = transformed,
                    metadata = mapOf(
                        "original_data" to data,
                        "format" to format,
                        "transformed_length" to transformed.length.toString()
                    )
                )
            }
        }
        
        // 데이터 분석 도구
        analysisTool = object : BaseTool(
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
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val data = parameters["data"] as? String ?: ""
                
                val wordCount = data.split("\\s+".toRegex()).size
                val charCount = data.length
                val hasNumbers = data.any { it.isDigit() }
                val hasUppercase = data.any { it.isUpperCase() }
                
                return io.github.spice.ToolResult.success(
                    result = "Analysis completed",
                    metadata = mapOf(
                        "word_count" to wordCount.toString(),
                        "char_count" to charCount.toString(),
                        "has_numbers" to hasNumbers.toString(),
                        "has_uppercase" to hasUppercase.toString(),
                        "analysis_summary" to "Data contains $wordCount words and $charCount characters"
                    )
                )
            }
        }
        
        toolHub = StaticToolHub(listOf(validationTool, transformTool, analysisTool))
    }
    
    @Test
    fun `ToolHubChainRunner 기본 실행 테스트`() = runBlocking {
        toolHub.start()
        
        val chainDefinition = toolChain("data_processing_chain") {
            step(
                name = "validate",
                toolName = "data_validator",
                parameters = mapOf("data" to "hello world")
            )
            step(
                name = "transform",
                toolName = "data_transformer",
                parameters = mapOf("format" to "uppercase"),
                parameterMapping = mapOf("validate_result" to "data")
            )
            step(
                name = "analyze",
                toolName = "data_analyzer",
                parameterMapping = mapOf("transform_result" to "data")
            )
        }
        
        val runner = ToolHubChainRunner(toolHub)
        val result = runner.executeChain(chainDefinition)
        
        assertTrue(result.success)
        assertEquals("data_processing_chain", result.chainName)
        assertEquals(3, result.totalSteps)
        assertEquals(3, result.successfulSteps)
        assertEquals(0, result.failedSteps)
        assertEquals(100.0, result.successRate)
        
        // 각 스텝 결과 확인
        assertTrue(result.stepResults.all { it.success })
        assertEquals(3, result.executionLogs.size)
        
        toolHub.stop()
    }
    
    @Test
    fun `ToolChainDefinition executeWith 확장 함수 테스트`() = runBlocking {
        toolHub.start()
        
        val chainDefinition = toolChain("simple_chain") {
            step(
                name = "validate_input",
                toolName = "data_validator",
                parameters = mapOf("data" to "test data")
            )
            step(
                name = "transform_data",
                toolName = "data_transformer",
                parameters = mapOf("format" to "json"),
                parameterMapping = mapOf("validate_input_result" to "data")
            )
        }
        
        val result = chainDefinition.executeWith(
            toolHub = toolHub,
            initialParameters = mapOf("user_id" to "test_user")
        )
        
        assertTrue(result.success)
        assertEquals(2, result.totalSteps)
        assertEquals(2, result.successfulSteps)
        
        // 초기 파라미터가 컨텍스트에 설정되었는지 확인
        assertEquals("test_user", result.finalContext.getMetadata("user_id"))
        
        toolHub.stop()
    }
    
    @Test
    fun `executeToolChain DSL 테스트`() = runBlocking {
        toolHub.start()
        
        val result = executeToolChain(
            toolHub = toolHub,
            chainName = "dsl_chain",
            initialParameters = mapOf("input_data" to "hello world")
        ) {
            step(
                name = "validate",
                toolName = "data_validator",
                parameterMapping = mapOf("input_data" to "data")
            )
            step(
                name = "transform",
                toolName = "data_transformer",
                parameters = mapOf("format" to "uppercase"),
                parameterMapping = mapOf("validate_result" to "data")
            )
        }
        
        assertTrue(result.success)
        assertEquals("dsl_chain", result.chainName)
        assertEquals(2, result.totalSteps)
        
        toolHub.stop()
    }
    
    @Test
    fun `체인 실행 실패 테스트`() = runBlocking {
        toolHub.start()
        
        val chainDefinition = toolChain("failing_chain") {
            step(
                name = "validate_bad_data",
                toolName = "data_validator",
                parameters = mapOf("data" to "hi") // 너무 짧은 데이터로 검증 실패
            )
            step(
                name = "transform",
                toolName = "data_transformer",
                parameters = mapOf("format" to "uppercase"),
                parameterMapping = mapOf("validate_bad_data_result" to "data")
            )
        }
        
        val runner = ToolHubChainRunner(toolHub)
        val result = runner.executeChain(chainDefinition)
        
        assertFalse(result.success)
        assertEquals(1, result.totalSteps) // 첫 번째 스텝에서 실패하므로 두 번째 스텝은 실행되지 않음
        assertEquals(0, result.successfulSteps)
        assertEquals(1, result.failedSteps)
        assertEquals(0.0, result.successRate)
        
        // 실패한 스텝 확인
        val failedStep = result.executionLogs.first()
        assertEquals("validate_bad_data", failedStep.stepName)
        assertEquals("data_validator", failedStep.toolName)
        assertFalse(failedStep.isSuccess)
        
        toolHub.stop()
    }
    
    @Test
    fun `조건부 스텝 실행 테스트`() = runBlocking {
        toolHub.start()
        
        val chainDefinition = toolChain("conditional_chain") {
            step(
                name = "validate",
                toolName = "data_validator",
                parameters = mapOf("data" to "hello world")
            )
            step(
                name = "conditional_transform",
                toolName = "data_transformer",
                parameters = mapOf("format" to "uppercase"),
                parameterMapping = mapOf("validate_result" to "data"),
                condition = { context ->
                    // 검증이 성공한 경우에만 변환 실행
                    context.sharedData["validate_success"] == true
                }
            )
            step(
                name = "always_analyze",
                toolName = "data_analyzer",
                parameterMapping = mapOf("conditional_transform_result" to "data")
            )
        }
        
        val runner = ToolHubChainRunner(toolHub)
        val result = runner.executeChain(chainDefinition)
        
        assertTrue(result.success)
        assertEquals(3, result.totalSteps)
        
        // 모든 스텝이 실행되었는지 확인
        assertEquals(3, result.executionLogs.size)
        assertEquals(listOf("validate", "conditional_transform", "always_analyze"), 
                    result.executionLogs.map { it.stepName })
        
        toolHub.stop()
    }
    
    @Test
    fun `체인 실행 결과 요약 및 로그 테스트`() = runBlocking {
        toolHub.start()
        
        val chainDefinition = toolChain("summary_test_chain") {
            step(
                name = "validate",
                toolName = "data_validator",
                parameters = mapOf("data" to "test data")
            )
            step(
                name = "transform",
                toolName = "data_transformer",
                parameters = mapOf("format" to "json"),
                parameterMapping = mapOf("validate_result" to "data")
            )
            step(
                name = "analyze",
                toolName = "data_analyzer",
                parameterMapping = mapOf("transform_result" to "data")
            )
        }
        
        val runner = ToolHubChainRunner(toolHub)
        val result = runner.executeChain(chainDefinition)
        
        // 요약 정보 확인
        val summary = result.getSummary()
        assertTrue(summary.contains("summary_test_chain"))
        assertTrue(summary.contains("3/3 steps succeeded"))
        assertTrue(summary.contains("100.0%"))
        
        // 상세 로그 확인
        val detailedLog = result.getDetailedLog()
        assertTrue(detailedLog.contains("=== ToolChain Execution Log: summary_test_chain ==="))
        assertTrue(detailedLog.contains("Status: SUCCESS"))
        assertTrue(detailedLog.contains("Steps: 3/3 succeeded"))
        assertTrue(detailedLog.contains("✅ Step 1: validate"))
        assertTrue(detailedLog.contains("✅ Step 2: transform"))
        assertTrue(detailedLog.contains("✅ Step 3: analyze"))
        
        // 개별 스텝 로그 요약
        result.executionLogs.forEach { log ->
            val stepSummary = log.getSummary()
            assertTrue(stepSummary.contains("SUCCESS"))
            assertTrue(stepSummary.contains(log.stepName))
            assertTrue(stepSummary.contains(log.toolName))
        }
        
        toolHub.stop()
    }
    
    @Test
    fun `체인 실행 시간 측정 테스트`() = runBlocking {
        toolHub.start()
        
        val chainDefinition = toolChain("timing_test_chain") {
            step(
                name = "validate",
                toolName = "data_validator",
                parameters = mapOf("data" to "timing test data")
            )
            step(
                name = "transform",
                toolName = "data_transformer",
                parameters = mapOf("format" to "uppercase"),
                parameterMapping = mapOf("validate_result" to "data")
            )
        }
        
        val runner = ToolHubChainRunner(toolHub)
        val result = runner.executeChain(chainDefinition)
        
        // 실행 시간 확인
        assertTrue(result.totalExecutionTimeMs > 0)
        
        // 각 스텝의 실행 시간 확인
        result.executionLogs.forEach { log ->
            assertTrue(log.executionTimeMs >= 0)
        }
        
        // 총 실행 시간이 각 스텝 실행 시간의 합과 일치하는지 확인
        val stepTotalTime = result.executionLogs.sumOf { it.executionTimeMs }
        assertEquals(stepTotalTime, result.totalExecutionTimeMs)
        
        toolHub.stop()
    }
    
    @Test
    fun `존재하지 않는 도구 호출 시 체인 실패 테스트`() = runBlocking {
        toolHub.start()
        
        val chainDefinition = toolChain("nonexistent_tool_chain") {
            step(
                name = "validate",
                toolName = "data_validator",
                parameters = mapOf("data" to "test data")
            )
            step(
                name = "nonexistent_step",
                toolName = "nonexistent_tool", // 존재하지 않는 도구
                parameters = mapOf("data" to "test")
            )
        }
        
        val runner = ToolHubChainRunner(toolHub)
        val result = runner.executeChain(chainDefinition)
        
        assertFalse(result.success)
        assertEquals(2, result.totalSteps)
        assertEquals(1, result.successfulSteps) // 첫 번째 스텝만 성공
        assertEquals(1, result.failedSteps)
        assertEquals(50.0, result.successRate)
        
        // 실패한 스텝 확인
        val failedStep = result.executionLogs.last()
        assertEquals("nonexistent_step", failedStep.stepName)
        assertEquals("nonexistent_tool", failedStep.toolName)
        assertFalse(failedStep.isSuccess)
        
        toolHub.stop()
    }
} 