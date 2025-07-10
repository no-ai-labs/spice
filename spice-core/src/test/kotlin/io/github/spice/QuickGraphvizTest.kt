package io.github.spice

import kotlin.test.Test

class QuickGraphvizTest {
    
    @Test
    fun `DOT 생성 기본 동작 확인`() {
        // Given: 간단한 메시지
        val messages = listOf(
            Message(content = "Hello", sender = "user", receiver = "agent")
        )
        
        // When: DOT 생성
        val generator = GraphvizFlowGenerator()
        val dotContent = generator.generateDotFromMessages(messages, "Test")
        
        // Then: 출력 확인
        println("=== DOT Content ===")
        println(dotContent)
        println("=== End DOT ===")
        println("Ends with }: ${dotContent.endsWith("}")}")
        println("Contains digraph: ${dotContent.contains("digraph")}")
        println("Length: ${dotContent.length}")
    }
} 