package com.codeint.objectboxkmp.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.reflect.KClass

internal class InMemoryObxStore(
    @Suppress("unused") private val name: String,
    private val registry: ObxAdapterRegistry,
) : ObxStore {
    private val boxes = mutableMapOf<KClass<*>, InMemoryObxBox<*>>()
    private val changeEvents = MutableSharedFlow<ObxChange>(extraBufferCapacity = 64)
    private var closed = false

    override val changes: Flow<ObxChange> = changeEvents.asSharedFlow()

    override fun <T : Any> box(type: KClass<T>): ObxBox<T> {
        if (closed) {
            throw ObxStoreOpenException(
                "ObjectBox KMP store '$name' is already closed. Open a new store before requesting boxes.",
            )
        }
        val adapter = registry.adapterFor(type)

        @Suppress("UNCHECKED_CAST")
        return boxes.getOrPut(type) {
            InMemoryObxBox(
                adapter = adapter,
                publishChange = changeEvents::tryEmit,
            )
        } as ObxBox<T>
    }

    override fun close() {
        closed = true
        boxes.clear()
    }
}

internal class InMemoryObxBox<T : Any>(
    private val adapter: ObxAdapter<T>,
    private val publishChange: (ObxChange) -> Boolean,
) : ObxBox<T> {
    private val rows = linkedMapOf<Long, T>()
    private var nextId = 1L

    override fun put(entity: T): Long {
        val existingId = adapter.getId(entity)
        val id = if (existingId == 0L) nextId++ else existingId
        rows[id] = adapter.withId(entity, id)
        publishChange(
            ObxChange(
                entityName = adapter.entityName,
                operation = ObxChangeOperation.Put,
                id = id,
            ),
        )
        return id
    }

    override fun get(id: Long): T? = rows[id]

    override fun getAll(): List<T> = rows.values.toList()

    override fun remove(id: Long): Boolean {
        val removed = rows.remove(id) != null
        if (removed) {
            publishChange(
                ObxChange(
                    entityName = adapter.entityName,
                    operation = ObxChangeOperation.Remove,
                    id = id,
                ),
            )
        }
        return removed
    }

    override fun count(): Long = rows.size.toLong()

    override fun query(): ObxQueryBuilder<T> {
        return ObxQueryBuilder(
            source = ::getAll,
            removeEntity = { entity -> remove(adapter.getId(entity)) },
        )
    }
}
