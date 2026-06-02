package com.codeint.objectboxkmp.runtime

import kotlin.reflect.KClass

class ObxAdapterRegistry(
    adapters: List<ObxAdapter<out Any>>,
) {
    private val adaptersByType = adapters.associateBy { it.type }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> adapterFor(type: KClass<T>): ObxAdapter<T> {
        return adaptersByType[type] as? ObxAdapter<T>
            ?: throw ObxConfigurationException(
                "No ObjectBox KMP adapter is registered for '${type.qualifiedName ?: type.simpleName}'. " +
                    "Make sure the entity is annotated with @ObxEntity in commonMain, the com.codeint.objectbox-kmp Gradle plugin is applied, " +
                    "and ObjectBoxKmp.configure(...) uses the generated GeneratedObxAdapters.registry().",
            )
    }
}
