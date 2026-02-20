#include <jni.h>
#import <Security/Security.h>
#import <CoreFoundation/CoreFoundation.h>

/**
 * Collects DER-encoded certificates from a SecTrustSettings domain
 * (user, admin) into a CFMutableArray, skipping certs marked as Deny.
 */
static void collectTrustSettingsCerts(SecTrustSettingsDomain domain,
                                      CFMutableArrayRef outCerts) {
    CFArrayRef certs = NULL;
    OSStatus status = SecTrustSettingsCopyCertificates(domain, &certs);
    if (status != errSecSuccess || certs == NULL) return;

    CFIndex count = CFArrayGetCount(certs);
    for (CFIndex i = 0; i < count; i++) {
        SecCertificateRef cert =
            (SecCertificateRef)CFArrayGetValueAtIndex(certs, i);

        // Check trust settings – skip if explicitly denied
        CFArrayRef trustSettings = NULL;
        OSStatus tsStatus =
            SecTrustSettingsCopyTrustSettings(cert, domain, &trustSettings);
        if (tsStatus == errSecSuccess && trustSettings != NULL) {
            BOOL denied = NO;
            CFIndex tsCount = CFArrayGetCount(trustSettings);
            for (CFIndex j = 0; j < tsCount; j++) {
                CFDictionaryRef entry =
                    (CFDictionaryRef)CFArrayGetValueAtIndex(trustSettings, j);
                CFNumberRef resultNum = (CFNumberRef)CFDictionaryGetValue(
                    entry, kSecTrustSettingsResult);
                if (resultNum != NULL) {
                    SInt32 result = 0;
                    CFNumberGetValue(resultNum, kCFNumberSInt32Type, &result);
                    if (result == kSecTrustSettingsResultDeny) {
                        denied = YES;
                        break;
                    }
                }
            }
            CFRelease(trustSettings);
            if (denied) continue;
        }

        CFDataRef derData = SecCertificateCopyData(cert);
        if (derData != NULL) {
            CFArrayAppendValue(outCerts, derData);
            CFRelease(derData);
        }
    }
    CFRelease(certs);
}

JNIEXPORT jobjectArray JNICALL
Java_io_github_kdroidfilter_nucleus_nativessl_mac_NativeSslBridge_nativeGetSystemCertificates(
    JNIEnv *env, jclass clazz) {

    CFMutableArrayRef allDer =
        CFArrayCreateMutable(kCFAllocatorDefault, 0, &kCFTypeArrayCallBacks);

    // 1. System root CAs (anchors shipped with macOS)
    CFArrayRef anchors = NULL;
    OSStatus status = SecTrustCopyAnchorCertificates(&anchors);
    if (status == errSecSuccess && anchors != NULL) {
        CFIndex count = CFArrayGetCount(anchors);
        for (CFIndex i = 0; i < count; i++) {
            SecCertificateRef cert =
                (SecCertificateRef)CFArrayGetValueAtIndex(anchors, i);
            CFDataRef derData = SecCertificateCopyData(cert);
            if (derData != NULL) {
                CFArrayAppendValue(allDer, derData);
                CFRelease(derData);
            }
        }
        CFRelease(anchors);
    }

    // 2. User-added certificates
    collectTrustSettingsCerts(kSecTrustSettingsDomainUser, allDer);

    // 3. Admin / MDM-deployed certificates
    collectTrustSettingsCerts(kSecTrustSettingsDomainAdmin, allDer);

    // Build Java byte[][] from the collected DER blobs
    CFIndex total = CFArrayGetCount(allDer);
    jclass byteArrayClass = (*env)->FindClass(env, "[B");
    jobjectArray result = (*env)->NewObjectArray(env, (jsize)total, byteArrayClass, NULL);
    if (result == NULL) {
        CFRelease(allDer);
        return NULL; // OOM – JVM will throw
    }

    for (CFIndex i = 0; i < total; i++) {
        CFDataRef derData = (CFDataRef)CFArrayGetValueAtIndex(allDer, i);
        CFIndex len = CFDataGetLength(derData);
        jbyteArray jBytes = (*env)->NewByteArray(env, (jsize)len);
        if (jBytes == NULL) {
            CFRelease(allDer);
            return NULL;
        }
        (*env)->SetByteArrayRegion(env, jBytes, 0, (jsize)len,
                                   (const jbyte *)CFDataGetBytePtr(derData));
        (*env)->SetObjectArrayElement(env, result, (jsize)i, jBytes);
        (*env)->DeleteLocalRef(env, jBytes);
    }

    CFRelease(allDer);
    return result;
}
