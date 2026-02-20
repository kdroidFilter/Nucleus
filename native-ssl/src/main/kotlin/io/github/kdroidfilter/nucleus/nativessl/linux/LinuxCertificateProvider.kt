package io.github.kdroidfilter.nucleus.nativessl.linux

import io.github.kdroidfilter.nucleus.nativessl.debugln
import java.io.File
import java.util.Base64

private const val TAG = "LinuxCertificateProvider"

private val BUNDLE_PATHS = listOf(
    "/etc/ssl/certs/ca-certificates.crt",   // Debian, Ubuntu, Arch
    "/etc/pki/tls/certs/ca-bundle.crt",     // Fedora, RHEL, CentOS
    "/etc/ssl/ca-bundle.pem",               // openSUSE
    "/etc/pki/tls/cacert.pem",              // OpenELEC
)

private val USER_CERT_DIRS = listOf(
    "/usr/local/share/ca-certificates",     // User/admin-installed certs (Debian/Ubuntu)
    "/etc/pki/ca-trust/source/anchors",     // User/admin-installed certs (Fedora/RHEL)
)

private const val PEM_BEGIN = "-----BEGIN CERTIFICATE-----"
private const val PEM_END = "-----END CERTIFICATE-----"

internal object LinuxCertificateProvider {

    fun getSystemCertificates(): List<ByteArray> {
        val seen = mutableSetOf<String>()
        val allCerts = mutableListOf<ByteArray>()

        // 1. Read system bundle (standard distro CA certs)
        for (path in BUNDLE_PATHS) {
            val file = File(path)
            if (file.isFile && file.canRead()) {
                debugln(TAG) { "Reading certificate bundle: $path" }
                val certs = parsePemBundle(file.readText())
                for (der in certs) {
                    val key = Base64.getEncoder().encodeToString(der)
                    if (seen.add(key)) allCerts.add(der)
                }
                if (certs.isNotEmpty()) {
                    debugln(TAG) { "Loaded ${certs.size} certificates from $path" }
                    break
                }
            }
        }

        // 2. Also scan user/admin-installed certificate directories
        for (dirPath in USER_CERT_DIRS) {
            readCertsFromDirectory(File(dirPath), seen, allCerts)
        }

        if (allCerts.isEmpty()) {
            debugln(TAG) { "No system certificates found on this Linux distribution" }
        } else {
            debugln(TAG) { "Total: ${allCerts.size} unique certificates" }
        }
        return allCerts
    }

    private fun readCertsFromDirectory(
        dir: File,
        seen: MutableSet<String>,
        out: MutableList<ByteArray>,
    ) {
        if (!dir.isDirectory || !dir.canRead()) return
        val files = dir.listFiles() ?: return
        var count = 0
        for (file in files) {
            if (file.isDirectory) {
                readCertsFromDirectory(file, seen, out)
                continue
            }
            if (file.extension !in listOf("pem", "crt")) continue
            for (der in parsePemBundle(file.readText())) {
                val key = Base64.getEncoder().encodeToString(der)
                if (seen.add(key)) {
                    out.add(der)
                    count++
                }
            }
        }
        if (count > 0) {
            debugln(TAG) { "Loaded $count additional certificates from ${dir.path}" }
        }
    }

    private fun parsePemBundle(pem: String): List<ByteArray> {
        val certs = mutableListOf<ByteArray>()
        var inCert = false
        val base64 = StringBuilder()

        for (line in pem.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed == PEM_BEGIN -> {
                    inCert = true
                    base64.setLength(0)
                }
                trimmed == PEM_END -> {
                    inCert = false
                    if (base64.isNotEmpty()) {
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            certs.add(Base64.getDecoder().decode(base64.toString()))
                        } catch (e: Exception) {
                            debugln(TAG) { "Skipping malformed PEM block: ${e.message}" }
                        }
                    }
                }
                inCert -> base64.append(trimmed)
            }
        }
        return certs
    }
}
