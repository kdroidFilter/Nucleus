package io.github.kdroidfilter.nucleus.updater

import io.github.kdroidfilter.nucleus.updater.provider.UpdateProvider

class UpdaterConfig {
    lateinit var currentVersion: String
    lateinit var provider: UpdateProvider
    var channel: String = "latest"
    var allowDowngrade: Boolean = false
    var allowPrerelease: Boolean = false
    var executableType: String? = null

    internal fun resolvedAllowPrerelease(): Boolean = allowPrerelease || (::currentVersion.isInitialized && currentVersion.contains("-"))
}

fun NucleusUpdater(block: UpdaterConfig.() -> Unit): NucleusUpdater {
    val config = UpdaterConfig().apply(block)
    return NucleusUpdater(config)
}
