package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult

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
    suspend fun evaluate(comm: Comm): Boolean
}

/**
 * Simple condition implementation
 */
class SimpleCondition(
    private val predicate: suspend (Comm) -> Boolean
) : ConditionEvaluator {
    override suspend fun evaluate(comm: Comm): Boolean = predicate(comm)
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
    
    override suspend fun evaluate(comm: Comm): Boolean {
        return when (operator) {
            LogicalOperator.AND -> conditions.all { it.evaluate(comm) }
            LogicalOperator.OR -> conditions.any { it.evaluate(comm) }
            LogicalOperator.NOT -> conditions.none { it.evaluate(comm) }
        }
    }
}

/**
 * Content-based conditions
 */
object ContentConditions {
    fun contains(text: String, ignoreCase: Boolean = true): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.content.contains(text, ignoreCase)
        }
    }
    
    fun startsWith(prefix: String, ignoreCase: Boolean = true): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.content.startsWith(prefix, ignoreCase)
        }
    }
    
    fun endsWith(suffix: String, ignoreCase: Boolean = true): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.content.endsWith(suffix, ignoreCase)
        }
    }
    
    fun matches(regex: Regex): ConditionEvaluator {
        return SimpleCondition { comm ->
            regex.containsMatchIn(comm.content)
        }
    }
    
    fun lengthGreaterThan(length: Int): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.content.length > length
        }
    }
    
    fun lengthLessThan(length: Int): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.content.length < length
        }
    }
    
    fun isEmpty(): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.content.isBlank()
        }
    }
    
    fun isNotEmpty(): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.content.isNotBlank()
        }
    }
}

/**
 * Data-based conditions (formerly MetadataConditions)
 */
object DataConditions {
    fun hasKey(key: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.data.containsKey(key)
        }
    }
    
    fun keyEquals(key: String, value: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.data[key] == value
        }
    }
    
    fun keyContains(key: String, substring: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.data[key]?.toString()?.contains(substring) == true
        }
    }
    
    fun hasAnyKey(vararg keys: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            keys.any { comm.data.containsKey(it) }
        }
    }
    
    fun hasAllKeys(vararg keys: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            keys.all { comm.data.containsKey(it) }
        }
    }
}

/**
 * Type-based conditions
 */
object TypeConditions {
    fun isType(type: CommType): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.type == type
        }
    }
    
    fun isAnyType(vararg types: CommType): ConditionEvaluator {
        return SimpleCondition { comm ->
            types.contains(comm.type)
        }
    }
    
    fun isNotType(type: CommType): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.type != type
        }
    }
}

/**
 * Sender-based conditions
 */
object SenderConditions {
    fun fromSender(senderId: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.from == senderId
        }
    }
    
    fun fromAnySender(vararg senderIds: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            senderIds.contains(comm.from)
        }
    }
    
    fun senderContains(substring: String): ConditionEvaluator {
        return SimpleCondition { comm ->
            comm.from.contains(substring)
        }
    }
}

/**
 * Time-based conditions
 */
object TimeConditions {
    fun withinLastMinutes(minutes: Long): ConditionEvaluator {
        return SimpleCondition { comm ->
            val now = System.currentTimeMillis()
            val cutoff = now - (minutes * 60 * 1000)
            comm.timestamp >= cutoff
        }
    }
    
    fun withinLastHours(hours: Long): ConditionEvaluator {
        return SimpleCondition { comm ->
            val now = System.currentTimeMillis()
            val cutoff = now - (hours * 60 * 60 * 1000)
            comm.timestamp >= cutoff
        }
    }
    
    fun olderThanMinutes(minutes: Long): ConditionEvaluator {
        return SimpleCondition { comm ->
            val now = System.currentTimeMillis()
            val cutoff = now - (minutes * 60 * 1000)
            comm.timestamp < cutoff
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
        action: suspend (Comm) -> Comm
    ): ConditionalFlowBuilder {
        branches.add(ConditionalBranch(condition, action))
        return this
    }
    
    /**
     * Add when/then branch with simple predicate
     */
    fun whenThen(
        predicate: suspend (Comm) -> Boolean,
        action: suspend (Comm) -> Comm
    ): ConditionalFlowBuilder {
        return whenThen(SimpleCondition(predicate), action)
    }
    
    /**
     * Set else branch
     */
    fun otherwise(action: suspend (Comm) -> Comm): ConditionalFlowBuilder {
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
    val action: suspend (Comm) -> Comm
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
    suspend fun execute(comm: Comm): Comm {
        // Try each branch in order
        for (branch in branches) {
            if (branch.condition.evaluate(comm)) {
                return branch.action(comm)
            }
        }
        
        // Execute else branch if no condition matched
        return elseBranch?.action?.invoke(comm) ?: comm
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
    
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // First apply conditional flow
        val processedComm = conditionalFlow.execute(comm)

        // Then process with base agent
        return baseAgent.processComm(processedComm)
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
        keywordMap: Map<String, suspend (Comm) -> Comm>,
        defaultAction: (suspend (Comm) -> Comm)? = null
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
        typeMap: Map<CommType, suspend (Comm) -> Comm>,
        defaultAction: (suspend (Comm) -> Comm)? = null
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
        senderMap: Map<String, suspend (Comm) -> Comm>,
        defaultAction: (suspend (Comm) -> Comm)? = null
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
        priorities: List<Pair<ConditionEvaluator, suspend (Comm) -> Comm>>,
        fallback: suspend (Comm) -> Comm
    ): ConditionalFlow {
        val builder = ConditionalFlowBuilder()
        
        priorities.forEach { (condition, action) ->
            builder.whenThen(condition, action)
        }
        
        builder.otherwise(fallback)
        
        return builder.build()
    }
} 