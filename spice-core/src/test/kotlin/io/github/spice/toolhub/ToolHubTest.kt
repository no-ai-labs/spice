package io.github.spice.toolhub

import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ToolHubTest {
    
    private lateinit var testTool1: Tool
    private lateinit var testTool2: Tool
    private lateinit var toolHub: StaticToolHub
    
    @BeforeEach
    fun setup() {
        // test용 도구 generation
        testTool1 = object : BaseTool(
            name = "test_calculator",
            description = "Simple calculator for testing",
            schema = ToolSchema(
                name = "test_calculator",
                description = "Simple calculator",
                parameters = mapOf(
                    "operation" to ParameterSchema("string", "Operation type", required = true),
                    "a" to ParameterSchema("number", "First number", required = true),
                    "b" to ParameterSchema("number", "Second number", required = true)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val operation = parameters["operation"] as? String ?: "add"
                val a = (parameters["a"] as? Number)?.toDouble() ?: 0.0
                val b = (parameters["b"] as? Number)?.toDouble() ?: 0.0
                
                val result = when (operation) {
                    "add" -> a + b
                    "subtract" -> a - b
                    "multiply" -> a * b
                    "divide" -> if (b != 0.0) a / b else throw IllegalArgumentException("Division by zero")
                    else -> throw IllegalArgumentException("Unknown operation: $operation")
                }
                
                return io.github.spice.ToolResult.success(
                    result = result.toString(),
                    metadata = mapOf(
                        "operation" to operation,
                        "operands" to "$a, $b"
                    )
                )
            }
        }
        
        testTool2 = object : BaseTool(
            name = "test_greeter",
            description = "Simple greeter for testing",
            schema = ToolSchema(
                name = "test_greeter",
                description = "Simple greeter",
                parameters = mapOf(
                    "name" to ParameterSchema("string", "Name to greet", required = true),
                    "language" to ParameterSchema("string", "Language for greeting", required = false)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val name = parameters["name"] as? String ?: "World"
                val language = parameters["language"] as? String ?: "en"
                
                val greeting = when (language) {
                    "ko" -> "안녕하세요, $name!"
                    "es" -> "¡Hola, $name!"
                    "fr" -> "Bonjour, $name!"
                    else -> "Hello, $name!"
                }
                
                return io.github.spice.ToolResult.success(
                    result = greeting,
                    metadata = mapOf(
                        "name" to name,
                        "language" to language
                    )
                )
            }
        }
        
        toolHub = StaticToolHub(listOf(testTool1, testTool2))
    }
    
    @Test
    fun `StaticToolHub 기본 기능 테스트`() = runBlocking {
        // ToolHub 시작
        toolHub.start()
        assertTrue(toolHub.isStarted())
        
        // 도구 목록 check
        val tools = toolHub.listTools()
        assertEquals(2, tools.size)
        assertEquals(setOf("test_calculator", "test_greeter"), tools.map { it.name }.toSet())
        
        // 도구 존재 check
        assertTrue(toolHub.hasTool("test_calculator"))
        assertTrue(toolHub.hasTool("test_greeter"))
        assertFalse(toolHub.hasTool("nonexistent_tool"))
        
        // 도구 검색
        val calculator = toolHub.findTool("test_calculator")
        assertEquals("test_calculator", calculator?.name)
        
        // ToolHub 종료
        toolHub.stop()
        assertFalse(toolHub.isStarted())
    }
    
    @Test
    fun `도구 실행 테스트`() = runBlocking {
        toolHub.start()
        val context = ToolContext()
        
        // 계산기 도구 execution
        val calcResult = toolHub.callTool(
            name = "test_calculator",
            parameters = mapOf(
                "operation" to "add",
                "a" to 10,
                "b" to 5
            ),
            context = context
        )
        
        assertTrue(calcResult.success)
        assertEquals("15.0", (calcResult as ToolResult.Success).output.toString())
        
        // 인사 도구 execution
        val greetResult = toolHub.callTool(
            name = "test_greeter",
            parameters = mapOf(
                "name" to "Alice",
                "language" to "ko"
            ),
            context = context
        )
        
        assertTrue(greetResult.success)
        assertEquals("안녕하세요, Alice!", (greetResult as ToolResult.Success).output.toString())
        
        // execution history check
        assertEquals(2, context.callHistory.size)
        assertEquals("test_calculator", context.callHistory[0].toolName)
        assertEquals("test_greeter", context.callHistory[1].toolName)
        
        toolHub.stop()
    }
    
    @Test
    fun `도구 실행 실패 테스트`() = runBlocking {
        toolHub.start()
        val context = ToolContext()
        
        // 존재하지 않는 도구 execution
        val notFoundResult = toolHub.callTool(
            name = "nonexistent_tool",
            parameters = emptyMap(),
            context = context
        )
        
        assertFalse(notFoundResult.success)
        assertTrue((notFoundResult as ToolResult.Error).message.contains("not found"))
        
        // 0으로 나누기 error
        val divisionResult = toolHub.callTool(
            name = "test_calculator",
            parameters = mapOf(
                "operation" to "divide",
                "a" to 10,
                "b" to 0
            ),
            context = context
        )
        
        assertFalse(divisionResult.success)
        assertTrue((divisionResult as ToolResult.Error).message.contains("Division by zero"))
        
        toolHub.stop()
    }
    
    @Test
    fun `ToolHub 상태 저장 및 로드 테스트`() = runBlocking {
        toolHub.start()
        
        // status 저장
        val savedState = toolHub.saveState()
        assertEquals("static", savedState["hub_type"])
        assertEquals(2, savedState["tool_count"])
        assertTrue(savedState["is_started"] as Boolean)
        
        // 새로운 ToolHub generation하고 status 로드
        val newToolHub = StaticToolHub(listOf(testTool1, testTool2))
        newToolHub.loadState(savedState)
        
        assertTrue(newToolHub.isStarted())
        assertEquals(2, newToolHub.listTools().size)
        
        toolHub.stop()
        newToolHub.stop()
    }
    
    @Test
    fun `ToolHub 실행 통계 테스트`() = runBlocking {
        toolHub.start()
        val context = ToolContext()
        
        // multiple 도구 execution
        repeat(3) {
            toolHub.callTool(
                name = "test_calculator",
                parameters = mapOf("operation" to "add", "a" to it, "b" to 1),
                context = context
            )
        }
        
        repeat(2) {
            toolHub.callTool(
                name = "test_greeter",
                parameters = mapOf("name" to "User$it"),
                context = context
            )
        }
        
        // statistics check
        val stats = toolHub.getExecutionStats(context)
        assertEquals(5, stats["total_executions"])
        
        val toolCounts = stats["tool_execution_counts"] as Map<String, Int>
        assertEquals(3, toolCounts["test_calculator"])
        assertEquals(2, toolCounts["test_greeter"])
        
        val successRate = stats["success_rate"] as Double
        assertEquals(100.0, successRate)
        
        toolHub.stop()
    }
    
    @Test
    fun `ToolHub DSL 테스트`() = runBlocking {
        val hubWithDSL = staticToolHub {
            addTool(testTool1)
            addTool(testTool2)
        }
        
        hubWithDSL.start()
        
        assertEquals(2, hubWithDSL.listTools().size)
        assertTrue(hubWithDSL.hasTool("test_calculator"))
        assertTrue(hubWithDSL.hasTool("test_greeter"))
        
        hubWithDSL.stop()
    }
    
    @Test
    fun `ToolHub 시작 전 도구 실행 시 오류 테스트`() = runBlocking {
        val context = ToolContext()
        
        // 시작하지 않은 ToolHubin 도구 execution
        val result = toolHub.callTool(
            name = "test_calculator",
            parameters = mapOf("operation" to "add", "a" to 1, "b" to 2),
            context = context
        )
        
        assertFalse(result.success)
        assertTrue((result as ToolResult.Error).message.contains("not started"))
    }
    
    @Test
    fun `중복 도구 이름 검증 테스트`() = runBlocking {
        val duplicateTool = object : BaseTool(
            name = "test_calculator", // 중복 이름
            description = "Duplicate calculator",
            schema = ToolSchema(
                name = "test_calculator",
                description = "Duplicate calculator",
                parameters = emptyMap()
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                return io.github.spice.ToolResult.success("duplicate")
            }
        }
        
        val hubWithDuplicates = StaticToolHub(listOf(testTool1, duplicateTool))
        
        // 중복 도구 이름으로 인한 시작 실패
        assertThrows<IllegalStateException> {
            runBlocking { hubWithDuplicates.start() }
        }
    }
} 