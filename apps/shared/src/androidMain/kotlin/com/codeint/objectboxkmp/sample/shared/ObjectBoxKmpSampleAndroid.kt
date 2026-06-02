package com.codeint.objectboxkmp.sample.shared

import android.content.Context
import com.codeint.objectboxkmp.runtime.AndroidObxMigrationStateStore
import com.codeint.objectboxkmp.runtime.ObxConfig
import com.codeint.objectboxkmp.runtime.ObxSchemaMismatchPolicy
import com.codeint.objectboxkmp.runtime.ObjectBoxKmpAndroid
import com.codeint.objectboxkmp.runtime.box
import com.codeint.objectboxkmp.sample.shared.generated.GeneratedObxAdapters
import com.codeint.objectboxkmp.sample.shared.generated.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.exception.DbSchemaException

object ObjectBoxKmpSampleAndroid {
    internal var configured = false

    fun init(context: Context) {
        if (configured) return

        val appContext = context.applicationContext
        ObjectBoxKmpAndroid.configureWithConfig(
            config = ObxConfig {
                name = "objectbox-kmp-sample"
                schemaMismatchPolicy = ObxSchemaMismatchPolicy.DeleteAndReopen
                schemaVersion = 2
                migrations {
                    migrate(from = 1, to = 2) {
                        val notes = box<Note>()
                        notes.getAll()
                            .filter { note -> note.title.startsWith("Legacy:") }
                            .forEach { note ->
                                notes.put(
                                    note.copy(
                                        title = note.title.removePrefix("Legacy:").trim(),
                                    ),
                                )
                            }
                    }
                }
            },
            registryProvider = { GeneratedObxAdapters.registry() },
            configBoxStoreProvider = { config -> openStore(appContext, config) },
            migrationStateStoreProvider = { AndroidObxMigrationStateStore(appContext) },
        )
        configured = true
    }

    private fun openStore(
        context: Context,
        config: ObxConfig,
    ): BoxStore {
        return try {
            buildStore(context, config.name)
        } catch (exception: DbSchemaException) {
            if (config.schemaMismatchPolicy != ObxSchemaMismatchPolicy.DeleteAndReopen) {
                throw exception
            }
            BoxStore.deleteAllFiles(context, config.name)
            buildStore(context, config.name)
        }
    }

    private fun buildStore(
        context: Context,
        name: String,
    ): BoxStore {
        return MyObjectBox.builder()
            .androidContext(context)
            .name(name)
            .build()
    }
}

internal actual fun ensureObjectBoxKmpConfigured() {
    check(ObjectBoxKmpSampleAndroid.configured) {
        "ObjectBoxKmpSampleAndroid.init(context) must be called before using CommonCrudSample."
    }
}
