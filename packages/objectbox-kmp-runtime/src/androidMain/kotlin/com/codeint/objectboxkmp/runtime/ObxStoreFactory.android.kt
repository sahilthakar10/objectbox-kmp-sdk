package com.codeint.objectboxkmp.runtime

import io.objectbox.BoxStore

object ObjectBoxKmpAndroid {
    private var boxStoreProvider: ((ObxConfig) -> BoxStore)? = null

    fun configure(
        config: ObxConfig = ObxConfig(),
        registryProvider: () -> ObxAdapterRegistry,
        boxStoreProvider: (String) -> BoxStore,
        migrationStateStoreProvider: (ObxConfig) -> ObxMigrationStateStore = {
            ObxMigrationStateStores.inMemory()
        },
    ) {
        configureWithConfig(
            config = config,
            registryProvider = registryProvider,
            configBoxStoreProvider = { storeConfig -> boxStoreProvider(storeConfig.name) },
            migrationStateStoreProvider = migrationStateStoreProvider,
        )
    }

    fun configureWithConfig(
        config: ObxConfig = ObxConfig(),
        registryProvider: () -> ObxAdapterRegistry,
        configBoxStoreProvider: (ObxConfig) -> BoxStore,
        migrationStateStoreProvider: (ObxConfig) -> ObxMigrationStateStore = {
            ObxMigrationStateStores.inMemory()
        },
    ) {
        this.boxStoreProvider = configBoxStoreProvider
        ObjectBoxKmp.configure(
            config = config,
            registryProvider = registryProvider,
            backend = ObxStoreFactory,
            migrationStateStoreProvider = migrationStateStoreProvider,
        )
    }

    internal fun boxStore(config: ObxConfig): BoxStore {
        return boxStoreProvider?.invoke(config)
            ?: throw ObxConfigurationException(
                "ObjectBoxKmpAndroid is not configured for store '${config.name}'. " +
                    "Call ObjectBoxKmpAndroid.configureWithConfig(...) before ObjectBoxKmp.open(), " +
                    "pass registryProvider = { GeneratedObxAdapters.registry() }, and provide a BoxStore provider such as MyObjectBox.builder().build().",
            )
    }
}

actual object ObxStoreFactory : ObxBackend {
    actual override fun open(
        config: ObxConfig,
        registry: ObxAdapterRegistry,
    ): ObxStore = AndroidObxStore(
        boxStore = ObjectBoxKmpAndroid.boxStore(config),
        registry = registry,
    )
}
