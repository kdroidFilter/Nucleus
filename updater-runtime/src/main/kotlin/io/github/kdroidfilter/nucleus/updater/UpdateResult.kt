package io.github.kdroidfilter.nucleus.updater

import io.github.kdroidfilter.nucleus.updater.exception.UpdateException

sealed class UpdateResult {
    data class Available(
        val info: UpdateInfo,
    ) : UpdateResult()

    data object NotAvailable : UpdateResult()

    data class Error(
        val exception: UpdateException,
    ) : UpdateResult()
}
