package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphvizFlowGeneratorTest {
    
    @Test
    fun `기본 메시지 흐름 DOT 생성 테스트`() {
        // Given: 간단한 메시지 흐름
        val messages = listOf(
            Message(
                content = "Hello",
                sender = "user",
                receiver = "agent",
                type = MessageType.TEXT
            ),
            Message(
                content = "Hello back!",
                sender = "agent",
                receiver = "user",
                type = MessageType.TEXT
            )
        )
        
        // When: DOT 파일 생성
        val dotContent = messages.generateGraphvizDot("Simple Chat Flow")
        
        // Then: DOT 형식 검증
        assertNotNull(dotContent)
        assertTrue(dotContent.startsWith("digraph"), "Should start with digraph")
        assertTrue(dotContent.endsWith("}"), "Should end with closing brace")
        
        // 그래프 제목 확인
        assertTrue(dotContent.contains("Simple Chat Flow"), "Should contain title")
        
        // 노드 정의 확인
        assertTrue(dotContent.contains("user"), "Should contain user node")
        assertTrue(dotContent.contains("agent"), "Should contain agent node")
        
        // 간선 정의 확인
        assertTrue(dotContent.contains("->"), "Should contain edges")
        assertTrue(dotContent.contains("TEXT"), "Should contain message type labels")
        
        // 범례 확인
        assertTrue(dotContent.contains("Legend"), "Should contain legend")
        assertTrue(dotContent.contains("cluster_legend"), "Should contain legend cluster")
        
        println("Generated DOT Content:")
        println(dotContent)
    }
    
    @Test
    fun `샘플 워크플로우 DOT 생성 테스트`() {
        // Given: 복잡한 워크플로우 메시지
        val workflowMessages = createSampleWorkflow()
        
        // When: DOT 생성
        val dotContent = workflowMessages.generateGraphvizDot("Data Processing Workflow")
        
        // Then: 워크플로우 구조 검증
        assertTrue(dotContent.contains("WORKFLOW_START"), "Should contain workflow start")
        assertTrue(dotContent.contains("WORKFLOW_END"), "Should contain workflow end")
        assertTrue(dotContent.contains("DATA"), "Should contain data messages")
        assertTrue(dotContent.contains("TOOL_CALL"), "Should contain tool calls")
        assertTrue(dotContent.contains("TOOL_RESULT"), "Should contain tool results")
        assertTrue(dotContent.contains("BRANCH"), "Should contain branch logic")
        assertTrue(dotContent.contains("RESULT"), "Should contain results")
        
        // 노드 타입별 색상 확인
        assertTrue(dotContent.contains("lightblue"), "Should use user color")
        assertTrue(dotContent.contains("lightgreen"), "Should use agent color")
        assertTrue(dotContent.contains("orange"), "Should use tool color")
        
        // 간선 색상 확인
        assertTrue(dotContent.contains("color=\"blue\""), "Should have blue edges for TEXT")
        assertTrue(dotContent.contains("color=\"red\""), "Should have red edges for TOOL_CALL")
        assertTrue(dotContent.contains("color=\"green\""), "Should have green edges for TOOL_RESULT")
        
        println("Workflow DOT length: ${dotContent.length} characters")
    }
    
    @Test
    fun `에러 흐름 DOT 생성 테스트`() {
        // Given: 에러가 포함된 메시지 흐름
        val errorMessages = createErrorWorkflow()
        
        // When: DOT 생성
        val dotContent = errorMessages.generateGraphvizDot("Error Handling Flow")
        
        // Then: 에러 처리 구조 검증
        assertTrue(dotContent.contains("ERROR"), "Should contain error messages")
        assertTrue(dotContent.contains("color=\"red\""), "Should have red error edges")
        assertTrue(dotContent.contains("risky"), "Should contain risky operations")
        assertTrue(dotContent.contains("dangerous"), "Should contain dangerous operations")
        
        // 에러 처리 툴팁 확인
        assertTrue(dotContent.contains("tooltip="), "Should contain tooltips")
        
        println("Error Flow DOT preview:")
        println(dotContent.split("\n").take(20).joinToString("\n"))
    }
    
    @Test
    fun `인터럽트 재개 흐름 DOT 생성 테스트`() {
        // Given: 인터럽트/재개가 포함된 메시지 흐름
        val interruptMessages = createInterruptWorkflow()
        
        // When: DOT 생성
        val dotContent = interruptMessages.generateGraphvizDot("Interrupt Resume Flow")
        
        // Then: 인터럽트 구조 검증
        assertTrue(dotContent.contains("INTERRUPT"), "Should contain interrupt messages")
        assertTrue(dotContent.contains("RESUME"), "Should contain resume messages")
        assertTrue(dotContent.contains("color=\"crimson\""), "Should have crimson interrupt edges")
        assertTrue(dotContent.contains("color=\"darkblue\""), "Should have darkblue resume edges")
        
        // 메타데이터 기반 처리 확인
        assertTrue(dotContent.contains("user_input_required"), "Should show interrupt reason in content")
        
        println("Interrupt Flow contains INTERRUPT: ${dotContent.contains("INTERRUPT")}")
        println("Interrupt Flow contains RESUME: ${dotContent.contains("RESUME")}")
    }
    
    @Test
    fun `AgentEngine 통합 플로우 DOT 생성 테스트`() = runBlocking {
        // Given: AgentEngine과 샘플 메시지
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(UniversalAgent())
        
        val sampleMessages = listOf(
            Message(
                content = "Process this data",
                sender = "user",
                type = MessageType.DATA
            ),
            Message(
                content = "Analyze results", 
                sender = "user",
                type = MessageType.TEXT
            ),
            Message(
                content = "Generate report",
                sender = "user", 
                type = MessageType.PROMPT
            )
        )
        
        // When: AgentEngine으로부터 플로우 DOT 생성
        val dotContent = agentEngine.generateFlowDot(sampleMessages, "Agent Processing Flow")
        
        // Then: 처리 결과 검증
        assertNotNull(dotContent)
        assertTrue(dotContent.contains("Agent Processing Flow"), "Should contain title")
        assertTrue(dotContent.contains("universal-agent"), "Should contain agent responses")
        
        // 처리된 메시지와 응답 모두 포함 확인
        assertTrue(dotContent.contains("user"), "Should contain user nodes")
        assertTrue(dotContent.contains("universal"), "Should contain universal agent")
        
        println("Agent Engine Flow DOT generated successfully")
    }
    
    @Test
    fun `다양한 메시지 타입별 색상 및 스타일 테스트`() {
        // Given: 모든 메시지 타입을 포함한 메시지 리스트
        val allTypeMessages = MessageType.values().map { messageType ->
            Message(
                content = "Message of type $messageType",
                sender = "test-sender",
                receiver = "test-receiver", 
                type = messageType
            )
        }
        
        // When: DOT 생성
        val dotContent = allTypeMessages.generateGraphvizDot("All Message Types")
        
        // Then: 각 메시지 타입별 색상 확인
        MessageType.values().forEach { messageType ->
            assertTrue(
                dotContent.contains(messageType.toString()),
                "Should contain $messageType in DOT"
            )
        }
        
        // 색상 매핑 확인
        assertTrue(dotContent.contains("color=\"blue\""), "Should have blue for TEXT")
        assertTrue(dotContent.contains("color=\"purple\""), "Should have purple for PROMPT")
        assertTrue(dotContent.contains("color=\"red\""), "Should have red for TOOL_CALL/ERROR")
        assertTrue(dotContent.contains("color=\"green\""), "Should have green for TOOL_RESULT")
        assertTrue(dotContent.contains("color=\"orange\""), "Should have orange for DATA")
        assertTrue(dotContent.contains("color=\"navy\""), "Should have navy for WORKFLOW")
        assertTrue(dotContent.contains("color=\"brown\""), "Should have brown for BRANCH/MERGE")
        assertTrue(dotContent.contains("color=\"crimson\""), "Should have crimson for INTERRUPT")
        assertTrue(dotContent.contains("color=\"darkblue\""), "Should have darkblue for RESUME")
        
        println("All message types DOT contains ${MessageType.values().size} different types")
    }
    
    @Test
    fun `노드 타입별 모양 및 색상 테스트`() {
        // Given: 다양한 노드 타입을 가진 메시지
        val messages = listOf(
            Message(content = "User message", sender = "user", receiver = "agent"),
            Message(content = "Agent response", sender = "my-agent", receiver = "user"),
            Message(content = "Tool execution", sender = "search-tool", receiver = "agent"),
            Message(content = "System message", sender = "system", receiver = "user")
        )
        
        // When: DOT 생성
        val dotContent = messages.generateGraphvizDot("Node Types Test")
        
        // Then: 노드 모양 확인
        assertTrue(dotContent.contains("shape=ellipse"), "Should have ellipse for user")
        assertTrue(dotContent.contains("shape=box"), "Should have box for agent")
        assertTrue(dotContent.contains("shape=diamond"), "Should have diamond for tool")
        assertTrue(dotContent.contains("shape=hexagon"), "Should have hexagon for system")
        
        // 노드 색상 확인
        assertTrue(dotContent.contains("fillcolor=\"lightblue\""), "Should have lightblue for user")
        assertTrue(dotContent.contains("fillcolor=\"lightgreen\""), "Should have lightgreen for agent")
        assertTrue(dotContent.contains("fillcolor=\"orange\""), "Should have orange for tool")
        assertTrue(dotContent.contains("fillcolor=\"lightgray\""), "Should have lightgray for system")
        
        println("Node types correctly mapped to shapes and colors")
    }
    
    @Test
    fun `DOT 파일 저장 기능 테스트`() {
        // Given: 간단한 메시지 흐름
        val messages = listOf(
            Message(content = "Test message", sender = "user", receiver = "agent")
        )
        
        val dotContent = messages.generateGraphvizDot("Save Test")
        
        // When: 파일로 저장
        val testFilePath = "test_flow.dot"
        dotContent.saveToDotFile(testFilePath)
        
        // Then: 파일 생성 확인
        val savedFile = java.io.File(testFilePath)
        assertTrue(savedFile.exists(), "DOT file should be created")
        
        val savedContent = savedFile.readText()
        assertTrue(savedContent.contains("Save Test"), "Saved content should match")
        assertTrue(savedContent.contains("digraph"), "Should be valid DOT format")
        
        // 정리
        savedFile.delete()
        
        println("DOT file save/load test completed successfully")
    }
    
    @Test
    fun `빈 메시지 리스트 처리 테스트`() {
        // Given: 빈 메시지 리스트
        val emptyMessages = emptyList<Message>()
        
        // When: DOT 생성
        val dotContent = emptyMessages.generateGraphvizDot("Empty Flow")
        
        // Then: 기본 구조는 유지
        assertTrue(dotContent.contains("digraph"), "Should still have digraph structure")
        assertTrue(dotContent.contains("Empty Flow"), "Should contain title")
        assertTrue(dotContent.contains("Legend"), "Should still have legend")
        assertTrue(dotContent.startsWith("digraph"), "Should be valid DOT")
        assertTrue(dotContent.endsWith("}"), "Should be properly closed")
        
        // 노드나 간선은 없어야 함 (범례 제외)
        val nodeLines = dotContent.lines().filter { 
            it.contains(" [") && !it.contains("legend_") && 
            !it.contains("node [") && !it.contains("edge [") &&
            !it.trim().startsWith("//") && !it.trim().startsWith("rankdir") &&
            !it.trim().startsWith("bgcolor") && !it.trim().startsWith("fontname") &&
            !it.trim().startsWith("fontsize") && !it.trim().startsWith("label=") &&
            !it.trim().startsWith("labelloc")
        }
        val edgeLines = dotContent.lines().filter { 
            it.contains(" ->") && !it.contains("legend_") 
        }
        

        
        assertTrue(nodeLines.isEmpty(), "Should have no user-defined nodes")
        assertTrue(edgeLines.isEmpty(), "Should have no user-defined edges")
        
        println("Empty message list handled gracefully")
    }
} 