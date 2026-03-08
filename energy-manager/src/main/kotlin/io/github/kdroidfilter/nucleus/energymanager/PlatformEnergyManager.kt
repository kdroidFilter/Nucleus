package io.github.kdroidfilter.nucleus.energymanager

internal interface PlatformEnergyManager {
    fun isAvailable(): Boolean

    fun enableEfficiencyMode(): EnergyManager.Result

    fun disableEfficiencyMode(): EnergyManager.Result

    fun enableThreadEfficiencyMode(): EnergyManager.Result

    fun disableThreadEfficiencyMode(): EnergyManager.Result

    fun keepScreenAwake(): EnergyManager.Result

    fun releaseScreenAwake(): EnergyManager.Result

    fun isScreenAwakeActive(): Boolean
}
