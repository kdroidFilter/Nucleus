package io.github.kdroidfilter.nucleus.energymanager.macos

import io.github.kdroidfilter.nucleus.energymanager.EnergyManager

internal object MacOsEnergyManager {
    fun isAvailable(): Boolean =
        NativeMacOsEnergyBridge.isLoaded &&
            runCatching { NativeMacOsEnergyBridge.nativeIsSupported() }.getOrDefault(false)

    fun enable(): EnergyManager.Result {
        if (!NativeMacOsEnergyBridge.isLoaded) {
            return EnergyManager.Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeMacOsEnergyBridge.nativeEnableEfficiencyMode()
            if (rc == 0) {
                EnergyManager.Result(true)
            } else {
                EnergyManager.Result(false, rc, "Native call failed with error code $rc")
            }
        } catch (e: UnsatisfiedLinkError) {
            EnergyManager.Result(false, -1, "Exception: ${e.message}")
        }
    }

    fun disable(): EnergyManager.Result {
        if (!NativeMacOsEnergyBridge.isLoaded) {
            return EnergyManager.Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeMacOsEnergyBridge.nativeDisableEfficiencyMode()
            if (rc == 0) {
                EnergyManager.Result(true)
            } else {
                EnergyManager.Result(false, rc, "Native call failed with error code $rc")
            }
        } catch (e: UnsatisfiedLinkError) {
            EnergyManager.Result(false, -1, "Exception: ${e.message}")
        }
    }
}
