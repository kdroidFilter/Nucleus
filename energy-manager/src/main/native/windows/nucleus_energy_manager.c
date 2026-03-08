/**
 * JNI bridge for Windows energy efficiency mode (EcoQoS).
 *
 * Provides native implementations for:
 *   - Checking if EcoQoS is supported (SetProcessInformation available)
 *   - Enabling efficiency mode (EcoQoS + IDLE_PRIORITY_CLASS)
 *   - Disabling efficiency mode (reset to default QoS + NORMAL_PRIORITY_CLASS)
 *
 * SetProcessInformation is resolved via GetProcAddress for runtime
 * compatibility with older Windows versions where it may not exist.
 *
 * Linked libraries: kernel32.lib
 */

#include <jni.h>
#include <windows.h>

/* ---- /NODEFAULTLIB stubs ----------------------------------------- */

#pragma function(memset)
void *memset(void *dest, int c, size_t count) {
    unsigned char *p = (unsigned char *)dest;
    while (count--) *p++ = (unsigned char)c;
    return dest;
}

int _fltused = 0;

/* ---- DllMain ----------------------------------------------------- */

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    (void)hinstDLL; (void)fdwReason; (void)lpvReserved;
    return TRUE;
}

/* ---- Fallback definitions for older SDK versions ----------------- */

#ifndef PROCESS_POWER_THROTTLING_CURRENT_VERSION
#define PROCESS_POWER_THROTTLING_CURRENT_VERSION 1
#endif
#ifndef PROCESS_POWER_THROTTLING_EXECUTION_SPEED
#define PROCESS_POWER_THROTTLING_EXECUTION_SPEED 0x1
#endif
#ifndef PROCESS_POWER_THROTTLING_IGNORE_TIMER_RESOLUTION
#define PROCESS_POWER_THROTTLING_IGNORE_TIMER_RESOLUTION 0x2
#endif

/* ProcessPowerThrottling = 4 in PROCESS_INFORMATION_CLASS enum */
#define MY_ProcessPowerThrottling 4

typedef struct {
    ULONG Version;
    ULONG ControlMask;
    ULONG StateMask;
} MY_PROCESS_POWER_THROTTLING_STATE;

typedef BOOL (WINAPI *PFN_SetProcessInformation)(
    HANDLE hProcess,
    int    ProcessInformationClass,
    LPVOID ProcessInformation,
    DWORD  ProcessInformationSize
);

static PFN_SetProcessInformation pfnSetProcessInfo = NULL;
static BOOL pfnResolved = FALSE;

static PFN_SetProcessInformation ResolveFn(void) {
    if (!pfnResolved) {
        HMODULE hK32 = GetModuleHandleW(L"kernel32.dll");
        if (hK32) {
            pfnSetProcessInfo = (PFN_SetProcessInformation)
                GetProcAddress(hK32, "SetProcessInformation");
        }
        pfnResolved = TRUE;
    }
    return pfnSetProcessInfo;
}

/* ---- nativeIsSupported ------------------------------------------- */

JNIEXPORT jboolean JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_NativeEnergyManagerBridge_nativeIsSupported(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    return ResolveFn() != NULL ? JNI_TRUE : JNI_FALSE;
}

/* ---- nativeEnableEfficiencyMode ---------------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_NativeEnergyManagerBridge_nativeEnableEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;

    PFN_SetProcessInformation pfn = ResolveFn();
    if (!pfn) return (jint)127; /* ERROR_PROC_NOT_FOUND */

    /* 1. Enable EcoQoS */
    MY_PROCESS_POWER_THROTTLING_STATE state;
    memset(&state, 0, sizeof(state));
    state.Version     = PROCESS_POWER_THROTTLING_CURRENT_VERSION;
    state.ControlMask = PROCESS_POWER_THROTTLING_EXECUTION_SPEED
                      | PROCESS_POWER_THROTTLING_IGNORE_TIMER_RESOLUTION;
    state.StateMask   = PROCESS_POWER_THROTTLING_EXECUTION_SPEED
                      | PROCESS_POWER_THROTTLING_IGNORE_TIMER_RESOLUTION;

    if (!pfn(GetCurrentProcess(), MY_ProcessPowerThrottling,
             &state, sizeof(state))) {
        return (jint)GetLastError();
    }

    /* 2. Set IDLE_PRIORITY_CLASS for green leaf icon */
    if (!SetPriorityClass(GetCurrentProcess(), IDLE_PRIORITY_CLASS)) {
        return (jint)GetLastError();
    }

    return 0;
}

/* ---- nativeDisableEfficiencyMode --------------------------------- */

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_energymanager_NativeEnergyManagerBridge_nativeDisableEfficiencyMode(
    JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;

    PFN_SetProcessInformation pfn = ResolveFn();
    if (!pfn) return (jint)127;

    /* 1. Disable EcoQoS (request HighQoS) */
    MY_PROCESS_POWER_THROTTLING_STATE state;
    memset(&state, 0, sizeof(state));
    state.Version     = PROCESS_POWER_THROTTLING_CURRENT_VERSION;
    state.ControlMask = PROCESS_POWER_THROTTLING_EXECUTION_SPEED
                      | PROCESS_POWER_THROTTLING_IGNORE_TIMER_RESOLUTION;
    state.StateMask   = 0; /* ControlMask set but StateMask = 0 -> HighQoS */

    if (!pfn(GetCurrentProcess(), MY_ProcessPowerThrottling,
             &state, sizeof(state))) {
        return (jint)GetLastError();
    }

    /* 2. Restore NORMAL_PRIORITY_CLASS */
    if (!SetPriorityClass(GetCurrentProcess(), NORMAL_PRIORITY_CLASS)) {
        return (jint)GetLastError();
    }

    return 0;
}
