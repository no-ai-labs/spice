package io.github.noailabs.spice

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

interface Identifiable {
    val id: String
}

open class Registry<T : Identifiable>(
    private val registryName: String
) {
    private val lock = ReentrantReadWriteLock()
    private val items = ConcurrentHashMap<String, T>()

    open fun register(value: T, allowReplace: Boolean = true): T {
        lock.write {
            if (!allowReplace && items.containsKey(value.id)) {
                throw IllegalArgumentException("$registryName already contains ${value.id}")
            }
            items[value.id] = value
        }
        return value
    }

    fun get(id: String): T? = lock.read { items[id] }

    fun getAll(): List<T> = lock.read { items.values.toList() }

    fun has(id: String): Boolean = lock.read { items.containsKey(id) }

    open fun unregister(id: String): Boolean = lock.write { items.remove(id) != null }

    open fun clear() = lock.write { items.clear() }

    fun size(): Int = lock.read { items.size }

    fun ensure(id: String, builder: () -> T): T {
        return lock.read { items[id] } ?: register(builder())
    }
}
