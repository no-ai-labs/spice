package io.github.spice.model

import kotlinx.serialization.Serializable

/**
 * 📨 ModelMessage - Standardized message structure
 * 
 * Autogen-style standardized message structure.
 * Provides a unified interface between various LLM models.
 */
@Serializable
data class ModelMessage(
    /**
     * Message role (system, user, assistant, function)
     */
    val role: String,
    
    /**
     * Message content
     */
    val content: String,
    
    /**
     * Additional metadata (optional)
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    
    companion object {
        /**
         * Create system message
         */
        fun system(content: String, metadata: Map<String, Any> = emptyMap()): ModelMessage {
            return ModelMessage("system", content, metadata)
        }
        
        /**
         * Create user message
         */
        fun user(content: String, metadata: Map<String, Any> = emptyMap()): ModelMessage {
            return ModelMessage("user", content, metadata)
        }
        
        /**
         * Create assistant message
         */
        fun assistant(content: String, metadata: Map<String, Any> = emptyMap()): ModelMessage {
            return ModelMessage("assistant", content, metadata)
        }
        
        /**
         * Create function call message
         */
        fun function(
            name: String,
            content: String,
            metadata: Map<String, Any> = emptyMap()
        ): ModelMessage {
            return ModelMessage("function", content, metadata + mapOf("function_name" to name))
        }
    }
    
    /**
     * Check if message is system message
     */
    fun isSystem(): Boolean = role == "system"
    
    /**
     * Check if message is user message
     */
    fun isUser(): Boolean = role == "user"
    
    /**
     * Check if message is assistant message
     */
    fun isAssistant(): Boolean = role == "assistant"
    
    /**
     * Check if message is function call message
     */
    fun isFunction(): Boolean = role == "function"
} 