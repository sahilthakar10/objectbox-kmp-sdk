package com.codeint.objectboxkmp.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ObxEntity(
    val name: String = "",
)
