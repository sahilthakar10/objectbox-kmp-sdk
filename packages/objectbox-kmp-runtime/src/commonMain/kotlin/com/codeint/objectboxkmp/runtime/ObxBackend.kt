package com.codeint.objectboxkmp.runtime

interface ObxBackend {
    fun open(
        config: ObxConfig,
        registry: ObxAdapterRegistry,
    ): ObxStore
}
