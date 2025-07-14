package io.github.spice.model

/**
 * 🗂️ ModelContext - Conversation context management
 * 
 * Performs a similar role to Autogen's BufferedContext.
 * Automatically manages message history within buffer size,
 * enabling context-aware conversations.
 */
data class ModelContext(
    /**
     * Buffer size (message count limit)
     */
    val bufferSize: Int = 20,
    
    /**
     * System prompt (fixed)
     */
    val systemPrompt: String? = null
) {
    private val messages = mutableListOf<ModelMessage>()
    
    /**
     * Add message with buffer management
     */
    fun addMessage(message: ModelMessage) {
        messages.add(message)
        
        // Manage buffer size while maintaining system messages
        while (messages.size > bufferSize) {
            // Remove first non-system message
            val firstNonSystemIndex = messages.indexOfFirst { !it.isSystem() }
            if (firstNonSystemIndex != -1) {
                messages.removeAt(firstNonSystemIndex)
            } else {
                // If all messages are system messages, remove the last one
                messages.removeAt(messages.size - 1)
            }
        }
    }
    
    /**
     * Add ModelMessage directly
     */
    fun addMessage(role: String, content: String, metadata: Map<String, Any> = emptyMap()) {
        addMessage(ModelMessage(role, content, metadata))
    }
    
    /**
     * Add user message (convenience function)
     */
    fun addUserMessage(content: String, metadata: Map<String, Any> = emptyMap()) {
        addMessage(ModelMessage.user(content, metadata))
    }
    
    /**
     * Add assistant message (convenience function)
     */
    fun addAssistantMessage(content: String, metadata: Map<String, Any> = emptyMap()) {
        addMessage(ModelMessage.assistant(content, metadata))
    }
    
    /**
     * Add system message (convenience function)
     */
    fun addSystemMessage(content: String, metadata: Map<String, Any> = emptyMap()) {
        addMessage(ModelMessage.system(content, metadata))
    }
    
    /**
     * Get all messages (read-only)
     */
    fun getMessages(): List<ModelMessage> {
        return messages.toList()
    }
    
    /**
     * Get all messages including system prompt
     */
    fun getAllMessages(): List<ModelMessage> {
        val allMessages = mutableListOf<ModelMessage>()
        
        // Add system prompt if set and first message is not system message
        if (systemPrompt != null && (messages.isEmpty() || !messages.first().isSystem())) {
            allMessages.add(ModelMessage.system(systemPrompt))
        }
        
        // Add existing messages
        allMessages.addAll(messages)
        
        return allMessages
    }
    
    /**
     * Clear context
     */
    fun clear() {
        messages.clear()
    }
    
    /**
     * Get message count
     */
    fun getMessageCount(): Int {
        return messages.size
    }
    
    /**
     * Check if context is empty
     */
    fun isEmpty(): Boolean {
        return messages.isEmpty()
    }
    
    /**
     * Get last message
     */
    fun getLastMessage(): ModelMessage? {
        return messages.lastOrNull()
    }
    
    /**
     * Get messages of specific role only
     */
    fun getMessagesByRole(role: String): List<ModelMessage> {
        return messages.filter { it.role == role }
    }
    
    /**
     * Get estimated token count (simple approximation)
     */
    fun getEstimatedTokenCount(): Int {
        val allMessages = getAllMessages()
        val totalTokens = messages.sumOf { it.content.length } // Simple token estimation
        return totalTokens / 4 // Rough approximation: 1 token ≈ 4 characters
    }
    
    /**
     * Get context summary
     */
    fun getSummary(): Map<String, Any> {
        return mapOf(
            "messageCount" to getMessageCount(),
            "bufferSize" to bufferSize,
            "estimatedTokens" to getEstimatedTokenCount(),
            "hasSystemPrompt" to (systemPrompt != null),
            "isEmpty" to isEmpty()
        )
    }
    
    /**
     * Copy context (create new instance)
     */
    fun copy(): ModelContext {
        val newContext = ModelContext(bufferSize, systemPrompt)
        messages.forEach { message ->
            newContext.addMessage(message)
        }
        return newContext
    }
    
    /**
     * Create context with different buffer size
     */
    fun withBufferSize(newBufferSize: Int): ModelContext {
        val newContext = ModelContext(newBufferSize, systemPrompt)
        messages.forEach { message ->
            newContext.addMessage(message)
        }
        return newContext
    }
    
    /**
     * Create context with different system prompt
     */
    fun withSystemPrompt(newSystemPrompt: String?): ModelContext {
        val newContext = ModelContext(bufferSize, newSystemPrompt)
        messages.forEach { message ->
            newContext.addMessage(message)
        }
        return newContext
    }
    
    /**
     * Buffer usage rate (%)
     */
    fun getBufferUsage(): Double {
        return if (bufferSize > 0) (getMessageCount().toDouble() / bufferSize) * 100 else 0.0
    }
    
    /**
     * Summary string
     */
    override fun toString(): String {
        return "ModelContext(messages=${getMessageCount()}/$bufferSize, tokens≈${getEstimatedTokenCount()})"
    }
} 