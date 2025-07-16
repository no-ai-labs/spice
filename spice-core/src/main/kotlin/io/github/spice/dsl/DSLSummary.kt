package io.github.spice.dsl

import io.github.spice.Agent
import io.github.spice.Tool

/**
 * 📋 DSL Summary Generator
 * 
 * Agent, Tool, Flow 등 DSL 객체들의 사람이 읽기 쉬운 요약 정보를 생성합니다.
 * 프로그래밍 문서화와 디버깅에 유용합니다.
 */

// =====================================
// AGENT DESCRIPTION
// =====================================

/**
 * Agent의 상세 정보를 Markdown 형식으로 출력
 */
fun Agent.describe(): String {
    val tools = getTools()
    val capabilities = capabilities.takeIf { it.isNotEmpty() } ?: listOf("No specific capabilities")
    
    return buildString {
        appendLine("## 🤖 Agent: $name")
        appendLine("**ID**: `$id`")
        appendLine("**Description**: $description")
        
        // Template 정보 추가
        if (this@describe is CoreAgent) {
            // CoreAgent의 경우 metadata에서 template 정보 확인
            appendLine("**Type**: Core DSL Agent")
        } else {
            appendLine("**Type**: Custom Implementation")
        }
        
        // Experimental 여부 확인 (id나 description에서 experimental 키워드 체크)
        val isExperimental = id.contains("experimental") || 
                           description.contains("experimental", ignoreCase = true) ||
                           name.contains("experimental", ignoreCase = true)
        if (isExperimental) {
            appendLine("**🧪 Experimental**: Yes - Advanced DSL feature")
        }
        
        appendLine()
        
        appendLine("### Capabilities")
        capabilities.forEach { capability ->
            appendLine("- $capability")
        }
        appendLine()
        
        appendLine("### Available Tools (${tools.size})")
        if (tools.isNotEmpty()) {
            tools.forEach { tool ->
                val toolIsExperimental = tool.name.contains("experimental") ||
                                       tool.description.contains("experimental", ignoreCase = true)
                val experimentalTag = if (toolIsExperimental) " 🧪" else ""
                appendLine("- **${tool.name}**$experimentalTag: ${tool.description}")
            }
        } else {
            appendLine("- No tools configured")
        }
        appendLine()
        
        appendLine("### Status")
        appendLine("- **Ready**: ${if (isReady()) "✅ Yes" else "❌ No"}")
        appendLine("- **Message Handler**: ${if (this@describe is CoreAgent) "✅ Configured" else "⚠️ Custom implementation"}")
        if (isExperimental) {
            appendLine("- **Experimental Feature**: ⚠️ Use with caution in production")
        }
        appendLine()
        
        appendLine("### Usage")
        appendLine("```kotlin")
        if (isExperimental) {
            appendLine("experimental {")
            appendLine("    val response = $id.processMessage(")
            appendLine("        Message(content = \"your message\", sender = \"user\")")
            appendLine("    )")
            appendLine("}")
        } else {
            appendLine("val response = $id.processMessage(")
            appendLine("    Message(content = \"your message\", sender = \"user\")")
            appendLine(")")
        }
        appendLine("```")
    }
}

/**
 * Agent의 간단한 한 줄 요약
 */
fun Agent.summarize(): String {
    val toolCount = getTools().size
    val status = if (isReady()) "ready" else "not ready"
    return "Agent '$name' ($id) - $toolCount tools, $status"
}

// =====================================
// TOOL DESCRIPTION  
// =====================================

/**
 * Tool의 상세 정보를 Markdown 형식으로 출력
 */
fun Tool.describe(): String {
    return buildString {
        appendLine("## 🔧 Tool: $name")
        appendLine("**Description**: $description")
        
        // Experimental 여부 확인
        val isExperimental = name.contains("experimental") || 
                           description.contains("experimental", ignoreCase = true)
        if (isExperimental) {
            appendLine("**🧪 Experimental**: Yes - Advanced DSL feature")
        }
        
        // Template 정보 확인 (schema의 parameters에서 template_alias 등 확인)
        val hasTemplateInfo = schema.parameters.values.any { 
            it.description.contains("template", ignoreCase = true) 
        }
        if (hasTemplateInfo) {
            appendLine("**📋 Template**: Generated from DSL template")
        }
        
        appendLine()
        
        appendLine("### Parameters")
        if (schema.parameters.isNotEmpty()) {
            appendLine("| Parameter | Type | Required | Description |")
            appendLine("|-----------|------|----------|-------------|")
            schema.parameters.forEach { (name, param) ->
                val required = if (param.required) "✅ Yes" else "❌ No"
                val desc = param.description.ifEmpty { "No description" }
                appendLine("| `$name` | `${param.type}` | $required | $desc |")
            }
        } else {
            appendLine("- No parameters required")
        }
        appendLine()
        
        appendLine("### Usage")
        appendLine("```kotlin")
        if (isExperimental) {
            appendLine("experimental {")
            appendLine("    val result = $name.execute(mapOf(")
            schema.parameters.forEach { (paramName, param) ->
                val example = when (param.type) {
                    "string" -> "\"example\""
                    "number" -> "42"
                    "boolean" -> "true"
                    else -> "\"value\""
                }
                appendLine("        \"$paramName\" to $example,")
            }
            appendLine("    ))")
            appendLine("    println(result.result)")
            appendLine("}")
        } else {
            appendLine("val result = $name.execute(mapOf(")
            schema.parameters.forEach { (paramName, param) ->
                val example = when (param.type) {
                    "string" -> "\"example\""
                    "number" -> "42"
                    "boolean" -> "true"
                    else -> "\"value\""
                }
                appendLine("    \"$paramName\" to $example,")
            }
            appendLine("))")
            appendLine("println(result.result)")
        }
        appendLine("```")
        appendLine()
        
        if (isExperimental) {
            appendLine("### ⚠️ Experimental Notice")
            appendLine("This tool uses experimental DSL features. API may change in future versions.")
            appendLine()
        }
        
        appendLine("### Schema")
        appendLine("```json")
        appendLine("{")
        appendLine("  \"name\": \"$name\",")
        appendLine("  \"description\": \"$description\",")
        if (isExperimental) {
            appendLine("  \"experimental\": true,")
        }
        appendLine("  \"parameters\": {")
        schema.parameters.entries.forEachIndexed { index, (paramName, param) ->
            val comma = if (index < schema.parameters.size - 1) "," else ""
            appendLine("    \"$paramName\": {")
            appendLine("      \"type\": \"${param.type}\",")
            appendLine("      \"required\": ${param.required},")
            appendLine("      \"description\": \"${param.description}\"")
            appendLine("    }$comma")
        }
        appendLine("  }")
        appendLine("}")
        appendLine("```")
    }
}

/**
 * Tool의 간단한 한 줄 요약
 */
fun Tool.summarize(): String {
    val paramCount = schema.parameters.size
    val requiredCount = schema.parameters.values.count { it.required }
    return "Tool '$name' - $paramCount parameters ($requiredCount required)"
}

// =====================================
// FLOW DESCRIPTION
// =====================================

/**
 * Flow의 상세 정보를 Markdown 형식으로 출력
 */
fun CoreFlow.describe(): String {
    return buildString {
        appendLine("## 🔄 Flow: $name")
        appendLine("**ID**: `$id`")
        appendLine("**Description**: $description")
        appendLine()
        
        appendLine("### Flow Steps (${steps.size})")
        if (steps.isNotEmpty()) {
            appendLine("```mermaid")
            appendLine("graph TD")
            steps.forEachIndexed { index, step ->
                val nextIndex = index + 1
                if (nextIndex < steps.size) {
                    appendLine("    ${step.id}[\"${step.agentId}\"] --> ${steps[nextIndex].id}[\"${steps[nextIndex].agentId}\"]")
                } else {
                    appendLine("    ${step.id}[\"${step.agentId}\"]")
                }
            }
            appendLine("```")
            appendLine()
            
            appendLine("| Step | Agent ID | Condition |")
            appendLine("|------|----------|-----------|")
            steps.forEach { step ->
                val condition = if (step.condition != null) "✅ Has condition" else "❌ Always execute"
                appendLine("| `${step.id}` | `${step.agentId}` | $condition |")
            }
        } else {
            appendLine("- No steps defined")
        }
        appendLine()
        
        appendLine("### Execution")
        appendLine("```kotlin")
        appendLine("val result = $id.execute(")
        appendLine("    Message(content = \"input\", sender = \"user\")")
        appendLine(")")
        appendLine("```")
        appendLine()
        
        appendLine("### Flow Analysis")
        appendLine("- **Total Steps**: ${steps.size}")
        appendLine("- **Conditional Steps**: ${steps.count { it.condition != null }}")
        appendLine("- **Linear Flow**: ${if (steps.none { it.condition != null }) "✅ Yes" else "❌ No (has conditions)"}")
    }
}

/**
 * Flow의 간단한 한 줄 요약
 */
fun CoreFlow.summarize(): String {
    val conditionalSteps = steps.count { it.condition != null }
    return "Flow '$name' ($id) - ${steps.size} steps, $conditionalSteps conditional"
}

// =====================================
// REGISTRY SUMMARIES
// =====================================

/**
 * 등록된 모든 Agent들의 요약 정보
 */
fun describeAllAgents(): String {
    val agents = AgentRegistry.getAllAgents()
    
    return buildString {
        appendLine("# 🤖 Registered Agents Summary")
        appendLine("**Total Agents**: ${agents.size}")
        appendLine()
        
        if (agents.isNotEmpty()) {
            appendLine("## Quick Overview")
            agents.forEach { agent ->
                appendLine("- ${agent.summarize()}")
            }
            appendLine()
            
            appendLine("## Detailed Information")
            agents.forEach { agent ->
                appendLine(agent.describe())
                appendLine("---")
                appendLine()
            }
        } else {
            appendLine("No agents registered yet.")
            appendLine()
            appendLine("To register an agent:")
            appendLine("```kotlin")
            appendLine("val agent = buildAgent { /* configuration */ }")
            appendLine("AgentRegistry.register(agent)")
            appendLine("```")
        }
    }
}

/**
 * 등록된 모든 Tool들의 요약 정보  
 */
fun describeAllTools(): String {
    val tools = ToolRegistry.getAllTools()
    
    return buildString {
        appendLine("# 🔧 Registered Tools Summary")
        appendLine("**Total Tools**: ${tools.size}")
        appendLine()
        
        if (tools.isNotEmpty()) {
            appendLine("## Quick Overview")
            tools.forEach { tool ->
                appendLine("- ${tool.summarize()}")
            }
            appendLine()
            
            appendLine("## Detailed Information")
            tools.forEach { tool ->
                appendLine(tool.describe())
                appendLine("---")
                appendLine()
            }
        } else {
            appendLine("No tools registered yet.")
            appendLine()
            appendLine("To register a tool:")
            appendLine("```kotlin")
            appendLine("val tool = tool(\"name\") { /* configuration */ }")
            appendLine("ToolRegistry.register(tool)")
            appendLine("```")
        }
    }
}

/**
 * 전체 DSL 환경의 요약 정보
 */
fun describeAllComponents(): String {
    val agents = AgentRegistry.getAllAgents()
    val tools = ToolRegistry.getAllTools()
    
    return buildString {
        appendLine("# 🌶️ Spice DSL Environment Summary")
        appendLine("Generated at: ${java.time.LocalDateTime.now()}")
        appendLine()
        
        appendLine("## Component Overview")
        appendLine("- **Agents**: ${agents.size}")
        appendLine("- **Tools**: ${tools.size}")
        appendLine("- **Ready Agents**: ${agents.count { it.isReady() }}")
        appendLine("- **Tools per Agent**: ${if (agents.isNotEmpty()) "%.1f".format(agents.sumOf { it.getTools().size }.toDouble() / agents.size) else "0"}")
        appendLine()
        
        appendLine("## Component Health")
        val healthyAgents = agents.count { it.isReady() }
        val healthPercentage = if (agents.isNotEmpty()) (healthyAgents * 100) / agents.size else 100
        appendLine("- **Agent Health**: $healthPercentage% ($healthyAgents/${agents.size} ready)")
        
        if (agents.any { !it.isReady() }) {
            appendLine("- **Issues Found**: Some agents are not ready")
            agents.filter { !it.isReady() }.forEach {
                appendLine("  - ❌ ${it.name} (${it.id}) is not ready")
            }
        }
        appendLine()
        
        appendLine("## Quick Actions")
        appendLine("```kotlin")
        appendLine("// List all components")
        appendLine("println(describeAllComponents())")
        appendLine()
        appendLine("// Describe specific agent")
        if (agents.isNotEmpty()) {
            appendLine("println(AgentRegistry.getAgent(\"${agents.first().id}\")?.describe())")
        }
        appendLine()
        appendLine("// Describe specific tool")
        if (tools.isNotEmpty()) {
            appendLine("println(ToolRegistry.getTool(\"${tools.first().name}\")?.describe())")
        }
        appendLine("```")
        appendLine()
        
        if (agents.isNotEmpty() || tools.isNotEmpty()) {
            appendLine("---")
            appendLine()
            appendLine(describeAllAgents())
            appendLine()
            appendLine(describeAllTools())
        }
    }
}

// =====================================
// UTILITY FUNCTIONS
// =====================================

/**
 * 마크다운을 콘솔 출력용으로 변환
 */
fun String.toConsoleOutput(): String {
    return this
        .replace("## ", "")
        .replace("### ", "  ")
        .replace("**", "")
        .replace("`", "")
        .replace("✅", "[✓]")
        .replace("❌", "[✗]")
        .replace("⚠️", "[!]")
}

/**
 * DSL 상태를 간단히 체크
 */
fun checkDSLHealth(): List<String> {
    val issues = mutableListOf<String>()
    val agents = AgentRegistry.getAllAgents()
    val tools = ToolRegistry.getAllTools()
    
    if (agents.isEmpty()) {
        issues.add("No agents registered")
    }
    
    if (tools.isEmpty()) {
        issues.add("No tools registered")
    }
    
    agents.filter { !it.isReady() }.forEach {
        issues.add("Agent '${it.name}' is not ready")
    }
    
    agents.filter { it.getTools().isEmpty() }.forEach {
        issues.add("Agent '${it.name}' has no tools configured")
    }
    
    return issues
} 

// =====================================
// MARKDOWN EXPORT FUNCTIONS
// =====================================

/**
 * 전체 DSL 환경을 마크다운 파일로 내보내기
 */
fun describeAllToMarkdown(
    filePath: String = "spice-dsl-documentation.md",
    includeTimestamp: Boolean = true,
    includeExamples: Boolean = true
): String {
    val content = buildString {
        appendLine("# 🌶️ Spice DSL Documentation")
        if (includeTimestamp) {
            appendLine("*Generated on: ${java.time.LocalDateTime.now()}*")
        }
        appendLine()
        
        appendLine("## 📊 Environment Overview")
        val agents = AgentRegistry.getAllAgents()
        val tools = ToolRegistry.getAllTools()
        
        appendLine("- **Total Agents**: ${agents.size}")
        appendLine("- **Total Tools**: ${tools.size}")
        appendLine("- **Experimental Agents**: ${agents.count { isExperimentalComponent(it.id, it.name, it.description) }}")
        appendLine("- **Experimental Tools**: ${tools.count { isExperimentalComponent(it.name, it.name, it.description) }}")
        appendLine("- **Ready Agents**: ${agents.count { it.isReady() }}")
        appendLine()
        
        // Table of Contents
        appendLine("## 📋 Table of Contents")
        appendLine("1. [Agents](#agents)")
        appendLine("2. [Tools](#tools)")
        appendLine("3. [Health Status](#health-status)")
        if (includeExamples) {
            appendLine("4. [Usage Examples](#usage-examples)")
        }
        appendLine()
        
        // Agents Section
        appendLine("## Agents")
        if (agents.isNotEmpty()) {
            agents.forEach { agent ->
                appendLine(agent.describe())
                appendLine("---")
                appendLine()
            }
        } else {
            appendLine("No agents registered.")
            appendLine()
        }
        
        // Tools Section
        appendLine("## Tools")
        if (tools.isNotEmpty()) {
            tools.forEach { tool ->
                appendLine(tool.describe())
                appendLine("---")
                appendLine()
            }
        } else {
            appendLine("No tools registered.")
            appendLine()
        }
        
        // Health Status
        appendLine("## Health Status")
        val healthIssues = checkDSLHealth()
        if (healthIssues.isEmpty()) {
            appendLine("✅ **All systems healthy!**")
        } else {
            appendLine("⚠️ **Issues detected:**")
            healthIssues.forEach { issue ->
                appendLine("- $issue")
            }
        }
        appendLine()
        
        // Usage Examples
        if (includeExamples) {
            appendLine("## Usage Examples")
            appendLine()
            appendLine("### Quick Start")
            appendLine("```kotlin")
            appendLine("// Import DSL functions")
            appendLine("import io.github.spice.dsl.*")
            appendLine()
            if (agents.isNotEmpty()) {
                val firstAgent = agents.first()
                appendLine("// Use existing agent")
                appendLine("val agent = AgentRegistry.getAgent(\"${firstAgent.id}\")")
                appendLine("val response = agent?.processMessage(")
                appendLine("    Message(content = \"Hello\", sender = \"user\")")
                appendLine(")")
                appendLine()
            }
            if (tools.isNotEmpty()) {
                val firstTool = tools.first()
                appendLine("// Use existing tool")
                appendLine("val tool = ToolRegistry.getTool(\"${firstTool.name}\")")
                if (firstTool.schema.parameters.isNotEmpty()) {
                    appendLine("val result = tool?.execute(mapOf(")
                    firstTool.schema.parameters.entries.take(2).forEach { (name, param) ->
                        val example = when (param.type) {
                            "string" -> "\"example\""
                            "number" -> "42"
                            else -> "\"value\""
                        }
                        appendLine("    \"$name\" to $example,")
                    }
                    appendLine("))")
                }
                appendLine()
            }
            appendLine("```")
            appendLine()
            
            appendLine("### Environment Inspection")
            appendLine("```kotlin")
            appendLine("// Check system health")
            appendLine("val issues = checkDSLHealth()")
            appendLine("println(\"Health issues: \${issues.size}\")")
            appendLine()
            appendLine("// Get detailed component information")
            appendLine("println(describeAllComponents())")
            appendLine()
            appendLine("// Export documentation")
            appendLine("writeMarkdownToFile(describeAllToMarkdown(), \"my-docs.md\")")
            appendLine("```")
        }
        
        appendLine()
        appendLine("---")
        appendLine("*Documentation generated by Spice DSL Summary Generator*")
    }
    
    return content
}

/**
 * 마크다운 내용을 파일로 저장
 */
fun writeMarkdownToFile(
    content: String, 
    filePath: String,
    overwrite: Boolean = true
): Boolean {
    return try {
        val file = java.io.File(filePath)
        if (!overwrite && file.exists()) {
            println("File already exists: $filePath (use overwrite=true to replace)")
            false
        } else {
            file.writeText(content)
            println("Documentation exported to: $filePath")
            true
        }
    } catch (e: Exception) {
        println("Failed to write markdown file: ${e.message}")
        false
    }
}

/**
 * 특정 컴포넌트들만 마크다운으로 내보내기
 */
fun exportComponentsToMarkdown(
    agentIds: List<String> = emptyList(),
    toolNames: List<String> = emptyList(),
    filePath: String = "selected-components.md"
): String {
    val content = buildString {
        appendLine("# 🌶️ Selected Spice Components")
        appendLine("*Generated on: ${java.time.LocalDateTime.now()}*")
        appendLine()
        
        if (agentIds.isNotEmpty()) {
            appendLine("## Selected Agents")
            agentIds.forEach { agentId ->
                val agent = AgentRegistry.getAgent(agentId)
                if (agent != null) {
                    appendLine(agent.describe())
                    appendLine("---")
                    appendLine()
                } else {
                    appendLine("⚠️ Agent not found: `$agentId`")
                    appendLine()
                }
            }
        }
        
        if (toolNames.isNotEmpty()) {
            appendLine("## Selected Tools")
            toolNames.forEach { toolName ->
                val tool = ToolRegistry.getTool(toolName)
                if (tool != null) {
                    appendLine(tool.describe())
                    appendLine("---")
                    appendLine()
                } else {
                    appendLine("⚠️ Tool not found: `$toolName`")
                    appendLine()
                }
            }
        }
        
        if (agentIds.isEmpty() && toolNames.isEmpty()) {
            appendLine("No components selected for export.")
        }
    }
    
    return content
}

/**
 * 컴포넌트가 experimental인지 확인하는 유틸리티 함수
 */
private fun isExperimentalComponent(id: String, name: String, description: String): Boolean {
    return id.contains("experimental", ignoreCase = true) ||
           name.contains("experimental", ignoreCase = true) ||
           description.contains("experimental", ignoreCase = true)
}

/**
 * 마크다운 내용을 콘솔에 출력 (파일 저장 없이)
 */
fun printMarkdownDocumentation(
    includeTimestamp: Boolean = true,
    includeExamples: Boolean = false
) {
    val markdown = describeAllToMarkdown(
        includeTimestamp = includeTimestamp,
        includeExamples = includeExamples
    )
    println(markdown.toConsoleOutput())
}

/**
 * 빠른 문서화 내보내기 - 현재 환경을 즉시 파일로 저장
 */
fun quickExportDocumentation(
    fileName: String = "spice-docs-${System.currentTimeMillis()}.md"
): Boolean {
    val markdown = describeAllToMarkdown(
        filePath = fileName,
        includeTimestamp = true,
        includeExamples = true
    )
    return writeMarkdownToFile(markdown, fileName)
} 