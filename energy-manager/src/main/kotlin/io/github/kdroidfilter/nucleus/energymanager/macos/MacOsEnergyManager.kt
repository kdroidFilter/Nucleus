package io.github.kdroidfilter.nucleus.energymanager.macos

import io.github.kdroidfilter.nucleus.energymanager.EnergyManager
import io.github.kdroidfilter.nucleus.energymanager.PlatformEnergyManager

internal object MacOsEnergyManager : PlatformEnergyManager {
    private val unsupported = EnergyManager.Result(false, -1, "Not yet implemented on macOS")

    private fun callNative(block: () -> Int): EnergyManager.Result {
        if (!NativeMacOsEnergyBridge.isLoaded) {
            return EnergyManager.Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = block()
            if (rc == 0) {
                EnergyManager.Result(true)
            } else {
                EnergyManager.Result(false, rc, "Native call failed with error code $rc")
            }
        } catch (e: UnsatisfiedLinkError) {
            EnergyManager.Result(false, -1, "Exception: ${e.message}")
        }
    }

    override fun isAvailable(): Boolean =
        NativeMacOsEnergyBridge.isLoaded &&
            runCatching { NativeMacOsEnergyBridge.nativeIsSupported() }.getOrDefault(false)

    override fun enableEfficiencyMode() = callNative { NativeMacOsEnergyBridge.nativeEnableEfficiencyMode() }

    override fun disableEfficiencyMode() = callNative { NativeMacOsEnergyBridge.nativeDisableEfficiencyMode() }

    override fun enableThreadEfficiencyMode(): EnergyManager.Result =
        callNative { NativeMacOsEnergyBridge.nativeEnableThreadEfficiencyMode() }

    override fun disableThreadEfficiencyMode(): EnergyManager.Result =
        callNative { NativeMacOsEnergyBridge.nativeDisableThreadEfficiencyMode() }

    override fun keepScreenAwake(): EnergyManager.Result = unsupported

    override fun releaseScreenAwake(): EnergyManager.Result = unsupported

    override fun isScreenAwakeActive(): Boolean = false
}
