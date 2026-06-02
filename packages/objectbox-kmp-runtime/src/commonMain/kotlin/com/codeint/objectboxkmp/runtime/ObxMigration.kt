package com.codeint.objectboxkmp.runtime

class ObxMigration internal constructor(
    val fromVersion: Int,
    val toVersion: Int,
    internal val migrate: ObxStore.() -> Unit,
)

class ObxMigrationPlan internal constructor(
    val targetVersion: Int,
    internal val migrations: List<ObxMigration>,
) {
    init {
        if (targetVersion < 1) {
            throw ObxSchemaException("ObjectBox KMP schema version must be >= 1, but was $targetVersion.")
        }
    }

    internal fun validate() {
        migrations.forEach { migration ->
            if (migration.fromVersion < 1) {
                throw ObxSchemaException(
                    "ObjectBox KMP migration fromVersion must be >= 1, but was ${migration.fromVersion}.",
                )
            }
            if (migration.toVersion <= migration.fromVersion) {
                throw ObxSchemaException(
                    "ObjectBox KMP migration ${migration.fromVersion}->${migration.toVersion} must move forward.",
                )
            }
            if (migration.toVersion > targetVersion) {
                throw ObxSchemaException(
                    "ObjectBox KMP migration ${migration.fromVersion}->${migration.toVersion} exceeds target version $targetVersion.",
                )
            }
        }
    }
}

class ObxMigrationPlanBuilder internal constructor() {
    var targetVersion: Int = 1
    private val migrations = mutableListOf<ObxMigration>()

    fun migrate(
        from: Int,
        to: Int,
        block: ObxStore.() -> Unit,
    ) {
        migrations += ObxMigration(
            fromVersion = from,
            toVersion = to,
            migrate = block,
        )
    }

    internal fun build(): ObxMigrationPlan {
        return ObxMigrationPlan(
            targetVersion = targetVersion,
            migrations = migrations.sortedWith(
                compareBy<ObxMigration> { it.fromVersion }.thenBy { it.toVersion },
            ),
        ).also { plan -> plan.validate() }
    }
}

interface ObxMigrationStateStore {
    fun readVersion(config: ObxConfig): Int?

    fun writeVersion(
        config: ObxConfig,
        version: Int,
    )
}

object ObxMigrationStateStores {
    fun inMemory(): ObxMigrationStateStore = InMemoryObxMigrationStateStore
}

internal object InMemoryObxMigrationStateStore : ObxMigrationStateStore {
    private val versions = mutableMapOf<String, Int>()

    override fun readVersion(config: ObxConfig): Int? = versions[config.name]

    override fun writeVersion(
        config: ObxConfig,
        version: Int,
    ) {
        versions[config.name] = version
    }
}

internal object ObxMigrationRunner {
    fun run(
        store: ObxStore,
        config: ObxConfig,
        stateStore: ObxMigrationStateStore,
    ) {
        val plan = config.migrationPlan
        val startingVersion: Int = stateStore.readVersion(config) ?: run {
            stateStore.writeVersion(config, plan.targetVersion)
            return
        }

        if (startingVersion == plan.targetVersion) return

        if (startingVersion > plan.targetVersion) {
            throw ObxSchemaException(
                "ObjectBox KMP store '${config.name}' is at schema version $startingVersion, " +
                    "but this SDK expects ${plan.targetVersion}. Downgrade migrations are not supported.",
            )
        }

        var currentVersion = startingVersion
        while (currentVersion < plan.targetVersion) {
            val migration = plan.migrations.firstOrNull { candidate ->
                candidate.fromVersion == currentVersion && candidate.toVersion <= plan.targetVersion
            } ?: throw ObxSchemaException(
                "Missing ObjectBox KMP migration from version $currentVersion " +
                    "to ${plan.targetVersion} for store '${config.name}'.",
            )

            try {
                store.write {
                    migration.migrate(this)
                }
                currentVersion = migration.toVersion
                stateStore.writeVersion(config, currentVersion)
            } catch (exception: ObxException) {
                throw exception
            } catch (exception: Throwable) {
                throw ObxSchemaException(
                    "ObjectBox KMP migration ${migration.fromVersion}->${migration.toVersion} failed.",
                    exception,
                )
            }
        }
    }
}
