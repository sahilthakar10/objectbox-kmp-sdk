package com.codeint.objectboxkmp.runtime

import android.content.Context
import java.io.File

class AndroidObxMigrationStateStore(
    context: Context,
) : ObxMigrationStateStore {
    private val root = File(context.applicationContext.filesDir, "objectbox-kmp-migrations")

    override fun readVersion(config: ObxConfig): Int? {
        val file = versionFile(config)
        if (!file.exists()) return null
        return file.readText().trim().toIntOrNull()
    }

    override fun writeVersion(
        config: ObxConfig,
        version: Int,
    ) {
        if (version < 1) {
            throw ObxSchemaException("ObjectBox KMP schema version must be >= 1, but was $version.")
        }
        val file = versionFile(config)
        file.parentFile?.mkdirs()
        file.writeText(version.toString())
    }

    private fun versionFile(config: ObxConfig): File {
        val safeName = config.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, "$safeName.version")
    }
}
