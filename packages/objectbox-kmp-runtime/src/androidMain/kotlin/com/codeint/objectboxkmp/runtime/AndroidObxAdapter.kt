package com.codeint.objectboxkmp.runtime

interface AndroidObxAdapter<T : Any, E : Any> : ObxAdapter<T> {
    val entityClass: Class<E>

    fun toDatabaseEntity(model: T): E

    fun fromDatabaseEntity(entity: E): T
}
