package io.github.kdroidfilter.nucleus.nativessl

import io.github.kdroidfilter.nucleus.nativessl.linux.LinuxCertificateProvider
import io.github.kdroidfilter.nucleus.nativessl.mac.NativeSslBridge
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private const val TAG = "NativeCertificateProvider"

internal object NativeCertificateProvider {
    private val os: String = System.getProperty("os.name", "").lowercase()

    fun getSystemCertificates(): List<X509Certificate> {
        val derCerts = getRawCertificates()
        if (derCerts.isEmpty()) return emptyList()

        val factory = CertificateFactory.getInstance("X.509")
        return derCerts.mapNotNull { der ->
            @Suppress("TooGenericExceptionCaught")
            try {
                factory.generateCertificate(der.inputStream()) as X509Certificate
            } catch (e: Exception) {
                debugln(TAG) { "Skipping unparseable certificate: ${e.message}" }
                null
            }
        }
    }

    private fun getRawCertificates(): List<ByteArray> =
        when {
            os.contains("mac") || os.contains("darwin") -> NativeSslBridge.getSystemCertificates()
            os.contains("linux") -> LinuxCertificateProvider.getSystemCertificates()
            else -> emptyList()
        }
}
