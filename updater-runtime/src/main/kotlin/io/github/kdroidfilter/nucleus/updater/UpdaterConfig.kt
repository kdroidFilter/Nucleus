package io.github.kdroidfilter.nucleus.updater

import io.github.kdroidfilter.nucleus.updater.provider.UpdateProvider

class UpdaterConfig {
    var currentVersion: String = System.getProperty("jpackage.app-version") ?: DEV_VERSION
    lateinit var provider: UpdateProvider
    var channel: String = "latest"
    var allowDowngrade: Boolean = false
    var allowPrerelease: Boolean = false
    var executableType: String? = null

    internal fun resolvedAllowPrerelease(): Boolean = allowPrerelease || currentVersion.contains("-")

    internal fun isDevMode(): Boolean = currentVersion == DEV_VERSION

    companion object {
        const val DEV_VERSION = "0.0.0-dev"
    }
}

fun NucleusUpdater(block: UpdaterConfig.() -> Unit): NucleusUpdater {
    val config = UpdaterConfig().apply(block)
    return NucleusUpdater(config)
}
