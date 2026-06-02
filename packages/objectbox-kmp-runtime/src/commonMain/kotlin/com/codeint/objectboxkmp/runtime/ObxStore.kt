package com.codeint.objectboxkmp.runtime

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

interface ObxStore {
    val changes: Flow<ObxChange>

    fun <T : Any> box(type: KClass<T>): ObxBox<T>

    fun <R> read(block: ObxStore.() -> R): R = block()

    fun <R> write(block: ObxStore.() -> R): R = block()

    fun close()
}
