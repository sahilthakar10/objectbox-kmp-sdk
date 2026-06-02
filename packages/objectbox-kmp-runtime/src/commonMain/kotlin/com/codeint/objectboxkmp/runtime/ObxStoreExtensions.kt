package com.codeint.objectboxkmp.runtime

inline fun <reified T : Any> ObxStore.box(): ObxBox<T> = box(T::class)
