package com.codeint.objectboxkmp.runtime

actual object ObxStoreFactory : ObxBackend {
    actual override fun open(
        config: ObxConfig,
        registry: ObxAdapterRegistry,
    ): ObxStore = InMemoryObxStore(config.name, registry)
}
