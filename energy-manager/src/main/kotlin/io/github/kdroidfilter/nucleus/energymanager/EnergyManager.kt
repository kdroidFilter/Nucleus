package io.github.kdroidfilter.nucleus.energymanager

import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.energymanager.linux.LinuxEnergyManager
import io.github.kdroidfilter.nucleus.energymanager.macos.MacOsEnergyManager
import io.github.kdroidfilter.nucleus.energymanager.windows.WindowsEnergyManager
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Manages process-level and thread-level energy efficiency mode.
 *
 * Windows: EcoQoS + IDLE_PRIORITY_CLASS (green leaf in Task Manager).
 * macOS: setpriority(PRIO_DARWIN_BG) + task_policy_set(TIER_5).
 * Linux: nice +19, ioprio IDLE, timerslack 100ms — reversible without root.
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

    private val unsupported = Result(false, -1, "Not supported on this platform")

    /**
     * Returns true if the energy efficiency API is available on this platform.
     */
    fun isAvailable(): Boolean =
        when (Platform.Current) {
            Platform.Windows -> WindowsEnergyManager.isAvailable()
            Platform.MacOS -> MacOsEnergyManager.isAvailable()
            Platform.Linux -> LinuxEnergyManager.isAvailable()
            else -> false
        }

    /**
     * Enables efficiency mode for the current process.
     */
    fun enableEfficiencyMode(): Result =
        when (Platform.Current) {
            Platform.Windows -> WindowsEnergyManager.enable()
            Platform.MacOS -> MacOsEnergyManager.enable()
            Platform.Linux -> LinuxEnergyManager.enable()
            else -> unsupported
        }

    /**
     * Disables efficiency mode, restoring default OS scheduling.
     */
    fun disableEfficiencyMode(): Result =
        when (Platform.Current) {
            Platform.Windows -> WindowsEnergyManager.disable()
            Platform.MacOS -> MacOsEnergyManager.disable()
            Platform.Linux -> LinuxEnergyManager.disable()
            else -> unsupported
        }

    /**
     * Enables efficiency mode for the calling thread only.
     *
     * Linux: fully supported (nice, ioprio, timerslack are per-thread).
     * macOS/Windows: no-op (returns success).
     */
    fun enableThreadEfficiencyMode(): Result =
        when (Platform.Current) {
            Platform.Linux -> LinuxEnergyManager.enableThread()
            Platform.MacOS -> MacOsEnergyManager.enableThread()
            Platform.Windows -> WindowsEnergyManager.enableThread()
            else -> unsupported
        }

    /**
     * Disables efficiency mode for the calling thread, restoring defaults.
     *
     * Linux: fully supported.
     * macOS/Windows: no-op (returns success).
     */
    fun disableThreadEfficiencyMode(): Result =
        when (Platform.Current) {
            Platform.Linux -> LinuxEnergyManager.disableThread()
            Platform.MacOS -> MacOsEnergyManager.disableThread()
            Platform.Windows -> WindowsEnergyManager.disableThread()
            else -> unsupported
        }

    /**
     * Executes [block] on a dedicated thread with efficiency mode enabled.
     *
     * The thread is created with efficiency mode applied before [block] runs,
     * and the dispatcher is shut down after [block] completes.
     * This is safe for coroutines because the block runs on a single, pinned thread.
     *
     * ```
     * EnergyManager.withEfficiencyMode {
     *     // This code runs on a low-priority, energy-efficient thread
     *     performBackgroundWork()
     * }
     * ```
     */
    suspend fun <T> withEfficiencyMode(block: suspend () -> T): T {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "nucleus-efficient").apply { isDaemon = true }
        }
        val dispatcher = executor.asCoroutineDispatcher()
        return try {
            withContext(dispatcher) {
                enableThreadEfficiencyMode()
                try {
                    block()
                } finally {
                    disableThreadEfficiencyMode()
                }
            }
        } finally {
            dispatcher.close()
            executor.shutdown()
        }
    }
}
