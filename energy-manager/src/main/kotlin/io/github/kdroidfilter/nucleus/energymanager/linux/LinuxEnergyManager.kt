package io.github.kdroidfilter.nucleus.energymanager.linux

import io.github.kdroidfilter.nucleus.energymanager.EnergyManager

internal object LinuxEnergyManager {
    fun isAvailable(): Boolean =
        NativeLinuxEnergyBridge.isLoaded &&
            runCatching { NativeLinuxEnergyBridge.nativeIsSupported() }.getOrDefault(false)

    fun enable(): EnergyManager.Result {
        if (!NativeLinuxEnergyBridge.isLoaded) {
            return EnergyManager.Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeLinuxEnergyBridge.nativeEnableEfficiencyMode()
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
        if (!NativeLinuxEnergyBridge.isLoaded) {
            return EnergyManager.Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeLinuxEnergyBridge.nativeDisableEfficiencyMode()
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
