package com.codeint.objectboxkmp.runtime

import kotlin.reflect.KClass

interface ObxAdapter<T : Any> {
    val type: KClass<T>
    val entityName: String

    fun getId(entity: T): Long

    fun withId(entity: T, id: Long): T
}
