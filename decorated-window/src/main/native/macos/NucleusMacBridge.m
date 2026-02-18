#import <Cocoa/Cocoa.h>
#include <jni.h>

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_NativeMacBridge_nativeUpdateColors(
    JNIEnv *env, jclass clazz, jlong nsWindowPtr) {
    if (nsWindowPtr == 0) return;
    @autoreleasepool {
        NSWindow *window = (__bridge NSWindow *)(void *)nsWindowPtr;
        id delegate = [window delegate];
        if (delegate && [delegate respondsToSelector:@selector(updateColors)]) {
            [delegate performSelector:@selector(updateColors)];
        }
    }
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_NativeMacBridge_nativeUpdateFullScreenButtons(
    JNIEnv *env, jclass clazz, jlong nsWindowPtr) {
    if (nsWindowPtr == 0) return;
    @autoreleasepool {
        NSWindow *window = (__bridge NSWindow *)(void *)nsWindowPtr;
        id delegate = [window delegate];
        if (delegate && [delegate respondsToSelector:@selector(updateFullScreenButtons)]) {
            [delegate performSelector:@selector(updateFullScreenButtons)];
        }
    }
}
