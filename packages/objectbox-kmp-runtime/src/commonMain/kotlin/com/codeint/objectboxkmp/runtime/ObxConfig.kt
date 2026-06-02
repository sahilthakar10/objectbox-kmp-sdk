package com.codeint.objectboxkmp.runtime

class ObxConfig internal constructor(
    val name: String,
    val debug: Boolean,
    val schemaMismatchPolicy: ObxSchemaMismatchPolicy,
    val migrationPlan: ObxMigrationPlan,
) {
    class Builder {
        var name: String = "objectbox-kmp"
        var debug: Boolean = false
        var schemaMismatchPolicy: ObxSchemaMismatchPolicy = ObxSchemaMismatchPolicy.Fail
        private var migrationPlanBuilder = ObxMigrationPlanBuilder()

        var schemaVersion: Int
            get() = migrationPlanBuilder.targetVersion
            set(value) {
                migrationPlanBuilder.targetVersion = value
            }

        fun migrations(block: ObxMigrationPlanBuilder.() -> Unit) {
            migrationPlanBuilder.apply(block)
        }

        fun build(): ObxConfig {
            if (name.isBlank()) {
                throw ObxConfigurationException(
                    "ObjectBox KMP store name must not be blank. Set ObxConfig.name to a stable non-empty value.",
                )
            }
            return ObxConfig(
                name = name,
                debug = debug,
                schemaMismatchPolicy = schemaMismatchPolicy,
                migrationPlan = migrationPlanBuilder.build(),
            )
        }
    }
}

enum class ObxSchemaMismatchPolicy {
    Fail,
    DeleteAndReopen,
}

fun ObxConfig(
    block: ObxConfig.Builder.() -> Unit = {},
): ObxConfig = ObxConfig.Builder().apply(block).build()
