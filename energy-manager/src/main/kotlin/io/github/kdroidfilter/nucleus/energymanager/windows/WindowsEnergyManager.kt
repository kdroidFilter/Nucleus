package io.github.kdroidfilter.nucleus.energymanager.windows

import io.github.kdroidfilter.nucleus.energymanager.EnergyManager

internal object WindowsEnergyManager {
    fun isAvailable(): Boolean =
        NativeWindowsEnergyBridge.isLoaded &&
            runCatching { NativeWindowsEnergyBridge.nativeIsSupported() }.getOrDefault(false)

    fun enable(): EnergyManager.Result {
        if (!NativeWindowsEnergyBridge.isLoaded) {
            return EnergyManager.Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeWindowsEnergyBridge.nativeEnableEfficiencyMode()
            if (rc == 0) {
                EnergyManager.Result(true)
            } else {
                EnergyManager.Result(false, rc, "Native call failed with error code $rc")
            }
        } catch (e: UnsatisfiedLinkError) {
            EnergyManager.Result(false, -1, "Exception: ${e.message}")
        }
    }

    fun enableThread(): EnergyManager.Result =
        EnergyManager.Result(
            true,
            message = "Thread-level not implemented on Windows, no-op",
        )

    fun disableThread(): EnergyManager.Result =
        EnergyManager.Result(
            true,
            message = "Thread-level not implemented on Windows, no-op",
        )

    fun disable(): EnergyManager.Result {
        if (!NativeWindowsEnergyBridge.isLoaded) {
            return EnergyManager.Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeWindowsEnergyBridge.nativeDisableEfficiencyMode()
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
