/**
 * JNI bridge for macOS energy efficiency mode.
 *
 * Provides native implementations for:
 *   - Checking if macOS energy APIs are available (always true on supported JDK)
 *   - Enabling efficiency mode (PRIO_DARWIN_BG + task_policy_set TIER_5)
 *   - Disabling efficiency mode (restore default priority and QoS tiers)
 *
 * PRIO_DARWIN_BG is the "master switch": a single syscall activates
 * CPU low priority, I/O throttling, network throttling, and E-core
 * confinement on Apple Silicon.
 *
 * task_policy_set with LATENCY_QOS_TIER_5 / THROUGHPUT_QOS_TIER_5
 * reinforces the signal via Mach task-level QoS parameters.
 *
 * No special privileges are required — any process can put itself
 * in background mode.
 *
 * Linked libraries: libSystem (automatic)
 */

#include <jni.h>
#include <sys/resource.h>
#include <mach/mach.h>
#include <mach/task_policy.h>
#include <errno.h>

/* Fallback definitions if headers do not provide these constants */
#ifndef PRIO_DARWIN_PROCESS
#define PRIO_DARWIN_PROCESS 4
#endif
#ifndef PRIO_DARWIN_BG
#define PRIO_DARWIN_BG 0x1000
#endif

/* ---- nativeIsSupported ------------------------------------------- */

JNIEXPORT jboolean JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_macos_NativeMacOsEnergyBridge_nativeIsSupported(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    /*
     * setpriority exists since macOS 10.5, QoS since 10.10.
     * Any macOS version supported by a modern JDK has these APIs.
     */
    return JNI_TRUE;
}

/* ---- nativeEnableEfficiencyMode ---------------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_macos_NativeMacOsEnergyBridge_nativeEnableEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    int result = 0;

    /*
     * 1. Background mode via PRIO_DARWIN_BG
     *    -> CPU MAXPRI_THROTTLE, I/O throttle, net throttle,
     *       E-cores only on Apple Silicon
     */
    errno = 0;
    if (setpriority(PRIO_DARWIN_PROCESS, 0, PRIO_DARWIN_BG) != 0) {
        result = errno;
    }

    /*
     * 2. Latency/throughput tiers at maximum efficiency (optional,
     *    reinforces the signal via the Mach task subsystem)
     */
    struct task_qos_policy qos;
    qos.task_latency_qos_tier    = LATENCY_QOS_TIER_5;
    qos.task_throughput_qos_tier = THROUGHPUT_QOS_TIER_5;
    task_policy_set(mach_task_self(),
                    TASK_BASE_QOS_POLICY,
                    (task_policy_t)&qos,
                    TASK_QOS_POLICY_COUNT);
    /* Non-fatal — PRIO_DARWIN_BG is the primary mechanism */

    return (jint)result;
}

/* ---- nativeDisableEfficiencyMode --------------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_macos_NativeMacOsEnergyBridge_nativeDisableEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    int result = 0;

    /* Remove background mode */
    errno = 0;
    if (setpriority(PRIO_DARWIN_PROCESS, 0, 0) != 0) {
        result = errno;
    }

    /* Reset tiers to "unspecified" (let the system decide) */
    struct task_qos_policy qos;
    qos.task_latency_qos_tier    = LATENCY_QOS_TIER_UNSPECIFIED;
    qos.task_throughput_qos_tier = THROUGHPUT_QOS_TIER_UNSPECIFIED;
    task_policy_set(mach_task_self(),
                    TASK_BASE_QOS_POLICY,
                    (task_policy_t)&qos,
                    TASK_QOS_POLICY_COUNT);

    return (jint)result;
}
