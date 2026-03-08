package io.github.kdroidfilter.nucleus.energymanager

/**
 * Manages process-level energy efficiency mode.
 *
 * On Windows 11+: activates EcoQoS (CPU frequency reduction, E-core routing)
 * combined with IDLE_PRIORITY_CLASS (green leaf icon in Task Manager).
 *
 * On Windows 10 1709+: activates LowQoS (reduced effect, battery only).
 *
 * On macOS/Linux or older Windows: no-op (isAvailable returns false).
 *
 * Intended usage: enable when the application is minimized or in the background,
 * disable when the application returns to the foreground.
 */
object EnergyManager {
    data class Result(
        val success: Boolean,
        val errorCode: Int = 0,
        val message: String = "",
    )

    private val isWindows: Boolean =
        System.getProperty("os.name", "").lowercase().contains("win")

    /**
     * Returns true if the energy efficiency API is available on this platform.
     */
    fun isAvailable(): Boolean {
        if (!isWindows) return false
        return NativeEnergyManagerBridge.isLoaded &&
            runCatching { NativeEnergyManagerBridge.nativeIsSupported() }.getOrDefault(false)
    }

    /**
     * Enables efficiency mode: EcoQoS + IDLE_PRIORITY_CLASS.
     * The process will use reduced CPU frequency, route to E-cores on hybrid CPUs,
     * and show the green leaf icon in Windows 11 22H2+ Task Manager.
     */
    fun enableEfficiencyMode(): Result {
        if (!isWindows) return Result(false, -1, "Not supported on this platform")
        if (!NativeEnergyManagerBridge.isLoaded) {
            return Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeEnergyManagerBridge.nativeEnableEfficiencyMode()
            if (rc == 0) {
                Result(true)
            } else {
                Result(false, rc, "Native call failed with error code $rc")
            }
        } catch (e: UnsatisfiedLinkError) {
            Result(false, -1, "Exception: ${e.message}")
        }
    }

    /**
     * Disables efficiency mode: resets to default QoS + NORMAL_PRIORITY_CLASS.
     */
    fun disableEfficiencyMode(): Result {
        if (!isWindows) return Result(false, -1, "Not supported on this platform")
        if (!NativeEnergyManagerBridge.isLoaded) {
            return Result(false, -1, "Native library not loaded")
        }
        return try {
            val rc = NativeEnergyManagerBridge.nativeDisableEfficiencyMode()
            if (rc == 0) {
                Result(true)
            } else {
                Result(false, rc, "Native call failed with error code $rc")
            }
        } catch (e: UnsatisfiedLinkError) {
            Result(false, -1, "Exception: ${e.message}")
        }
    }
}
