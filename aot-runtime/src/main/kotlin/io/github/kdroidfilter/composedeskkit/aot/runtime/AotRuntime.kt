package io.github.kdroidfilter.composedeskkit.aot.runtime

import java.lang.management.ManagementFactory

public enum class AotRuntimeMode {
    OFF,
    TRAINING,
    RUNTIME,
}

public object AotRuntime {
    private const val MODE_PROPERTY = "composedeskkit.aot.mode"
    private const val AOT_RUNTIME_FLAG = "-XX:AOTCache="
    private const val AOT_TRAINING_FLAG = "-XX:AOTCacheOutput="

    @JvmStatic
    public fun mode(): AotRuntimeMode {
        parseModeProperty(System.getProperty(MODE_PROPERTY))?.let { return it }
        return detectMode(ManagementFactory.getRuntimeMXBean().inputArguments)
    }

    @JvmStatic
    public fun isRuntime(): Boolean = mode() == AotRuntimeMode.RUNTIME

    @JvmStatic
    public fun isTraining(): Boolean = mode() == AotRuntimeMode.TRAINING

    internal fun detectMode(inputArguments: List<String>): AotRuntimeMode =
        when {
            inputArguments.any { it.startsWith(AOT_TRAINING_FLAG) } -> AotRuntimeMode.TRAINING
            inputArguments.any { it.startsWith(AOT_RUNTIME_FLAG) } -> AotRuntimeMode.RUNTIME
            else -> AotRuntimeMode.OFF
        }

    internal fun parseModeProperty(rawValue: String?): AotRuntimeMode? =
        when (rawValue?.trim()?.lowercase()) {
            null, "" -> null
            "train", "training" -> AotRuntimeMode.TRAINING
            "runtime", "run", "on", "use", "enabled" -> AotRuntimeMode.RUNTIME
            "off", "disabled", "none" -> AotRuntimeMode.OFF
            else -> null
        }
}
