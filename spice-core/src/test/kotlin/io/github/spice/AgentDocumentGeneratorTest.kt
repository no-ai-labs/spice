package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentDocumentGeneratorTest {
    
    @Test
    fun `AgentEngine 전체 문서 생성 테스트`() = runBlocking {
        // Given: various Agent가 등록된 AgentEngine
        val agentEngine = AgentEngine()
        
        // basic Agent들 등록
        agentEngine.registerAgent(PromptAgent())
        agentEngine.registerAgent(DataAgent())
        agentEngine.registerAgent(ResultAgent())
        
        // DSL Agent 등록
        val customAgent = buildAgent {
            id = "custom-test-agent"
            name = "Custom Test Agent"
            description = "Agent created for documentation testing"
            
            capabilities {
                add("testing")
                add("documentation")
                add("validation")
            }
            
            tools {
                custom("test_tool") {
                    description = "Simple test tool"
                    parameter("input", "string", "Test input", required = true)
                    parameter("format", "string", "Output format", required = false)
                    
                    execute { params ->
                        ToolResult.success("Test executed: ${params["input"]}")
                    }
                }
            }
        }
        agentEngine.registerAgent(customAgent)
        
        // When: 문서 generation
        val documentation = agentEngine.generateDocumentation()
        
        // Then: 문서 내용 validation
        assertNotNull(documentation)
        assertTrue(documentation.isNotEmpty(), "Documentation should not be empty")
        
        // 제목 check
        assertTrue(documentation.contains("🌶️ Spice Agent Documentation"), "Should contain main title")
        
        // summary 섹션 check
        assertTrue(documentation.contains("## 📊 Engine Status Summary"), "Should contain summary section")
        assertTrue(documentation.contains("Registered Agents"), "Should show registered agent count")
        assertTrue(documentation.contains("Available Tools"), "Should show tool count")
        
        // 목차 check
        assertTrue(documentation.contains("## 📋 Table of Contents"), "Should contain table of contents")
        assertTrue(documentation.contains("Custom Test Agent"), "Should list custom agent")
        
        // Agent 세부 information check
        assertTrue(documentation.contains("prompt-agent"), "Should contain prompt agent ID")
        assertTrue(documentation.contains("data-agent"), "Should contain data agent ID")
        assertTrue(documentation.contains("result-agent"), "Should contain result agent ID")
        assertTrue(documentation.contains("custom-test-agent"), "Should contain custom agent ID")
        
        // 사용 예제 check
        assertTrue(documentation.contains("## 🚀 Usage Examples"), "Should contain usage examples")
        assertTrue(documentation.contains("agentEngine.receive(message)"), "Should show message processing example")
        
        // 푸터 check
        assertTrue(documentation.contains("🌶️ **Powered by Spice Framework**"), "Should contain footer")
        
        println("Generated Documentation Length: ${documentation.length} characters")
        println("Documentation Preview (first 500 chars):")
        println(documentation.take(500))
    }
    
    @Test
    fun `개별 Agent 문서 생성 테스트`() {
        // Given: Tool이 있는 커스텀 Agent
        val agent = buildAgent {
            id = "documented-agent"
            name = "Well Documented Agent"
            description = "This agent has comprehensive documentation for testing purposes"
            
            capabilities {
                add("text_processing")
                add("data_analysis")
                add("api_calls")
            }
            
            tools {
                custom("analyzer") {
                    description = "Analyzes text data"
                    parameter("text", "string", "Text to analyze", required = true)
                    parameter("analysis_type", "string", "Type of analysis", required = true)
                    parameter("depth", "number", "Analysis depth", required = false)
                    
                    execute { params ->
                        ToolResult.success("Analysis complete")
                    }
                }
                
                custom("formatter") {
                    description = "Formats output data"
                    parameter("data", "string", "Data to format", required = true)
                    parameter("format", "string", "Target format", required = false)
                    
                    execute { params ->
                        ToolResult.success("Formatting complete")
                    }
                }
            }
        }
        
        // When: 개별 Agent 문서 generation
        val documentation = agent.generateDocumentation()
        
        // Then: 문서 내용 validation
        assertNotNull(documentation)
        assertTrue(documentation.isNotEmpty(), "Documentation should not be empty")
        
        // basic information check
        assertTrue(documentation.contains("# Well Documented Agent"), "Should contain agent name as title")
        assertTrue(documentation.contains("**ID:** `documented-agent`"), "Should contain agent ID")
        assertTrue(documentation.contains("DSLAgent"), "Should contain agent type")
        
        // 설명 check
        assertTrue(documentation.contains("## Description"), "Should contain description section")
        assertTrue(documentation.contains("comprehensive documentation"), "Should contain agent description")
        
        // Capabilities check
        assertTrue(documentation.contains("## Capabilities"), "Should contain capabilities section")
        assertTrue(documentation.contains("`text_processing`"), "Should list text_processing capability")
        assertTrue(documentation.contains("`data_analysis`"), "Should list data_analysis capability")
        assertTrue(documentation.contains("`api_calls`"), "Should list api_calls capability")
        
        // Tools check
        assertTrue(documentation.contains("## Tools"), "Should contain tools section")
        assertTrue(documentation.contains("### 🔧 analyzer"), "Should contain analyzer tool")
        assertTrue(documentation.contains("### 🔧 formatter"), "Should contain formatter tool")
        assertTrue(documentation.contains("Analyzes text data"), "Should contain tool description")
        
        // status check
        assertTrue(documentation.contains("## Status"), "Should contain status section")
        assertTrue(documentation.contains("**Ready:** ✅ Yes"), "Should show ready status")
        assertTrue(documentation.contains("**Tool Count:** 2"), "Should show tool count")
        assertTrue(documentation.contains("**Capability Count:** 3"), "Should show capability count")
        
        println("Individual Agent Documentation Length: ${documentation.length} characters")
    }
    
    @Test
    fun `확장 함수를 통한 문서 생성 테스트`() = runBlocking {
        // Given: Agent와 AgentEngine setting
        val agentEngine = AgentEngine()
        val testAgent = textProcessingAgent(
            id = "extension-test-agent",
            name = "Extension Test Agent"
        ) { text -> "Processed: $text" }
        
        agentEngine.registerAgent(testAgent)
        
        // When: 확장 function로 문서 generation
        val engineDoc = agentEngine.generateDocumentation()
        val agentDoc = testAgent.generateDocumentation()
        
        // Then: 두 문서 all generation됨
        assertTrue(engineDoc.isNotEmpty(), "Engine documentation should not be empty")
        assertTrue(agentDoc.isNotEmpty(), "Agent documentation should not be empty")
        
        // 내용 check
        assertTrue(engineDoc.contains("Extension Test Agent"), "Engine doc should contain agent name")
        assertTrue(agentDoc.contains("# Extension Test Agent"), "Agent doc should have agent as title")
    }
    
    @Test
    fun `다양한 Agent 타입의 문서 생성 테스트`() = runBlocking {
        // Given: all basic Agent type 등록
        val agentEngine = AgentEngine()
        
        agentEngine.registerAgent(PromptAgent())
        agentEngine.registerAgent(DataAgent())
        agentEngine.registerAgent(ResultAgent())
        agentEngine.registerAgent(BranchAgent())
        agentEngine.registerAgent(MergeAgent())
        
        // When: 문서 generation
        val documentation = agentEngine.generateDocumentation()
        
        // Then: all Agent type이 문서에 including됨
        assertTrue(documentation.contains("PromptAgent"), "Should contain PromptAgent")
        assertTrue(documentation.contains("DataAgent"), "Should contain DataAgent")
        assertTrue(documentation.contains("ResultAgent"), "Should contain ResultAgent")
        assertTrue(documentation.contains("BranchAgent"), "Should contain BranchAgent")
        assertTrue(documentation.contains("MergeAgent"), "Should contain MergeAgent")
        
        // 각 Agent type별 권장 사용사례 check
        assertTrue(documentation.contains("사용자 프롬프트 처리"), "Should contain PromptAgent use case")
        assertTrue(documentation.contains("데이터 수집 및 정리"), "Should contain DataAgent use case")
        assertTrue(documentation.contains("결과 포맷팅 및 시각화"), "Should contain ResultAgent use case")
        assertTrue(documentation.contains("조건부 로직 처리"), "Should contain BranchAgent use case")
        assertTrue(documentation.contains("다중 흐름 병합"), "Should contain MergeAgent use case")
        
        // message type 호환성 테이블 check
        assertTrue(documentation.contains("Message Type Compatibility"), "Should contain compatibility tables")
        assertTrue(documentation.contains("| `TEXT` |"), "Should show TEXT message type")
        assertTrue(documentation.contains("| `PROMPT` |"), "Should show PROMPT message type")
        assertTrue(documentation.contains("✅"), "Should show supported message types")
        assertTrue(documentation.contains("❌"), "Should show unsupported message types")
    }
    
    @Test
    fun `Tool 문서화 상세 테스트`() {
        // Given: 복잡한 Tool을 가진 Agent
        val complexAgent = buildAgent {
            id = "tool-demo-agent"
            name = "Tool Demo Agent"
            description = "Agent demonstrating complex tool documentation"
            
            tools {
                custom("advanced_calculator") {
                    description = "Advanced mathematical calculator with multiple operations"
                    parameter("numbers", "array", "Array of numbers to calculate", required = true)
                    parameter("operation", "string", "Mathematical operation (add, subtract, multiply, divide)", required = true)
                    parameter("precision", "number", "Decimal precision for result", required = false)
                    parameter("format", "string", "Output format (decimal, fraction, scientific)", required = false)
                    
                    execute { params ->
                        ToolResult.success("Calculation complete")
                    }
                }
                
                custom("data_processor") {
                    description = "Processes various data formats"
                    parameter("input_data", "string", "Raw input data", required = true)
                    parameter("input_format", "string", "Format of input data", required = true)
                    parameter("output_format", "string", "Desired output format", required = true)
                    parameter("validate", "boolean", "Whether to validate data", required = false)
                    
                    execute { params ->
                        ToolResult.success("Data processing complete")
                    }
                }
            }
        }
        
        // When: Agent 문서 generation
        val documentation = complexAgent.generateDocumentation()
        
        // Then: Tool 문서화 check
        
        // Tool 이름과 설명
        assertTrue(documentation.contains("### 🔧 advanced_calculator"), "Should contain calculator tool")
        assertTrue(documentation.contains("### 🔧 data_processor"), "Should contain processor tool")
        assertTrue(documentation.contains("Advanced mathematical calculator"), "Should contain tool description")
        
        // 파라미터 테이블 check
        assertTrue(documentation.contains("**Parameters:**"), "Should contain parameters section")
        assertTrue(documentation.contains("| Parameter | Type | Required | Description |"), "Should contain parameter table header")
        
        // 개별 파라미터 check
        assertTrue(documentation.contains("| `numbers` | `array` | ✅ Yes |"), "Should show required parameter")
        assertTrue(documentation.contains("| `precision` | `number` | ❌ No |"), "Should show optional parameter")
        assertTrue(documentation.contains("| `validate` | `boolean` | ❌ No |"), "Should show boolean parameter")
        
        // 사용 예제 check
        assertTrue(documentation.contains("**Usage Example:**"), "Should contain usage examples")
        assertTrue(documentation.contains("```json"), "Should contain JSON example")
        assertTrue(documentation.contains("\"type\": \"TOOL_CALL\""), "Should show tool call format")
        assertTrue(documentation.contains("\"toolName\": \"advanced_calculator\""), "Should show tool name")
        
        println("Complex Tool Documentation Preview:")
        println(documentation.split("\n").filter { it.contains("🔧") || it.contains("Parameter") }.take(10).joinToString("\n"))
    }
    
    @Test
    fun `빈 AgentEngine 문서 생성 테스트`() {
        // Given: Agent가 등록되지 않은 빈 AgentEngine
        val emptyEngine = AgentEngine()
        
        // When: 문서 generation
        val documentation = emptyEngine.generateDocumentation()
        
        // Then: basic structure는 유지되지만 Agent information는 비어있음
        assertTrue(documentation.contains("🌶️ Spice Agent Documentation"), "Should contain title")
        assertTrue(documentation.contains("| Registered Agents | 0 |"), "Should show 0 agents")
        assertTrue(documentation.contains("| Available Tools | 0 |"), "Should show 0 tools")
        assertTrue(documentation.contains("## 🚀 Usage Examples"), "Should still contain usage examples")
        
        // 빈 statusin도 문서 structure가 유지되는지 check
        assertNotNull(documentation)
        assertTrue(documentation.length > 100, "Should still have substantial content")
    }
} 