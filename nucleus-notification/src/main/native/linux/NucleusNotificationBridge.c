#include <libnotify/notify.h>
#include <glib.h>
#include <gdk-pixbuf/gdk-pixbuf.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <jni.h>

/* Global variables */
static JavaVM *g_jvm = NULL;
static GMainLoop *main_loop = NULL;
static int debug_mode = 0;

/* Global references to callbacks - stored for callback invocation */
static jobject g_clickedCallbackObj = NULL;
static jobject g_closedCallbackObj = NULL;
static jobject g_buttonCallbackObj = NULL;
static jmethodID g_clickedMethod = NULL;
static jmethodID g_closedMethod = NULL;
static jmethodID g_buttonMethod = NULL;

/* Active notifications map - maps notification ID to callback data */
static GHashTable *notifications_map = NULL;

/* Debug logging */
static void debug_log(const char *format, ...) {
    if (debug_mode) {
        va_list args;
        va_start(args, format);
        vprintf(format, args);
        va_end(args);
    }
}

/* Suppress unused parameter warnings */
#define UNUSED(x) (void)(x)

/* Get JNI environment - thread safe */
static JNIEnv* get_jni_env() {
    JNIEnv *env = NULL;
    if (g_jvm) {
        int getEnvResult = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_8);
        if (getEnvResult == JNI_EDETACHED) {
            (*g_jvm)->AttachCurrentThread(g_jvm, (void**)&env, NULL);
        }
    }
    return env;
}

/* Invoke Java callback on click */
static void invoke_clicked_callback(jlong notification_ptr) {
    if (g_clickedCallbackObj && g_clickedMethod) {
        JNIEnv *env = get_jni_env();
        if (env) {
            (*env)->CallVoidMethod(env, g_clickedCallbackObj, g_clickedMethod, notification_ptr, (jlong)NULL);
        }
    }
}

/* Invoke Java callback on close */
static void invoke_closed_callback(jlong notification_ptr) {
    if (g_closedCallbackObj && g_closedMethod) {
        JNIEnv *env = get_jni_env();
        if (env) {
            (*env)->CallVoidMethod(env, g_closedCallbackObj, g_closedMethod, notification_ptr, (jlong)NULL);
        }
    }
}

/* Invoke Java button callback - passes button label as second parameter */
static void invoke_button_callback(jlong notification_ptr, const char *button_label) {
    if (g_buttonCallbackObj && g_buttonMethod) {
        JNIEnv *env = get_jni_env();
        if (env) {
            jstring buttonStr = (*env)->NewStringUTF(env, button_label);
            (*env)->CallVoidMethod(env, g_buttonCallbackObj, g_buttonMethod, notification_ptr, buttonStr, (jlong)NULL);
            (*env)->DeleteLocalRef(env, buttonStr);
        }
    }
}

/* Button callback - calls Java button callback */
static void button_action_callback(NotifyNotification *notification, char *action, gpointer user_data) {
    jlong ptr = (jlong)(intptr_t)user_data;
    debug_log("Button clicked: %s (ptr: %ld)\n", action, (long)ptr);
    invoke_button_callback(ptr, action);
    UNUSED(notification);
}

/* Default click callback */
static void default_click_callback(NotifyNotification *notification, char *action, gpointer user_data) {
    jlong ptr = (jlong)(intptr_t)user_data;
    debug_log("Notification clicked (ptr: %ld)\n", (long)ptr);
    invoke_clicked_callback(ptr);
    UNUSED(notification);
    UNUSED(action);
}

/* Closed signal callback */
static void notification_closed_callback(NotifyNotification *notification, gpointer user_data) {
    jlong ptr = (jlong)(intptr_t)user_data;
    debug_log("Notification closed (ptr: %ld)\n", (long)ptr);
    invoke_closed_callback(ptr);
    UNUSED(notification);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;

    notifications_map = g_hash_table_new_full(g_direct_hash, g_direct_equal, NULL, NULL);

    debug_log("JNI_OnLoad: Linux Notification Bridge initialized\n");

    UNUSED(reserved);
    return JNI_VERSION_1_8;
}

/* Set debug mode */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_setDebugMode(
    JNIEnv *env, jclass clazz, jint enable) {
    UNUSED(env);
    UNUSED(clazz);
    debug_mode = enable;
    debug_log("Debug mode %s\n", enable ? "enabled" : "disabled");
}

/* Initialize notification library */
JNIEXPORT jint JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_init(
    JNIEnv *env, jclass clazz, jstring japp_name) {

    UNUSED(clazz);

    if (notify_is_initted()) {
        debug_log("Notification system already initialized\n");
        return 1;
    }

    const char *app_name = japp_name ? (*env)->GetStringUTFChars(env, japp_name, NULL) : "Nucleus";

    int result = notify_init(app_name) ? 1 : 0;

    if (japp_name) (*env)->ReleaseStringUTFChars(env, japp_name, app_name);

    if (result) {
        debug_log("Notification system initialized\n");
    } else {
        debug_log("Failed to initialize notification system\n");
    }

    return result;
}

/* Create notification */
JNIEXPORT jlong JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_createNotification(
    JNIEnv *env, jclass clazz, jstring jsummary, jstring jbody, jstring jicon_path) {

    UNUSED(clazz);

    if (!notify_is_initted()) {
        debug_log("Notification system not initialized\n");
        return -1;
    }

    const char *summary = jsummary ? (*env)->GetStringUTFChars(env, jsummary, NULL) : "";
    const char *body = jbody ? (*env)->GetStringUTFChars(env, jbody, NULL) : "";
    const char *icon_path = jicon_path ? (*env)->GetStringUTFChars(env, jicon_path, NULL) : NULL;

    debug_log("Creating notification - Summary: %s, Body: %s, Icon: %s\n",
              summary, body, icon_path ? icon_path : "none");

    NotifyNotification *notification = notify_notification_new(summary, body, icon_path);

    if (jsummary) (*env)->ReleaseStringUTFChars(env, jsummary, summary);
    if (jbody) (*env)->ReleaseStringUTFChars(env, jbody, body);
    if (jicon_path) (*env)->ReleaseStringUTFChars(env, jicon_path, icon_path);

    if (notification == NULL) {
        debug_log("Failed to create notification\n");
        return -1;
    }

    /* Generate a unique ID for this notification */
    static int notification_id = 0;
    intptr_t ptr_value = ++notification_id;

    /* Store notification pointer for callbacks */
    g_hash_table_insert(notifications_map, GINT_TO_POINTER(ptr_value), notification);

    debug_log("Notification created with ID: %ld\n", (long)ptr_value);

    return (jlong)ptr_value;
}

/* Add button to notification */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_addButtonToNotification(
    JNIEnv *env, jclass clazz, jlong notification_ptr, jstring jbutton_id, jstring jbutton_label) {

    UNUSED(clazz);

    NotifyNotification *notification = g_hash_table_lookup(notifications_map, GINT_TO_POINTER(notification_ptr));
    if (notification == NULL) {
        debug_log("Cannot add button: notification not found\n");
        return;
    }

    const char *button_id = jbutton_id ? (*env)->GetStringUTFChars(env, jbutton_id, NULL) : "";
    const char *button_label = jbutton_label ? (*env)->GetStringUTFChars(env, jbutton_label, NULL) : "";

    debug_log("Adding button - ID: %s, Label: %s\n", button_id, button_label);

    /* Use callback with user_data containing the notification ptr */
    notify_notification_add_action(notification, button_id, button_label,
                                  button_action_callback, (gpointer)notification_ptr, NULL);

    if (jbutton_id) (*env)->ReleaseStringUTFChars(env, jbutton_id, button_id);
    if (jbutton_label) (*env)->ReleaseStringUTFChars(env, jbutton_label, button_label);
}

/* Set notification clicked callback */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_setNotificationClickedCallback(
    JNIEnv *env, jclass clazz, jlong notification_ptr, jobject callback) {

    if (g_clickedCallbackObj) {
        (*env)->DeleteGlobalRef(env, g_clickedCallbackObj);
        g_clickedCallbackObj = NULL;
        g_clickedMethod = NULL;
    }

    if (callback) {
        g_clickedCallbackObj = (*env)->NewGlobalRef(env, callback);
        jclass cls = (*env)->GetObjectClass(env, callback);
        g_clickedMethod = (*env)->GetMethodID(env, cls, "invoke", "(JJ)V");
        debug_log("Clicked callback set\n");
    }

    /* Also set up the notification click action */
    NotifyNotification *notification = g_hash_table_lookup(notifications_map, GINT_TO_POINTER(notification_ptr));
    if (notification && callback) {
        notify_notification_add_action(notification, "default", "Default",
                                      default_click_callback, (gpointer)notification_ptr, NULL);
    }

    UNUSED(clazz);
}

/* Set notification closed callback */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_setNotificationClosedCallback(
    JNIEnv *env, jclass clazz, jlong notification_ptr, jobject callback) {

    if (g_closedCallbackObj) {
        (*env)->DeleteGlobalRef(env, g_closedCallbackObj);
        g_closedCallbackObj = NULL;
        g_closedMethod = NULL;
    }

    if (callback) {
        g_closedCallbackObj = (*env)->NewGlobalRef(env, callback);
        jclass cls = (*env)->GetObjectClass(env, callback);
        g_closedMethod = (*env)->GetMethodID(env, cls, "invoke", "(JJ)V");
        debug_log("Closed callback set\n");
    }

    /* Connect to closed signal */
    NotifyNotification *notification = g_hash_table_lookup(notifications_map, GINT_TO_POINTER(notification_ptr));
    if (notification && callback) {
        g_signal_connect(notification, "closed", G_CALLBACK(notification_closed_callback), (gpointer)notification_ptr);
    }

    UNUSED(clazz);
}

/* Set button callback */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_setButtonCallback(
    JNIEnv *env, jclass clazz, jobject callback) {

    if (g_buttonCallbackObj) {
        (*env)->DeleteGlobalRef(env, g_buttonCallbackObj);
        g_buttonCallbackObj = NULL;
        g_buttonMethod = NULL;
    }

    if (callback) {
        g_buttonCallbackObj = (*env)->NewGlobalRef(env, callback);
        jclass cls = (*env)->GetObjectClass(env, callback);
        g_buttonMethod = (*env)->GetMethodID(env, cls, "invoke", "(JLjava/lang/String;J)V");
        debug_log("Button callback set\n");
    }

    UNUSED(clazz);
}

/* Set notification image from file */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_setNotificationImage(
    JNIEnv *env, jclass clazz, jlong notification_ptr, jstring jimage_path) {

    UNUSED(clazz);

    NotifyNotification *notification = g_hash_table_lookup(notifications_map, GINT_TO_POINTER(notification_ptr));
    if (notification == NULL) {
        debug_log("Cannot set image: notification not found\n");
        return;
    }

    const char *image_path = jimage_path ? (*env)->GetStringUTFChars(env, jimage_path, NULL) : NULL;

    if (image_path) {
        debug_log("Loading image from: %s\n", image_path);

        GdkPixbuf *pixbuf = gdk_pixbuf_new_from_file(image_path, NULL);
        if (pixbuf != NULL) {
            notify_notification_set_image_from_pixbuf(notification, pixbuf);
            g_object_unref(pixbuf);
            debug_log("Image set successfully\n");
        } else {
            debug_log("Failed to load image: %s\n", image_path);
        }

        (*env)->ReleaseStringUTFChars(env, jimage_path, image_path);
    }
}

/* Send notification */
JNIEXPORT jint JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_sendNotification(
    JNIEnv *env, jclass clazz, jlong notification_ptr) {

    UNUSED(env);
    UNUSED(clazz);

    NotifyNotification *notification = g_hash_table_lookup(notifications_map, GINT_TO_POINTER(notification_ptr));
    if (notification == NULL) {
        debug_log("Cannot send notification: not found\n");
        return -1;
    }

    debug_log("Sending notification\n");

    GError *error = NULL;
    if (!notify_notification_show(notification, &error)) {
        debug_log("Failed to send notification: %s\n", error ? error->message : "unknown error");
        if (error) g_error_free(error);
        return -1;
    }

    debug_log("Notification sent successfully\n");
    return 0;
}

/* Close notification */
JNIEXPORT jint JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_closeNotification(
    JNIEnv *env, jclass clazz, jlong notification_ptr) {

    UNUSED(env);
    UNUSED(clazz);

    NotifyNotification *notification = g_hash_table_lookup(notifications_map, GINT_TO_POINTER(notification_ptr));
    if (notification == NULL) {
        debug_log("Cannot close notification: not found\n");
        return -1;
    }

    debug_log("Closing notification\n");

    GError *error = NULL;
    if (!notify_notification_close(notification, &error)) {
        debug_log("Failed to close notification: %s\n", error ? error->message : "unknown error");
        if (error) g_error_free(error);
        return -1;
    }

    g_hash_table_remove(notifications_map, GINT_TO_POINTER(notification_ptr));

    debug_log("Notification closed successfully\n");
    return 0;
}

/* Cleanup notification */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_cleanupNotification(
    JNIEnv *env, jclass clazz, jlong notification_ptr) {

    UNUSED(env);
    UNUSED(clazz);

    debug_log("Cleaning up notification: %ld\n", (long)notification_ptr);

    NotifyNotification *notification = g_hash_table_lookup(notifications_map, GINT_TO_POINTER(notification_ptr));
    if (notification != NULL) {
        g_object_unref(notification);
    }
    g_hash_table_remove(notifications_map, GINT_TO_POINTER(notification_ptr));
}

/* Run main loop */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_runMainLoop(
    JNIEnv *env, jclass clazz) {

    UNUSED(env);
    UNUSED(clazz);

    if (main_loop == NULL) {
        main_loop = g_main_loop_new(NULL, FALSE);
    }

    debug_log("Starting main loop\n");
    g_main_loop_run(main_loop);
}

/* Quit main loop */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_quitMainLoop(
    JNIEnv *env, jclass clazz) {

    UNUSED(env);
    UNUSED(clazz);

    debug_log("Stopping main loop\n");

    if (main_loop != NULL && g_main_loop_is_running(main_loop)) {
        g_main_loop_quit(main_loop);
        g_main_loop_unref(main_loop);
        main_loop = NULL;
    }
}

/* Cleanup all resources */
JNIEXPORT void JNICALL Java_io_github_kdroidfilter_nucleus_notification_linux_NativeNotificationBridge_cleanup(
    JNIEnv *env, jclass clazz) {

    UNUSED(clazz);

    debug_log("Cleaning up all resources\n");

    if (notifications_map) {
        g_hash_table_destroy(notifications_map);
        notifications_map = NULL;
    }

    if (main_loop) {
        if (g_main_loop_is_running(main_loop)) {
            g_main_loop_quit(main_loop);
        }
        g_main_loop_unref(main_loop);
        main_loop = NULL;
    }

    notify_uninit();

    if (g_clickedCallbackObj && env) {
        (*env)->DeleteGlobalRef(env, g_clickedCallbackObj);
        g_clickedCallbackObj = NULL;
    }
    if (g_closedCallbackObj && env) {
        (*env)->DeleteGlobalRef(env, g_closedCallbackObj);
        g_closedCallbackObj = NULL;
    }
}
