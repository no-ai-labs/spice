package io.github.noailabs.spice.springboot.statemachine.config

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.runner.GraphRunner
import io.github.noailabs.spice.springboot.statemachine.actions.CheckpointSaveAction
import io.github.noailabs.spice.springboot.statemachine.actions.EventPublishAction
import io.github.noailabs.spice.springboot.statemachine.actions.NodeExecutionAction
import io.github.noailabs.spice.springboot.statemachine.actions.ToolRetryAction
import io.github.noailabs.spice.springboot.statemachine.actuator.StateMachineHealthIndicator
import io.github.noailabs.spice.springboot.statemachine.actuator.StateMachineMetrics
import io.github.noailabs.spice.springboot.statemachine.actuator.StateMachineVisualizationEndpoint
import io.github.noailabs.spice.springboot.statemachine.core.GraphToStateMachineAdapter
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import io.github.noailabs.spice.springboot.statemachine.core.SpiceStateMachineFactory
import io.github.noailabs.spice.springboot.statemachine.guards.PermissionGuard
import io.github.noailabs.spice.springboot.statemachine.guards.RetryableErrorGuard
import io.github.noailabs.spice.springboot.statemachine.guards.ValidationGuard
import io.github.noailabs.spice.springboot.statemachine.listeners.HitlStateMachineListener
import io.github.noailabs.spice.springboot.statemachine.listeners.MetricsCollector
import io.github.noailabs.spice.springboot.statemachine.listeners.NodeExecutionLogger
import io.github.noailabs.spice.springboot.statemachine.persistence.StateMachineCheckpointBridge
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineBuilder
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.listener.StateMachineListener
import org.springframework.statemachine.StateMachinePersist
import io.micrometer.core.instrument.MeterRegistry
import java.util.EnumSet
import java.util.UUID

@AutoConfiguration
@EnableConfigurationProperties(StateMachineProperties::class)
@ConditionalOnProperty(name = ["spice.statemachine.enabled"], havingValue = "true", matchIfMissing = true)
class StateMachineAutoConfiguration {

    @Bean
    fun validationGuard(): ValidationGuard = ValidationGuard()

    @Bean
    fun retryableErrorGuard(): RetryableErrorGuard = RetryableErrorGuard()

    @Bean
    fun permissionGuard(): PermissionGuard = PermissionGuard()

    @Bean
    fun stateMachineFactory(): StateMachineFactory<ExecutionState, SpiceEvent> {
        // Custom factory wrapper that creates new state machines from builder
        return object : StateMachineFactory<ExecutionState, SpiceEvent> {
            override fun getStateMachine(): StateMachine<ExecutionState, SpiceEvent> {
                val builder = StateMachineBuilder.builder<ExecutionState, SpiceEvent>()
                builder.configureStates()
                    .withStates()
                    .initial(ExecutionState.READY)
                    .states(EnumSet.allOf(ExecutionState::class.java))

                builder.configureTransitions()
                    .withExternal()
                    .source(ExecutionState.READY)
                    .target(ExecutionState.RUNNING)
                    .event(SpiceEvent.START)
                    .and()
                    .withExternal()
                    .source(ExecutionState.RUNNING)
                    .target(ExecutionState.WAITING)
                    .event(SpiceEvent.PAUSE_FOR_HITL)
                    .and()
                    .withExternal()
                    .source(ExecutionState.RUNNING)
                    .target(ExecutionState.COMPLETED)
                    .event(SpiceEvent.COMPLETE)
                    .and()
                    .withExternal()
                    .source(ExecutionState.RUNNING)
                    .target(ExecutionState.FAILED)
                    .event(SpiceEvent.FAIL)
                    .and()
                    .withExternal()
                    .source(ExecutionState.WAITING)
                    .target(ExecutionState.RUNNING)
                    .event(SpiceEvent.RESUME)
                    .and()
                    .withExternal()
                    .source(ExecutionState.WAITING)
                    .target(ExecutionState.FAILED)
                    .event(SpiceEvent.TIMEOUT)

                return builder.build()
            }

            override fun getStateMachine(machineId: String?): StateMachine<ExecutionState, SpiceEvent> {
                return getStateMachine()  // Same as default for now
            }

            override fun getStateMachine(uuid: UUID?): StateMachine<ExecutionState, SpiceEvent> {
                return getStateMachine()  // Same as default for now
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun spiceStateMachineFactory(
        stateMachineFactory: StateMachineFactory<ExecutionState, SpiceEvent>,
        listeners: List<StateMachineListener<ExecutionState, SpiceEvent>> = emptyList()
    ): SpiceStateMachineFactory {
        return SpiceStateMachineFactory(stateMachineFactory, listeners)
    }

    @Bean
    fun checkpointBridge(
        @Autowired(required = false) persister: StateMachinePersist<ExecutionState, SpiceEvent, String>?
    ): StateMachineCheckpointBridge = StateMachineCheckpointBridge(persister)

    @Bean
    @ConditionalOnBean(GraphRunner::class)
    fun nodeExecutionAction(graphRunner: GraphRunner): NodeExecutionAction = NodeExecutionAction(graphRunner)

    @Bean
    fun checkpointSaveAction(
        checkpointBridge: StateMachineCheckpointBridge,
        publisher: ApplicationEventPublisher,
        @Autowired(required = false) checkpointStore: CheckpointStore?
    ): CheckpointSaveAction = CheckpointSaveAction(checkpointBridge, publisher, checkpointStore)

    @Bean
    fun eventPublishAction(publisher: ApplicationEventPublisher): EventPublishAction = EventPublishAction(publisher)

    @Bean
    fun toolRetryAction(properties: StateMachineProperties): ToolRetryAction =
        ToolRetryAction(properties.retry)

    @Bean
    @ConditionalOnBean(GraphRunner::class)
    fun graphToStateMachineAdapter(
        stateMachineFactory: SpiceStateMachineFactory,
        nodeExecutionAction: NodeExecutionAction,
        checkpointSaveAction: CheckpointSaveAction,
        eventPublishAction: EventPublishAction,
        toolRetryAction: ToolRetryAction,
        retryableErrorGuard: RetryableErrorGuard
    ): GraphToStateMachineAdapter {
        return GraphToStateMachineAdapter(
            stateMachineFactory = stateMachineFactory,
            nodeExecutionAction = nodeExecutionAction,
            checkpointSaveAction = checkpointSaveAction,
            eventPublishAction = eventPublishAction,
            toolRetryAction = toolRetryAction,
            retryableErrorGuard = retryableErrorGuard
        )
    }

    @Bean
    @ConditionalOnBean(StateMachineMetrics::class)
    fun metricsCollector(metrics: StateMachineMetrics): StateMachineListener<ExecutionState, SpiceEvent> =
        MetricsCollector(metrics)

    @Bean
    fun nodeExecutionLogger(publisher: ApplicationEventPublisher): StateMachineListener<ExecutionState, SpiceEvent> {
        return NodeExecutionLogger(publisher)
    }

    @Bean
    fun hitlStateMachineListener(
        checkpointSaveAction: CheckpointSaveAction
    ): StateMachineListener<ExecutionState, SpiceEvent> = HitlStateMachineListener(checkpointSaveAction)

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    fun stateMachineMetrics(
        meterRegistry: MeterRegistry
    ): StateMachineMetrics = StateMachineMetrics(meterRegistry)

    @Bean
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    fun stateMachineHealthIndicator(properties: StateMachineProperties): HealthIndicator {
        return StateMachineHealthIndicator(properties)
    }

    @Bean
    @ConditionalOnProperty(name = ["spice.statemachine.visualization.enabled"], havingValue = "true", matchIfMissing = true)
    fun visualizationEndpoint(
        factory: StateMachineFactory<ExecutionState, SpiceEvent>,
        properties: StateMachineProperties
    ): StateMachineVisualizationEndpoint = StateMachineVisualizationEndpoint(factory, properties)
}
