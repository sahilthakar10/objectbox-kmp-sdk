package com.codeint.objectboxkmp.runtime

data class ObxChange(
    val entityName: String,
    val operation: ObxChangeOperation,
    val id: Long? = null,
)

enum class ObxChangeOperation {
    Put,
    Remove,
}
