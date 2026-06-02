package com.codeint.objectboxkmp.runtime

interface ObxBox<T : Any> {
    fun put(entity: T): Long

    fun get(id: Long): T?

    fun getAll(): List<T>

    fun remove(id: Long): Boolean

    fun count(): Long

    fun query(): ObxQueryBuilder<T>
}
