package io.github.noailabs.spice.springboot.statemachine.actuator

import io.github.noailabs.spice.ExecutionState
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple Micrometer-backed metrics for tracking active workflows.
 */
class StateMachineMetrics(
    meterRegistry: MeterRegistry
) {
    private val transitionsCounter = meterRegistry.counter("spice.statemachine.transitions")
    private val waitingGauge = meterRegistry.gauge("spice.statemachine.waiting", AtomicInteger(0))!!
    private val runningGauge = meterRegistry.gauge("spice.statemachine.running", AtomicInteger(0))!!

    fun onStateChange(from: ExecutionState?, to: ExecutionState) {
        transitionsCounter.increment()
        when (to) {
            ExecutionState.RUNNING -> runningGauge.incrementAndGet()
            ExecutionState.WAITING -> waitingGauge.incrementAndGet()
            else -> Unit
        }

        if (from == ExecutionState.RUNNING) {
            runningGauge.updateAndGet { (it - 1).coerceAtLeast(0) }
        } else if (from == ExecutionState.WAITING) {
            waitingGauge.updateAndGet { (it - 1).coerceAtLeast(0) }
        }
    }
}
