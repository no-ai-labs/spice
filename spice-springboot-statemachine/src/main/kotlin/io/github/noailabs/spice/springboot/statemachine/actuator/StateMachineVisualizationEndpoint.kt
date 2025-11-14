package io.github.noailabs.spice.springboot.statemachine.actuator

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.config.StateMachineProperties
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.statemachine.config.StateMachineFactory

/**
 * Actuator-style controller that returns GraphViz DOT or Mermaid diagrams for the state machine.
 */
@RestController
@RequestMapping("/actuator/statemachine")
class StateMachineVisualizationEndpoint(
    private val stateMachineFactory: StateMachineFactory<ExecutionState, SpiceEvent>,
    private val properties: StateMachineProperties
) {
    @GetMapping("/visualize", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun visualize(): String = buildGraphViz()

    @GetMapping("/mermaid", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun mermaid(): String = buildMermaid()

    private fun buildGraphViz(): String {
        return """
            digraph G {
                rankdir=LR;
                node [shape=circle];

                READY [shape=doublecircle];
                COMPLETED [shape=doublecircle];
                FAILED [shape=doublecircle];

                READY -> RUNNING [label="START"];
                RUNNING -> WAITING [label="HITL"];
                RUNNING -> COMPLETED [label="COMPLETE"];
                RUNNING -> FAILED [label="FAIL"];
                WAITING -> RUNNING [label="RESUME"];
                WAITING -> FAILED [label="TIMEOUT"];
            }
        """.trimIndent()
    }

    private fun buildMermaid(): String {
        return """
            stateDiagram-v2
                [*] --> READY
                READY --> RUNNING: START
                RUNNING --> WAITING: HITL
                RUNNING --> COMPLETED: COMPLETE
                RUNNING --> FAILED: FAIL
                WAITING --> RUNNING: RESUME
                WAITING --> FAILED: TIMEOUT
                COMPLETED --> [*]
                FAILED --> [*]
        """.trimIndent()
    }
}
