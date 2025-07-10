package io.github.spice

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageJsonSerializationTest {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    @Test
    fun `기본 Message JSON 직렬화 및 역직렬화 테스트`() {
        // Given: 기본 Message 객체
        val originalMessage = Message(
            content = "Hello, Spice!",
            sender = "user",
            type = MessageType.TEXT,
            receiver = "agent",
            metadata = mapOf(
                "priority" to "high",
                "version" to "1.0"
            )
        )
        
        // When: JSON으로 직렬화
        val jsonString = json.encodeToString(originalMessage)
        
        // Then: JSON 형태 확인
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("Hello, Spice!"))
        assertTrue(jsonString.contains("TEXT"))
        assertTrue(jsonString.contains("priority"))
        println("Serialized JSON:")
        println(jsonString)
        
        // When: JSON에서 역직렬화
        val deserializedMessage = json.decodeFromString<Message>(jsonString)
        
        // Then: 원본과 동일한지 확인
        assertEquals(originalMessage.id, deserializedMessage.id)
        assertEquals(originalMessage.content, deserializedMessage.content)
        assertEquals(originalMessage.sender, deserializedMessage.sender)
        assertEquals(originalMessage.receiver, deserializedMessage.receiver)
        assertEquals(originalMessage.type, deserializedMessage.type)
        assertEquals(originalMessage.metadata, deserializedMessage.metadata)
        assertEquals(originalMessage.timestamp, deserializedMessage.timestamp)
        assertEquals(originalMessage.parentId, deserializedMessage.parentId)
        assertEquals(originalMessage.conversationId, deserializedMessage.conversationId)
    }
    
    @Test
    fun `모든 MessageType JSON 직렬화 테스트`() {
        val messageTypes = MessageType.values()
        
        messageTypes.forEach { messageType ->
            // Given: 각 MessageType별 Message
            val message = Message(
                content = "Test message for $messageType",
                sender = "test-sender",
                type = messageType,
                metadata = mapOf("messageType" to messageType.toString())
            )
            
            // When: JSON 직렬화 및 역직렬화
            val jsonString = json.encodeToString(message)
            val deserializedMessage = json.decodeFromString<Message>(jsonString)
            
            // Then: MessageType이 정확히 보존되는지 확인
            assertEquals(messageType, deserializedMessage.type, "MessageType should be preserved for $messageType")
            assertEquals(message.content, deserializedMessage.content)
            assertEquals(message.sender, deserializedMessage.sender)
            assertEquals(message.metadata, deserializedMessage.metadata)
        }
    }
    
    @Test
    fun `복잡한 메타데이터가 있는 Message JSON 직렬화 테스트`() {
        // Given: 복잡한 메타데이터를 가진 Message
        val complexMessage = Message(
            content = "Complex message with metadata",
            sender = "complex-agent",
            type = MessageType.TOOL_CALL,
            receiver = "tool-runner",
            metadata = mapOf(
                "toolName" to "web_search",
                "param_query" to "Kotlin serialization",
                "param_limit" to "10",
                "priority" to "medium",
                "timeout" to "30000",
                "retryCount" to "3",
                "source" to "user_interface",
                "requestId" to "req-12345",
                "sessionId" to "session-abcde"
            ),
            parentId = "parent-message-id",
            conversationId = "conversation-xyz"
        )
        
        // When: JSON 직렬화 및 역직렬화
        val jsonString = json.encodeToString(complexMessage)
        val deserializedMessage = json.decodeFromString<Message>(jsonString)
        
        // Then: 모든 복잡한 데이터가 보존되는지 확인
        assertEquals(complexMessage.content, deserializedMessage.content)
        assertEquals(complexMessage.sender, deserializedMessage.sender)
        assertEquals(complexMessage.receiver, deserializedMessage.receiver)
        assertEquals(complexMessage.type, deserializedMessage.type)
        assertEquals(complexMessage.parentId, deserializedMessage.parentId)
        assertEquals(complexMessage.conversationId, deserializedMessage.conversationId)
        
        // 메타데이터 세부 확인
        assertEquals(complexMessage.metadata.size, deserializedMessage.metadata.size)
        complexMessage.metadata.forEach { (key, value) ->
            assertEquals(value, deserializedMessage.metadata[key], "Metadata key '$key' should be preserved")
        }
        
        println("Complex Message JSON:")
        println(jsonString)
    }
    
    @Test
    fun `Message 체인 JSON 직렬화 테스트`() {
        // Given: 연결된 Message 체인
        val firstMessage = Message(
            content = "Start of conversation",
            sender = "user",
            type = MessageType.WORKFLOW_START
        )
        
        val secondMessage = firstMessage.createReply(
            content = "Processing your request",
            sender = "agent",
            type = MessageType.TEXT
        )
        
        val thirdMessage = secondMessage.createReply(
            content = "Task completed",
            sender = "agent",
            type = MessageType.WORKFLOW_END
        )
        
        val messageChain = listOf(firstMessage, secondMessage, thirdMessage)
        
        // When: Message 체인을 JSON 배열로 직렬화
        val jsonString = json.encodeToString(messageChain)
        val deserializedChain = json.decodeFromString<List<Message>>(jsonString)
        
        // Then: 체인 구조가 보존되는지 확인
        assertEquals(messageChain.size, deserializedChain.size)
        
        deserializedChain.forEachIndexed { index, message ->
            val original = messageChain[index]
            assertEquals(original.id, message.id)
            assertEquals(original.content, message.content)
            assertEquals(original.sender, message.sender)
            assertEquals(original.type, message.type)
            assertEquals(original.parentId, message.parentId)
            assertEquals(original.conversationId, message.conversationId)
        }
        
        // 체인 연결 확인
        assertEquals(firstMessage.id, deserializedChain[1].parentId)
        assertEquals(secondMessage.id, deserializedChain[2].parentId)
        assertEquals(firstMessage.id, deserializedChain[2].conversationId)
        
        println("Message Chain JSON:")
        println(jsonString)
    }
    
    @Test
    fun `ToolResult JSON 직렬화 테스트`() {
        // Given: 성공 및 실패 ToolResult
        val successResult = ToolResult.success(
            result = "Tool execution successful",
            metadata = mapOf(
                "executionTime" to "150ms",
                "toolVersion" to "1.2.3"
            )
        )
        
        val errorResult = ToolResult.error(
            error = "Tool execution failed",
            metadata = mapOf(
                "errorCode" to "E001",
                "retryAfter" to "5000"
            )
        )
        
        // When: JSON 직렬화 및 역직렬화
        val successJson = json.encodeToString(successResult)
        val errorJson = json.encodeToString(errorResult)
        
        val deserializedSuccess = json.decodeFromString<ToolResult>(successJson)
        val deserializedError = json.decodeFromString<ToolResult>(errorJson)
        
        // Then: ToolResult 데이터 보존 확인
        
        // 성공 케이스
        assertEquals(successResult.success, deserializedSuccess.success)
        assertEquals(successResult.result, deserializedSuccess.result)
        assertEquals(successResult.error, deserializedSuccess.error)
        assertEquals(successResult.metadata, deserializedSuccess.metadata)
        
        // 실패 케이스
        assertEquals(errorResult.success, deserializedError.success)
        assertEquals(errorResult.result, deserializedError.result)
        assertEquals(errorResult.error, deserializedError.error)
        assertEquals(errorResult.metadata, deserializedError.metadata)
        
        println("Success ToolResult JSON:")
        println(successJson)
        println("\nError ToolResult JSON:")
        println(errorJson)
    }
    
    @Test
    fun `ToolSchema JSON 직렬화 테스트`() {
        // Given: 복잡한 ToolSchema
        val toolSchema = ToolSchema(
            name = "advanced_calculator",
            description = "Advanced mathematical calculator with multiple operations",
            parameters = mapOf(
                "numbers" to ParameterSchema(
                    type = "array",
                    description = "Array of numbers to calculate",
                    required = true
                ),
                "operation" to ParameterSchema(
                    type = "string",
                    description = "Mathematical operation to perform",
                    required = true
                ),
                "precision" to ParameterSchema(
                    type = "number",
                    description = "Decimal precision for result",
                    required = false
                ),
                "format" to ParameterSchema(
                    type = "string",
                    description = "Output format",
                    required = false
                )
            )
        )
        
        // When: JSON 직렬화 및 역직렬화
        val jsonString = json.encodeToString(toolSchema)
        val deserializedSchema = json.decodeFromString<ToolSchema>(jsonString)
        
        // Then: ToolSchema 데이터 보존 확인
        assertEquals(toolSchema.name, deserializedSchema.name)
        assertEquals(toolSchema.description, deserializedSchema.description)
        assertEquals(toolSchema.parameters.size, deserializedSchema.parameters.size)
        
        toolSchema.parameters.forEach { (paramName, paramSchema) ->
            val deserializedParam = deserializedSchema.parameters[paramName]
            assertNotNull(deserializedParam, "Parameter '$paramName' should exist")
            assertEquals(paramSchema.type, deserializedParam.type)
            assertEquals(paramSchema.description, deserializedParam.description)
            assertEquals(paramSchema.required, deserializedParam.required)
        }
        
        println("ToolSchema JSON:")
        println(jsonString)
    }
    
    @Test
    fun `JSON에서 Message 생성 후 AgentEngine 통합 테스트`() {
        // Given: JSON 문자열로부터 Message 생성
        val jsonMessage = """
        {
            "id": "test-message-001",
            "content": "Process this JSON message",
            "type": "TEXT",
            "sender": "json-client",
            "receiver": "json-processor",
            "metadata": {
                "source": "json",
                "format": "application/json",
                "version": "2.0"
            },
            "timestamp": ${System.currentTimeMillis()},
            "parentId": null,
            "conversationId": "json-conversation"
        }
        """.trimIndent()
        
        // When: JSON에서 Message 역직렬화
        val message = json.decodeFromString<Message>(jsonMessage)
        
        // AgentEngine으로 처리
        val universalAgent = UniversalAgent()
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(universalAgent)
        
        val result = kotlin.runCatching {
            kotlinx.coroutines.runBlocking {
                agentEngine.receive(message)
            }
        }
        
        // Then: 처리 결과 확인
        assertTrue(result.isSuccess, "JSON message should be processed successfully")
        val agentMessage = result.getOrThrow()
        
        assertTrue(agentMessage.success)
        assertEquals("universal-agent", agentMessage.agentId)
        assertTrue(agentMessage.response.content.contains("Process this JSON message"))
        
        // 처리 결과를 다시 JSON으로 직렬화
        val responseJson = json.encodeToString(agentMessage.response)
        println("Response JSON:")
        println(responseJson)
        
        // 역직렬화하여 데이터 무결성 확인
        val deserializedResponse = json.decodeFromString<Message>(responseJson)
        assertEquals(agentMessage.response.content, deserializedResponse.content)
        assertEquals(agentMessage.response.sender, deserializedResponse.sender)
        assertEquals(agentMessage.response.type, deserializedResponse.type)
    }
} 