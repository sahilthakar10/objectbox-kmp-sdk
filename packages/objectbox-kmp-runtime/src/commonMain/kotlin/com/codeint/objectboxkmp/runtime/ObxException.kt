package com.codeint.objectboxkmp.runtime

sealed class ObxException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ObxConfigurationException(
    message: String,
    cause: Throwable? = null,
) : ObxException(message, cause)

class ObxStoreOpenException(
    message: String,
    cause: Throwable? = null,
) : ObxException(message, cause)

class ObxSchemaException(
    message: String,
    cause: Throwable? = null,
) : ObxException(message, cause)

class ObxQueryException(
    message: String,
    cause: Throwable? = null,
) : ObxException(message, cause)

class ObxTransactionException(
    message: String,
    cause: Throwable? = null,
) : ObxException(message, cause)
