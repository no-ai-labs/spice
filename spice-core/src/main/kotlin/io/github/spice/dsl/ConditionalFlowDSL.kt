package io.github.spice.dsl

import io.github.spice.*

/**
 * 🔀 Conditional Flow DSL
 * 
 * Enhanced DSL for complex conditional routing with when/then/else patterns
 * and compound condition support.
 */

/**
 * Condition evaluator interface
 */
interface ConditionEvaluator {
    suspend fun evaluate(message: Message): Boolean
}

/**
 * Simple condition implementation
 */
class SimpleCondition(
    private val predicate: suspend (Message) -> Boolean
) : ConditionEvaluator {
    override suspend fun evaluate(message: Message): Boolean = predicate(message)
}

/**
 * Compound condition types
 */
enum class LogicalOperator {
    AND, OR, NOT
}

/**
 * Compound condition implementation
 */
class CompoundCondition(
    private val operator: LogicalOperator,
    private val conditions: List<ConditionEvaluator>
) : ConditionEvaluator {
    
    override suspend fun evaluate(message: Message): Boolean {
        return when (operator) {
            LogicalOperator.AND -> conditions.all { it.evaluate(message) }
            LogicalOperator.OR -> conditions.any { it.evaluate(message) }
            LogicalOperator.NOT -> conditions.none { it.evaluate(message) }
        }
    }
}

/**
 * Content-based conditions
 */
object ContentConditions {
    fun contains(text: String, ignoreCase: Boolean = true): ConditionEvaluator {
        return SimpleCondition { message ->
            message.content.contains(text, ignoreCase)
        }
    }
    
    fun startsWith(prefix: String, ignoreCase: Boolean = true): ConditionEvaluator {
        return SimpleCondition { message ->
            message.content.startsWith(prefix, ignoreCase)
        }
    }
    
    fun endsWith(suffix: String, ignoreCase: Boolean = true): ConditionEvaluator {
        return SimpleCondition { message ->
            message.content.endsWith(suffix, ignoreCase)
        }
    }
    
    fun matches(regex: Regex): ConditionEvaluator {
        return SimpleCondition { message ->
            regex.containsMatchIn(message.content)
        }
    }
    
    fun lengthGreaterThan(length: Int): ConditionEvaluator {
        return SimpleCondition { message ->
            message.content.length > length
        }
    }
    
    fun lengthLessThan(length: Int): ConditionEvaluator {
        return SimpleCondition { message ->
            message.content.length < length
        }
    }
    
    fun isEmpty(): ConditionEvaluator {
        return SimpleCondition { message ->
            message.content.isBlank()
        }
    }
    
    fun isNotEmpty(): ConditionEvaluator {
        return SimpleCondition { message ->
            message.content.isNotBlank()
        }
    }
}

/**
 * Metadata-based conditions
 */
object MetadataConditions {
    fun hasKey(key: String): ConditionEvaluator {
        return SimpleCondition { message ->
            message.metadata.containsKey(key)
        }
    }
    
    fun keyEquals(key: String, value: String): ConditionEvaluator {
        return SimpleCondition { message ->
            message.metadata[key] == value
        }
    }
    
    fun keyContains(key: String, substring: String): ConditionEvaluator {
        return SimpleCondition { message ->
            message.metadata[key]?.contains(substring) == true
        }
    }
    
    fun hasAnyKey(vararg keys: String): ConditionEvaluator {
        return SimpleCondition { message ->
            keys.any { message.metadata.containsKey(it) }
        }
    }
    
    fun hasAllKeys(vararg keys: String): ConditionEvaluator {
        return SimpleCondition { message ->
            keys.all { message.metadata.containsKey(it) }
        }
    }
}

/**
 * Type-based conditions
 */
object TypeConditions {
    fun isType(type: MessageType): ConditionEvaluator {
        return SimpleCondition { message ->
            message.type == type
        }
    }
    
    fun isAnyType(vararg types: MessageType): ConditionEvaluator {
        return SimpleCondition { message ->
            types.contains(message.type)
        }
    }
    
    fun isNotType(type: MessageType): ConditionEvaluator {
        return SimpleCondition { message ->
            message.type != type
        }
    }
}

/**
 * Sender-based conditions
 */
object SenderConditions {
    fun fromSender(senderId: String): ConditionEvaluator {
        return SimpleCondition { message ->
            message.sender == senderId
        }
    }
    
    fun fromAnySender(vararg senderIds: String): ConditionEvaluator {
        return SimpleCondition { message ->
            senderIds.contains(message.sender)
        }
    }
    
    fun senderContains(substring: String): ConditionEvaluator {
        return SimpleCondition { message ->
            message.sender.contains(substring)
        }
    }
}

/**
 * Time-based conditions
 */
object TimeConditions {
    fun withinLastMinutes(minutes: Long): ConditionEvaluator {
        return SimpleCondition { message ->
            val now = System.currentTimeMillis()
            val cutoff = now - (minutes * 60 * 1000)
            message.timestamp >= cutoff
        }
    }
    
    fun withinLastHours(hours: Long): ConditionEvaluator {
        return SimpleCondition { message ->
            val now = System.currentTimeMillis()
            val cutoff = now - (hours * 60 * 60 * 1000)
            message.timestamp >= cutoff
        }
    }
    
    fun olderThanMinutes(minutes: Long): ConditionEvaluator {
        return SimpleCondition { message ->
            val now = System.currentTimeMillis()
            val cutoff = now - (minutes * 60 * 1000)
            message.timestamp < cutoff
        }
    }
}

/**
 * Conditional flow builder
 */
class ConditionalFlowBuilder {
    private val branches = mutableListOf<ConditionalBranch>()
    private var elseBranch: ConditionalBranch? = null
    
    /**
     * Add when/then branch
     */
    fun whenThen(
        condition: ConditionEvaluator,
        action: suspend (Message) -> Message
    ): ConditionalFlowBuilder {
        branches.add(ConditionalBranch(condition, action))
        return this
    }
    
    /**
     * Add when/then branch with simple predicate
     */
    fun whenThen(
        predicate: suspend (Message) -> Boolean,
        action: suspend (Message) -> Message
    ): ConditionalFlowBuilder {
        return whenThen(SimpleCondition(predicate), action)
    }
    
    /**
     * Set else branch
     */
    fun otherwise(action: suspend (Message) -> Message): ConditionalFlowBuilder {
        elseBranch = ConditionalBranch(SimpleCondition { true }, action)
        return this
    }
    
    /**
     * Build conditional flow
     */
    fun build(): ConditionalFlow {
        return ConditionalFlow(branches.toList(), elseBranch)
    }
}

/**
 * Conditional branch definition
 */
data class ConditionalBranch(
    val condition: ConditionEvaluator,
    val action: suspend (Message) -> Message
)

/**
 * Conditional flow execution
 */
class ConditionalFlow(
    private val branches: List<ConditionalBranch>,
    private val elseBranch: ConditionalBranch?
) {
    
    /**
     * Execute conditional flow
     */
    suspend fun execute(message: Message): Message {
        // Try each branch in order
        for (branch in branches) {
            if (branch.condition.evaluate(message)) {
                return branch.action(message)
            }
        }
        
        // Execute else branch if no condition matched
        return elseBranch?.action?.invoke(message) ?: message
    }
}

/**
 * Extension functions for condition composition
 */
infix fun ConditionEvaluator.and(other: ConditionEvaluator): ConditionEvaluator {
    return CompoundCondition(LogicalOperator.AND, listOf(this, other))
}

infix fun ConditionEvaluator.or(other: ConditionEvaluator): ConditionEvaluator {
    return CompoundCondition(LogicalOperator.OR, listOf(this, other))
}

fun not(condition: ConditionEvaluator): ConditionEvaluator {
    return CompoundCondition(LogicalOperator.NOT, listOf(condition))
}

/**
 * Extension function for Agent with conditional flow
 */
class ConditionalAgent(
    private val baseAgent: Agent,
    private val conditionalFlow: ConditionalFlow
) : Agent by baseAgent {
    
    override suspend fun processMessage(message: Message): Message {
        // First apply conditional flow
        val processedMessage = conditionalFlow.execute(message)
        
        // Then process with base agent
        return baseAgent.processMessage(processedMessage)
    }
}

/**
 * DSL entry points
 */
fun conditionalFlow(init: ConditionalFlowBuilder.() -> Unit): ConditionalFlow {
    val builder = ConditionalFlowBuilder()
    builder.init()
    return builder.build()
}

fun Agent.withConditionalFlow(flow: ConditionalFlow): ConditionalAgent {
    return ConditionalAgent(this, flow)
}

fun Agent.withConditionalFlow(init: ConditionalFlowBuilder.() -> Unit): ConditionalAgent {
    val flow = conditionalFlow(init)
    return ConditionalAgent(this, flow)
}

/**
 * Utility functions for common patterns
 */
object ConditionalPatterns {
    
    /**
     * Route by content keywords
     */
    fun routeByKeywords(
        keywordMap: Map<String, suspend (Message) -> Message>,
        defaultAction: (suspend (Message) -> Message)? = null
    ): ConditionalFlow {
        val builder = ConditionalFlowBuilder()
        
        keywordMap.forEach { (keyword, action) ->
            builder.whenThen(ContentConditions.contains(keyword), action)
        }
        
        defaultAction?.let { builder.otherwise(it) }
        
        return builder.build()
    }
    
    /**
     * Route by message type
     */
    fun routeByType(
        typeMap: Map<MessageType, suspend (Message) -> Message>,
        defaultAction: (suspend (Message) -> Message)? = null
    ): ConditionalFlow {
        val builder = ConditionalFlowBuilder()
        
        typeMap.forEach { (type, action) ->
            builder.whenThen(TypeConditions.isType(type), action)
        }
        
        defaultAction?.let { builder.otherwise(it) }
        
        return builder.build()
    }
    
    /**
     * Route by sender
     */
    fun routeBySender(
        senderMap: Map<String, suspend (Message) -> Message>,
        defaultAction: (suspend (Message) -> Message)? = null
    ): ConditionalFlow {
        val builder = ConditionalFlowBuilder()
        
        senderMap.forEach { (sender, action) ->
            builder.whenThen(SenderConditions.fromSender(sender), action)
        }
        
        defaultAction?.let { builder.otherwise(it) }
        
        return builder.build()
    }
    
    /**
     * Priority routing with fallback
     */
    fun priorityRouting(
        priorities: List<Pair<ConditionEvaluator, suspend (Message) -> Message>>,
        fallback: suspend (Message) -> Message
    ): ConditionalFlow {
        val builder = ConditionalFlowBuilder()
        
        priorities.forEach { (condition, action) ->
            builder.whenThen(condition, action)
        }
        
        builder.otherwise(fallback)
        
        return builder.build()
    }
} 