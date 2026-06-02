package com.codeint.objectboxkmp.sample.shared

import com.codeint.objectboxkmp.runtime.ObxAdapterRegistry
import com.codeint.objectboxkmp.sample.shared.generated.GeneratedObxAdapters

internal actual fun generatedObxAdapterRegistry(): ObxAdapterRegistry {
    return GeneratedObxAdapters.registry()
}
