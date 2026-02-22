#import <Cocoa/Cocoa.h>
#import <UserNotifications/UserNotifications.h>
#import <jni.h>

static JavaVM *g_jvm = NULL;

static NSMutableDictionary *g_notifications = nil;
static NSObject *g_callbackLock = nil;
static jobject g_clickedCallbackObj = NULL;
static jobject g_closedCallbackObj = NULL;
static jobject g_buttonCallbackObj = NULL;
static jmethodID g_clickedMethod = NULL;
static jmethodID g_closedMethod = NULL;
static jmethodID g_buttonMethod = NULL;
static BOOL g_isBundleValid = NO;
static BOOL g_notificationsAvailable = NO;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    g_notifications = [[NSMutableDictionary alloc] init];
    g_callbackLock = [[NSObject alloc] init];
    
    NSBundle *mainBundle = [NSBundle mainBundle];
    if (mainBundle && mainBundle.bundleURL) {
        NSString *bundlePath = mainBundle.bundleURL.path;
        if (bundlePath && bundlePath.length > 0 && ![bundlePath hasSuffix:@"/bin"]) {
            g_isBundleValid = YES;
        }
    }
    
    return JNI_VERSION_1_8;
}

@interface NotificationCenterDelegate : NSObject <UNUserNotificationCenterDelegate>
@end

@implementation NotificationCenterDelegate

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       didReceiveNotificationResponse:(UNNotificationResponse *)response
                withCompletionHandler:(void (^)(void))completionHandler {
    NSString *identifier = response.notification.request.identifier;
    NSDictionary *notifDict = nil;
    
    @synchronized(g_callbackLock) {
        notifDict = g_notifications[identifier];
    }
    
    if (notifDict && g_jvm) {
        JNIEnv *env = NULL;
        (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_8);
        
        if (env) {
            NSString *actionId = response.actionIdentifier;
            NSNumber *ptrNum = notifDict[@"ptr"];
            jlong ptr = [ptrNum longValue];
            
            if ([actionId isEqualToString:UNNotificationDefaultActionIdentifier]) {
                if (g_clickedCallbackObj && g_clickedMethod) {
                    (*env)->CallVoidMethod(env, g_clickedCallbackObj, g_clickedMethod, ptr, (jlong)NULL);
                }
            } else if (g_buttonCallbackObj && g_buttonMethod) {
                const char *buttonIdChars = [actionId UTF8String];
                jstring buttonIdStr = (*env)->NewStringUTF(env, buttonIdChars);
                (*env)->CallVoidMethod(env, g_buttonCallbackObj, g_buttonMethod, ptr, buttonIdStr, (jlong)NULL);
            }
        }
    }
    
    completionHandler();
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
       withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    if (@available(macOS 11.0, *)) {
        completionHandler(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionSound);
    } else {
        completionHandler(UNNotificationPresentationOptionAlert | UNNotificationPresentationOptionSound);
    }
}

@end

static NotificationCenterDelegate *g_delegate = NULL;

static void ensureDelegate() {
    if (!g_isBundleValid) {
        return;
    }
    
    if (g_delegate == NULL) {
        @try {
            g_delegate = [[NotificationCenterDelegate alloc] init];
            [[UNUserNotificationCenter currentNotificationCenter] setDelegate:g_delegate];
            
            [[UNUserNotificationCenter currentNotificationCenter] requestAuthorizationWithOptions:(UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge)
                completionHandler:^(BOOL granted, NSError * _Nullable error) {
                    if (error) {
                        NSLog(@"Notification authorization error: %@", error);
                    }
                    g_notificationsAvailable = granted;
                }];
            g_notificationsAvailable = YES;
        } @catch (NSException *exception) {
            NSLog(@"Exception initializing notifications: %@", exception);
            g_notificationsAvailable = NO;
        }
    }
}

JNIEXPORT jlong JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_createNotification(
    JNIEnv *env, jclass clazz, jstring jtitle, jstring jbody, jstring jiconPath) {

    if (!g_isBundleValid) {
        NSLog(@"Notifications require running from an app bundle, not from bin directory");
        return -1;
    }

    ensureDelegate();

    if (!g_notificationsAvailable) {
        return -1;
    }

    const char *titleChars = jtitle ? (*env)->GetStringUTFChars(env, jtitle, NULL) : "";
    const char *bodyChars = jbody ? (*env)->GetStringUTFChars(env, jbody, NULL) : "";
    
    NSString *titleStr = [NSString stringWithUTF8String:titleChars ? titleChars : ""];
    NSString *bodyStr = [NSString stringWithUTF8String:bodyChars ? bodyChars : ""];

    if (jtitle) (*env)->ReleaseStringUTFChars(env, jtitle, titleChars);
    if (jbody) (*env)->ReleaseStringUTFChars(env, jbody, bodyChars);

    NSUUID *uuid = [NSUUID UUID];
    NSString *identifier = [uuid UUIDString];
    jlong ptrValue = (jlong)(intptr_t)arc4random();

    NSDictionary *notifDict = @{
        @"title": titleStr ?: @"",
        @"body": bodyStr ?: @"",
        @"identifier": identifier,
        @"ptr": @(ptrValue)
    };

    @synchronized(g_callbackLock) {
        g_notifications[identifier] = notifDict;
    }

    return ptrValue;
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_addButtonToNotification(
    JNIEnv *env, jclass clazz, jlong notificationPtr, jstring jbuttonId, jstring jbuttonLabel) {
    const char *buttonIdChars = jbuttonId ? (*env)->GetStringUTFChars(env, jbuttonId, NULL) : "";
    const char *buttonLabelChars = jbuttonLabel ? (*env)->GetStringUTFChars(env, jbuttonLabel, NULL) : "";
    
    if (jbuttonId) (*env)->ReleaseStringUTFChars(env, jbuttonId, buttonIdChars);
    if (jbuttonLabel) (*env)->ReleaseStringUTFChars(env, jbuttonLabel, buttonLabelChars);
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_setNotificationClickedCallback(
    JNIEnv *env, jclass clazz, jlong notificationPtr, jobject callback) {

    if (g_clickedCallbackObj) {
        (*env)->DeleteGlobalRef(env, g_clickedCallbackObj);
        g_clickedCallbackObj = NULL;
    }

    if (callback) {
        g_clickedCallbackObj = (*env)->NewGlobalRef(env, callback);
        g_clickedMethod = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, callback), "invoke", "(JJ)V");
    }
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_setNotificationClosedCallback(
    JNIEnv *env, jclass clazz, jlong notificationPtr, jobject callback) {

    if (g_closedCallbackObj) {
        (*env)->DeleteGlobalRef(env, g_closedCallbackObj);
        g_closedCallbackObj = NULL;
    }

    if (callback) {
        g_closedCallbackObj = (*env)->NewGlobalRef(env, callback);
        g_closedMethod = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, callback), "invoke", "(JJ)V");
    }
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_setNotificationImage(
    JNIEnv *env, jclass clazz, jlong notificationPtr, jstring jimagePath) {
}

JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_sendNotification(
    JNIEnv *env, jclass clazz, jlong notificationPtr) {

    if (!g_isBundleValid || !g_notificationsAvailable) {
        return -1;
    }

    __block NSString *foundIdentifier = nil;

    @synchronized(g_callbackLock) {
        for (NSString *key in g_notifications) {
            NSDictionary *dict = g_notifications[key];
            if ([[dict[@"ptr"] stringValue] isEqualToString:[NSString stringWithFormat:@"%ld", (long)notificationPtr]]) {
                foundIdentifier = key;
                break;
            }
        }
    }

    if (!foundIdentifier) {
        return -1;
    }

    NSDictionary *notifDict = nil;
    @synchronized(g_callbackLock) {
        notifDict = g_notifications[foundIdentifier];
    }

    UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
    content.title = notifDict[@"title"];
    content.body = notifDict[@"body"];

    UNNotificationTrigger *trigger = [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:0.1 repeats:NO];
    UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:foundIdentifier
                                                                          content:content
                                                                          trigger:trigger];

    @try {
        [[UNUserNotificationCenter currentNotificationCenter] addNotificationRequest:request
            withCompletionHandler:^(NSError * _Nullable error) {
                if (error) {
                    NSLog(@"Error sending notification: %@", error);
                }
            }];
    } @catch (NSException *exception) {
        NSLog(@"Exception sending notification: %@", exception);
        return -1;
    }

    return 0;
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_hideNotification(
    JNIEnv *env, jclass clazz, jlong notificationPtr) {

    if (!g_isBundleValid || !g_notificationsAvailable) {
        return;
    }

    __block NSString *foundIdentifier = nil;

    @synchronized(g_callbackLock) {
        for (NSString *key in g_notifications) {
            NSDictionary *dict = g_notifications[key];
            if ([[dict[@"ptr"] stringValue] isEqualToString:[NSString stringWithFormat:@"%ld", (long)notificationPtr]]) {
                foundIdentifier = key;
                break;
            }
        }
    }

    if (foundIdentifier) {
        @try {
            [[UNUserNotificationCenter currentNotificationCenter] removeDeliveredNotificationsWithIdentifiers:@[foundIdentifier]];
        } @catch (NSException *exception) {
            NSLog(@"Exception hiding notification: %@", exception);
        }
        @synchronized(g_callbackLock) {
            [g_notifications removeObjectForKey:foundIdentifier];
        }
    }
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_notification_mac_NativeNotificationBridge_cleanupNotification(
    JNIEnv *env, jclass clazz, jlong notificationPtr) {

    __block NSString *foundIdentifier = nil;

    @synchronized(g_callbackLock) {
        for (NSString *key in g_notifications) {
            NSDictionary *dict = g_notifications[key];
            if ([[dict[@"ptr"] stringValue] isEqualToString:[NSString stringWithFormat:@"%ld", (long)notificationPtr]]) {
                foundIdentifier = key;
                break;
            }
        }
    }

    if (foundIdentifier) {
        @synchronized(g_callbackLock) {
            [g_notifications removeObjectForKey:foundIdentifier];
        }
    }
}
