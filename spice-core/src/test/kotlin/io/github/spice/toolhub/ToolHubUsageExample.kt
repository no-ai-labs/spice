package io.github.spice.toolhub

import io.github.spice.*
import io.github.spice.agents.VertexAgent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * 🎯 ToolHub 사용 예제
 * 
 * 이 클래스는 ToolHub 시스템의 다양한 사용법을 보여주는 예제들을 포함합니다.
 */
class ToolHubUsageExample {
    
    @Test
    fun `기본 ToolHub 사용 예제`() = runBlocking {
        // 1. 도구들 정의
        val webSearchTool = object : BaseTool(
            name = "web_search",
            description = "웹에서 정보를 검색합니다",
            schema = ToolSchema(
                name = "web_search",
                description = "웹 검색 도구",
                parameters = mapOf(
                    "query" to ParameterSchema("string", "검색할 키워드", required = true),
                    "limit" to ParameterSchema("number", "검색 결과 수", required = false)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val query = parameters["query"] as? String ?: ""
                val limit = (parameters["limit"] as? Number)?.toInt() ?: 5
                
                // 실제 웹 검색 로직 (여기서는 모의 구현)
                val results = (1..limit).map { "검색 결과 $it: $query 관련 정보" }
                
                return io.github.spice.ToolResult.success(
                    result = results.joinToString("\n"),
                    metadata = mapOf(
                        "query" to query,
                        "resultCount" to results.size.toString()
                    )
                )
            }
        }
        
        val fileReadTool = object : BaseTool(
            name = "file_read",
            description = "파일을 읽습니다",
            schema = ToolSchema(
                name = "file_read",
                description = "파일 읽기 도구",
                parameters = mapOf(
                    "path" to ParameterSchema("string", "읽을 파일 경로", required = true)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val path = parameters["path"] as? String ?: ""
                
                // 실제 파일 읽기 로직 (여기서는 모의 구현)
                val content = "파일 내용: $path"
                
                return io.github.spice.ToolResult.success(
                    result = content,
                    metadata = mapOf(
                        "path" to path,
                        "size" to content.length.toString()
                    )
                )
            }
        }
        
        // 2. ToolHub 생성 및 시작
        val toolHub = staticToolHub {
            addTool(webSearchTool)
            addTool(fileReadTool)
        }
        
        toolHub.start()
        
        // 3. 도구 실행
        val context = ToolContext()
        
        val searchResult = toolHub.callTool(
            name = "web_search",
            parameters = mapOf(
                "query" to "Kotlin coroutines",
                "limit" to 3
            ),
            context = context
        )
        
        println("🔍 검색 결과:")
        println(searchResult)
        
        val fileResult = toolHub.callTool(
            name = "file_read",
            parameters = mapOf("path" to "/path/to/file.txt"),
            context = context
        )
        
        println("\n📄 파일 읽기 결과:")
        println(fileResult)
        
        // 4. 실행 통계 확인
        if (toolHub is StaticToolHub) {
            val stats = toolHub.getExecutionStats(context)
            println("\n📊 실행 통계:")
            println("총 실행 횟수: ${stats["total_executions"]}")
            println("성공률: ${stats["success_rate"]}%")
        }
        
        toolHub.stop()
    }
    
    @Test
    fun `Agent와 ToolHub 통합 예제`() = runBlocking {
        // 1. 도구 정의
        val calculatorTool = object : BaseTool(
            name = "calculator",
            description = "수학 계산을 수행합니다",
            schema = ToolSchema(
                name = "calculator",
                description = "계산기 도구",
                parameters = mapOf(
                    "operation" to ParameterSchema("string", "연산 종류", required = true),
                    "a" to ParameterSchema("number", "첫 번째 숫자", required = true),
                    "b" to ParameterSchema("number", "두 번째 숫자", required = true)
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
        
        // 2. ToolHub 생성
        val toolHub = createStaticToolHub(calculatorTool)
        
        // 3. Agent 생성 및 ToolHub 통합
        val baseAgent = object : BaseAgent(
            id = "math-agent",
            name = "Math Agent",
            description = "수학 계산을 도와주는 Agent",
            capabilities = listOf("mathematics", "calculation")
        ) {
            override suspend fun processMessage(message: Message): Message {
                return message.createReply(
                    content = "수학 Agent가 처리했습니다: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT
                )
            }
        }
        
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // 4. Agent를 통한 도구 호출
        val toolCallMessage = Message(
            id = "calc-1",
            type = MessageType.TOOL_CALL,
            content = "계산 수행",
            sender = "user",
            metadata = mapOf(
                "toolName" to "calculator",
                "param_operation" to "multiply",
                "param_a" to 15,
                "param_b" to 7
            )
        )
        
        val response = toolHubAgent.processMessage(toolCallMessage)
        
        println("🤖 Agent 응답:")
        println("타입: ${response.type}")
        println("내용: ${response.content}")
        println("메타데이터: ${response.metadata}")
        
        // 5. 일반 메시지 처리
        val textMessage = Message(
            id = "text-1",
            type = MessageType.TEXT,
            content = "안녕하세요",
            sender = "user"
        )
        
        val textResponse = toolHubAgent.processMessage(textMessage)
        println("\n💬 텍스트 응답:")
        println(textResponse.content)
        
        // 6. 도구 실행 통계
        val stats = toolHubAgent.getToolExecutionStats()
        println("\n📊 도구 실행 통계:")
        println("총 실행 횟수: ${stats["total_executions"]}")
        println("실행 히스토리: ${stats["execution_history"]}")
    }
    
    @Test
    fun `ToolChain과 ToolHub 통합 예제`() = runBlocking {
        // 1. 데이터 처리 도구들 정의
        val validatorTool = object : BaseTool(
            name = "validator",
            description = "데이터 유효성 검사",
            schema = ToolSchema(
                name = "validator",
                description = "데이터 검증 도구",
                parameters = mapOf(
                    "data" to ParameterSchema("string", "검증할 데이터", required = true)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val data = parameters["data"] as? String ?: ""
                
                return if (data.isNotBlank() && data.length >= 3) {
                    io.github.spice.ToolResult.success(
                        result = data,
                        metadata = mapOf("validation" to "passed")
                    )
                } else {
                    io.github.spice.ToolResult.error("데이터가 유효하지 않습니다")
                }
            }
        }
        
        val transformerTool = object : BaseTool(
            name = "transformer",
            description = "데이터 변환",
            schema = ToolSchema(
                name = "transformer",
                description = "데이터 변환 도구",
                parameters = mapOf(
                    "data" to ParameterSchema("string", "변환할 데이터", required = true),
                    "format" to ParameterSchema("string", "변환 형식", required = false)
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
                    else -> data
                }
                
                return io.github.spice.ToolResult.success(
                    result = transformed,
                    metadata = mapOf(
                        "original" to data,
                        "format" to format
                    )
                )
            }
        }
        
        val analyzerTool = object : BaseTool(
            name = "analyzer",
            description = "데이터 분석",
            schema = ToolSchema(
                name = "analyzer",
                description = "데이터 분석 도구",
                parameters = mapOf(
                    "data" to ParameterSchema("string", "분석할 데이터", required = true)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val data = parameters["data"] as? String ?: ""
                
                val analysis = mapOf(
                    "length" to data.length,
                    "words" to data.split("\\s+".toRegex()).size,
                    "uppercase_chars" to data.count { it.isUpperCase() },
                    "lowercase_chars" to data.count { it.isLowerCase() }
                )
                
                return io.github.spice.ToolResult.success(
                    result = "분석 완료: ${analysis}",
                    metadata = analysis.mapValues { it.value.toString() }
                )
            }
        }
        
        // 2. ToolHub 생성
        val toolHub = staticToolHub {
            addTool(validatorTool)
            addTool(transformerTool)
            addTool(analyzerTool)
        }
        
        toolHub.start()
        
        // 3. ToolChain 정의 및 실행
        val result = executeToolChain(
            toolHub = toolHub,
            chainName = "data_processing_pipeline",
            initialParameters = mapOf("input" to "Hello World")
        ) {
            step(
                name = "validate_data",
                toolName = "validator",
                parameterMapping = mapOf("input" to "data")
            )
            step(
                name = "transform_data",
                toolName = "transformer",
                parameters = mapOf("format" to "uppercase"),
                parameterMapping = mapOf("validate_data_result" to "data")
            )
            step(
                name = "analyze_data",
                toolName = "analyzer",
                parameterMapping = mapOf("transform_data_result" to "data")
            )
        }
        
        println("🔗 ToolChain 실행 결과:")
        println(result.getSummary())
        
        println("\n📋 상세 로그:")
        println(result.getDetailedLog())
        
        println("\n🎯 최종 컨텍스트:")
        result.finalContext.metadata.forEach { (key, value) ->
            println("  $key: $value")
        }
        
        toolHub.stop()
    }
    
    @Test
    fun `DSL을 사용한 ToolHub Agent 생성 예제`() = runBlocking {
        // 1. 도구 정의
        val greetingTool = object : BaseTool(
            name = "greeting",
            description = "인사말 생성",
            schema = ToolSchema(
                name = "greeting",
                description = "인사말 생성 도구",
                parameters = mapOf(
                    "name" to ParameterSchema("string", "이름", required = true),
                    "language" to ParameterSchema("string", "언어", required = false)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val name = parameters["name"] as? String ?: "World"
                val language = parameters["language"] as? String ?: "ko"
                
                val greeting = when (language) {
                    "ko" -> "안녕하세요, ${name}님!"
                    "en" -> "Hello, $name!"
                    "ja" -> "こんにちは、${name}さん！"
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
        
        // 2. ToolHub 생성
        val toolHub = createStaticToolHub(greetingTool)
        
        // 3. DSL을 사용한 Agent 생성
        val agent = toolHubAgent(
            id = "greeting-agent",
            name = "Greeting Agent",
            description = "인사말을 생성하는 Agent",
            toolHub = toolHub
        ) {
            capabilities("greeting", "multilingual")
            
            messageHandler { message ->
                when (message.type) {
                    MessageType.TEXT -> {
                        // 텍스트 메시지에서 이름 추출하여 자동 인사
                        val name = message.content.substringAfter("안녕 ").substringBefore(" ")
                        if (name.isNotBlank() && name != message.content) {
                            val greetingMessage = Message(
                                id = "auto-greeting-${message.id}",
                                type = MessageType.TOOL_CALL,
                                content = "자동 인사",
                                sender = message.sender,
                                metadata = mapOf(
                                    "toolName" to "greeting",
                                    "param_name" to name,
                                    "param_language" to "ko"
                                )
                            )
                            // 재귀적으로 도구 호출 처리
                            return@messageHandler processMessage(greetingMessage)
                        } else {
                            message.createReply(
                                content = "안녕하세요! 저는 인사말 Agent입니다. '안녕 [이름]' 형태로 말씀해 주세요.",
                                sender = "greeting-agent",
                                type = MessageType.TEXT
                            )
                        }
                    }
                    else -> {
                        message.createReply(
                            content = "인사말 Agent가 처리했습니다: ${message.content}",
                            sender = "greeting-agent",
                            type = MessageType.TEXT
                        )
                    }
                }
            }
        }
        
        // 4. Agent 테스트
        val testMessage = Message(
            id = "test-1",
            type = MessageType.TEXT,
            content = "안녕 알파",
            sender = "user"
        )
        
        val response = agent.processMessage(testMessage)
        
        println("🤖 DSL Agent 응답:")
        println("타입: ${response.type}")
        println("내용: ${response.content}")
        
        // 5. 직접 도구 호출 테스트
        val toolCallMessage = Message(
            id = "tool-test-1",
            type = MessageType.TOOL_CALL,
            content = "인사말 생성",
            sender = "user",
            metadata = mapOf(
                "toolName" to "greeting",
                "param_name" to "멘타트",
                "param_language" to "en"
            )
        )
        
        val toolResponse = agent.processMessage(toolCallMessage)
        
        println("\n🛠️ 도구 호출 응답:")
        println("타입: ${toolResponse.type}")
        println("내용: ${toolResponse.content}")
        
        // 6. 통계 확인
        val stats = agent.getToolExecutionStats()
        println("\n📊 실행 통계:")
        stats.forEach { (key, value) ->
            println("  $key: $value")
        }
    }
} 