package io.github.kdroidfilter.nucleus.updater.provider

import io.github.kdroidfilter.nucleus.updater.Platform

interface UpdateProvider {
    fun getUpdateMetadataUrl(
        channel: String,
        platform: Platform,
    ): String

    fun getDownloadUrl(
        fileName: String,
        version: String,
    ): String

    fun authHeaders(): Map<String, String> = emptyMap()
}
