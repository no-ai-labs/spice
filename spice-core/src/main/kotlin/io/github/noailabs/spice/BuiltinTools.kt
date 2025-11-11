package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 🛠️ Built-in Tools for Spice Framework
 * 
 * Standard tools included with the framework
 */

/**
 * Calculator Tool - Mathematical operations
 */
fun calculatorTool(): Tool {
    return SimpleTool(
        name = "calculator",
        description = "Performs mathematical calculations",
        parameterSchemas = mapOf(
            "expression" to ParameterSchema("string", "Mathematical expression to evaluate", true)
        )
    ) { params ->
        val expression = params["expression"] as? String ?: return@SimpleTool ToolResult(
            success = false,
            error = "Expression parameter is required"
        )
        
        try {
            val result = evaluateExpression(expression)
            ToolResult(success = true, result = result.toString())
        } catch (e: Exception) {
            ToolResult(success = false, error = "Failed to evaluate: ${e.message}")
        }
    }
}

/**
 * Text Processor Tool - Text analysis and processing
 */
fun textProcessorTool(): Tool {
    return SimpleTool(
        name = "textProcessor",
        description = "Processes and analyzes text",
        parameterSchemas = mapOf(
            "text" to ParameterSchema("string", "Text to process", true),
            "operation" to ParameterSchema("string", "Operation: sentiment, length, uppercase, lowercase", true)
        )
    ) { params ->
        val text = params["text"] as? String ?: return@SimpleTool ToolResult(
            success = false,
            error = "Text parameter is required"
        )
        
        val operation = params["operation"] as? String ?: return@SimpleTool ToolResult(
            success = false,
            error = "Operation parameter is required"
        )
        
        val result = when (operation.lowercase()) {
            "sentiment" -> if (text.contains("love", ignoreCase = true) || 
                             text.contains("good", ignoreCase = true) || 
                             text.contains("great", ignoreCase = true) ||
                             text.contains("amazing", ignoreCase = true)) "positive" else "neutral"
            "length" -> text.length.toString()
            "uppercase" -> text.uppercase()
            "lowercase" -> text.lowercase()
            else -> "Unknown operation: $operation"
        }
        
        ToolResult(success = true, result = result)
    }
}

/**
 * DateTime Tool - Date and time operations
 */
fun dateTimeTool(): Tool {
    return SimpleTool(
        name = "datetime",
        description = "Date and time operations",
        parameterSchemas = mapOf(
            "operation" to ParameterSchema("string", "Operation: now, current, date, time, timestamp, format", true),
            "format" to ParameterSchema("string", "Date format (optional)", false)
        )
    ) { params ->
        val operation = params["operation"] as? String ?: return@SimpleTool ToolResult(
            success = false,
            error = "Operation parameter is required"
        )
        
        val result = when (operation.lowercase()) {
            "current", "now" -> {
                val format = params["format"] as? String ?: "yyyy-MM-dd HH:mm:ss"
                try {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(format))
                } catch (e: Exception) {
                    LocalDateTime.now().toString()
                }
            }
            "date" -> {
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
            "time" -> {
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            }
            "timestamp" -> {
                System.currentTimeMillis().toString()
            }
            "format" -> {
                val format = params["format"] as? String ?: "yyyy-MM-dd"
                LocalDateTime.now().format(DateTimeFormatter.ofPattern(format))
            }
            else -> "Unknown operation: $operation"
        }
        
        ToolResult(success = true, result = result)
    }
}

/**
 * Random Tool - Random number generation
 */
fun randomTool(): Tool {
    return SimpleTool(
        name = "random",
        description = "Random number and data generation",
        parameterSchemas = mapOf(
            "type" to ParameterSchema("string", "Type: number, boolean, choice", true),
            "min" to ParameterSchema("number", "Minimum value (for number)", false),
            "max" to ParameterSchema("number", "Maximum value (for number)", false),
            "choices" to ParameterSchema("string", "Comma-separated choices (for choice)", false)
        )
    ) { params ->
        val type = params["type"] as? String ?: return@SimpleTool ToolResult(
            success = false,
            error = "Type parameter is required"
        )
        
        val result = when (type.lowercase()) {
            "number" -> {
                val min = (params["min"] as? Number)?.toInt() ?: 1
                val max = (params["max"] as? Number)?.toInt() ?: 100
                Random.nextInt(min, max + 1).toString()
            }
            "boolean" -> Random.nextBoolean().toString()
            "choice" -> {
                val choices = (params["choices"] as? String)?.split(",")?.map { it.trim() }
                if (choices.isNullOrEmpty()) {
                    "No choices provided"
                } else {
                    choices.random()
                }
            }
            else -> "Unknown type: $type"
        }
        
        ToolResult(success = true, result = result)
    }
}

/**
 * Simple expression evaluator (basic math)
 */
private fun evaluateExpression(expression: String): Double {
    // Very basic evaluator - handles +, -, *, /
    val cleanExpr = expression.replace(" ", "")
    
    // Handle basic operations
    return when {
        "+" in cleanExpr -> {
            val parts = cleanExpr.split("+")
            parts.sumOf { it.toDouble() }
        }
        "-" in cleanExpr && cleanExpr.indexOf("-") > 0 -> {
            val parts = cleanExpr.split("-")
            parts[0].toDouble() - parts.drop(1).sumOf { it.toDouble() }
        }
        "*" in cleanExpr -> {
            val parts = cleanExpr.split("*")
            parts.fold(1.0) { acc, part -> acc * part.toDouble() }
        }
        "/" in cleanExpr -> {
            val parts = cleanExpr.split("/")
            parts.drop(1).fold(parts[0].toDouble()) { acc, part -> acc / part.toDouble() }
        }
        else -> cleanExpr.toDouble()
    }
}

/**
 * Simple Tool implementation for built-in tools
 */
class SimpleTool(
    override val name: String,
    override val description: String,
    private val parameterSchemas: Map<String, ParameterSchema>,
    private val executor: suspend (Map<String, Any>) -> ToolResult
) : Tool {

    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = parameterSchemas
    )

    override fun canExecute(parameters: Map<String, Any?>): Boolean {
        // Check if all required parameters are present
        val validation = validateParameters(parameters)
        return validation.valid
    }

    override suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult> {
        @Suppress("UNCHECKED_CAST")
        val nonNullParams = parameters.filterValues { it != null } as Map<String, Any>
        return SpiceResult.success(executor(nonNullParams))
    }
} 