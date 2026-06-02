package com.codeint.objectboxkmp.sample.shared

import com.codeint.objectboxkmp.annotations.ObxEntity
import com.codeint.objectboxkmp.annotations.ObxId

@ObxEntity
data class Note(
    @ObxId val id: Long = 0,
    val title: String,
    val done: Boolean = false,
)
