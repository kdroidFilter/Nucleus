package io.github.kdroidfilter.nucleus.energymanager.linux

import io.github.kdroidfilter.nucleus.energymanager.EnergyManager
import io.github.kdroidfilter.nucleus.energymanager.PlatformEnergyManager

internal object LinuxEnergyManager : PlatformEnergyManager {
    private fun callNative(block: () -> Int): EnergyManager.Result {
        if (!NativeLinuxEnergyBridge.isLoaded) {
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
        NativeLinuxEnergyBridge.isLoaded &&
            runCatching { NativeLinuxEnergyBridge.nativeIsSupported() }.getOrDefault(false)

    override fun enableEfficiencyMode() = callNative { NativeLinuxEnergyBridge.nativeEnableEfficiencyMode() }

    override fun disableEfficiencyMode() = callNative { NativeLinuxEnergyBridge.nativeDisableEfficiencyMode() }

    override fun enableThreadEfficiencyMode(): EnergyManager.Result =
        callNative { NativeLinuxEnergyBridge.nativeEnableThreadEfficiencyMode() }

    override fun disableThreadEfficiencyMode(): EnergyManager.Result =
        callNative { NativeLinuxEnergyBridge.nativeDisableThreadEfficiencyMode() }

    @Synchronized
    override fun keepScreenAwake(): EnergyManager.Result = callNative { NativeLinuxEnergyBridge.nativeKeepScreenAwake() }

    @Synchronized
    override fun releaseScreenAwake(): EnergyManager.Result = callNative { NativeLinuxEnergyBridge.nativeReleaseScreenAwake() }

    override fun isScreenAwakeActive(): Boolean =
        NativeLinuxEnergyBridge.isLoaded &&
            runCatching { NativeLinuxEnergyBridge.nativeIsScreenAwakeActive() }.getOrDefault(false)
}
