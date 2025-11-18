package io.github.noailabs.spice.springboot.statemachine.guards

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.guard.Guard

/**
 * Optional RBAC hook. When no permissions are configured it always allows transitions.
 */
class PermissionGuard : Guard<ExecutionState, SpiceEvent> {
    override fun evaluate(context: StateContext<ExecutionState, SpiceEvent>): Boolean {
        val required = context.extendedState.variables["requiredPermission"] as? String ?: return true
        val granted = context.extendedState.variables["grantedPermissions"] as? Collection<*> ?: return false
        return granted.mapNotNull { it?.toString() }.contains(required)
    }
}
