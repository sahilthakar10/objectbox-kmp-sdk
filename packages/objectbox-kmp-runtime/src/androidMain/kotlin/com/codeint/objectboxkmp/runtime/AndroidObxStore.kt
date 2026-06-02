package com.codeint.objectboxkmp.runtime

import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.reflect.KClass

class AndroidObxStore(
    private val boxStore: BoxStore,
    private val registry: ObxAdapterRegistry,
) : ObxStore {
    private val changeEvents = MutableSharedFlow<ObxChange>(extraBufferCapacity = 64)
    private var closed = false

    override val changes: Flow<ObxChange> = changeEvents.asSharedFlow()

    override fun <T : Any> box(type: KClass<T>): ObxBox<T> {
        if (closed) {
            throw ObxStoreOpenException(
                "ObjectBox KMP Android store is already closed. Open a new store before requesting boxes.",
            )
        }
        val adapter = registry.adapterFor(type)
        if (adapter !is AndroidObxAdapter<*, *>) {
            throw ObxConfigurationException(
                "Adapter for '${type.qualifiedName ?: type.simpleName}' does not support Android ObjectBox. " +
                    "Android KSP must generate Android adapters from common schema metadata. " +
                    "Apply the com.codeint.objectbox-kmp Gradle plugin and use the Android generated registry in ObjectBoxKmpAndroid.configureWithConfig(...).",
            )
        }

        @Suppress("UNCHECKED_CAST")
        return try {
            AndroidObjectBox(
                objectBox = boxStore.boxFor(adapter.entityClass as Class<Any>),
                adapter = adapter as AndroidObxAdapter<T, Any>,
                publishChange = changeEvents::tryEmit,
            )
        } catch (exception: ClassCastException) {
            throw ObxConfigurationException(
                "ObjectBox KMP generated adapter type mismatch for '${type.qualifiedName ?: type.simpleName}'. " +
                    "Clean the project and rebuild so common schema metadata and Android adapters are generated from the same sources.",
                exception,
            )
        } catch (exception: RuntimeException) {
            throw ObxStoreOpenException(
                "ObjectBox KMP failed to open Android box for '${type.qualifiedName ?: type.simpleName}'. " +
                    "Check that MyObjectBox was generated and the BoxStore provider uses the same generated package as GeneratedObxAdapters.",
                exception,
            )
        }
    }

    override fun close() {
        closed = true
        boxStore.close()
    }

    override fun <R> read(block: ObxStore.() -> R): R {
        return try {
            boxStore.callInReadTx { block() }
        } catch (exception: RuntimeException) {
            throw ObxTransactionException(
                "ObjectBox KMP read transaction failed on Android. " +
                    "Check the transaction block for thrown exceptions and avoid write operations inside read transactions.",
                exception,
            )
        }
    }

    override fun <R> write(block: ObxStore.() -> R): R {
        return try {
            boxStore.callInTxNoException { block() }
        } catch (exception: RuntimeException) {
            throw ObxTransactionException(
                "ObjectBox KMP write transaction failed on Android. " +
                    "Check the transaction block and ObjectBox schema/model compatibility.",
                exception,
            )
        }
    }
}

private class AndroidObjectBox<T : Any, E : Any>(
    private val objectBox: Box<E>,
    private val adapter: AndroidObxAdapter<T, E>,
    private val publishChange: (ObxChange) -> Boolean,
) : ObxBox<T> {
    override fun put(entity: T): Long {
        val databaseEntity = adapter.toDatabaseEntity(entity)
        val id = objectBox.put(databaseEntity)
        publishChange(
            ObxChange(
                entityName = adapter.entityName,
                operation = ObxChangeOperation.Put,
                id = id,
            ),
        )
        return id
    }

    override fun get(id: Long): T? {
        return objectBox.get(id)?.let(adapter::fromDatabaseEntity)
    }

    override fun getAll(): List<T> {
        return objectBox.all.map(adapter::fromDatabaseEntity)
    }

    override fun remove(id: Long): Boolean {
        val removed = objectBox.remove(id)
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

    override fun count(): Long {
        return objectBox.count()
    }

    override fun query(): ObxQueryBuilder<T> {
        return ObxQueryBuilder(
            source = ::getAll,
            removeEntity = { entity -> remove(adapter.getId(entity)) },
        )
    }
}
