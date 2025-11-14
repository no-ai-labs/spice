package io.github.noailabs.spice.springboot.statemachine.actions

import io.github.noailabs.spice.springboot.statemachine.events.WorkflowCompletedEvent
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
}
