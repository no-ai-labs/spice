# Flow Orchestration Guide

## Understanding Flows

Flows coordinate multiple agents to accomplish complex tasks. They define sequences of operations, handle data transformation between steps, and manage the overall workflow lifecycle.

## Basic Flow Concepts

### Flow Structure
```kotlin
val workflow = flow {
    name = "Data Processing Pipeline"
    description = "Processes data through multiple stages"
    
    // Define steps
    step("validate") { comm ->
        validatorAgent.process(comm)
    }
    
    step("transform") { comm ->
        transformerAgent.process(comm)
    }
    
    step("save") { comm ->
        persistenceAgent.process(comm)
    }
}

// Execute flow
val result = workflow.execute(comm("Process this data"))
```

## Flow Patterns

### 1. Sequential Flow
```kotlin
val sequentialFlow = flow {
    name = "Document Analysis"
    
    step("extract") { comm ->
        // Extract text from document
        extractorAgent.process(comm)
    }
    
    step("analyze") { previousResult ->
        // Analyze extracted text
        analyzerAgent.process(previousResult)
    }
    
    step("summarize") { previousResult ->
        // Create summary
        summarizerAgent.process(previousResult)
    }
}
```

### 2. Conditional Flow
```kotlin
val conditionalFlow = flow {
    name = "Customer Support"
    
    step("classify") { comm ->
        classifierAgent.process(comm)
    }
    
    conditional("route") { result ->
        when (result.data["category"]) {
            "technical" -> technicalAgent.process(result)
            "billing" -> billingAgent.process(result)
            "general" -> generalAgent.process(result)
            else -> errorComm("Unknown category", "flow")
        }
    }
    
    step("respond") { result ->
        responseAgent.process(result)
    }
}
```

### 3. Parallel Flow
```kotlin
val parallelFlow = flow {
    name = "Multi-Analysis"
    
    parallel("analyze") { comm ->
        listOf(
            async { sentimentAgent.process(comm) },
            async { keywordAgent.process(comm) },
            async { languageAgent.process(comm) }
        )
    }
    
    step("combine") { results ->
        // Combine results from parallel operations
        val combined = results.map { it.await() }
        combinerAgent.process(
            comm("Combined analysis") {
                data("results", combined)
            }
        )
    }
}
```

### 4. Loop Flow
```kotlin
val iterativeFlow = flow {
    name = "Refinement Process"
    
    var iteration = 0
    val maxIterations = 5
    
    loop("refine") { comm ->
        iteration++
        val result = refinementAgent.process(comm)
        
        // Check if we should continue
        val quality = result.data["quality"] as? Double ?: 0.0
        val shouldContinue = quality < 0.9 && iteration < maxIterations
        
        LoopResult(result, shouldContinue)
    }
    
    step("finalize") { result ->
        finalizerAgent.process(result)
    }
}
```

## Advanced Flow Features

### 1. Error Handling in Flows
```kotlin
val robustFlow = flow {
    name = "Robust Processing"
    
    errorHandler { error, step ->
        // Log error
        println("Error in step $step: ${error.message}")
        
        // Return fallback response
        errorComm("Processing failed at $step: ${error.message}", "flow")
    }
    
    step("risky") { comm ->
        riskyAgent.process(comm)
    }
    
    step("cleanup") { result ->
        // Always executes, even after errors
        cleanupAgent.process(result)
    }
}
```

### 2. Flow with State Management
```kotlin
class StatefulFlow {
    private val state = mutableMapOf<String, Any>()
    
    val workflow = flow {
        name = "Stateful Process"
        
        step("init") { comm ->
            state["startTime"] = Instant.now()
            state["originalInput"] = comm.content
            initAgent.process(comm)
        }
        
        step("process") { comm ->
            state["processedCount"] = (state["processedCount"] as? Int ?: 0) + 1
            processorAgent.process(comm)
        }
        
        step("report") { result ->
            val duration = Duration.between(
                state["startTime"] as Instant,
                Instant.now()
            )
            
            reportAgent.process(
                result.withData("duration", duration.toMillis())
                      .withData("processedCount", state["processedCount"] ?: 0)
            )
        }
    }
    
    fun getState() = state.toMap()
    fun clearState() = state.clear()
}
```

### 3. Dynamic Flow Composition
```kotlin
class DynamicFlowBuilder {
    fun buildFlow(steps: List<String>): Flow {
        return flow {
            name = "Dynamic Flow"
            
            steps.forEach { stepName ->
                step(stepName) { comm ->
                    val agent = AgentRegistry.get(stepName) 
                        ?: throw IllegalStateException("Agent $stepName not found")
                    agent.process(comm)
                }
            }
        }
    }
}

// Usage
val flowBuilder = DynamicFlowBuilder()
val customFlow = flowBuilder.buildFlow(
    listOf("validator", "processor", "formatter")
)
```

### 4. Flow with Retry Logic
```kotlin
val retryFlow = flow {
    name = "Retry Flow"
    
    retryableStep("api-call", maxRetries = 3, delay = 1000) { comm ->
        apiAgent.process(comm)
    }
    
    step("process-response") { result ->
        responseProcessor.process(result)
    }
}

// Custom retry implementation
fun FlowBuilder.retryableStep(
    name: String,
    maxRetries: Int = 3,
    delay: Long = 1000,
    handler: suspend (Comm) -> Comm
) {
    step(name) { comm ->
        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return@step handler(comm)
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    delay(delay * (attempt + 1))
                }
            }
        }
        throw lastError ?: RuntimeException("Retry failed")
    }
}
```

## Flow Monitoring and Metrics

### Flow with Metrics Collection
```kotlin
class MetricFlow {
    private val metrics = mutableListOf<FlowMetric>()
    
    data class FlowMetric(
        val step: String,
        val duration: Duration,
        val success: Boolean,
        val timestamp: Instant = Instant.now()
    )
    
    val workflow = flow {
        name = "Monitored Flow"
        
        instrumentedStep("step1") { comm ->
            processingAgent.process(comm)
        }
        
        instrumentedStep("step2") { comm ->
            validationAgent.process(comm)
        }
    }
    
    private fun FlowBuilder.instrumentedStep(
        name: String,
        handler: suspend (Comm) -> Comm
    ) {
        step(name) { comm ->
            val start = Instant.now()
            try {
                val result = handler(comm)
                metrics.add(FlowMetric(
                    step = name,
                    duration = Duration.between(start, Instant.now()),
                    success = true
                ))
                result
            } catch (e: Exception) {
                metrics.add(FlowMetric(
                    step = name,
                    duration = Duration.between(start, Instant.now()),
                    success = false
                ))
                throw e
            }
        }
    }
    
    fun getMetrics() = metrics.toList()
    fun getAverageDuration(step: String) = 
        metrics.filter { it.step == step }
               .map { it.duration.toMillis() }
               .average()
}
```

## Testing Flows

### Unit Testing Flows
```kotlin
class FlowTest {
    @Test
    fun `test sequential flow`() = runBlocking {
        // Create mock agents
        val mockAgent1 = buildAgent {
            id = "mock1"
            handle { comm ->
                comm.reply("Step 1 done", id)
            }
        }
        
        val mockAgent2 = buildAgent {
            id = "mock2"
            handle { comm ->
                assertTrue(comm.content.contains("Step 1"))
                comm.reply("Step 2 done", id)
            }
        }
        
        // Create flow
        val testFlow = flow {
            step("one") { mockAgent1.process(it) }
            step("two") { mockAgent2.process(it) }
        }
        
        // Execute and verify
        val result = testFlow.execute(comm("Start"))
        assertEquals("Step 2 done", result.content)
    }
    
    @Test
    fun `test flow error handling`() = runBlocking {
        val errorFlow = flow {
            errorHandler { _, _ ->
                errorComm("Handled error", "flow")
            }
            
            step("fail") {
                throw RuntimeException("Test error")
            }
        }
        
        val result = errorFlow.execute(comm("Test"))
        assertEquals(CommType.ERROR, result.type)
        assertTrue(result.content.contains("Handled error"))
    }
}
```

## Flow Best Practices

1. **Keep flows focused** - Each flow should have a single, clear purpose
2. **Handle errors gracefully** - Always include error handling
3. **Monitor performance** - Add metrics for critical flows
4. **Test thoroughly** - Test both happy path and error scenarios
5. **Document flow purpose** - Use meaningful names and descriptions
6. **Avoid deep nesting** - Break complex flows into sub-flows
7. **Make flows idempotent** - Ensure repeated execution is safe
8. **Use appropriate patterns** - Choose the right flow pattern for your use case
9. **Manage state carefully** - Be explicit about state management
10. **Version your flows** - Track changes to critical workflows

## Common Flow Recipes

### Data Processing Pipeline
```kotlin
val dataPipeline = flow {
    name = "ETL Pipeline"
    
    step("extract") { extractAgent.process(it) }
    step("transform") { transformAgent.process(it) }
    step("load") { loadAgent.process(it) }
    step("verify") { verifyAgent.process(it) }
}
```

### Request Approval Workflow
```kotlin
val approvalFlow = flow {
    name = "Approval Workflow"
    
    step("submit") { submissionAgent.process(it) }
    step("review") { reviewAgent.process(it) }
    conditional("approve") { result ->
        if (result.data["approved"] == true) {
            approvedAgent.process(result)
        } else {
            rejectedAgent.process(result)
        }
    }
    step("notify") { notificationAgent.process(it) }
}
```

## Next: [Advanced Features](advanced-features.md)