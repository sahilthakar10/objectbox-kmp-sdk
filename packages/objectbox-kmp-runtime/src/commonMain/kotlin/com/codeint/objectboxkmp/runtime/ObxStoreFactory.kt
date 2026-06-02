package com.codeint.objectboxkmp.runtime

object ObjectBoxKmp {
    private var registryProvider: (() -> ObxAdapterRegistry)? = null
    private var defaultConfig: ObxConfig = ObxConfig()
    private var backend: ObxBackend? = null
    private var migrationStateStoreProvider: (ObxConfig) -> ObxMigrationStateStore = {
        ObxMigrationStateStores.inMemory()
    }

    fun configure(registryProvider: () -> ObxAdapterRegistry) {
        configure(
            config = ObxConfig(),
            registryProvider = registryProvider,
            backend = ObxStoreFactory,
        )
    }

    fun configure(
        config: ObxConfig = ObxConfig(),
        registryProvider: () -> ObxAdapterRegistry,
        backend: ObxBackend = ObxStoreFactory,
        migrationStateStoreProvider: (ObxConfig) -> ObxMigrationStateStore = {
            ObxMigrationStateStores.inMemory()
        },
    ) {
        this.defaultConfig = config
        this.registryProvider = registryProvider
        this.backend = backend
        this.migrationStateStoreProvider = migrationStateStoreProvider
    }

    fun open(name: String = defaultConfig.name): ObxStore {
        return open(
            ObxConfig {
                this.name = name
                debug = defaultConfig.debug
                schemaMismatchPolicy = defaultConfig.schemaMismatchPolicy
                schemaVersion = defaultConfig.migrationPlan.targetVersion
                migrations {
                    defaultConfig.migrationPlan.migrations.forEach { migration ->
                        migrate(
                            from = migration.fromVersion,
                            to = migration.toVersion,
                            block = migration.migrate,
                        )
                    }
                }
            },
        )
    }

    fun open(config: ObxConfig): ObxStore {
        val registry = registryProvider?.invoke()
            ?: throw ObxConfigurationException(
                "ObjectBox KMP is not configured. Call the platform initializer before ObjectBoxKmp.open(). " +
                    "On Android, call ObjectBoxKmpAndroid.configureWithConfig(..., registryProvider = { GeneratedObxAdapters.registry() }, ...). " +
                    "On iOS/native, call ObjectBoxKmp.configure(registryProvider = { GeneratedObxAdapters.registry() }).",
            )
        val configuredBackend = backend ?: throw ObxConfigurationException(
            "ObjectBox KMP backend is not configured. Use ObjectBoxKmpAndroid.configureWithConfig(...) on Android " +
                "or ObjectBoxKmp.configure(...) with a backend before opening a store.",
        )

        return try {
            configuredBackend.open(config, registry).also { store ->
                ObxMigrationRunner.run(
                    store = store,
                    config = config,
                    stateStore = migrationStateStoreProvider(config),
                )
            }
        } catch (exception: ObxException) {
            throw exception
        } catch (exception: Throwable) {
            throw ObxStoreOpenException(
                "Failed to open ObjectBox KMP store '${config.name}'. " +
                    "Check platform initialization, generated adapter registry, ObjectBox schema compatibility, and configured migrations.",
                exception,
            )
        }
    }
}

expect object ObxStoreFactory : ObxBackend {
    override fun open(
        config: ObxConfig,
        registry: ObxAdapterRegistry,
    ): ObxStore
}
