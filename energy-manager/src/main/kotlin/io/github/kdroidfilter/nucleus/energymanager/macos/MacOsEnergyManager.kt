package io.github.kdroidfilter.nucleus.energymanager.macos

import io.github.kdroidfilter.nucleus.energymanager.EnergyManager

private val NOT_IMPLEMENTED = EnergyManager.Result(false, -1, "macOS energy manager not yet implemented")

internal object MacOsEnergyManager {
    @Suppress("FunctionOnlyReturningConstant")
    fun isAvailable(): Boolean = false

    fun enable(): EnergyManager.Result = NOT_IMPLEMENTED

    fun disable(): EnergyManager.Result = NOT_IMPLEMENTED
}
