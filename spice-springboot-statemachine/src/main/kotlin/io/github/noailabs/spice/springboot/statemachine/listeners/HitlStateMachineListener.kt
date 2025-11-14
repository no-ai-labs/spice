package io.github.noailabs.spice.springboot.statemachine.listeners

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.actions.CheckpointSaveAction
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State

/**
 * Persists checkpoints automatically whenever the machine enters WAITING.
 */
class HitlStateMachineListener(
    private val checkpointSaveAction: CheckpointSaveAction,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : StateMachineListenerAdapter<ExecutionState, SpiceEvent>() {

    override fun stateChanged(from: State<ExecutionState, SpiceEvent>?, to: State<ExecutionState, SpiceEvent>?) {
        if (to?.id == ExecutionState.WAITING) {
            val stateMachine = to.stateMachine ?: return
            val alreadySaved = stateMachine.extendedState.variables["checkpointSaved"] as? Boolean ?: false
            if (alreadySaved) {
                stateMachine.extendedState.variables.remove("checkpointSaved")
                return
            }
            val graph = stateMachine.extendedState.variables["graph"] as? io.github.noailabs.spice.graph.Graph ?: return
            val runId = stateMachine.extendedState.variables["runId"]?.toString() ?: return
            coroutineScope.launch {
                checkpointSaveAction.save(runId, graph, stateMachine)
            }
        }
    }
}
