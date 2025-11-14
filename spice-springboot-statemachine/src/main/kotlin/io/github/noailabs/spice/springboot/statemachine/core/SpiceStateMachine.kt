package io.github.noailabs.spice.springboot.statemachine.core

import io.github.noailabs.spice.ExecutionState
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.StateMachine
import reactor.core.publisher.Mono

/**
 * Thin wrapper around Spring StateMachine that exposes helper methods tailored for Spice graphs.
 */
class SpiceStateMachine(
    private val delegate: StateMachine<ExecutionState, SpiceEvent>
) {
    fun start() {
        delegate.startReactively().block()
    }

    fun stop() {
        delegate.stopReactively().block()
    }

    fun currentState(): ExecutionState = delegate.state.id

    fun sendEvent(event: SpiceEvent): Boolean =
        delegate.sendEvent(Mono.just(MessageBuilder.withPayload(event).build())).doOnSuccess {
            delegate.extendedState.variables["lastEvent"] = event
        }.block() == true

    fun asSpringStateMachine(): StateMachine<ExecutionState, SpiceEvent> = delegate
}
