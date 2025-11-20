package io.github.noailabs.spice.springboot.statemachine.actions

import io.github.noailabs.spice.springboot.statemachine.events.WorkflowCompletedEvent
import io.github.noailabs.spice.springboot.statemachine.events.WorkflowResumedEvent
import org.springframework.context.ApplicationEventPublisher

/**
 * Publishes Spring application events for observability integrations.
 */
class EventPublishAction(
    private val publisher: ApplicationEventPublisher
) {
    fun publishWorkflowCompleted(event: WorkflowCompletedEvent) {
        publisher.publishEvent(event)
    }

    /**
     * Publish workflow resumed event when HITL checkpoint is resumed.
     *
     * @since 1.0.3
     */
    fun publishWorkflowResumed(event: WorkflowResumedEvent) {
        publisher.publishEvent(event)
    }
}
