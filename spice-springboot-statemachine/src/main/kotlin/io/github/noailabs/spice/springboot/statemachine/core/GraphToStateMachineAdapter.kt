package io.github.noailabs.spice.springboot.statemachine.core

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.springboot.statemachine.actions.CheckpointSaveAction
import io.github.noailabs.spice.springboot.statemachine.actions.EventPublishAction
import io.github.noailabs.spice.springboot.statemachine.actions.NodeExecutionAction
import io.github.noailabs.spice.springboot.statemachine.actions.ToolRetryAction
import io.github.noailabs.spice.springboot.statemachine.events.WorkflowCompletedEvent
import io.github.noailabs.spice.springboot.statemachine.guards.RetryableErrorGuard
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Bridges GraphRunner execution with the Spring state machine.
 */
class GraphToStateMachineAdapter(
    private val stateMachineFactory: SpiceStateMachineFactory,
    private val nodeExecutionAction: NodeExecutionAction,
    private val checkpointSaveAction: CheckpointSaveAction,
    private val eventPublishAction: EventPublishAction,
    private val toolRetryAction: ToolRetryAction,
    private val retryableErrorGuard: RetryableErrorGuard
) {
    private val logger = LoggerFactory.getLogger(GraphToStateMachineAdapter::class.java)

    suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
        val stateMachine = stateMachineFactory.create()
        val springStateMachine = stateMachine.asSpringStateMachine()
        val runId = message.runId ?: "${graph.id}:${System.currentTimeMillis()}"
        springStateMachine.extendedState.variables["graphId"] = graph.id
        springStateMachine.extendedState.variables["runId"] = runId
        springStateMachine.extendedState.variables["graph"] = graph
        springStateMachine.extendedState.variables["nodeId"] = graph.entryPoint
        springStateMachine.extendedState.variables["message"] = message
        stateMachine.start()

        stateMachine.sendEvent(SpiceEvent.START)

        var attempt = 0
        var lastResult: SpiceResult<SpiceMessage>? = null
        var currentMessage = message
        var running = true
        while (running) {
            lastResult = nodeExecutionAction.execute(graph, currentMessage)
            springStateMachine.extendedState.variables["message"] =
                (lastResult as? SpiceResult.Success)?.value ?: springStateMachine.extendedState.variables["message"]
            when (lastResult) {
                is SpiceResult.Success -> {
                    currentMessage = lastResult.value
                    springStateMachine.extendedState.variables["nodeId"] = lastResult.value.nodeId
                    val nextState = lastResult.value.state
                    when (nextState) {
                        ExecutionState.WAITING -> {
                            stateMachine.sendEvent(SpiceEvent.PAUSE_FOR_HITL)
                            checkpointSaveAction.save(runId, graph, springStateMachine)
                            springStateMachine.extendedState.variables["checkpointSaved"] = true
                            running = false
                        }

                        ExecutionState.COMPLETED -> {
                            stateMachine.sendEvent(SpiceEvent.COMPLETE)
                            eventPublishAction.publishWorkflowCompleted(
                                WorkflowCompletedEvent(
                                    runId = runId,
                                    graphId = graph.id,
                                    finalState = ExecutionState.COMPLETED,
                                    timestamp = Clock.System.now(),
                                    metadata = mapOf("attempts" to attempt + 1)
                                )
                            )
                            running = false
                        }

                        ExecutionState.FAILED -> {
                            stateMachine.sendEvent(SpiceEvent.FAIL)
                            running = false
                        }

                        ExecutionState.RUNNING -> {
                            // Graph runner reported RUNNING meaning more work pending, loop again
                            continue
                        }

                        ExecutionState.READY -> running = false
                    }
                }

                is SpiceResult.Failure -> {
                    springStateMachine.extendedState.variables["lastError"] = lastResult.error
                    val backoff = nextBackoff(attempt, lastResult.error)
                    if (backoff != null) {
                        attempt += 1
                        springStateMachine.extendedState.variables["retryCount"] = attempt
                        logger.warn(
                            "Retrying graph {} (attempt {}), waiting {}ms due to {}",
                            graph.id,
                            attempt,
                            backoff,
                            lastResult.error.code
                        )
                        stateMachine.sendEvent(SpiceEvent.TOOL_ERROR)
                        delay(backoff)
                        stateMachine.sendEvent(SpiceEvent.RETRY)
                        continue
                    } else {
                        stateMachine.sendEvent(SpiceEvent.FAIL)
                        eventPublishAction.publishWorkflowCompleted(
                            WorkflowCompletedEvent(
                                runId = runId,
                                graphId = graph.id,
                                finalState = ExecutionState.FAILED,
                                timestamp = Clock.System.now(),
                                metadata = mapOf(
                                    "error" to lastResult.error.message,
                                    "attempts" to attempt + 1
                                )
                            )
                        )
                        running = false
                    }
                }
            }
        }

        stateMachine.stop()
        return lastResult ?: SpiceResult.failure(
            SpiceError.executionError("Graph execution did not produce any result", graph.id)
        )
    }

    private fun nextBackoff(attempt: Int, error: SpiceError?): Long? {
        return if (retryableErrorGuard.isRetryable(error)) {
            toolRetryAction.nextBackoff(attempt)
        } else {
            null
        }
    }
}
