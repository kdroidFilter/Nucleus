package io.github.kdroidfilter.nucleus.nativessl.windows

import io.github.kdroidfilter.nucleus.nativessl.debugln
import java.security.KeyStore

private const val TAG = "WindowsCertificateProvider"

private val WINDOWS_STORES = listOf(
    "Windows-ROOT",  // Trusted Root Certification Authorities
    "Windows-MY",    // Personal certificates (user-installed)
)

internal object WindowsCertificateProvider {

    fun getSystemCertificates(): List<ByteArray> {
        val seen = mutableSetOf<String>()
        val allCerts = mutableListOf<ByteArray>()

        for (storeName in WINDOWS_STORES) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val keyStore = KeyStore.getInstance(storeName)
                keyStore.load(null, null)

                var count = 0
                for (alias in keyStore.aliases()) {
                    val cert = keyStore.getCertificate(alias) ?: continue
                    val der = cert.encoded
                    val fingerprint = java.util.Base64.getEncoder().encodeToString(der)
                    if (seen.add(fingerprint)) {
                        allCerts.add(der)
                        count++
                    }
                }
                debugln(TAG) { "Loaded $count certificates from $storeName" }
            } catch (e: Exception) {
                debugln(TAG) { "Could not read store $storeName: ${e.message}" }
            }
        }

        if (allCerts.isEmpty()) {
            debugln(TAG) { "No system certificates found on Windows" }
        } else {
            debugln(TAG) { "Total: ${allCerts.size} unique certificates" }
        }
        return allCerts
    }
}
