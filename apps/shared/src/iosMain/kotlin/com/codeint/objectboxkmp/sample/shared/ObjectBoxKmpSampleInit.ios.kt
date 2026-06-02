package com.codeint.objectboxkmp.sample.shared

import com.codeint.objectboxkmp.runtime.ObjectBoxKmp
import com.codeint.objectboxkmp.runtime.ObxAdapterRegistry
import com.codeint.objectboxkmp.runtime.ObxConfig
import com.codeint.objectboxkmp.runtime.box

private var configured = false

internal actual fun ensureObjectBoxKmpConfigured() {
    if (!configured) {
        ObjectBoxKmp.configure(
            config = ObxConfig {
                name = "objectbox-kmp-sample"
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
            registryProvider = ::generatedObxAdapterRegistry,
        )
        configured = true
    }
}

internal expect fun generatedObxAdapterRegistry(): ObxAdapterRegistry
