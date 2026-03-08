/**
 * JNI bridge for Linux energy efficiency mode.
 *
 * Provides native implementations for:
 *   - Checking if Linux energy APIs are available (compile-time check)
 *   - Enabling efficiency mode (nice +19, timer slack 100ms, ioprio IDLE)
 *   - Disabling efficiency mode (restore defaults)
 *
 * Three independent mechanisms are combined:
 *   1. setpriority(PRIO_PROCESS) — CPU scheduling nice value
 *   2. prctl(PR_SET_TIMERSLACK)  — timer coalescing (equivalent to
 *      IGNORE_TIMER_RESOLUTION on Windows)
 *   3. ioprio_set(IOPRIO_CLASS_IDLE) — I/O scheduling class
 *
 * All three are reversible without root on any mainstream distribution.
 * SCHED_IDLE is intentionally NOT used (not reversible without
 * CAP_SYS_NICE when RLIMIT_NICE = 0, the default).
 *
 * No special privileges are required.
 *
 * Linked libraries: libc (automatic)
 */

#include <jni.h>
#include <sys/resource.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>

/* ---- ioprio constants -------------------------------------------- */

#define IOPRIO_WHO_PROCESS 1
#define IOPRIO_CLASS_IDLE  3
#define IOPRIO_CLASS_SHIFT 13

/* ---- nativeIsSupported ------------------------------------------- */

JNIEXPORT jboolean JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_linux_NativeLinuxEnergyBridge_nativeIsSupported(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
#ifdef SYS_ioprio_set
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

/* ---- nativeEnableEfficiencyMode ---------------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_linux_NativeLinuxEnergyBridge_nativeEnableEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    int first_error = 0;

    /* 1. CPU nice +19 */
    errno = 0;
    if (setpriority(PRIO_PROCESS, 0, 19) != 0) {
        first_error = errno;
    }

    /* 2. Timer slack 100 ms (coalescing) */
    if (prctl(PR_SET_TIMERSLACK, (unsigned long)100000000L, 0, 0, 0) != 0) {
        if (first_error == 0) first_error = errno;
    }

    /* 3. I/O scheduling class IDLE */
#ifdef SYS_ioprio_set
    if (syscall(SYS_ioprio_set, IOPRIO_WHO_PROCESS, 0,
                (IOPRIO_CLASS_IDLE << IOPRIO_CLASS_SHIFT) | 0) != 0) {
        if (first_error == 0) first_error = errno;
    }
#endif

    return (jint)first_error;
}

/* ---- nativeEnableThreadEfficiencyMode ---------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_linux_NativeLinuxEnergyBridge_nativeEnableThreadEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    int first_error = 0;

    /* 1. CPU nice +19 (per-thread on Linux — each thread is a schedulable entity) */
    errno = 0;
    if (setpriority(PRIO_PROCESS, 0, 19) != 0) {
        first_error = errno;
    }

    /* 2. Timer slack 100 ms (always per-thread) */
    if (prctl(PR_SET_TIMERSLACK, (unsigned long)100000000L, 0, 0, 0) != 0) {
        if (first_error == 0) first_error = errno;
    }

    /* 3. I/O scheduling class IDLE (per-thread with IOPRIO_WHO_PROCESS + tid 0) */
#ifdef SYS_ioprio_set
    if (syscall(SYS_ioprio_set, IOPRIO_WHO_PROCESS, 0,
                (IOPRIO_CLASS_IDLE << IOPRIO_CLASS_SHIFT) | 0) != 0) {
        if (first_error == 0) first_error = errno;
    }
#endif

    return (jint)first_error;
}

/* ---- nativeDisableThreadEfficiencyMode --------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_linux_NativeLinuxEnergyBridge_nativeDisableThreadEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    int first_error = 0;

    /* 1. CPU nice reset to 0 — may fail with EACCES without CAP_SYS_NICE,
     *    which is expected: thread-level mode is meant to be used with
     *    withEfficiencyMode() where the thread is discarded afterward. */
    errno = 0;
    if (setpriority(PRIO_PROCESS, 0, 0) != 0 && errno != EACCES) {
        first_error = errno;
    }

    /* 2. Timer slack reset to thread default */
    if (prctl(PR_SET_TIMERSLACK, 0L, 0, 0, 0) != 0) {
        if (first_error == 0) first_error = errno;
    }

    /* 3. I/O scheduling class reset to default */
#ifdef SYS_ioprio_set
    if (syscall(SYS_ioprio_set, IOPRIO_WHO_PROCESS, 0, 0) != 0) {
        if (first_error == 0) first_error = errno;
    }
#endif

    return (jint)first_error;
}

/* ---- nativeDisableEfficiencyMode --------------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_linux_NativeLinuxEnergyBridge_nativeDisableEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    int first_error = 0;

    /* 1. CPU nice reset to 0 */
    errno = 0;
    if (setpriority(PRIO_PROCESS, 0, 0) != 0) {
        first_error = errno;
    }

    /* 2. Timer slack reset to thread default */
    if (prctl(PR_SET_TIMERSLACK, 0L, 0, 0, 0) != 0) {
        if (first_error == 0) first_error = errno;
    }

    /* 3. I/O scheduling class reset to default */
#ifdef SYS_ioprio_set
    if (syscall(SYS_ioprio_set, IOPRIO_WHO_PROCESS, 0, 0) != 0) {
        if (first_error == 0) first_error = errno;
    }
#endif

    return (jint)first_error;
}
