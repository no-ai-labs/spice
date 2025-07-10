package io.github.spice

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 📘 Spice Agent Documentation Generator
 * 
 * AgentEngine에 등록된 Agent들의 정보를 마크다운 형식으로 문서화합니다.
 * 각 Agent의 기본 정보, 능력, 도구, 사용 예제를 포함한 종합적인 문서를 생성합니다.
 */
class AgentDocumentGenerator {
    
    /**
     * AgentEngine의 상태를 기반으로 마크다운 문서 생성
     */
    fun generateDocumentation(agentEngine: AgentEngine): String {
        val engineStatus = agentEngine.getEngineStatus()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        return buildString {
            appendTitle(engineStatus, timestamp)
            appendSummary(engineStatus)
            appendTableOfContents(engineStatus.agentList)
            
            engineStatus.agentList.forEach { agentInfo ->
                appendAgentDetails(agentInfo)
            }
            
            appendUsageExamples(engineStatus.agentList)
            appendFooter(timestamp)
        }
    }
    
    /**
     * 특정 Agent의 상세 문서 생성
     */
    fun generateAgentDocumentation(agent: Agent): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        return buildString {
            appendLine("# ${agent.name}")
            appendLine()
            appendLine("**ID:** `${agent.id}`")
            appendLine("**Type:** ${agent::class.simpleName}")
            appendLine("**Generated:** $timestamp")
            appendLine()
            
            appendLine("## Description")
            appendLine(agent.description)
            appendLine()
            
            appendLine("## Capabilities")
            if (agent.capabilities.isNotEmpty()) {
                agent.capabilities.forEach { capability ->
                    appendLine("- `$capability`")
                }
            } else {
                appendLine("_No specific capabilities defined._")
            }
            appendLine()
            
            appendLine("## Tools")
            val tools = agent.getTools()
            if (tools.isNotEmpty()) {
                tools.forEach { tool ->
                    appendToolDocumentation(tool)
                }
            } else {
                appendLine("_No tools available._")
            }
            appendLine()
            
            appendLine("## Status")
            appendLine("- **Ready:** ${if (agent.isReady()) "✅ Yes" else "❌ No"}")
            appendLine("- **Tool Count:** ${tools.size}")
            appendLine("- **Capability Count:** ${agent.capabilities.size}")
        }
    }
    
    private fun StringBuilder.appendTitle(engineStatus: EngineStatus, timestamp: String) {
        appendLine("# 🌶️ Spice Agent Documentation")
        appendLine()
        appendLine("**Generated:** $timestamp")
        appendLine("**Spice Framework Version:** v1.0.0")
        appendLine()
        appendLine("---")
        appendLine()
    }
    
    private fun StringBuilder.appendSummary(engineStatus: EngineStatus) {
        appendLine("## 📊 Engine Status Summary")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|--------|-------|")
        appendLine("| Registered Agents | ${engineStatus.registeredAgents} |")
        appendLine("| Active Contexts | ${engineStatus.activeContexts} |")
        appendLine("| Available Tools | ${engineStatus.registeredTools} |")
        appendLine("| Agent Types | ${engineStatus.agentList.map { it.type }.distinct().size} |")
        appendLine()
    }
    
    private fun StringBuilder.appendTableOfContents(agentList: List<AgentInfo>) {
        appendLine("## 📋 Table of Contents")
        appendLine()
        agentList.forEach { agent ->
            val anchor = agent.id.replace("-", "").lowercase()
            appendLine("- [${agent.name}](#${anchor}) (`${agent.id}`) - ${agent.type}")
        }
        appendLine()
        appendLine("---")
        appendLine()
    }
    
    private fun StringBuilder.appendAgentDetails(agentInfo: AgentInfo) {
        val anchor = agentInfo.id.replace("-", "").lowercase()
        
        appendLine("## ${agentInfo.name} {#${anchor}}")
        appendLine()
        
        // 기본 정보 테이블
        appendLine("### Basic Information")
        appendLine()
        appendLine("| Property | Value |")
        appendLine("|----------|-------|")
        appendLine("| **ID** | `${agentInfo.id}` |")
        appendLine("| **Type** | `${agentInfo.type}` |")
        appendLine("| **Status** | ${if (agentInfo.isReady) "🟢 Ready" else "🔴 Not Ready"} |")
        appendLine("| **Tool Count** | ${agentInfo.toolCount} |")
        appendLine("| **Capability Count** | ${agentInfo.capabilities.size} |")
        appendLine()
        
        // 능력 목록
        appendLine("### Capabilities")
        appendLine()
        if (agentInfo.capabilities.isNotEmpty()) {
            agentInfo.capabilities.forEach { capability ->
                appendLine("- 🔧 `$capability`")
            }
        } else {
            appendLine("_No specific capabilities defined._")
        }
        appendLine()
        
        // 사용 권장사항
        appendLine("### Recommended Use Cases")
        appendLine()
        appendRecommendedUseCases(agentInfo)
        appendLine()
        
        // 메시지 타입 호환성
        appendLine("### Message Type Compatibility")
        appendLine()
        appendMessageTypeCompatibility(agentInfo)
        appendLine()
        
        appendLine("---")
        appendLine()
    }
    
    private fun StringBuilder.appendRecommendedUseCases(agentInfo: AgentInfo) {
        when (agentInfo.type) {
            "PromptAgent" -> {
                appendLine("- 사용자 프롬프트 처리")
                appendLine("- 텍스트 생성 및 변환")
                appendLine("- 대화형 상호작용")
            }
            "DataAgent" -> {
                appendLine("- 데이터 수집 및 정리")
                appendLine("- 파일 처리")
                appendLine("- 데이터 변환 작업")
            }
            "ResultAgent" -> {
                appendLine("- 결과 포맷팅 및 시각화")
                appendLine("- 보고서 생성")
                appendLine("- 최종 결과 처리")
            }
            "BranchAgent" -> {
                appendLine("- 조건부 로직 처리")
                appendLine("- 워크플로우 분기")
                appendLine("- 의사결정 자동화")
            }
            "MergeAgent" -> {
                appendLine("- 다중 흐름 병합")
                appendLine("- 결과 통합")
                appendLine("- 동기화 작업")
            }
            "DSLAgent" -> {
                appendLine("- 커스텀 로직 실행")
                appendLine("- 특화된 작업 처리")
                appendLine("- 유연한 메시지 처리")
            }
            else -> {
                agentInfo.capabilities.forEach { capability ->
                    when (capability) {
                        "text_processing" -> appendLine("- 텍스트 분석 및 처리")
                        "api_calls" -> appendLine("- 외부 API 연동")
                        "data_analysis" -> appendLine("- 데이터 분석 작업")
                        "file_handling" -> appendLine("- 파일 입출력 처리")
                        "message_routing" -> appendLine("- 메시지 라우팅 및 전달")
                        else -> appendLine("- ${capability.replace("_", " ").replaceFirstChar { it.uppercase() }} 관련 작업")
                    }
                }
                if (agentInfo.capabilities.isEmpty()) {
                    appendLine("- 범용 메시지 처리")
                    appendLine("- 기본적인 Agent 기능")
                }
            }
        }
    }
    
    private fun StringBuilder.appendMessageTypeCompatibility(agentInfo: AgentInfo) {
        appendLine("| Message Type | Support | Notes |")
        appendLine("|--------------|---------|-------|")
        
        val supportedTypes = when (agentInfo.type) {
            "PromptAgent" -> listOf("TEXT", "PROMPT", "SYSTEM")
            "DataAgent" -> listOf("DATA", "TEXT", "WORKFLOW_START", "WORKFLOW_END")
            "ResultAgent" -> listOf("TEXT", "DATA", "SYSTEM", "RESULT")
            "BranchAgent" -> listOf("TEXT", "PROMPT", "SYSTEM", "BRANCH")
            "MergeAgent" -> listOf("TEXT", "PROMPT", "SYSTEM", "MERGE")
            "DSLAgent" -> listOf("TEXT", "PROMPT", "SYSTEM", "DATA", "TOOL_CALL")
            else -> listOf("TEXT", "PROMPT", "SYSTEM") // 기본값
        }
        
        MessageType.values().forEach { messageType ->
            val isSupported = supportedTypes.contains(messageType.name)
            val supportIcon = if (isSupported) "✅" else "❌"
            val notes = when {
                isSupported && messageType.name == "TOOL_CALL" && agentInfo.toolCount > 0 -> "Has ${agentInfo.toolCount} tools"
                isSupported && messageType.name in listOf("WORKFLOW_START", "WORKFLOW_END") -> "Workflow control"
                isSupported -> "Supported"
                else -> "Not supported"
            }
            appendLine("| `${messageType.name}` | $supportIcon | $notes |")
        }
    }
    
    private fun StringBuilder.appendToolDocumentation(tool: Tool) {
        appendLine("### 🔧 ${tool.name}")
        appendLine()
        appendLine("**Description:** ${tool.description}")
        appendLine()
        
        if (tool.schema.parameters.isNotEmpty()) {
            appendLine("**Parameters:**")
            appendLine()
            appendLine("| Parameter | Type | Required | Description |")
            appendLine("|-----------|------|----------|-------------|")
            
            tool.schema.parameters.forEach { (paramName, paramSchema) ->
                val required = if (paramSchema.required) "✅ Yes" else "❌ No"
                appendLine("| `$paramName` | `${paramSchema.type}` | $required | ${paramSchema.description} |")
            }
            appendLine()
        }
        
        // 사용 예제 생성
        appendLine("**Usage Example:**")
        appendLine("```json")
        appendLine("{")
        appendLine("  \"type\": \"TOOL_CALL\",")
        appendLine("  \"content\": \"Execute ${tool.name}\",")
        appendLine("  \"metadata\": {")
        appendLine("    \"toolName\": \"${tool.name}\"")
        
        tool.schema.parameters.entries.take(2).forEach { (paramName, paramSchema) ->
            val exampleValue = when (paramSchema.type) {
                "string" -> "\"example_value\""
                "number" -> "42"
                "boolean" -> "true"
                "array" -> "[\"item1\", \"item2\"]"
                else -> "\"example\""
            }
            appendLine("    \"param_$paramName\": $exampleValue,")
        }
        
        if (tool.schema.parameters.isNotEmpty()) {
            // 마지막 콤마 제거를 위해 다시 처리
            setLength(length - 2) // 마지막 쉼표와 개행 제거
            appendLine()
        }
        
        appendLine("  }")
        appendLine("}")
        appendLine("```")
        appendLine()
    }
    
    private fun StringBuilder.appendUsageExamples(agentList: List<AgentInfo>) {
        appendLine("## 🚀 Usage Examples")
        appendLine()
        
        appendLine("### Basic Agent Registration")
        appendLine("```kotlin")
        appendLine("val agentEngine = AgentEngine()")
        appendLine()
        agentList.take(3).forEach { agent ->
            appendLine("// Register ${agent.name}")
            appendLine("agentEngine.registerAgent(${agent.type}(")
            appendLine("    id = \"${agent.id}\",")
            appendLine("    name = \"${agent.name}\"")
            appendLine("))")
            appendLine()
        }
        appendLine("```")
        appendLine()
        
        appendLine("### Message Processing Example")
        appendLine("```kotlin")
        appendLine("// Send a message to the agent engine")
        appendLine("val message = Message(")
        appendLine("    content = \"Hello, Spice agents!\",")
        appendLine("    sender = \"user\",")
        appendLine("    type = MessageType.TEXT")
        appendLine(")")
        appendLine()
        appendLine("val result = agentEngine.receive(message)")
        appendLine("println(\"Response: \${result.response.content}\")")
        appendLine("println(\"Processed by: \${result.agentName}\")")
        appendLine("```")
        appendLine()
        
        appendLine("### Workflow Example")
        appendLine("```kotlin")
        appendLine("// Start a workflow")
        appendLine("val workflowStart = Message(")
        appendLine("    content = \"Start data processing workflow\",")
        appendLine("    sender = \"user\",")
        appendLine("    type = MessageType.WORKFLOW_START")
        appendLine(")")
        appendLine()
        appendLine("agentEngine.processWorkflow(workflowStart).collect { agentMessage ->")
        appendLine("    println(\"\${agentMessage.agentName}: \${agentMessage.response.content}\")")
        appendLine("}")
        appendLine("```")
        appendLine()
    }
    
    private fun StringBuilder.appendFooter(timestamp: String) {
        appendLine("---")
        appendLine()
        appendLine("## 📚 Additional Resources")
        appendLine()
        appendLine("- [Spice Framework GitHub](https://github.com/your-org/spice-framework)")
        appendLine("- [API Documentation](https://docs.spice-framework.io)")
        appendLine("- [Community Discord](https://discord.gg/spice-framework)")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("*Documentation generated automatically by Spice Agent Documentation Generator at $timestamp*")
        appendLine()
        appendLine("🌶️ **Powered by Spice Framework** - JVM Multi-Agent System")
    }
}

/**
 * AgentEngine 확장 함수 - 간편한 문서 생성
 */
fun AgentEngine.generateDocumentation(): String {
    return AgentDocumentGenerator().generateDocumentation(this)
}

/**
 * Agent 확장 함수 - 개별 Agent 문서 생성
 */
fun Agent.generateDocumentation(): String {
    return AgentDocumentGenerator().generateAgentDocumentation(this)
}

/**
 * 파일로 문서 저장
 */
fun AgentEngine.saveDocumentationToFile(filePath: String = "AgentDoc.md") {
    val documentation = generateDocumentation()
    java.io.File(filePath).writeText(documentation)
    println("📝 Agent documentation saved to: $filePath")
}

/**
 * 개별 Agent 문서를 파일로 저장
 */
fun Agent.saveDocumentationToFile(filePath: String = "${id}-doc.md") {
    val documentation = generateDocumentation()
    java.io.File(filePath).writeText(documentation)
    println("📝 Agent documentation for '$name' saved to: $filePath")
} 