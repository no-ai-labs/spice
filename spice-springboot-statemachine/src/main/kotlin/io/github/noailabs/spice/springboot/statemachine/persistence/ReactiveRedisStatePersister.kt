package io.github.noailabs.spice.springboot.statemachine.persistence

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.StateMachineContext
import org.springframework.statemachine.persist.ReactiveStateMachinePersister
import org.springframework.statemachine.support.DefaultExtendedState
import org.springframework.statemachine.support.DefaultStateMachineContext
import reactor.core.publisher.Mono

/**
 * Persists state machine context as JSON into Redis.
 */
class ReactiveRedisStatePersister(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val json: Json = Json
) : ReactiveStateMachinePersister<ExecutionState, SpiceEvent, String> {

    @Serializable
    private data class PersistedContext(
        val state: ExecutionState,
        val extendedState: Map<String, String> = emptyMap()
    )

    override fun persist(
        stateMachine: StateMachine<ExecutionState, SpiceEvent>,
        contextObj: String
    ): Mono<Void> {
        val payload = PersistedContext(
            state = stateMachine.state.id,
            extendedState = stateMachine.extendedState.variables
                .mapNotNull { (key, value) ->
                    key?.toString()?.let { k -> k to serializeExtendedValue(value) }
                }.toMap()
        )
        val jsonPayload = json.encodeToString(payload)
        return redisTemplate.opsForValue().set(key(contextObj), jsonPayload).then()
    }

    override fun restore(
        stateMachine: StateMachine<ExecutionState, SpiceEvent>?,
        contextObj: String
    ): Mono<StateMachineContext<ExecutionState, SpiceEvent>> {
        return redisTemplate.opsForValue().get(key(contextObj)).flatMap { jsonPayload ->
            runCatching {
                json.decodeFromString(PersistedContext.serializer(), jsonPayload)
            }.getOrNull()?.let { persisted ->
                val extendedState = DefaultExtendedState().apply {
                    persisted.extendedState.forEach { (k, v) -> variables[k] = v }
                }
                Mono.just(
                    DefaultStateMachineContext(
                        null,
                        persisted.state,
                        null,
                        extendedState,
                        null,
                        "spice-state-machine"
                    )
                )
            } ?: Mono.empty()
        }
    }

    private fun key(id: String) = "spice:sm:$id"

    private fun serializeExtendedValue(value: Any?): String {
        return when (value) {
            null -> JsonNull.toString()
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> json.encodeToString(JsonElement.serializer(), toJsonElement(value))
        }
    }

    private fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(
                value.mapNotNull { (k, v) ->
                    val key = k?.toString() ?: return@mapNotNull null
                    key to toJsonElement(v)
                }.toMap()
            )
            is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
            is Array<*> -> JsonArray(value.map { toJsonElement(it) })
            is IntArray -> JsonArray(value.map { JsonPrimitive(it) })
            is LongArray -> JsonArray(value.map { JsonPrimitive(it) })
            is DoubleArray -> JsonArray(value.map { JsonPrimitive(it) })
            is FloatArray -> JsonArray(value.map { JsonPrimitive(it) })
            is BooleanArray -> JsonArray(value.map { JsonPrimitive(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
}
