package io.github.noailabs.spice.arbiter

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.runner.GraphRunner
import io.github.noailabs.spice.state.ExecutionStateMachine
import io.github.noailabs.spice.validation.DeadLetterHandler
import io.github.noailabs.spice.validation.DeadLetterRecord
import io.github.noailabs.spice.validation.SchemaValidationPipeline
import io.github.noailabs.spice.validation.ValidationError

/**
 * Arbiter consumes messages from a queue, validates them, and delegates to the GraphRunner.
 */
class Arbiter(
    private val queue: MessageQueue,
    private val graphRunner: GraphRunner,
    private val validationPipeline: SchemaValidationPipeline = SchemaValidationPipeline(),
    private val stateMachine: ExecutionStateMachine = ExecutionStateMachine(),
    private val deadLetterHandler: DeadLetterHandler? = null
) {

    suspend fun start(topic: String, graphProvider: (SpiceMessage) -> Graph) {
        queue.consume(topic) { envelope ->
            handleEnvelope(envelope, graphProvider)
        }
    }

    private suspend fun handleEnvelope(
        envelope: QueueEnvelope,
        graphProvider: (SpiceMessage) -> Graph
    ) {
        val validation = validationPipeline.validateMessage(envelope.message)
        if (!validation.isValid) {
            deadLetterHandler?.handle(
                DeadLetterRecord(
                    payloadType = "SpiceMessage",
                    payload = envelope.message,
                    errors = validation.errors
                )
            )
            queue.fail(envelope.topic, envelope.id, "validation_failed")
            return
        }

        val normalized = try {
            stateMachine.ensureHistoryValid(envelope.message)
            if (envelope.message.state == ExecutionState.READY) {
                stateMachine.transition(
                    envelope.message,
                    ExecutionState.RUNNING,
                    reason = "Arbiter dispatch",
                    nodeId = null
                )
            } else {
                envelope.message
            }
        } catch (ex: IllegalStateException) {
            deadLetterHandler?.handle(
                DeadLetterRecord(
                    payloadType = "SpiceMessage",
                    payload = envelope.message,
                    errors = listOf(ValidationError("state", ex.message ?: "Invalid state"))
                )
            )
            queue.fail(envelope.topic, envelope.id, "state_invalid")
            return
        }

        val graph = graphProvider(normalized)
        when (val result = graphRunner.execute(graph, normalized)) {
            is SpiceResult.Success -> {
                queue.ack(envelope.topic, envelope.id)
            }
            is SpiceResult.Failure -> {
                deadLetterHandler?.handle(
                    DeadLetterRecord(
                        payloadType = "SpiceMessage",
                        payload = normalized,
                        errors = listOf(
                            ValidationError("execution", result.error.message)
                        )
                    )
                )
                queue.fail(envelope.topic, envelope.id, result.error.message)
            }
        }
    }
}
